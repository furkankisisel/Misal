package com.example.misal.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.misal.domain.model.Call
import com.example.misal.domain.repository.AuthRepository
import com.example.misal.domain.repository.CallRepository
import com.example.misal.domain.webrtc.WebRtcManager
import com.example.misal.fcm.IncomingCallService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.webrtc.IceCandidate
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription
import org.webrtc.SdpObserver
import org.webrtc.RtpReceiver
import org.webrtc.MediaConstraints
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

@HiltViewModel
class CallViewModel @Inject constructor(
    private val callRepository: CallRepository,
    private val authRepository: AuthRepository,
    val webRtcManager: WebRtcManager,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    companion object {
        private const val TAG = "CallViewModel"
    }

    private val _incomingCall = MutableStateFlow<Call?>(null)
    val incomingCall: StateFlow<Call?> = _incomingCall.asStateFlow()

    private val _activeCall = MutableStateFlow<Call?>(null)
    val activeCall: StateFlow<Call?> = _activeCall.asStateFlow()

    private val _remoteStream = MutableStateFlow<MediaStream?>(null)
    val remoteStream: StateFlow<MediaStream?> = _remoteStream.asStateFlow()

    private var callDetailsJob: Job? = null
    private var iceCandidatesJob: Job? = null
    private var timeoutJob: Job? = null
    private var incomingCallListenerJob: Job? = null

    // Buffer ICE candidates until remote description is set
    private val pendingIceCandidates = mutableListOf<IceCandidate>()
    private var isRemoteDescriptionSet = false

    init {
        startIncomingCallListener()
    }

    private fun startIncomingCallListener() {
        incomingCallListenerJob?.cancel()
        incomingCallListenerJob = viewModelScope.launch {
            try {
                callRepository.getIncomingCall().collect { call ->
                    Log.d(TAG, "Incoming call update: ${call?.id}, status=${call?.status}")
                    _incomingCall.value = call
                    if (call != null) {
                        startTimeoutTimer(call.id)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Incoming call listener error, will retry in 5s", e)
                delay(5000)
                startIncomingCallListener()
            }
        }
    }

    private fun startTimeoutTimer(callId: String) {
        timeoutJob?.cancel()
        timeoutJob = viewModelScope.launch {
            delay(30000)
            if (_incomingCall.value?.id == callId || _activeCall.value?.id == callId) {
                if (_activeCall.value?.status == "ringing" || _incomingCall.value?.status == "ringing") {
                    Log.d(TAG, "Call $callId timed out")
                    callRepository.markAsMissed(callId)
                    _incomingCall.value = null
                    _activeCall.value = null
                    cleanupWebRtc()
                }
            }
        }
    }

    fun startCall(receiverId: String, type: String) {
        Log.d(TAG, "startCall: receiverId=$receiverId, type=$type")
        viewModelScope.launch {
            try {
                callRepository.startCall(receiverId, type).onSuccess { callId ->
                    Log.d(TAG, "Call created: $callId")
                    val call = Call(
                        id = callId,
                        callerId = authRepository.getCurrentUserId() ?: "",
                        receiverId = receiverId,
                        type = type,
                        status = "ringing"
                    )
                    _activeCall.value = call
                    startTimeoutTimer(callId)
                    setupPeerConnection(callId, isCaller = true, callType = type)
                }.onFailure { e ->
                    Log.e(TAG, "Failed to start call", e)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception in startCall", e)
            }
        }
    }

    fun acceptCall(call: Call) {
        Log.d(TAG, "acceptCall: ${call.id}")
        timeoutJob?.cancel()

        // Stop the IncomingCallService notification immediately
        stopIncomingCallService()

        viewModelScope.launch {
            try {
                // First update Firestore
                callRepository.acceptCall(call.id)
                Log.d(TAG, "Firestore status updated to accepted")

                _incomingCall.value = null
                _activeCall.value = call.copy(status = "accepted")

                // Setup WebRTC peer connection for the callee
                setupPeerConnection(call.id, isCaller = false, callType = call.type)

                // The offer will be handled by observeCallDetails
                // DO NOT handle it here to avoid double-processing
            } catch (e: Exception) {
                Log.e(TAG, "Exception in acceptCall", e)
            }
        }
    }

    fun rejectCall(callId: String) {
        Log.d(TAG, "rejectCall: $callId")
        stopIncomingCallService()
        viewModelScope.launch {
            callRepository.rejectCall(callId)
            _incomingCall.value = null
        }
    }

    fun endCall() {
        _activeCall.value?.id?.let { callId ->
            Log.d(TAG, "endCall: $callId")
            stopIncomingCallService()
            viewModelScope.launch {
                callRepository.endCall(callId)
                cleanupWebRtc()
                _activeCall.value = null
            }
        }
    }

    private fun stopIncomingCallService() {
        try {
            val intent = Intent(appContext, IncomingCallService::class.java).apply {
                action = "ACTION_STOP_SERVICE"
            }
            appContext.startService(intent)
            Log.d(TAG, "IncomingCallService stop requested")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop IncomingCallService", e)
        }
    }

    private fun setupPeerConnection(callId: String, isCaller: Boolean, callType: String = "audio") {
        Log.d(TAG, "setupPeerConnection: callId=$callId, isCaller=$isCaller, type=$callType")

        // Reset state for new call
        isRemoteDescriptionSet = false
        pendingIceCandidates.clear()

        webRtcManager.createPeerConnection(object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {
                Log.d(TAG, "onSignalingChange: $state")
            }
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                Log.d(TAG, "onIceConnectionChange: $state")
                if (state == PeerConnection.IceConnectionState.DISCONNECTED ||
                    state == PeerConnection.IceConnectionState.FAILED) {
                    viewModelScope.launch { endCall() }
                }
            }
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
                Log.d(TAG, "onIceGatheringChange: $state")
            }

            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate?.let {
                    Log.d(TAG, "Local ICE candidate: ${it.sdpMid}")
                    viewModelScope.launch {
                        callRepository.sendIceCandidate(callId, isCaller, it)
                    }
                }
            }
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            override fun onAddStream(stream: MediaStream?) {
                Log.d(TAG, "onAddStream: ${stream?.videoTracks?.size} video, ${stream?.audioTracks?.size} audio tracks")
                _remoteStream.value = stream
            }
            override fun onRemoveStream(stream: MediaStream?) {
                _remoteStream.value = null
            }
            override fun onDataChannel(channel: org.webrtc.DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                Log.d(TAG, "onAddTrack: ${streams?.size} streams")
            }
        })

        // Add media tracks BEFORE creating offer/answer so they are in the SDP
        webRtcManager.startLocalAudio(false)
        if (callType == "video") {
            webRtcManager.startLocalVideoCapture()
        }

        if (isCaller) {
            createOffer(callId, callType)
        }

        observeCallDetails(callId, isCaller)
        observeIceCandidates(callId, isCaller)
    }

    private fun createOffer(callId: String, callType: String) {
        Log.d(TAG, "createOffer for $callId, type=$callType")
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", if (callType == "video") "true" else "false"))
        }
        webRtcManager.peerConnection?.createOffer(object : CustomSdpObserver() {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                Log.d(TAG, "Offer created successfully")
                sdp?.let {
                    webRtcManager.peerConnection?.setLocalDescription(object : CustomSdpObserver() {
                        override fun onSetSuccess() {
                            Log.d(TAG, "Local description (offer) set")
                            viewModelScope.launch {
                                callRepository.sendOffer(callId, it)
                                Log.d(TAG, "Offer sent to Firestore")
                            }
                        }
                    }, it)
                }
            }
            override fun onCreateFailure(error: String?) {
                Log.e(TAG, "Offer creation failed: $error")
            }
        }, constraints)
    }

    private fun createAnswer(callId: String, callType: String) {
        Log.d(TAG, "createAnswer for $callId, type=$callType")
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", if (callType == "video") "true" else "false"))
        }
        webRtcManager.peerConnection?.createAnswer(object : CustomSdpObserver() {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                Log.d(TAG, "Answer created successfully")
                sdp?.let {
                    webRtcManager.peerConnection?.setLocalDescription(object : CustomSdpObserver() {
                        override fun onSetSuccess() {
                            Log.d(TAG, "Local description (answer) set")
                            viewModelScope.launch {
                                callRepository.sendAnswer(callId, it)
                                Log.d(TAG, "Answer sent to Firestore")
                            }
                        }
                    }, it)
                }
            }
            override fun onCreateFailure(error: String?) {
                Log.e(TAG, "Answer creation failed: $error")
            }
        }, constraints)
    }

    /**
     * Safely set remote description and flush any buffered ICE candidates.
     */
    private fun setRemoteDescriptionSafely(sdp: SessionDescription, callId: String, shouldCreateAnswer: Boolean, callType: String) {
        if (isRemoteDescriptionSet) {
            Log.d(TAG, "Remote description already set, skipping")
            return
        }
        Log.d(TAG, "Setting remote description: ${sdp.type}")
        webRtcManager.peerConnection?.setRemoteDescription(object : CustomSdpObserver() {
            override fun onSetSuccess() {
                Log.d(TAG, "Remote description set successfully")
                isRemoteDescriptionSet = true

                // Flush buffered ICE candidates
                Log.d(TAG, "Flushing ${pendingIceCandidates.size} buffered ICE candidates")
                for (candidate in pendingIceCandidates) {
                    webRtcManager.peerConnection?.addIceCandidate(candidate)
                }
                pendingIceCandidates.clear()

                if (shouldCreateAnswer) {
                    createAnswer(callId, callType)
                }
            }
            override fun onSetFailure(error: String?) {
                Log.e(TAG, "Failed to set remote description: $error")
            }
        }, sdp)
    }

    private fun observeCallDetails(callId: String, isCaller: Boolean) {
        callDetailsJob?.cancel()
        callDetailsJob = viewModelScope.launch {
            callRepository.observeCallDetails(callId).collect { call ->
                if (call == null) {
                    Log.d(TAG, "Call document deleted, ending call")
                    cleanupWebRtc()
                    _activeCall.value = null
                    _incomingCall.value = null
                    return@collect
                }

                Log.d(TAG, "Call detail: status=${call.status}, hasOffer=${call.offer != null}, hasAnswer=${call.answer != null}")

                if (call.status == "rejected" || call.status == "ended" || call.status == "missed") {
                    cleanupWebRtc()
                    _activeCall.value = null
                    _incomingCall.value = null
                    return@collect
                }

                // Caller receives answer from callee
                if (isCaller && call.answer != null && !isRemoteDescriptionSet) {
                    Log.d(TAG, "Caller: received answer from callee")
                    val type = SessionDescription.Type.fromCanonicalForm(call.answer["type"] as String)
                    val sdp = call.answer["sdp"] as String
                    setRemoteDescriptionSafely(
                        SessionDescription(type, sdp),
                        callId,
                        shouldCreateAnswer = false,
                        callType = call.type
                    )
                }

                // Callee receives offer from caller
                if (!isCaller && call.offer != null && !isRemoteDescriptionSet) {
                    Log.d(TAG, "Callee: received offer from caller")
                    val type = SessionDescription.Type.fromCanonicalForm(call.offer["type"] as String)
                    val sdp = call.offer["sdp"] as String
                    setRemoteDescriptionSafely(
                        SessionDescription(type, sdp),
                        callId,
                        shouldCreateAnswer = true,
                        callType = call.type
                    )
                }

                _activeCall.value = call
            }
        }
    }

    private fun observeIceCandidates(callId: String, isCaller: Boolean) {
        iceCandidatesJob?.cancel()
        iceCandidatesJob = viewModelScope.launch {
            callRepository.observeIceCandidates(callId, isCaller).collect { candidate ->
                if (isRemoteDescriptionSet) {
                    Log.d(TAG, "Adding remote ICE candidate: ${candidate.sdpMid}")
                    webRtcManager.peerConnection?.addIceCandidate(candidate)
                } else {
                    Log.d(TAG, "Buffering remote ICE candidate (remote desc not set yet): ${candidate.sdpMid}")
                    pendingIceCandidates.add(candidate)
                }
            }
        }
    }

    private fun cleanupWebRtc() {
        Log.d(TAG, "cleanupWebRtc")
        webRtcManager.release()
        _remoteStream.value = null
        callDetailsJob?.cancel()
        iceCandidatesJob?.cancel()
        timeoutJob?.cancel()
        isRemoteDescriptionSet = false
        pendingIceCandidates.clear()
    }

    override fun onCleared() {
        super.onCleared()
        cleanupWebRtc()
        incomingCallListenerJob?.cancel()
    }
}

open class CustomSdpObserver : SdpObserver {
    override fun onCreateSuccess(sdp: SessionDescription?) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(error: String?) {
        Log.e("CustomSdpObserver", "onCreateFailure: $error")
    }
    override fun onSetFailure(error: String?) {
        Log.e("CustomSdpObserver", "onSetFailure: $error")
    }
}

package com.example.misal.domain.webrtc

import android.content.Context
import android.util.Log
import org.webrtc.*

class WebRtcManager(private val context: Context) {

    companion object {
        private const val TAG = "WebRtcManager"
        private var isInitialized = false
    }

    private var peerConnectionFactory: PeerConnectionFactory? = null
    var peerConnection: PeerConnection? = null
        private set

    private var localAudioTrack: AudioTrack? = null
    private var localVideoTrack: VideoTrack? = null
    private var videoCapturer: CameraVideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null

    private var eglBase: EglBase? = null

    fun getEglBaseContext(): EglBase.Context? = eglBase?.eglBaseContext

    private fun ensureInitialized() {
        if (peerConnectionFactory != null) return

        try {
            if (!isInitialized) {
                PeerConnectionFactory.initialize(
                    PeerConnectionFactory.InitializationOptions.builder(context)
                        .setEnableInternalTracer(false)
                        .createInitializationOptions()
                )
                isInitialized = true
            }

            if (eglBase == null) {
                eglBase = EglBase.create()
            }

            val options = PeerConnectionFactory.Options()
            val videoEncoderFactory = DefaultVideoEncoderFactory(eglBase!!.eglBaseContext, true, true)
            val videoDecoderFactory = DefaultVideoDecoderFactory(eglBase!!.eglBaseContext)

            peerConnectionFactory = PeerConnectionFactory.builder()
                .setOptions(options)
                .setVideoEncoderFactory(videoEncoderFactory)
                .setVideoDecoderFactory(videoDecoderFactory)
                .createPeerConnectionFactory()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize PeerConnectionFactory", e)
        }
    }

    fun createPeerConnection(observer: PeerConnection.Observer) {
        ensureInitialized()

        try {
            peerConnection?.close()
            peerConnection?.dispose()
        } catch (_: Exception) {}
        peerConnection = null

        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
        )

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }

        peerConnection = peerConnectionFactory?.createPeerConnection(rtcConfig, observer)
        Log.d(TAG, "PeerConnection created: ${peerConnection != null}")
    }

    fun startLocalAudio(isMuted: Boolean = false) {
        try {
            ensureInitialized()
            val audioSource = peerConnectionFactory?.createAudioSource(MediaConstraints())
            localAudioTrack = peerConnectionFactory?.createAudioTrack("local_audio_track", audioSource)
            localAudioTrack?.setEnabled(!isMuted)
            localAudioTrack?.let {
                peerConnection?.addTrack(it, listOf("stream"))
            }
            Log.d(TAG, "Local audio started, muted=$isMuted")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start local audio", e)
        }
    }

    /**
     * Creates the video track, starts camera capture, and adds the track
     * to the peer connection. Does NOT attach any renderer (UI).
     * Call this BEFORE createOffer/createAnswer so the video track is
     * included in the SDP.
     */
    fun startLocalVideoCapture() {
        try {
            ensureInitialized()
            if (localVideoTrack != null) {
                Log.d(TAG, "Local video track already exists, skipping")
                return
            }
            surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBase?.eglBaseContext)
            videoCapturer = createCameraCapturer(Camera2Enumerator(context))

            if (videoCapturer == null) {
                Log.w(TAG, "No camera found, skipping video")
                return
            }
            val videoSource = peerConnectionFactory?.createVideoSource(videoCapturer!!.isScreencast)
            videoCapturer?.initialize(surfaceTextureHelper, context, videoSource?.capturerObserver)
            videoCapturer?.startCapture(1280, 720, 30)

            localVideoTrack = peerConnectionFactory?.createVideoTrack("local_video_track", videoSource)
            localVideoTrack?.let {
                peerConnection?.addTrack(it, listOf("stream"))
            }
            Log.d(TAG, "Local video capture started (no renderer attached yet)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start local video capture", e)
        }
    }

    /**
     * Initializes a SurfaceViewRenderer and attaches the existing local
     * video track to it for display. Call this from the UI after
     * startLocalVideoCapture().
     */
    fun attachLocalRenderer(renderer: SurfaceViewRenderer) {
        try {
            ensureInitialized()
            renderer.init(eglBase?.eglBaseContext, null)
            renderer.setEnableHardwareScaler(true)
            renderer.setMirror(true)
            localVideoTrack?.addSink(renderer)
            Log.d(TAG, "Local renderer attached, track=${localVideoTrack != null}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to attach local renderer", e)
        }
    }

    private fun createCameraCapturer(enumerator: CameraEnumerator): CameraVideoCapturer? {
        val deviceNames = enumerator.deviceNames
        for (deviceName in deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                return enumerator.createCapturer(deviceName, null)
            }
        }
        for (deviceName in deviceNames) {
            if (enumerator.isBackFacing(deviceName)) {
                return enumerator.createCapturer(deviceName, null)
            }
        }
        return null
    }

    fun toggleMute(mute: Boolean) {
        localAudioTrack?.setEnabled(!mute)
    }

    fun toggleVideo(enable: Boolean) {
        localVideoTrack?.setEnabled(enable)
        try {
            if (enable) {
                videoCapturer?.startCapture(1280, 720, 30)
            } else {
                videoCapturer?.stopCapture()
            }
        } catch (e: Exception) {
            Log.e(TAG, "toggleVideo error", e)
        }
    }

    fun switchCamera() {
        videoCapturer?.switchCamera(null)
    }

    fun initSurfaceView(renderer: SurfaceViewRenderer) {
        ensureInitialized()
        try {
            renderer.init(eglBase?.eglBaseContext, null)
            renderer.setEnableHardwareScaler(true)
            renderer.setMirror(false) // Remote video shouldn't be mirrored
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init surface view", e)
        }
    }

    fun release() {
        Log.d(TAG, "Releasing WebRTC resources (keeping factory)")
        try { videoCapturer?.stopCapture() } catch (_: Exception) {}
        try { videoCapturer?.dispose() } catch (_: Exception) {}
        videoCapturer = null

        try { surfaceTextureHelper?.dispose() } catch (_: Exception) {}
        surfaceTextureHelper = null

        localAudioTrack = null
        localVideoTrack = null

        try { peerConnection?.close() } catch (_: Exception) {}
        try { peerConnection?.dispose() } catch (_: Exception) {}
        peerConnection = null
    }
}

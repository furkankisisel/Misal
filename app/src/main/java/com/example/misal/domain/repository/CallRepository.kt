package com.example.misal.domain.repository

import com.example.misal.domain.model.Call
import kotlinx.coroutines.flow.Flow
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription

interface CallRepository {
    fun getIncomingCall(): Flow<Call?>
    suspend fun startCall(receiverId: String, type: String): Result<String>
    suspend fun sendOffer(callId: String, offer: SessionDescription): Result<Unit>
    suspend fun sendAnswer(callId: String, answer: SessionDescription): Result<Unit>
    suspend fun acceptCall(callId: String): Result<Unit>
    suspend fun rejectCall(callId: String): Result<Unit>
    suspend fun endCall(callId: String): Result<Unit>
    suspend fun markAsMissed(callId: String): Result<Unit>
    suspend fun sendIceCandidate(callId: String, isCaller: Boolean, candidate: IceCandidate): Result<Unit>
    fun observeCallDetails(callId: String): Flow<Call?>
    fun observeIceCandidates(callId: String, isCaller: Boolean): Flow<IceCandidate>
    fun getCallHistory(): Flow<List<Call>>
}

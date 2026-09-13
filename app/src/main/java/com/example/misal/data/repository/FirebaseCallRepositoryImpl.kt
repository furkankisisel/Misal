package com.example.misal.data.repository

import com.example.misal.domain.model.Call
import com.example.misal.domain.repository.CallRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription
import javax.inject.Inject

class FirebaseCallRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : CallRepository {

    override fun getIncomingCall(): Flow<Call?> = callbackFlow {
        val currentUserId = auth.currentUser?.uid
        if (currentUserId == null) {
            trySend(null)
            close()
            return@callbackFlow
        }

        val listener = firestore.collection("calls")
            .whereEqualTo("receiverId", currentUserId)
            .whereEqualTo("status", "ringing")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    return@addSnapshotListener
                }
                
                if (snapshot != null && !snapshot.isEmpty) {
                    val doc = snapshot.documents.first()
                    val call = doc.toObject(Call::class.java)?.copy(id = doc.id)
                    trySend(call)
                } else {
                    trySend(null)
                }
            }

        awaitClose { listener.remove() }
    }

    override suspend fun startCall(receiverId: String, type: String): Result<String> {
        return try {
            val currentUserId = auth.currentUser?.uid ?: throw Exception("User not logged in")
            val callRef = firestore.collection("calls").document()
            
            val call = Call(
                id = callRef.id,
                callerId = currentUserId,
                receiverId = receiverId,
                type = type,
                status = "ringing",
                timestamp = System.currentTimeMillis()
            )
            
            callRef.set(call).await()
            Result.success(callRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun sendOffer(callId: String, offer: SessionDescription): Result<Unit> {
        return try {
            val offerMap = hashMapOf(
                "type" to offer.type.canonicalForm(),
                "sdp" to offer.description
            )
            firestore.collection("calls").document(callId).update("offer", offerMap).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun sendAnswer(callId: String, answer: SessionDescription): Result<Unit> {
        return try {
            val answerMap = hashMapOf(
                "type" to answer.type.canonicalForm(),
                "sdp" to answer.description
            )
            firestore.collection("calls").document(callId).update("answer", answerMap).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun acceptCall(callId: String): Result<Unit> {
        return updateCallStatus(callId, "accepted")
    }

    override suspend fun rejectCall(callId: String): Result<Unit> {
        return updateCallStatus(callId, "rejected")
    }

    override suspend fun markAsMissed(callId: String): Result<Unit> {
        return try {
            val callRef = firestore.collection("calls").document(callId)
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(callRef)
                if (snapshot.exists() && snapshot.getString("status") == "ringing") {
                    transaction.update(callRef, "status", "missed")
                }
            }.await()
            cleanupCallData(callId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun endCall(callId: String): Result<Unit> {
        return updateCallStatus(callId, "ended")
    }

    private suspend fun cleanupCallData(callId: String) {
        try {
            val callRef = firestore.collection("calls").document(callId)
            val callerCandidates = callRef.collection("callerCandidates").get().await()
            val calleeCandidates = callRef.collection("calleeCandidates").get().await()
            
            val batch = firestore.batch()
            for (doc in callerCandidates.documents) batch.delete(doc.reference)
            for (doc in calleeCandidates.documents) batch.delete(doc.reference)
            // DO NOT delete callRef, we need it for Call History!
            batch.commit().await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun updateCallStatus(callId: String, newStatus: String): Result<Unit> {
        return try {
            // Using a simple update. The UI will handle the cleanup.
            if (newStatus == "ended" || newStatus == "rejected" || newStatus == "missed") {
                cleanupCallData(callId)
            } else {
                firestore.collection("calls").document(callId).update("status", newStatus).await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun sendIceCandidate(
        callId: String,
        isCaller: Boolean,
        candidate: IceCandidate
    ): Result<Unit> {
        return try {
            val collectionName = if (isCaller) "callerCandidates" else "calleeCandidates"
            val candidateMap = hashMapOf(
                "sdpMid" to candidate.sdpMid,
                "sdpMLineIndex" to candidate.sdpMLineIndex,
                "sdp" to candidate.sdp
            )
            firestore.collection("calls").document(callId)
                .collection(collectionName).add(candidateMap).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun observeCallDetails(callId: String): Flow<Call?> = callbackFlow {
        val listener = firestore.collection("calls").document(callId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) {
                    trySend(null)
                    return@addSnapshotListener
                }
                
                val call = snapshot.toObject(Call::class.java)?.copy(id = snapshot.id)
                trySend(call)
            }
        awaitClose { listener.remove() }
    }

    override fun observeIceCandidates(callId: String, isCaller: Boolean): Flow<IceCandidate> = callbackFlow {
        // If I am the caller, I want to listen to callee's candidates.
        val collectionName = if (isCaller) "calleeCandidates" else "callerCandidates"
        val listener = firestore.collection("calls").document(callId)
            .collection(collectionName)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    return@addSnapshotListener
                }
                
                for (change in snapshot.documentChanges) {
                    if (change.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                        val data = change.document.data
                        val sdpMid = data["sdpMid"] as? String ?: continue
                        val sdpMLineIndex = (data["sdpMLineIndex"] as? Number)?.toInt() ?: continue
                        val sdp = data["sdp"] as? String ?: continue
                        
                        val candidate = IceCandidate(sdpMid, sdpMLineIndex, sdp)
                        trySend(candidate)
                    }
                }
            }
        awaitClose { listener.remove() }
    }
    override fun getCallHistory(): Flow<List<Call>> = callbackFlow {
        val currentUserId = auth.currentUser?.uid
        if (currentUserId == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listener = firestore.collection("calls")
            .where(
                com.google.firebase.firestore.Filter.or(
                    com.google.firebase.firestore.Filter.equalTo("callerId", currentUserId),
                    com.google.firebase.firestore.Filter.equalTo("receiverId", currentUserId)
                )
            )
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val calls = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(Call::class.java)?.copy(id = doc.id)
                    }.sortedByDescending { it.timestamp }
                    trySend(calls)
                } else {
                    trySend(emptyList())
                }
            }

        awaitClose { listener.remove() }
    }
}

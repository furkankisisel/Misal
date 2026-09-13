package com.example.misal.fcm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.misal.MainActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * DO NOT use @AndroidEntryPoint or @Inject here.
 * When the app is killed / in the background, Hilt's Application component
 * may not be ready and the service creation will crash with
 * "Unable to create service" / UninitializedPropertyAccessException.
 *
 * Instead, access Firebase singletons directly via getInstance().
 */
class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "MyFirebaseMessagingService"
    }

    // Use a SupervisorJob so one failure doesn't cancel other coroutines
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token received")
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null) {
            serviceScope.launch {
                try {
                    FirebaseFirestore.getInstance()
                        .collection("users").document(userId)
                        .update("fcmToken", token)
                        .await()
                    Log.d(TAG, "FCM token updated in Firestore")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to update FCM token", e)
                }
            }
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "FCM message received: type=${remoteMessage.data["type"]}, priority=${remoteMessage.priority}")

        val type = remoteMessage.data["type"]
        if (type == "incoming_call") {
            handleIncomingCall(remoteMessage)
            return
        }

        // Standard chat message handling
        val title = remoteMessage.data["title"] ?: remoteMessage.notification?.title ?: "Yeni Mesaj"
        val body = remoteMessage.data["body"] ?: remoteMessage.notification?.body ?: "Bir mesaj aldınız."
        val chatId = remoteMessage.data["chatId"]

        showNotification(title, body, chatId)
    }

    private fun handleIncomingCall(remoteMessage: RemoteMessage) {
        val callId = remoteMessage.data["callId"] ?: return
        val callerName = remoteMessage.data["callerName"] ?: "Arayan"
        val callType = remoteMessage.data["callType"] ?: "audio"

        Log.d(TAG, "Incoming call: callId=$callId, caller=$callerName, type=$callType")

        try {
            val intent = Intent(this, IncomingCallService::class.java).apply {
                putExtra("callId", callId)
                putExtra("callerName", callerName)
                putExtra("callType", callType)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            Log.d(TAG, "IncomingCallService started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start IncomingCallService, falling back to notification", e)
            // Fallback: show a regular high-priority notification
            showCallNotification(callId, callerName, callType)
        }
    }

    /**
     * Fallback notification shown if the foreground service can't be started
     * (e.g., background execution limits on some OEM devices).
     */
    private fun showCallNotification(callId: String, callerName: String, callType: String) {
        val channelId = "misal_incoming_calls"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Gelen Aramalar",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Gelen aramaları gösterir"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val fullScreenIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            putExtra("incoming_call", true)
            putExtra("callId", callId)
            putExtra("callerName", callerName)
            putExtra("callType", callType)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle("Gelen Arama")
            .setContentText("$callerName arıyor...")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .setFullScreenIntent(pendingIntent, true)
            .build()

        notificationManager.notify(1001, notification)
    }

    private fun showNotification(title: String, message: String, chatId: String?) {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            if (chatId != null) {
                putExtra("chatId", chatId)
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = "misal_chat_channel"
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Sohbet Bildirimleri",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }
}

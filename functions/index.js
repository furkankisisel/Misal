const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const admin = require("firebase-admin");

admin.initializeApp();

exports.sendChatNotification = onDocumentCreated("chats/{chatId}/messages/{messageId}", async (event) => {
    const snapshot = event.data;
    if (!snapshot) {
        console.log("No data associated with the event");
        return;
    }
    const messageData = snapshot.data();
    
    const chatId = event.params.chatId;
    const senderId = messageData.senderId;
    const isDeleted = messageData.isDeleted;
    
    // Don't send notification for deleted messages
    if (isDeleted) return;

    try {
        // Fetch the chat to get participants
        const chatDoc = await admin.firestore().collection("chats").doc(chatId).get();
        if (!chatDoc.exists) return;
        
        const chatData = chatDoc.data();
        const participants = chatData.participants || [];
        
        // Remove the sender from the recipients list
        const recipients = participants.filter(id => id !== senderId);
        
        if (recipients.length === 0) return;

        // Fetch sender details
        const senderDoc = await admin.firestore().collection("users").doc(senderId).get();
        const senderName = senderDoc.exists ? senderDoc.data().name : "Biri";

        // Fetch FCM tokens of recipients
        const tokens = [];
        for (const recipientId of recipients) {
            const userDoc = await admin.firestore().collection("users").doc(recipientId).get();
            if (userDoc.exists) {
                const token = userDoc.data().fcmToken;
                if (token) {
                    tokens.push(token);
                }
            }
        }
        
        if (tokens.length === 0) {
            console.log("No FCM tokens found for recipients");
            return;
        }

        // Send FCM Message
        const payload = {
            tokens: tokens,
            notification: {
                title: chatData.isGroup ? chatData.groupName : senderName,
                body: chatData.isGroup ? `${senderName}: Şifreli mesaj gönderdi` : "Şifreli bir mesaj gönderdi"
            },
            data: {
                chatId: chatId,
                title: chatData.isGroup ? chatData.groupName : senderName,
                body: chatData.isGroup ? `${senderName}: Şifreli mesaj` : "Yeni mesaj"
            }
        };

        const response = await admin.messaging().sendEachForMulticast(payload);
        console.log(response.successCount + ' messages were sent successfully');
        
    } catch (error) {
        console.error("Error sending notification:", error);
    }
});

exports.sendCallNotification = onDocumentCreated("calls/{callId}", async (event) => {
    const snapshot = event.data;
    if (!snapshot) return;
    
    const callData = snapshot.data();
    if (callData.status !== "ringing") return; // Guard: only notify if it's ringing
    
    const callerId = callData.callerId;
    const receiverId = callData.receiverId;
    const callType = callData.type; // "audio" or "video"
    const callId = event.params.callId;

    try {
        // Fetch caller details
        const callerDoc = await admin.firestore().collection("users").doc(callerId).get();
        const callerName = callerDoc.exists ? callerDoc.data().name : "Arayan kişi";

        // Fetch receiver FCM token
        const receiverDoc = await admin.firestore().collection("users").doc(receiverId).get();
        if (!receiverDoc.exists) return;
        
        const fcmToken = receiverDoc.data().fcmToken;
        if (!fcmToken) {
            console.log("No FCM token for receiver", receiverId);
            return;
        }

        // Send High Priority FCM Data Message
        // We do NOT use "notification" payload so it doesn't show a standard notification
        // We only use "data" payload to silently wake up the app and trigger the IncomingCallService
        const payload = {
            token: fcmToken,
            data: {
                type: "incoming_call",
                callId: callId,
                callerId: callerId,
                callerName: callerName,
                callType: callType || "audio"
            },
            android: {
                priority: "high"
            }
        };

        await admin.messaging().send(payload);
        console.log(`Call notification sent to ${receiverId} for call ${callId}`);
        
    } catch (error) {
        console.error("Error sending call notification:", error);
    }
});

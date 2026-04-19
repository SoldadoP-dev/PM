const functions = require('firebase-functions/v1');
const admin = require('firebase-admin');
admin.initializeApp();

// 1. Notificaciones de Chats Privados
exports.sendChatNotification = functions.firestore
    .document('chats/{chatId}/messages/{messageId}')
    .onCreate(async (snap, context) => {
        const message = snap.data();
        if (!message.text && !message.imageUrl && !message.videoUrl) return null;
        const senderId = message.senderId;
        const chatId = context.params.chatId;
        const chatDoc = await admin.firestore().collection('chats').doc(chatId).get();
        const chatRoom = chatDoc.data();
        if (!chatRoom) return null;
        const receiverId = chatRoom.participants.find(p => p !== senderId);
        if (!receiverId) return null;
        const [senderDoc, receiverDoc] = await Promise.all([
            admin.firestore().collection('users').doc(senderId).get(),
            admin.firestore().collection('users').doc(receiverId).get()
        ]);
        const senderName = senderDoc.data().username || "Alguien";
        const token = receiverDoc.data().fcmToken;
        if (!token) return null;
        let bodyText = message.text;
        if (message.imageUrl) bodyText = "📷 Te ha enviado una imagen";
        if (message.videoUrl) bodyText = "🎥 Te ha enviado un vídeo";
        const payload = {
            token: token,
            notification: { title: senderName, body: bodyText },
            android: { priority: "high", notification: { channelId: "channel_pm_high_priority", sound: "default" } },
            data: { title: senderName, body: bodyText, type: "message", chatId: chatId }
        };
        try { return await admin.messaging().send(payload); } catch (error) { return null; }
    });

// 2. Notificaciones de Actividad
exports.sendActivityNotification = functions.firestore
    .document('notifications/{notificationId}')
    .onCreate(async (snap, context) => {
        const notif = snap.data();
        const receiverDoc = await admin.firestore().collection('users').doc(notif.toUserId).get();
        const token = receiverDoc.data().fcmToken;
        if (!token) return null;
        let title = "Nueva notificación";
        let body = "";
        switch(notif.type) {
            case "like": title = "¡Nuevo Like!"; body = `A ${notif.fromUsername} le ha gustado tu publicación.`; break;
            case "comment": title = "Nuevo comentario"; body = `${notif.fromUsername} ha comentado: ${notif.content}`; break;
            case "follow_request": title = "Nueva petición"; body = `${notif.fromUsername} quiere seguirte.`; break;
        }
        const payload = {
            token: token,
            notification: { title: title, body: body },
            android: { priority: "high", notification: { channelId: "channel_pm_high_priority" } },
            data: { title, body, type: notif.type, targetId: notif.targetId || "" }
        };
        try { return await admin.messaging().send(payload); } catch (error) { return null; }
    });

// 3. Reiniciar asistencias a las 6:00 AM hora España
exports.resetAttendances = functions.pubsub
    .schedule('0 6 * * *')
    .timeZone('Europe/Madrid')
    .onRun(async (context) => {
        const snapshot = await admin.firestore().collection('attendances').get();
        const batch = admin.firestore().batch();
        snapshot.docs.forEach((doc) => {
            batch.delete(doc.ref);
        });
        await batch.commit();
        console.log('Asistencias reiniciadas con éxito');
        return null;
    });

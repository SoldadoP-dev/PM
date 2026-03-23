package com.example.pm

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirebaseRepository {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    suspend fun getCurrentUser(): User? {
        val uid = auth.currentUser?.uid ?: return null
        return firestore.collection("users").document(uid).get().await().toObject(User::class.java)
    }

    suspend fun getVenues(): List<Venue> {
        // Mantenemos el espacio tal cual lo pediste
        return firestore.collection(" venues").get().await().toObjects(Venue::class.java)
    }

    suspend fun toggleAttendance(venueId: String) {
        val uid = auth.currentUser?.uid ?: return
        val attendanceRef = firestore.collection("attendances")
        
        val existing = attendanceRef
            .whereEqualTo("userId", uid)
            .whereEqualTo("venueId", venueId)
            .get()
            .await()

        if (existing.isEmpty) {
            val attendance = Attendance(userId = uid, venueId = venueId)
            attendanceRef.add(attendance).await()
        } else {
            for (doc in existing.documents) {
                doc.reference.delete().await()
            }
        }
    }

    fun getAttendanceForVenue(venueId: String): Flow<List<Attendance>> = callbackFlow {
        val listener = firestore.collection("attendances")
            .whereEqualTo("venueId", venueId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                val attendances = snapshot?.toObjects(Attendance::class.java) ?: emptyList()
                trySend(attendances)
            }
        awaitClose { listener.remove() }
    }

    fun getStories(followingUids: List<String>): Flow<List<Story>> = callbackFlow {
        if (followingUids.isEmpty()) { trySend(emptyList()); return@callbackFlow }
        val listener = firestore.collection("stories")
            .whereIn("userId", followingUids)
            .orderBy("expiresAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                val currentTime = com.google.firebase.Timestamp.now()
                val stories = snapshot?.toObjects(Story::class.java)?.filter { it.expiresAt > currentTime } ?: emptyList()
                trySend(stories)
            }
        awaitClose { listener.remove() }
    }

    fun getMessages(chatId: String): Flow<List<Message>> = callbackFlow {
        val listener = firestore.collection("chats").document(chatId).collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                val messages = snapshot?.toObjects(Message::class.java) ?: emptyList()
                trySend(messages)
            }
        awaitClose { listener.remove() }
    }

    suspend fun sendMessage(chatId: String, text: String) {
        val uid = auth.currentUser?.uid ?: return
        val message = Message(senderId = uid, text = text)
        firestore.collection("chats").document(chatId).collection("messages").add(message).await()
    }
}

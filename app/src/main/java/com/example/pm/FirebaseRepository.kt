package com.example.pm

import android.net.Uri
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

class FirebaseRepository {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val storage = FirebaseStorage.getInstance()

    suspend fun getCurrentUser(): User? {
        val uid = auth.currentUser?.uid ?: return null
        return firestore.collection("users").document(uid).get().await().toObject(User::class.java)
    }

    suspend fun uploadImage(uri: Uri, path: String): String {
        // Generamos un nombre único con extensión .jpg
        val ref = storage.reference.child(path).child("${UUID.randomUUID()}.jpg")
        
        // Subimos el archivo
        ref.putFile(uri).await()
        
        // Pedimos la URL de descarga una vez confirmada la subida
        return ref.downloadUrl.await().toString()
    }

    suspend fun updateProfilePicture(uri: Uri) {
        val uid = auth.currentUser?.uid ?: return
        val url = uploadImage(uri, "profile_pics")
        firestore.collection("users").document(uid).update("photoUrl", url).await()
    }

    suspend fun uploadStory(uri: Uri) {
        val user = getCurrentUser() ?: return
        val url = uploadImage(uri, "stories")
        val story = Story(
            userId = user.uid,
            username = user.username,
            imageUrl = url,
            expiresAt = Timestamp(System.currentTimeMillis() / 1000 + 86400, 0) // 24h
        )
        firestore.collection("stories").add(story).await()
    }

    suspend fun getVenues(): List<Venue> {
        return firestore.collection("venues").get().await().toObjects(Venue::class.java)
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
        val uid = auth.currentUser?.uid ?: ""
        val allUids = (followingUids + uid).filter { it.isNotEmpty() }
        
        if (allUids.isEmpty()) {
            trySend(emptyList())
            return@callbackFlow
        }

        // Firestore 'whereIn' supports up to 10 elements. 
        val query = if (allUids.size <= 10) {
            firestore.collection("stories")
                .whereIn("userId", allUids)
                .orderBy("expiresAt", Query.Direction.DESCENDING)
        } else {
            firestore.collection("stories")
                .orderBy("expiresAt", Query.Direction.DESCENDING)
        }

        val listener = query.addSnapshotListener { snapshot, error ->
            if (error != null) return@addSnapshotListener
            val currentTime = Timestamp.now()
            val stories = snapshot?.toObjects(Story::class.java)
                ?.filter { it.expiresAt > currentTime } ?: emptyList()
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

package com.example.pm

import android.net.Uri
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage
) {

    fun getCurrentUserFlow(): Flow<User?> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(null)
            close()
            return@callbackFlow
        }
        val listener = firestore.collection("users").document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                trySend(snapshot?.toObject(User::class.java))
            }
        awaitClose { listener.remove() }
    }

    suspend fun getCurrentUser(): User? {
        val uid = auth.currentUser?.uid ?: return null
        return firestore.collection("users").document(uid).get().await().toObject(User::class.java)
    }

    suspend fun getOtherUser(userId: String): User? {
        return firestore.collection("users").document(userId).get().await().toObject(User::class.java)
    }

    suspend fun uploadImage(uri: Uri, path: String): String {
        val ref = storage.reference.child(path).child("${UUID.randomUUID()}.jpg")
        ref.putFile(uri).await()
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
            userPhotoUrl = user.photoUrl,
            imageUrl = url,
            expiresAt = Timestamp(System.currentTimeMillis() / 1000 + 86400, 0) // 24h
        )
        firestore.collection("stories").add(story).await()
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

    fun getStories(followingUids: List<String>): Flow<List<Story>> = callbackFlow {
        val uid = auth.currentUser?.uid ?: ""
        val allUids = (followingUids + uid).filter { it.isNotEmpty() }
        
        if (allUids.isEmpty()) {
            trySend(emptyList())
            return@callbackFlow
        }

        // Si hay más de 10 seguidos, Firestore no permite 'whereIn'. 
        // En una app real, haríamos queries por lotes o usaríamos otra estructura.
        // Para que sea funcional y seguro, filtraremos client-side si superamos el límite.
        val query = if (allUids.size <= 10) {
            firestore.collection("stories")
                .whereIn("userId", allUids)
                .orderBy("expiresAt", Query.Direction.DESCENDING)
        } else {
            firestore.collection("stories")
                .orderBy("expiresAt", Query.Direction.DESCENDING)
                .limit(100) // Evitar traer demasiados datos
        }

        val listener = query.addSnapshotListener { snapshot, error ->
            if (error != null) return@addSnapshotListener
            val currentTime = Timestamp.now()
            var stories = snapshot?.toObjects(Story::class.java)
                ?.filter { it.expiresAt > currentTime } ?: emptyList()
            
            // Si superamos los 10, filtramos manualmente por seguridad y precisión
            if (allUids.size > 10) {
                stories = stories.filter { allUids.contains(it.userId) }
            }
            
            trySend(stories)
        }
        awaitClose { listener.remove() }
    }

    fun getStoriesByUser(userId: String): Flow<List<Story>> = callbackFlow {
        val listener = firestore.collection("stories")
            .whereEqualTo("userId", userId)
            .orderBy("expiresAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
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

    suspend fun uploadFile(uri: Uri, folder: String): String {
        val extension = if (folder == "videos") "mp4" else "jpg"
        val ref = storage.reference.child(folder).child("${UUID.randomUUID()}.$extension")
        ref.putFile(uri).await()
        return ref.downloadUrl.await().toString()
    }

    suspend fun sendMessage(chatId: String, text: String, imageUrl: String? = null, videoUrl: String? = null) {
        val uid = auth.currentUser?.uid ?: return
        val user = getCurrentUser() ?: return
        val message = Message(
            senderId = uid,
            text = text,
            imageUrl = imageUrl,
            videoUrl = videoUrl,
            timestamp = Timestamp.now()
        )
        firestore.collection("chats").document(chatId).collection("messages").add(message).await()
        
        val lastMsg = when {
            videoUrl != null -> "🎥 Vídeo"
            imageUrl != null -> "📷 Foto"
            else -> text
        }
        firestore.collection("chats").document(chatId).update(
            "lastMessage", lastMsg,
            "lastTimestamp", Timestamp.now()
        ).await()

        val chatDoc = firestore.collection("chats").document(chatId).get().await()
        val participants = chatDoc.get("participants") as? List<String> ?: emptyList()
        val otherId = participants.find { it != uid }
        if (otherId != null) {
            sendNotification(ActivityNotification(
                fromUserId = uid,
                fromUsername = user.username,
                toUserId = otherId,
                type = "message",
                content = lastMsg,
                targetId = chatId
            ))
        }
    }

    fun setTyping(chatId: String, isTyping: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        firestore.collection("chats").document(chatId).update("typingUsers.$uid", isTyping)
    }

    fun getChatRoom(chatId: String): Flow<ChatRoom?> = callbackFlow {
        val listener = firestore.collection("chats").document(chatId)
            .addSnapshotListener { snapshot, _ ->
                trySend(snapshot?.toObject(ChatRoom::class.java))
            }
        awaitClose { listener.remove() }
    }

    suspend fun sendNotification(notification: ActivityNotification) {
        if (notification.toUserId == notification.fromUserId) return
        val ref = firestore.collection("notifications").document()
        ref.set(notification.copy(id = ref.id, timestamp = Timestamp.now())).await()
    }

    suspend fun createPost(uri: Uri, caption: String) {
        val user = getCurrentUser() ?: return
        val imageUrl = uploadImage(uri, "posts")
        val post = Post(
            userId = user.uid,
            username = user.username,
            userPhotoUrl = user.photoUrl,
            imageUrl = imageUrl,
            caption = caption,
            timestamp = Timestamp.now()
        )
        firestore.collection("posts").add(post).await()
    }

    fun getUserPosts(userId: String): Flow<List<Post>> = callbackFlow {
        val listener = firestore.collection("posts")
            .whereEqualTo("userId", userId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                val posts = snapshot?.toObjects(Post::class.java) ?: emptyList()
                trySend(posts)
            }
        awaitClose { listener.remove() }
    }

    suspend fun toggleLike(postId: String) {
        val uid = auth.currentUser?.uid ?: return
        val user = getCurrentUser() ?: return
        val postRef = firestore.collection("posts").document(postId)
        
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(postRef)
            val post = snapshot.toObject(Post::class.java) ?: return@runTransaction
            val newLikedBy = post.likedBy.toMutableList()
            if (newLikedBy.contains(uid)) newLikedBy.remove(uid) else newLikedBy.add(uid)
            transaction.update(postRef, "likedBy", newLikedBy)
            transaction.update(postRef, "likesCount", newLikedBy.size)
        }.await()

        val postDoc = postRef.get().await().toObject(Post::class.java)
        if (postDoc != null && postDoc.likedBy.contains(uid) && postDoc.userId != uid) {
            sendNotification(ActivityNotification(
                fromUserId = uid,
                fromUsername = user.username,
                toUserId = postDoc.userId,
                type = "like",
                targetId = postId
            ))
        }
    }

    suspend fun addComment(postId: String, text: String) {
        val user = getCurrentUser() ?: return
        val comment = Comment(
            postId = postId,
            userId = user.uid,
            username = user.username,
            text = text,
            timestamp = Timestamp.now()
        )
        firestore.collection("posts").document(postId).collection("comments").add(comment).await()

        val postDoc = firestore.collection("posts").document(postId).get().await().toObject(Post::class.java)
        if (postDoc != null && postDoc.userId != user.uid) {
            sendNotification(ActivityNotification(
                fromUserId = user.uid,
                fromUsername = user.username,
                toUserId = postDoc.userId,
                type = "comment",
                content = text,
                targetId = postId
            ))
        }
    }

    fun getComments(postId: String): Flow<List<Comment>> = callbackFlow {
        val listener = firestore.collection("posts").document(postId).collection("comments")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                val comments = snapshot?.toObjects(Comment::class.java) ?: emptyList()
                trySend(comments)
            }
        awaitClose { listener.remove() }
    }

    fun getGlobalPosts(): Flow<List<Post>> = callbackFlow {
        val listener = firestore.collection("posts")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                val posts = snapshot?.toObjects(Post::class.java) ?: emptyList()
                trySend(posts)
            }
        awaitClose { listener.remove() }
    }
}

package com.example.pm

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage,
    @ApplicationContext private val context: Context
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

    suspend fun uploadFile(uri: Uri, path: String, isVideo: Boolean = false): String {
        val extension = if (isVideo) "mp4" else "jpg"
        val ref = storage.reference.child(path).child("${UUID.randomUUID()}.$extension")
        ref.putFile(uri).await()
        return ref.downloadUrl.await().toString()
    }

    private suspend fun uploadBitmap(bitmap: Bitmap, path: String): String = withContext(Dispatchers.IO) {
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos)
        val data = baos.toByteArray()
        val ref = storage.reference.child(path).child("${UUID.randomUUID()}.jpg")
        ref.putBytes(data).await()
        return@withContext ref.downloadUrl.await().toString()
    }

    private suspend fun loadBitmapFromUri(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                val source = android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
                android.graphics.ImageDecoder.decodeBitmap(source)
            } else {
                @Suppress("DEPRECATION")
                android.provider.MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun cropTo9x16(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val targetRatio = 9f / 16f
        val currentRatio = width.toFloat() / height.toFloat()

        var newWidth = width
        var newHeight = height
        var startX = 0
        var startY = 0

        if (currentRatio > targetRatio) {
            newWidth = (height * targetRatio).toInt()
            startX = (width - newWidth) / 2
        } else if (currentRatio < targetRatio) {
            newHeight = (width / targetRatio).toInt()
            startY = (height - newHeight) / 2
        }

        return Bitmap.createBitmap(bitmap, startX, startY, newWidth, newHeight)
    }

    private suspend fun generateVideoThumbnail(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            retriever.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } catch (e: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    suspend fun updateProfilePicture(uri: Uri) {
        val uid = auth.currentUser?.uid ?: return
        val url = uploadFile(uri, "profile_pics")
        firestore.collection("users").document(uid).update("photoUrl", url).await()
    }

    suspend fun uploadStory(uri: Uri, isVideo: Boolean = false) {
        val user = getCurrentUser() ?: return
        val videoUrl = if (isVideo) uploadFile(uri, "stories", true) else null
        val imageUrl = if (isVideo) {
            val bitmap = generateVideoThumbnail(uri)
            if (bitmap != null) {
                uploadBitmap(bitmap, "stories_thumbnails")
            } else ""
        } else {
            val bitmap = loadBitmapFromUri(uri)
            if (bitmap != null) {
                uploadBitmap(bitmap, "stories")
            } else {
                uploadFile(uri, "stories", false)
            }
        }

        val story = Story(
            userId = user.uid,
            username = user.username,
            userPhotoUrl = user.photoUrl,
            imageUrl = imageUrl,
            videoUrl = videoUrl,
            expiresAt = Timestamp(System.currentTimeMillis() / 1000 + 86400, 0)
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

        val query = if (allUids.size <= 10) {
            firestore.collection("stories")
                .whereIn("userId", allUids)
        } else {
            firestore.collection("stories")
        }

        val listener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                android.util.Log.e("FirebaseRepository", "Error getting stories", error)
                return@addSnapshotListener
            }
            val currentTime = Timestamp.now()
            var stories = snapshot?.toObjects(Story::class.java)
                ?.filter { it.expiresAt > currentTime }
                ?.sortedByDescending { it.expiresAt } ?: emptyList()
            
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
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("FirebaseRepository", "Error getting user stories", error)
                    return@addSnapshotListener
                }
                val currentTime = Timestamp.now()
                val stories = snapshot?.toObjects(Story::class.java)
                    ?.filter { it.expiresAt > currentTime }
                    ?.sortedBy { it.expiresAt } ?: emptyList()
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

    suspend fun sendMessage(chatId: String, text: String, imageUrl: String? = null, videoUrl: String? = null) {
        val uid = auth.currentUser?.uid ?: return
        val user = getCurrentUser() ?: return
        val message = Message(
            senderId = uid,
            text = text,
            imageUrl = imageUrl,
            videoUrl = videoUrl,
            timestamp = Timestamp.now(),
            isRead = false
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
                targetId = chatId,
                isRead = false
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
        // FORZAMOS isRead a false explícitamente al guardar en Firebase
        val finalNotif = notification.copy(
            id = ref.id, 
            timestamp = Timestamp.now(),
            isRead = false
        )
        ref.set(finalNotif).await()
    }

    suspend fun createPost(uri: Uri, caption: String, isVideo: Boolean = false) {
        val user = getCurrentUser() ?: return
        val videoUrl = if (isVideo) uploadFile(uri, "posts", true) else null
        val imageUrl = if (isVideo) {
            val bitmap = generateVideoThumbnail(uri)
            if (bitmap != null) uploadBitmap(bitmap, "posts_thumbnails") else ""
        } else {
            uploadFile(uri, "posts", false)
        }

        val post = Post(
            userId = user.uid,
            username = user.username,
            userPhotoUrl = user.photoUrl,
            imageUrl = imageUrl,
            videoUrl = videoUrl,
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
        
        val postDoc = postRef.get().await().toObject(Post::class.java) ?: return
        
        if (postDoc.likedBy.contains(uid)) {
            postRef.update(
                "likedBy", FieldValue.arrayRemove(uid),
                "likesCount", FieldValue.increment(-1)
            ).await()
        } else {
            postRef.update(
                "likedBy", FieldValue.arrayUnion(uid),
                "likesCount", FieldValue.increment(1)
            ).await()
            
            if (postDoc.userId != uid) {
                sendNotification(ActivityNotification(
                    fromUserId = uid,
                    fromUsername = user.username,
                    toUserId = postDoc.userId,
                    type = "like",
                    targetId = postId,
                    isRead = false
                ))
            }
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
                targetId = postId,
                isRead = false
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

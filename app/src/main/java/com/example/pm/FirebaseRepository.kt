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

    fun getCurrentUserFlow(): Flow<User?> = getUserFlow(auth.currentUser?.uid ?: "")

    fun getUserFlow(userId: String): Flow<User?> = callbackFlow {
        if (userId.isEmpty()) {
            trySend(null)
            close()
            return@callbackFlow
        }
        val listener = firestore.collection("users").document(userId)
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

    suspend fun getAllUsers(): List<User> {
        return firestore.collection("users").get().await().toObjects(User::class.java)
    }

    suspend fun updateUser(username: String, bio: String, photoUrl: String, isPrivate: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        firestore.collection("users").document(uid).update(
            "username", username,
            "bio", bio,
            "photoUrl", photoUrl,
            "isPrivate", isPrivate
        ).await()

        // Actualizar la privacidad de todos los posts existentes del usuario usando un Batch
        val postsQuery = firestore.collection("posts").whereEqualTo("userId", uid).get().await()
        if (!postsQuery.isEmpty) {
            val batch = firestore.batch()
            for (doc in postsQuery.documents) {
                batch.update(doc.reference, "isPrivate", isPrivate)
            }
            batch.commit().await()
        }
    }

    suspend fun updateGhostMode(enabled: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        firestore.collection("users").document(uid).update("ghostMode", enabled).await()
    }

    suspend fun updateOnlineStatus(isOnline: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        firestore.collection("users").document(uid).update("isOnline", isOnline)
    }

    suspend fun getOtherUser(userId: String): User? {
        if (userId == "group") return null
        return firestore.collection("users").document(userId).get().await().toObject(User::class.java)
    }

    suspend fun getOrCreateChat(targetUserId: String, onResult: (String) -> Unit) {
        val currentUserId = auth.currentUser?.uid ?: return
        val participants = listOf(currentUserId, targetUserId).sorted()
        
        val existing = firestore.collection("chats")
            .whereEqualTo("participants", participants)
            .whereEqualTo("isGroup", false)
            .get()
            .await()

        if (!existing.isEmpty) {
            onResult(existing.documents.first().id)
        } else {
            val chatRoom = ChatRoom(participants = participants)
            val ref = firestore.collection("chats").add(chatRoom).await()
            onResult(ref.id)
        }
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

    suspend fun markStoryAsSeen(storyId: String) {
        val uid = auth.currentUser?.uid ?: return
        firestore.collection("stories").document(storyId)
            .update("seenBy", FieldValue.arrayUnion(uid)).await()
    }

    suspend fun toggleAttendance(venueId: String, selectedTags: List<String> = emptyList()) {
        val uid = auth.currentUser?.uid ?: return
        val attendanceRef = firestore.collection("attendances")
        
        val existing = attendanceRef
            .whereEqualTo("userId", uid)
            .whereEqualTo("venueId", venueId)
            .get()
            .await()

        if (existing.isEmpty) {
            // Eliminar cualquier otra asistencia antes de agregar la nueva
            clearAttendance()
            
            val attendance = Attendance(userId = uid, venueId = venueId, selectedTags = selectedTags)
            attendanceRef.add(attendance).await()
        } else {
            for (doc in existing.documents) {
                doc.reference.delete().await()
            }
        }
    }

    suspend fun clearAttendance() {
        val uid = auth.currentUser?.uid ?: return
        val attendanceRef = firestore.collection("attendances")
        val otherAttendances = attendanceRef.whereEqualTo("userId", uid).get().await()
        for (doc in otherAttendances.documents) {
            doc.reference.delete().await()
        }
    }

    suspend fun getUserAttendance(): Attendance? {
        val uid = auth.currentUser?.uid ?: return null
        val snapshot = firestore.collection("attendances")
            .whereEqualTo("userId", uid)
            .get()
            .await()
        return snapshot.toObjects(Attendance::class.java).firstOrNull()
    }

    fun getUserAttendanceFlow(): Flow<Attendance?> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(null)
            close()
            return@callbackFlow
        }
        val listener = firestore.collection("attendances")
            .whereEqualTo("userId", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                trySend(snapshot?.toObjects(Attendance::class.java)?.firstOrNull())
            }
        awaitClose { listener.remove() }
    }
    
    suspend fun getVenueById(venueId: String): Venue? {
        if (venueId.isEmpty()) return null
        return firestore.collection("venues").document(venueId).get().await().toObject(Venue::class.java)
    }

    fun getStories(uids: List<String>): Flow<List<Story>> = callbackFlow {
        val allUids = uids.filter { it.isNotEmpty() }
        
        if (allUids.isEmpty()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val query = if (allUids.size <= 10) {
            firestore.collection("stories")
                .whereIn("userId", allUids)
        } else {
            firestore.collection("stories")
        }

        val listener = query.addSnapshotListener { snapshot, error ->
            if (error != null) return@addSnapshotListener
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
                if (error != null) return@addSnapshotListener
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

    suspend fun sendMessage(
        chatId: String, 
        text: String, 
        imageUrl: String? = null, 
        videoUrl: String? = null,
        venueInviteId: String? = null,
        venueInviteName: String? = null
    ) {
        val uid = auth.currentUser?.uid ?: return
        val user = getCurrentUser() ?: return
        
        // Remove deletedTimestamp for everyone so they see the new message
        val chatRef = firestore.collection("chats").document(chatId)
        val chatDoc = chatRef.get().await()
        val participants = chatDoc.get("participants") as? List<String> ?: emptyList()
        
        val updates = mutableMapOf<String, Any>()
        participants.forEach { pUid ->
            updates["deletedTimestamps.$pUid"] = FieldValue.delete()
        }
        chatRef.update(updates).await()

        val message = Message(
            senderId = uid,
            senderName = user.username,
            senderPhotoUrl = user.photoUrl,
            text = text,
            imageUrl = imageUrl,
            videoUrl = videoUrl,
            timestamp = Timestamp.now(),
            isRead = false,
            venueInviteId = venueInviteId,
            venueInviteName = venueInviteName
        )
        firestore.collection("chats").document(chatId).collection("messages").add(message).await()
        
        val lastMsg = when {
            venueInviteId != null -> "📍 Invitación a ${venueInviteName}"
            videoUrl != null -> "🎥 Vídeo"
            imageUrl != null -> "📷 Foto"
            else -> text
        }
        chatRef.update(
            "lastMessage", lastMsg,
            "lastTimestamp", Timestamp.now()
        ).await()

        val isGroup = chatDoc.getBoolean("isGroup") ?: false
        if (!isGroup) {
            val otherId = participants.find { it != uid }
            if (otherId != null) {
                sendNotification(ActivityNotification(
                    fromUserId = uid,
                    fromUsername = user.username,
                    toUserId = otherId,
                    type = if (venueInviteId != null) "venue_invitation" else "message",
                    content = lastMsg,
                    targetId = chatId,
                    isRead = false
                ))
            }
        }
    }

    suspend fun sendVenueInvitation(venueId: String, venueName: String, selectedUserIds: List<String>, selectedChatIds: List<String>) {
        val currentUser = getCurrentUser() ?: return
        
        // Enviar a chats existentes
        selectedChatIds.forEach { chatId ->
            sendMessage(chatId, "¡Vente conmigo a $venueName!", venueInviteId = venueId, venueInviteName = venueName)
        }
        
        // Enviar a usuarios individuales (creando chat si no existe)
        selectedUserIds.forEach { userId ->
            val chatId = getOrCreateChatId(userId)
            sendMessage(chatId, "¡Vente conmigo a $venueName!", venueInviteId = venueId, venueInviteName = venueName)
        }
    }
    
    suspend fun getOrCreateChatId(targetUserId: String): String {
        val currentUserId = auth.currentUser?.uid ?: return ""
        val participants = listOf(currentUserId, targetUserId).sorted()
        
        val existing = firestore.collection("chats")
            .whereEqualTo("participants", participants)
            .whereEqualTo("isGroup", false)
            .get()
            .await()

        return if (!existing.isEmpty) {
            existing.documents.first().id
        } else {
            val chatRoom = ChatRoom(participants = participants)
            val ref = firestore.collection("chats").add(chatRoom).await()
            ref.id
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
//a
    suspend fun createMeetup(name: String, invitedUids: List<String>, photoUri: Uri? = null) {
        val currentUser = getCurrentUser() ?: return
        val meetupRef = firestore.collection("meetups").document()
        val photoUrl = photoUri?.let { uploadFile(it, "meetup_pics") }

        val chatRoom = ChatRoom(
            participants = listOf(currentUser.uid),
            isGroup = true,
            name = name,
            photoUrl = photoUrl,
            meetupId = meetupRef.id
        )
        val chatRef = firestore.collection("chats").add(chatRoom).await()

        val meetup = Meetup(
            id = meetupRef.id,
            creatorId = currentUser.uid,
            name = name,
            photoUrl = photoUrl,
            invitedUids = invitedUids,
            acceptedUids = listOf(currentUser.uid),
            timestamp = Timestamp.now(),
            chatId = chatRef.id
        )
        meetupRef.set(meetup).await()

        invitedUids.forEach { invitedUid ->
            sendNotification(ActivityNotification(
                fromUserId = currentUser.uid,
                fromUsername = currentUser.username,
                toUserId = invitedUid,
                type = "meetup_invitation",
                content = "Te ha invitado a la quedada: $name",
                targetId = chatRef.id // Send chatId for direct navigation
            ))
        }
    }

    suspend fun respondToMeetup(meetupId: String, accept: Boolean) {
        val currentUser = getCurrentUser() ?: return
        val meetupRef = firestore.collection("meetups").document(meetupId)
        val meetup = meetupRef.get().await().toObject(Meetup::class.java) ?: return

        if (accept) {
            meetupRef.update("acceptedUids", FieldValue.arrayUnion(currentUser.uid)).await()
            if (meetup.chatId.isNotEmpty()) {
                firestore.collection("chats").document(meetup.chatId)
                    .update("participants", FieldValue.arrayUnion(currentUser.uid)).await()
            }
        } else {
            meetupRef.update("invitedUids", FieldValue.arrayRemove(currentUser.uid)).await()
        }
    }

    suspend fun leaveGroup(chatId: String) {
        val user = getCurrentUser() ?: return
        val uid = user.uid
        val chatRef = firestore.collection("chats").document(chatId)
        
        chatRef.update(
            "participants", FieldValue.arrayRemove(uid),
            "deletedTimestamps.$uid", Timestamp.now()
        ).await()

        // Send system message
        val message = Message(
            senderId = "system",
            senderName = "Sistema",
            text = "${user.username} ha abandonado el grupo",
            timestamp = Timestamp.now()
        )
        chatRef.collection("messages").add(message).await()
        chatRef.update("lastMessage", "${user.username} salió", "lastTimestamp", Timestamp.now())
    }

    suspend fun sendNotification(notification: ActivityNotification) {
        if (notification.toUserId == notification.fromUserId) return
        val ref = firestore.collection("notifications").document()
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
            timestamp = Timestamp.now(),
            isPrivate = user.isPrivate
        )
        firestore.collection("posts").add(post).await()
    }

    fun getUserPosts(userId: String): Flow<List<Post>> = callbackFlow {
        val listener = firestore.collection("posts")
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val posts = snapshot?.toObjects(Post::class.java)?.sortedByDescending { it.timestamp } ?: emptyList()
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
            userPhotoUrl = user.photoUrl,
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

    suspend fun toggleCommentLike(postId: String, commentId: String) {
        val uid = auth.currentUser?.uid ?: return
        val user = getCurrentUser() ?: return
        val commentRef = firestore.collection("posts").document(postId).collection("comments").document(commentId)
        val doc = commentRef.get().await()
        val likedBy = doc.get("likedBy") as? List<String> ?: emptyList()
        val commentOwnerId = doc.getString("userId") ?: ""
        
        if (likedBy.contains(uid)) {
            commentRef.update(
                "likedBy", FieldValue.arrayRemove(uid),
                "likesCount", FieldValue.increment(-1)
            ).await()
        } else {
            commentRef.update(
                "likedBy", FieldValue.arrayUnion(uid),
                "likesCount", FieldValue.increment(1)
            ).await()
            
            // Enviar notificación al dueño del comentario
            if (commentOwnerId.isNotEmpty() && commentOwnerId != uid) {
                sendNotification(ActivityNotification(
                    fromUserId = uid,
                    fromUsername = user.username,
                    toUserId = commentOwnerId,
                    type = "comment_like",
                    targetId = postId,
                    isRead = false
                ))
            }
        }
    }

    fun getGlobalPosts(): Flow<List<Post>> = callbackFlow {
        val listener = firestore.collection("posts")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val posts = snapshot?.toObjects(Post::class.java)?.filter { !it.isPrivate } ?: emptyList()
                trySend(posts)
            }
        awaitClose { listener.remove() }
    }

    suspend fun getPost(postId: String): Post? {
        return try {
            firestore.collection("posts").document(postId).get().await().toObject(Post::class.java)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun deletePost(postId: String) {
        val uid = auth.currentUser?.uid ?: return
        val postDoc = firestore.collection("posts").document(postId).get().await().toObject(Post::class.java) ?: return
        
        if (postDoc.userId == uid) {
            val comments = firestore.collection("posts").document(postId).collection("comments").get().await()
            for (comment in comments.documents) {
                comment.reference.delete().await()
            }
            try {
                if (postDoc.imageUrl != null) {
                    storage.getReferenceFromUrl(postDoc.imageUrl).delete().await()
                }
                if (postDoc.videoUrl != null) {
                    storage.getReferenceFromUrl(postDoc.videoUrl).delete().await()
                }
            } catch (e: Exception) {
            }
            firestore.collection("posts").document(postId).delete().await()
        }
    }

    suspend fun deleteChatForUser(chatId: String) {
        val uid = auth.currentUser?.uid ?: return
        firestore.collection("chats").document(chatId).update(
            "deletedTimestamps.$uid", Timestamp.now()
        ).await()
    }

    fun getAllAvailableTags(): Flow<List<Tag>> = callbackFlow {
        val listener = firestore.collection("tags")
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                trySend(snapshot?.toObjects(Tag::class.java) ?: emptyList())
            }
        awaitClose { listener.remove() }
    }

    fun getVenueAttendances(venueId: String): Flow<List<Attendance>> = callbackFlow {
        val listener = firestore.collection("attendances")
            .whereEqualTo("venueId", venueId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                trySend(snapshot?.toObjects(Attendance::class.java) ?: emptyList())
            }
        awaitClose { listener.remove() }
    }

    suspend fun getFollowers(userId: String): List<User> {
        val user = firestore.collection("users").document(userId).get().await().toObject(User::class.java)
        val followers = mutableListOf<User>()
        user?.followerUids?.forEach { uid ->
            firestore.collection("users").document(uid).get().await().toObject(User::class.java)?.let {
                followers.add(it)
            }
        }
        return followers
    }

    fun getChatsFlow(): Flow<List<ChatRoom>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val listener = firestore.collection("chats")
            .whereArrayContains("participants", uid)
            .addSnapshotListener { snapshot, _ ->
                val chatList = snapshot?.toObjects(ChatRoom::class.java) ?: emptyList()
                trySend(chatList.sortedByDescending { it.lastTimestamp })
            }
        awaitClose { listener.remove() }
    }

    suspend fun createChatMessageVideo(chatId: String, uri: Uri) {
        val videoUrl = uploadFile(uri, "chat_videos", true)
        val thumbnailBitmap = generateVideoThumbnail(uri)
        val imageUrl = if (thumbnailBitmap != null) {
            uploadBitmap(thumbnailBitmap, "chat_thumbnails")
        } else null
        
        sendMessage(chatId, "", imageUrl = imageUrl, videoUrl = videoUrl)
    }

    suspend fun getMeetup(meetupId: String): Meetup? {
        return firestore.collection("meetups").document(meetupId).get().await().toObject(Meetup::class.java)
    }

    suspend fun getMeetupByChatId(chatId: String): Meetup? {
        val meetupSnapshot = firestore.collection("meetups")
            .whereEqualTo("chatId", chatId)
            .get()
            .await()
        return meetupSnapshot.toObjects(Meetup::class.java).firstOrNull()
    }

    suspend fun getUserAttendance(userId: String): Attendance? {
        val snapshot = firestore.collection("attendances")
            .whereEqualTo("userId", userId)
            .get()
            .await()
        return snapshot.toObjects(Attendance::class.java).firstOrNull()
    }
}

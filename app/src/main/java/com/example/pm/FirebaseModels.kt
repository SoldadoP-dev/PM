package com.example.pm

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.PropertyName

/**
 * Representa a un usuario en la plataforma.
 */
data class User(
    @DocumentId val uid: String = "",
    val username: String = "",
    val photoUrl: String = "",
    val bio: String = "",
    val followersCount: Int = 0,
    val followingCount: Int = 0,
    val followingUids: List<String> = emptyList(),
    val followerUids: List<String> = emptyList(),
    val pendingFollowRequests: List<String> = emptyList(),
    val fcmToken: String? = null
)

/**
 * Representa un local nocturno (discoteca, bar, club).
 */
data class Venue(
    @DocumentId var id: String = "",
    var name: String = "",
    var location: GeoPoint = GeoPoint(0.0, 0.0),
    var address: String = "",
    var rating: Double = 0.0,
    @get:PropertyName("Category") @set:PropertyName("Category") var category: String = ""
)

/**
 * Registra la intención de un usuario de asistir a un local.
 */
data class Attendance(
    @DocumentId val id: String = "",
    val userId: String = "",
    val venueId: String = "",
    val timestamp: Timestamp = Timestamp.now(),
    val status: String = "going"
)

/**
 * Publicación temporal (Story) que expira a las 24h.
 */
data class Story(
    @DocumentId val id: String = "",
    val userId: String = "",
    val username: String = "", 
    val userPhotoUrl: String = "", 
    val imageUrl: String = "",
    val expiresAt: Timestamp = Timestamp.now()
)

/**
 * Sala de chat entre dos o más usuarios.
 */
data class ChatRoom(
    @DocumentId val id: String = "",
    val participants: List<String> = emptyList(),
    val lastMessage: String = "",
    val lastTimestamp: Timestamp = Timestamp.now(),
    val typingUsers: Map<String, Boolean> = emptyMap()
)

/**
 * Mensaje individual dentro de una sala de chat.
 */
data class Message(
    @DocumentId val id: String = "",
    val senderId: String = "",
    val text: String = "",
    val imageUrl: String? = null,
    val videoUrl: String? = null,
    val timestamp: Timestamp = Timestamp.now(),
    @get:PropertyName("isRead") @set:PropertyName("isRead") var isRead: Boolean = false
)

/**
 * Notificación de actividad.
 */
data class ActivityNotification(
    @DocumentId val id: String = "",
    val fromUserId: String = "",
    val fromUsername: String = "",
    val toUserId: String = "",
    val type: String = "", 
    val content: String = "", 
    val targetId: String = "", 
    val timestamp: Timestamp = Timestamp.now(),
    val isRead: Boolean = false
)

/**
 * Publicación permanente en el perfil.
 */
data class Post(
    @DocumentId val id: String = "",
    val userId: String = "",
    val username: String = "",
    val userPhotoUrl: String = "",
    val imageUrl: String = "",
    val caption: String = "",
    val timestamp: Timestamp = Timestamp.now(),
    val likesCount: Int = 0,
    val likedBy: List<String> = emptyList()
)

/**
 * Comentario en una publicación.
 */
data class Comment(
    @DocumentId val id: String = "",
    val postId: String = "",
    val userId: String = "",
    val username: String = "",
    val text: String = "",
    val timestamp: Timestamp = Timestamp.now()
)

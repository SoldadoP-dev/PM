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
    val hiddenUids: List<String> = emptyList(),
    val fcmToken: String? = null,
    @get:PropertyName("isOnline") @set:PropertyName("isOnline") var isOnline: Boolean = false,
    val ghostMode: Boolean = false
)

/**
 * Representa un local nocturno.
 */
data class Venue(
    @DocumentId var id: String = "",
    var name: String = "",
    var location: GeoPoint = GeoPoint(0.0, 0.0),
    var address: String = "",
    var rating: Double = 0.0,
    var photoUrl: String = "",
    var logoUrl: String = "",
    @get:PropertyName("Category") @set:PropertyName("Category") var category: String = ""
)

/**
 * Registra la intención de un usuario de asistir con sus etiquetas elegidas.
 */
data class Attendance(
    @DocumentId val id: String = "",
    val userId: String = "",
    val venueId: String = "",
    val timestamp: Timestamp = Timestamp.now(),
    val status: String = "going",
    val selectedTags: List<String> = emptyList() // Almacena hasta 3 etiquetas
)

/**
 * Etiqueta disponible en la base de datos.
 */
data class Tag(
    @DocumentId val id: String = "",
    val name: String = ""
)

/**
 * Publicación temporal (Story).
 */
data class Story(
    @DocumentId val id: String = "",
    val userId: String = "",
    val username: String = "", 
    val userPhotoUrl: String = "", 
    val imageUrl: String = "",
    val videoUrl: String? = null,
    val expiresAt: Timestamp = Timestamp.now(),
    val seenBy: List<String> = emptyList()
)

/**
 * Sala de chat.
 */
data class ChatRoom(
    @DocumentId val id: String = "",
    val participants: List<String> = emptyList(),
    val lastMessage: String = "",
    val lastTimestamp: Timestamp = Timestamp.now(),
    val typingUsers: Map<String, Boolean> = emptyMap(),
    val deletedTimestamps: Map<String, Timestamp> = emptyMap(),
    @get:PropertyName("isGroup") @set:PropertyName("isGroup") var isGroup: Boolean = false,
    val name: String? = null,
    val photoUrl: String? = null,
    val meetupId: String? = null
)

/**
 * Mensaje individual.
 */
data class Message(
    @DocumentId val id: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val senderPhotoUrl: String = "",
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
    @get:PropertyName("isRead") @set:PropertyName("isRead") var isRead: Boolean = false
)

/**
 * Quedada (Meetup)
 */
data class Meetup(
    @DocumentId val id: String = "",
    val creatorId: String = "",
    val name: String = "",
    val photoUrl: String? = null,
    val invitedUids: List<String> = emptyList(),
    val acceptedUids: List<String> = emptyList(),
    val timestamp: Timestamp = Timestamp.now(),
    val chatId: String = ""
)

/**
 * Publicación permanente.
 */
data class Post(
    @DocumentId val id: String = "",
    val userId: String = "",
    val username: String = "",
    val userPhotoUrl: String = "",
    val imageUrl: String? = null,
    val videoUrl: String? = null,
    val caption: String = "",
    val timestamp: Timestamp = Timestamp.now(),
    val likesCount: Int = 0,
    val likedBy: List<String> = emptyList()
)

/**
 * Comentario.
 */
data class Comment(
    @DocumentId val id: String = "",
    val postId: String = "",
    val userId: String = "",
    val username: String = "",
    val userPhotoUrl: String = "",
    val text: String = "",
    val timestamp: Timestamp = Timestamp.now(),
    val likesCount: Int = 0,
    val likedBy: List<String> = emptyList()
)

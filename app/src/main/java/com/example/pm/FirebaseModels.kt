package com.example.pm

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.GeoPoint

/**
 * Representa a un usuario en la plataforma.
 * @property uid Identificador único de Firebase Auth.
 * @property username Nombre único para mostrar.
 * @property followingUids Lista de IDs de personas a las que sigue el usuario.
 * @property followerUids Lista de IDs de personas que siguen al usuario.
 * @property pendingFollowRequests Solicitudes de seguimiento recibidas y no aceptadas.
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
    val pendingFollowRequests: List<String> = emptyList()
)

/**
 * Representa un local nocturno (discoteca, bar, club).
 * @property location Punto geográfico (Latitud/Longitud) para el mapa.
 */
data class Venue(
    @DocumentId val id: String = "",
    val name: String = "",
    val location: GeoPoint = GeoPoint(0.0, 0.0),
    val address: String = "",
    val rating: Double = 0.0,
    val category: String = ""
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
    val lastTimestamp: Timestamp = Timestamp.now()
)

/**
 * Mensaje individual dentro de una sala de chat.
 */
data class Message(
    @DocumentId val id: String = "",
    val senderId: String = "",
    val text: String = "",
    val timestamp: Timestamp = Timestamp.now()
)

/**
 * Notificación de actividad (Seguimiento, Solicitudes, etc.).
 */
data class ActivityNotification(
    @DocumentId val id: String = "",
    val fromUserId: String = "",
    val fromUsername: String = "",
    val toUserId: String = "",
    val type: String = "", // Ejemplo: "follow_request"
    val timestamp: Timestamp = Timestamp.now(),
    val isRead: Boolean = false
)

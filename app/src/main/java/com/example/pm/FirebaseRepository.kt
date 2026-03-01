package com.example.pm

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Clase encargada de gestionar todas las operaciones con Firebase (Firestore y Auth).
 * Actúa como la única fuente de verdad para los datos de la aplicación.
 */
class FirebaseRepository {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    /**
     * Obtiene el perfil del usuario actualmente autenticado desde Firestore.
     * @return Objeto [User] o null si no hay sesión activa o el documento no existe.
     */
    suspend fun getCurrentUser(): User? {
        val uid = auth.currentUser?.uid ?: return null
        return firestore.collection("users").document(uid).get().await().toObject(User::class.java)
    }

    /**
     * Recupera la lista completa de locales nocturnos registrados.
     * @return Lista de objetos [Venue].
     */
    suspend fun getVenues(): List<Venue> {
        return firestore.collection("venues").get().await().toObjects(Venue::class.java)
    }

    /**
     * Escucha en tiempo real quiénes han confirmado asistencia a un local específico.
     * @param venueId ID del local a consultar.
     * @return Flow que emite la lista de asistencias cada vez que hay un cambio en Firestore.
     */
    fun getAttendanceForVenue(venueId: String): Flow<List<Attendance>> = callbackFlow {
        val listener = firestore.collection("attendance")
            .whereEqualTo("venueId", venueId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                val attendances = snapshot?.toObjects(Attendance::class.java) ?: emptyList()
                trySend(attendances)
            }
        // Se cierra el listener cuando se cancela el Flow para evitar fugas de memoria
        awaitClose { listener.remove() }
    }

    /**
     * Obtiene las historias activas de las personas a las que sigue el usuario.
     * @param followingUids Lista de IDs de los usuarios seguidos.
     * @return Flow con la lista de historias filtradas por tiempo de expiración.
     */
    fun getStories(followingUids: List<String>): Flow<List<Story>> = callbackFlow {
        if (followingUids.isEmpty()) {
            trySend(emptyList())
            return@callbackFlow
        }
        
        val listener = firestore.collection("stories")
            .whereIn("userId", followingUids)
            .orderBy("expiresAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                val currentTime = com.google.firebase.Timestamp.now()
                // Filtramos manualmente las historias que ya han expirado
                val stories = snapshot?.toObjects(Story::class.java)
                    ?.filter { it.expiresAt > currentTime } ?: emptyList()
                trySend(stories)
            }
        awaitClose { listener.remove() }
    }

    /**
     * Escucha los mensajes de una conversación en orden cronológico.
     * @param chatId ID único de la sala de chat.
     */
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

    /**
     * Envía un mensaje de texto a una sala de chat específica.
     */
    suspend fun sendMessage(chatId: String, text: String) {
        val uid = auth.currentUser?.uid ?: return
        val message = Message(senderId = uid, text = text)
        firestore.collection("chats").document(chatId).collection("messages").add(message).await()
    }
}

package com.example.pm.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pm.FirebaseRepository
import com.example.pm.Story
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StoryViewModel @Inject constructor(
    private val repository: FirebaseRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _userStoriesMap = MutableStateFlow<Map<String, List<Story>>>(emptyMap())
    val userStoriesMap: StateFlow<Map<String, List<Story>>> = _userStoriesMap

    private val _orderedUserIds = MutableStateFlow<List<String>>(emptyList())
    val orderedUserIds: StateFlow<List<String>> = _orderedUserIds

    fun loadAllStories(startUserId: String) {
        viewModelScope.launch {
            val currentUser = repository.getCurrentUser() ?: return@launch
            val followingUids = currentUser.followingUids
            
            // Si empezamos por nuestra propia historia, incluimos a los seguidos después.
            // Si empezamos por la de un amigo, SOLO cargamos historias de amigos (sin la nuestra al final).
            val allTargetUids = if (startUserId == currentUser.uid) {
                (listOf(currentUser.uid) + followingUids).distinct()
            } else {
                followingUids.filter { it != currentUser.uid }
            }

            repository.getStories(allTargetUids).collect { allStories ->
                val grouped = storiesToUserMap(allStories)
                _userStoriesMap.value = grouped
                
                // Ordenamos: el startUserId siempre va primero, luego el resto
                val ids = grouped.keys.toMutableList()
                if (ids.contains(startUserId)) {
                    ids.remove(startUserId)
                    ids.add(0, startUserId)
                }
                _orderedUserIds.value = ids
            }
        }
    }

    fun markAsSeen(storyId: String) {
        viewModelScope.launch {
            repository.markStoryAsSeen(storyId)
        }
    }

    private fun storiesToUserMap(stories: List<Story>): Map<String, List<Story>> {
        return stories.groupBy { it.userId }
            .mapValues { entry -> entry.value.sortedBy { it.expiresAt } }
    }
}

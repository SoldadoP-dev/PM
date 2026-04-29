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

    val currentUserId: String? get() = auth.currentUser?.uid

    private var hasInitialized = false

    fun loadAllStories(startUserId: String) {
        if (hasInitialized) return

        viewModelScope.launch {
            val currentUser = repository.getCurrentUser() ?: return@launch
            val followingUids = currentUser.followingUids
            
            val allTargetUids = (listOf(currentUser.uid) + followingUids).distinct()

            repository.getStories(allTargetUids).collect { allStories ->
                val grouped = allStories.groupBy { it.userId }
                    .mapValues { entry -> entry.value.sortedBy { it.expiresAt } }
                _userStoriesMap.value = grouped
                
                if (!hasInitialized) {
                    // ORDEN LINEAL ESTRICTO: Yo -> Amigos en orden de siguiendo
                    val masterList = (listOf(currentUser.uid) + followingUids)
                        .filter { grouped.containsKey(it) }

                    val startIndex = masterList.indexOf(startUserId)
                    if (startIndex != -1) {
                        // Lista desde el pulsado hasta el final
                        _orderedUserIds.value = masterList.subList(startIndex, masterList.size)
                        hasInitialized = true
                    }
                }
            }
        }
    }

    fun markAsSeen(storyId: String) {
        viewModelScope.launch {
            repository.markStoryAsSeen(storyId)
        }
    }
}

package com.example.pm.ui.viewmodels

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pm.FirebaseRepository
import com.example.pm.Story
import com.example.pm.User
import com.example.pm.Venue
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: FirebaseRepository,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _venues = MutableStateFlow<List<Venue>>(emptyList())
    val venues: StateFlow<List<Venue>> = _venues

    private val _stories = MutableStateFlow<List<Story>>(emptyList())
    val stories: StateFlow<List<Story>> = _stories

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser

    private val _isUploadingStory = MutableStateFlow(false)
    val isUploadingStory: StateFlow<Boolean> = _isUploadingStory

    init {
        loadVenues()
        observeCurrentUserAndStories()
    }

    private fun loadVenues() {
        val collectionNames = listOf(" venues", "venues", "Venues")
        viewModelScope.launch {
            val allVenues = mutableListOf<Venue>()
            for (colName in collectionNames) {
                try {
                    val snapshot = firestore.collection(colName).get().await()
                    val batch = snapshot.documents.mapNotNull { doc ->
                        try {
                            Venue(
                                id = doc.id,
                                name = doc.getString("name") ?: doc.getString("Name") ?: "Sin nombre",
                                location = doc.getGeoPoint("location") ?: GeoPoint(40.41, -3.70),
                                address = doc.getString("address") ?: doc.getString("Address") ?: "Madrid",
                                rating = (doc.get("rating") ?: doc.get("Rating") ?: 0.0).toString().toDoubleOrNull() ?: 0.0,
                                category = doc.getString("Category") ?: doc.getString("category") ?: "Club"
                            )
                        } catch (e: Exception) { null }
                    }
                    allVenues.addAll(batch)
                } catch (e: Exception) { }
            }
            _venues.value = allVenues.distinctBy { it.id }
        }
    }

    private fun observeCurrentUserAndStories() {
        viewModelScope.launch {
            repository.getCurrentUserFlow().collectLatest { user ->
                _currentUser.value = user
                if (user != null) {
                    val following = user.followingUids
                    repository.getStories(following).collect {
                        _stories.value = it
                    }
                }
            }
        }
    }

    fun uploadStory(uri: Uri, isVideo: Boolean = false) {
        viewModelScope.launch {
            _isUploadingStory.value = true
            try {
                repository.uploadStory(uri, isVideo)
            } catch (e: Exception) {
                android.util.Log.e("HomeViewModel", "Error uploading story", e)
            } finally {
                _isUploadingStory.value = false
            }
        }
    }

    fun toggleAttendance(venueId: String) {
        viewModelScope.launch {
            repository.toggleAttendance(venueId)
        }
    }
}

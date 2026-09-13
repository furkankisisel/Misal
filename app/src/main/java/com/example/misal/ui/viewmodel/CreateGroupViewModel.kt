package com.example.misal.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.misal.domain.repository.ChatRepository
import com.example.misal.domain.repository.AuthRepository
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class UserSelection(val id: String, val email: String, val name: String, var isSelected: Boolean = false)

@HiltViewModel
class CreateGroupViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val authRepository: AuthRepository,
    private val firestore: FirebaseFirestore
) : ViewModel() {

    private val _users = MutableStateFlow<List<UserSelection>>(emptyList())
    val users: StateFlow<List<UserSelection>> = _users.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _groupCreated = MutableStateFlow(false)
    val groupCreated: StateFlow<Boolean> = _groupCreated.asStateFlow()

    init {
        loadUsers()
    }

    private fun loadUsers() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val currentUserId = authRepository.getCurrentUserId()
                val snapshot = firestore.collection("users").get().await()
                val userList = snapshot.documents.mapNotNull { doc ->
                    if (doc.id == currentUserId) null else {
                        UserSelection(
                            id = doc.id,
                            email = doc.getString("email") ?: "",
                            name = doc.getString("name") ?: "İsimsiz"
                        )
                    }
                }
                _users.value = userList
            } catch (e: Exception) {
                _error.value = "Kullanıcılar yüklenemedi: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun toggleUserSelection(userId: String) {
        _users.value = _users.value.map {
            if (it.id == userId) it.copy(isSelected = !it.isSelected) else it
        }
    }

    fun createGroup(groupName: String) {
        val selectedUsers = _users.value.filter { it.isSelected }.map { it.id }
        if (groupName.isBlank()) {
            _error.value = "Grup adı boş olamaz"
            return
        }
        if (selectedUsers.isEmpty()) {
            _error.value = "En az bir kişi seçmelisiniz"
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            val result = chatRepository.createGroup(groupName, selectedUsers)
            result.onSuccess {
                _groupCreated.value = true
            }.onFailure {
                _error.value = "Grup oluşturulamadı: ${it.message}"
            }
            _isLoading.value = false
        }
    }
}

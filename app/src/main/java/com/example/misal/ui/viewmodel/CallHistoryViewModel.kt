package com.example.misal.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.misal.domain.model.Call
import com.example.misal.domain.model.User
import com.example.misal.domain.repository.AuthRepository
import com.example.misal.domain.repository.CallRepository
import com.example.misal.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CallHistoryItem(
    val call: Call,
    val otherUser: User?,
    val isIncoming: Boolean
)

@HiltViewModel
class CallHistoryViewModel @Inject constructor(
    private val callRepository: CallRepository,
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _callHistory = MutableStateFlow<List<CallHistoryItem>>(emptyList())
    val callHistory: StateFlow<List<CallHistoryItem>> = _callHistory.asStateFlow()

    private val userCache = mutableMapOf<String, User>()

    init {
        loadCallHistory()
    }

    private fun loadCallHistory() {
        viewModelScope.launch {
            val currentUserId = authRepository.getCurrentUserId() ?: return@launch
            
            callRepository.getCallHistory().collectLatest { calls ->
                // To avoid multiple UI jumps, we can map to CallHistoryItem.
                // If a user is not in cache, we will fetch it.
                val items = mutableListOf<CallHistoryItem>()
                for (call in calls) {
                    val isIncoming = call.receiverId == currentUserId
                    val otherUserId = if (isIncoming) call.callerId else call.receiverId
                    
                    var otherUser = userCache[otherUserId]
                    if (otherUser == null) {
                        try {
                            val userResult = userRepository.getUserById(otherUserId)
                            userResult.getOrNull()?.let {
                                userCache[otherUserId] = it
                                // Trigger UI update later
                                updateItemsWithCache(calls, currentUserId)
                            }
                        } catch (e: Exception) {
                            // ignore
                        }
                    }
                    items.add(CallHistoryItem(call, otherUser, isIncoming))
                }
                _callHistory.value = items
            }
        }
    }
    
    private fun updateItemsWithCache(calls: List<Call>, currentUserId: String) {
        val items = calls.map { call ->
            val isIncoming = call.receiverId == currentUserId
            val otherUserId = if (isIncoming) call.callerId else call.receiverId
            CallHistoryItem(call, userCache[otherUserId], isIncoming)
        }
        _callHistory.value = items
    }
}

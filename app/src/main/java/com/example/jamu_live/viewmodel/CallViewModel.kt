package com.example.jamu_live.viewmodel

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.jamu_live.model.CallState
import com.example.jamu_live.model.User
import com.example.jamu_live.model.UserStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class CallViewModel(application: Application) : AndroidViewModel(application) {

    private val sharedPrefs = application.getSharedPreferences("jamu_live_prefs", Context.MODE_PRIVATE)

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _friendsList = MutableStateFlow<List<User>>(
        listOf(
            User(id = "1", name = "Sardor", status = UserStatus.ONLINE, avatarColorHex = "#4CAF50"),
            User(id = "2", name = "Malika", status = UserStatus.ONLINE, avatarColorHex = "#E91E63"),
            User(id = "3", name = "Aziz", status = UserStatus.ONLINE, avatarColorHex = "#2196F3"),
            User(id = "4", name = "Lobar", status = UserStatus.OFFLINE, avatarColorHex = "#9C27B0"),
            User(id = "5", name = "Jasur", status = UserStatus.ONLINE, avatarColorHex = "#FF9800")
        )
    )
    val friendsList: StateFlow<List<User>> = _friendsList.asStateFlow()

    private val _callState = MutableStateFlow<CallState>(CallState.Idle)
    val callState: StateFlow<CallState> = _callState.asStateFlow()

    private var timerJob: Job? = null
    private var simulatedRingingJob: Job? = null

    init {
        // Load saved user name if previously logged in
        val savedName = sharedPrefs.getString("user_name", null)
        if (!savedName.isNullOrBlank()) {
            val savedId = sharedPrefs.getString("user_id", null) ?: UUID.randomUUID().toString()
            _currentUser.value = User(
                id = savedId,
                name = savedName,
                status = UserStatus.ONLINE,
                avatarColorHex = "#3F51B5"
            )
        }
    }

    fun login(name: String) {
        if (name.isBlank()) return
        val trimmedName = name.trim()
        val userId = UUID.randomUUID().toString()

        sharedPrefs.edit {
            putString("user_name", trimmedName)
            putString("user_id", userId)
        }

        val newUser = User(
            id = userId,
            name = trimmedName,
            status = UserStatus.ONLINE,
            avatarColorHex = "#3F51B5"
        )
        _currentUser.value = newUser
    }

    fun logout() {
        endCall()
        sharedPrefs.edit {
            remove("user_name")
            remove("user_id")
        }
        _currentUser.value = null
    }

    fun addFriend(name: String) {
        if (name.isBlank()) return
        val colors = listOf("#009688", "#673AB7", "#FF5722", "#795548", "#607D8B")
        val randomColor = colors.random()
        val newFriend = User(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            status = UserStatus.ONLINE,
            avatarColorHex = randomColor
        )
        _friendsList.update { list -> list + newFriend }
    }

    fun startCall(targetUser: User) {
        simulatedRingingJob?.cancel()
        _callState.value = CallState.OutgoingCalling(targetUser)

        // Simulate friend answering after 3.5 seconds
        simulatedRingingJob = viewModelScope.launch {
            delay(3500)
            if (_callState.value is CallState.OutgoingCalling) {
                acceptInCall(targetUser)
            }
        }
    }

    fun simulateIncomingCall(fromFriend: User) {
        simulatedRingingJob?.cancel()
        _callState.value = CallState.IncomingCall(fromFriend)
    }

    fun acceptCall() {
        val currentState = _callState.value
        if (currentState is CallState.IncomingCall) {
            acceptInCall(currentState.callerUser)
        }
    }

    private fun acceptInCall(peer: User) {
        simulatedRingingJob?.cancel()
        _callState.value = CallState.InCall(peerUser = peer)
        startCallTimer()
    }

    fun declineCall() {
        simulatedRingingJob?.cancel()
        _callState.value = CallState.CallEnded("Qo'ng'iroq rad etildi")
        viewModelScope.launch {
            delay(1500)
            if (_callState.value is CallState.CallEnded) {
                _callState.value = CallState.Idle
            }
        }
    }

    fun endCall() {
        simulatedRingingJob?.cancel()
        timerJob?.cancel()
        timerJob = null

        val currentState = _callState.value
        if (currentState !is CallState.Idle) {
            _callState.value = CallState.CallEnded("Qo'ng'iroq yakunlandi")
            viewModelScope.launch {
                delay(1500)
                if (_callState.value is CallState.CallEnded) {
                    _callState.value = CallState.Idle
                }
            }
        }
    }

    fun toggleMute() {
        val state = _callState.value
        if (state is CallState.InCall) {
            _callState.value = state.copy(isMuted = !state.isMuted)
        }
    }

    fun toggleCamera() {
        val state = _callState.value
        if (state is CallState.InCall) {
            _callState.value = state.copy(isCameraOn = !state.isCameraOn)
        }
    }

    fun switchCamera() {
        val state = _callState.value
        if (state is CallState.InCall) {
            _callState.value = state.copy(isFrontCamera = !state.isFrontCamera)
        }
    }

    private fun startCallTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                val state = _callState.value
                if (state is CallState.InCall) {
                    _callState.value = state.copy(callDurationSeconds = state.callDurationSeconds + 1)
                } else {
                    break
                }
            }
        }
    }
}

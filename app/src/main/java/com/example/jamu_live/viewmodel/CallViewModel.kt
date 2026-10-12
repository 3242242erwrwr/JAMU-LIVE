package com.example.jamu_live.viewmodel

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.jamu_live.model.CallState
import com.example.jamu_live.model.User
import com.example.jamu_live.model.UserStatus
import com.example.jamu_live.network.VercelServerRepository
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
    val serverRepository = VercelServerRepository()

    private val defaultFriends = listOf(
        User(id = "1", name = "Sardor", status = UserStatus.ONLINE, avatarColorHex = "#4CAF50"),
        User(id = "2", name = "Malika", status = UserStatus.ONLINE, avatarColorHex = "#E91E63"),
        User(id = "3", name = "Aziz", status = UserStatus.ONLINE, avatarColorHex = "#2196F3"),
        User(id = "4", name = "Lobar", status = UserStatus.OFFLINE, avatarColorHex = "#9C27B0"),
        User(id = "5", name = "Jasur", status = UserStatus.ONLINE, avatarColorHex = "#FF9800")
    )

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _friendsList = MutableStateFlow<List<User>>(defaultFriends)
    val friendsList: StateFlow<List<User>> = _friendsList.asStateFlow()

    private val _callState = MutableStateFlow<CallState>(CallState.Idle)
    val callState: StateFlow<CallState> = _callState.asStateFlow()

    private val _serverUrl = MutableStateFlow(
        sharedPrefs.getString("server_url", "https://jamulive.vercel.app") ?: "https://jamulive.vercel.app"
    )
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    private var timerJob: Job? = null
    private var syncJob: Job? = null
    private var missedPollCount = 0

    init {
        serverRepository.baseUrl = _serverUrl.value

        // Load saved user name if previously logged in
        val savedName = sharedPrefs.getString("user_name", null)
        if (!savedName.isNullOrBlank()) {
            val savedId = sharedPrefs.getString("user_id", null) ?: UUID.randomUUID().toString()
            val user = User(
                id = savedId,
                name = savedName,
                status = UserStatus.ONLINE,
                avatarColorHex = "#3F51B5"
            )
            _currentUser.value = user
            startServerSync(user)
        }
    }

    fun setServerUrl(url: String) {
        val cleanUrl = url.trim().removeSuffix("/")
        if (cleanUrl.isNotBlank()) {
            _serverUrl.value = cleanUrl
            serverRepository.baseUrl = cleanUrl
            sharedPrefs.edit { putString("server_url", cleanUrl) }
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
        startServerSync(newUser)
    }

    fun logout() {
        endCall()
        syncJob?.cancel()
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
        _friendsList.update { list ->
            if (list.none { it.id == newFriend.id }) list + newFriend else list
        }
    }

    private fun startServerSync(user: User) {
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            // Initial registration
            serverRepository.registerUser(user)

            while (true) {
                try {
                    val result = serverRepository.pollStatus(user.id)
                    if (result != null) {
                        missedPollCount = 0

                        // Merge online friends with current friends list so list is persistent
                        val onlineFriends = result.onlineUsers.filter { it.id != user.id }
                        _friendsList.update { currentList ->
                            val mergedMap = currentList.associateBy { it.id }.toMutableMap()
                            onlineFriends.forEach { online ->
                                mergedMap[online.id] = online
                            }
                            mergedMap.values.toList()
                        }

                        // Handle server-side call signaling
                        val callInfo = result.callInfo
                        if (callInfo != null) {
                            val peerUser = User(
                                id = callInfo.peerId,
                                name = callInfo.peerName,
                                status = UserStatus.ONLINE
                            )

                            when (callInfo.state) {
                                "incoming" -> {
                                    if (_callState.value is CallState.Idle) {
                                        _callState.value = CallState.IncomingCall(peerUser)
                                    }
                                }
                                "incall" -> {
                                    val current = _callState.value
                                    if (current is CallState.OutgoingCalling || current is CallState.IncomingCall) {
                                        _callState.value = CallState.InCall(peerUser = peerUser)
                                        startCallTimer()
                                    }
                                }
                            }
                        } else {
                            // Only end call if peer explicitly ended it
                            val currentState = _callState.value
                            if (currentState is CallState.InCall || currentState is CallState.OutgoingCalling) {
                                _callState.value = CallState.CallEnded("Suhbatdosh muloqotni yakunladi")
                                delay(1500)
                                if (_callState.value is CallState.CallEnded) {
                                    _callState.value = CallState.Idle
                                }
                            }
                        }
                    } else {
                        // Re-register if server missed context
                        missedPollCount++
                        if (missedPollCount >= 2) {
                            serverRepository.registerUser(user)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                delay(2000) // Poll Vercel server every 2 seconds
            }
        }
    }

    fun startCall(targetUser: User) {
        val user = _currentUser.value ?: return
        _callState.value = CallState.OutgoingCalling(targetUser)

        viewModelScope.launch {
            serverRepository.startCall(user, targetUser)
        }
    }

    fun simulateIncomingCall(fromFriend: User) {
        _callState.value = CallState.IncomingCall(fromFriend)
    }

    fun acceptCall() {
        val currentState = _callState.value
        val user = _currentUser.value ?: return
        if (currentState is CallState.IncomingCall) {
            viewModelScope.launch {
                serverRepository.respondCall(user.id, "accept")
                _callState.value = CallState.InCall(peerUser = currentState.callerUser)
                startCallTimer()
            }
        }
    }

    fun declineCall() {
        val user = _currentUser.value
        if (user != null) {
            viewModelScope.launch {
                serverRepository.respondCall(user.id, "decline")
            }
        }
        _callState.value = CallState.CallEnded("Qo'ng'iroq rad etildi")
        viewModelScope.launch {
            delay(1500)
            if (_callState.value is CallState.CallEnded) {
                _callState.value = CallState.Idle
            }
        }
    }

    fun endCall() {
        val user = _currentUser.value
        if (user != null) {
            viewModelScope.launch {
                serverRepository.respondCall(user.id, "end")
            }
        }
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

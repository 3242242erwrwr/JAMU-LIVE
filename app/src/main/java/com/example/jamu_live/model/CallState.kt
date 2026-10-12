package com.example.jamu_live.model

sealed class CallState {
    object Idle : CallState()
    data class OutgoingCalling(val targetUser: User) : CallState()
    data class IncomingCall(val callerUser: User) : CallState()
    data class InCall(
        val peerUser: User,
        val isMuted: Boolean = false,
        val isCameraOn: Boolean = true,
        val isFrontCamera: Boolean = true,
        val callDurationSeconds: Int = 0
    ) : CallState()
    data class CallEnded(val reason: String) : CallState()
}

package com.example.jamu_live.model

enum class UserStatus {
    ONLINE,
    OFFLINE,
    IN_CALL
}

data class User(
    val id: String,
    val name: String,
    val status: UserStatus = UserStatus.ONLINE,
    val avatarColorHex: String = "#3F51B5"
)

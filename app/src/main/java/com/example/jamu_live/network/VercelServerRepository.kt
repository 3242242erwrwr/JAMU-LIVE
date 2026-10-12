package com.example.jamu_live.network

import com.example.jamu_live.model.User
import com.example.jamu_live.model.UserStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class PollResult(
    val onlineUsers: List<User>,
    val callInfo: CallInfo?
)

data class CallInfo(
    val callId: String,
    val peerId: String,
    val peerName: String,
    val role: String, // "caller" or "callee"
    val state: String // "calling", "incoming", "incall"
)

class VercelServerRepository {

    // Default Vercel deployment URL (Can be updated to custom Vercel domain)
    var baseUrl: String = "https://jamu-live.vercel.app"

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun registerUser(user: User): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("id", user.id)
                put("name", user.name)
                put("avatarColorHex", user.avatarColorHex)
            }
            val request = Request.Builder()
                .url("$baseUrl/api/users")
                .post(json.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun pollStatus(userId: String): PollResult? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/api/call/check?userId=$userId")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val bodyStr = response.body?.string() ?: return@withContext null
                val json = JSONObject(bodyStr)

                val usersList = mutableListOf<User>()
                if (json.has("users")) {
                    val usersArray = json.getJSONArray("users")
                    for (i in 0 until usersArray.length()) {
                        val obj = usersArray.getJSONObject(i)
                        val id = obj.optString("id")
                        val name = obj.optString("name")
                        val color = obj.optString("avatarColorHex", "#3F51B5")
                        if (id.isNotBlank() && name.isNotBlank()) {
                            usersList.add(
                                User(
                                    id = id,
                                    name = name,
                                    status = UserStatus.ONLINE,
                                    avatarColorHex = color
                                )
                            )
                        }
                    }
                }

                var callInfo: CallInfo? = null
                if (json.has("call") && !json.isNull("call")) {
                    val callObj = json.getJSONObject("call")
                    callInfo = CallInfo(
                        callId = callObj.optString("callId"),
                        peerId = callObj.optString("peerId"),
                        peerName = callObj.optString("peerName"),
                        role = callObj.optString("role"),
                        state = callObj.optString("state")
                    )
                }

                PollResult(onlineUsers = usersList, callInfo = callInfo)
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun startCall(caller: User, target: User): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("callerId", caller.id)
                put("callerName", caller.name)
                put("targetId", target.id)
                put("targetName", target.name)
            }
            val request = Request.Builder()
                .url("$baseUrl/api/call/start")
                .post(json.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun respondCall(userId: String, action: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("userId", userId)
                put("action", action) // "accept", "decline", "end"
            }
            val request = Request.Builder()
                .url("$baseUrl/api/call/respond")
                .post(json.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}

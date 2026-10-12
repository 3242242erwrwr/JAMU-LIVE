package com.example.jamu_live.agora

import android.content.Context
import android.view.TextureView
import android.widget.FrameLayout
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig
import io.agora.rtc2.video.VideoCanvas

class AgoraManager private constructor() {

    var rtcEngine: RtcEngine? = null
    var remoteUid: Int = 0
    var isJoined: Boolean = false

    var onUserJoinedCallback: ((Int) -> Unit)? = null
    var onUserOfflineCallback: ((Int) -> Unit)? = null

    private val rtcEventHandler = object : IRtcEngineEventHandler() {
        override fun onUserJoined(uid: Int, elapsed: Int) {
            remoteUid = uid
            onUserJoinedCallback?.invoke(uid)
        }

        override fun onFirstRemoteVideoFrame(uid: Int, width: Int, height: Int, elapsed: Int) {
            remoteUid = uid
            onUserJoinedCallback?.invoke(uid)
        }

        @Deprecated("Deprecated in Java")
        override fun onFirstRemoteVideoDecoded(uid: Int, width: Int, height: Int, elapsed: Int) {
            remoteUid = uid
            onUserJoinedCallback?.invoke(uid)
        }

        override fun onUserOffline(uid: Int, reason: Int) {
            if (remoteUid == uid) {
                remoteUid = 0
            }
            onUserOfflineCallback?.invoke(uid)
        }

        override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
            isJoined = true
        }
    }

    fun init(context: Context, appId: String): Boolean {
        if (rtcEngine != null) return true
        return try {
            val config = RtcEngineConfig().apply {
                mContext = context.applicationContext
                mAppId = appId
                mEventHandler = rtcEventHandler
                mChannelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION
            }
            rtcEngine = RtcEngine.create(config)
            rtcEngine?.setChannelProfile(Constants.CHANNEL_PROFILE_COMMUNICATION)
            rtcEngine?.setClientRole(Constants.CLIENT_ROLE_BROADCASTER)
            rtcEngine?.enableVideo()
            rtcEngine?.enableAudio()
            rtcEngine?.startPreview()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun setupLocalVideo(context: Context, container: FrameLayout) {
        container.removeAllViews()
        val textureView = TextureView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        container.addView(textureView)

        rtcEngine?.enableVideo()
        rtcEngine?.startPreview()
        rtcEngine?.setupLocalVideo(VideoCanvas(textureView, VideoCanvas.RENDER_MODE_HIDDEN, 0))
    }

    fun setupRemoteVideo(context: Context, container: FrameLayout, uid: Int) {
        if (uid <= 0) return
        container.removeAllViews()
        val textureView = TextureView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        container.addView(textureView)

        rtcEngine?.setupRemoteVideo(VideoCanvas(textureView, VideoCanvas.RENDER_MODE_HIDDEN, uid))
    }

    fun joinChannel(channelName: String, uid: Int) {
        val validUid = if (uid <= 0) (Math.abs(uid) % 900000) + 10000 else uid
        rtcEngine?.setChannelProfile(Constants.CHANNEL_PROFILE_COMMUNICATION)
        rtcEngine?.setClientRole(Constants.CLIENT_ROLE_BROADCASTER)
        rtcEngine?.enableVideo()
        rtcEngine?.enableAudio()
        rtcEngine?.startPreview()
        rtcEngine?.joinChannel(null, channelName, "", validUid)
    }

    fun switchCamera() {
        rtcEngine?.switchCamera()
    }

    fun toggleCamera(enabled: Boolean) {
        rtcEngine?.enableLocalVideo(enabled)
        if (enabled) {
            rtcEngine?.startPreview()
        } else {
            rtcEngine?.stopPreview()
        }
    }

    fun toggleMute(muted: Boolean) {
        rtcEngine?.muteLocalAudioStream(muted)
    }

    fun leaveChannel() {
        try {
            rtcEngine?.stopPreview()
            rtcEngine?.leaveChannel()
            isJoined = false
            remoteUid = 0
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        val instance: AgoraManager by lazy { AgoraManager() }
        const val AGORA_APP_ID = "aab8521a00774a44b1c78eb35e076632" // Test App ID
    }
}

package com.example.jamu_live.agora

import android.content.Context
import android.view.SurfaceView
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
            rtcEngine?.enableVideo()
            rtcEngine?.enableAudio()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun setupLocalVideo(context: Context, container: FrameLayout) {
        container.removeAllViews()
        val surfaceView = SurfaceView(context)
        surfaceView.setZOrderMediaOverlay(true)
        container.addView(surfaceView)

        rtcEngine?.setupLocalVideo(VideoCanvas(surfaceView, VideoCanvas.RENDER_MODE_HIDDEN, 0))
        rtcEngine?.startPreview()
    }

    fun setupRemoteVideo(context: Context, container: FrameLayout, uid: Int) {
        container.removeAllViews()
        val surfaceView = SurfaceView(context)
        container.addView(surfaceView)

        rtcEngine?.setupRemoteVideo(VideoCanvas(surfaceView, VideoCanvas.RENDER_MODE_HIDDEN, uid))
    }

    fun joinChannel(channelName: String, uid: Int) {
        rtcEngine?.joinChannel(null, channelName, "ExtraData", uid)
    }

    fun switchCamera() {
        rtcEngine?.switchCamera()
    }

    fun toggleCamera(enabled: Boolean) {
        rtcEngine?.enableLocalVideo(enabled)
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

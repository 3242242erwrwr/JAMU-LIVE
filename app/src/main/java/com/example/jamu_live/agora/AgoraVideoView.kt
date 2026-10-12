package com.example.jamu_live.agora

import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun AgoraLocalVideoView(
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { ctx ->
            FrameLayout(ctx).also { container ->
                AgoraManager.instance.setupLocalVideo(ctx, container)
            }
        },
        modifier = modifier
    )
}

@Composable
fun AgoraRemoteVideoView(
    remoteUid: Int,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { ctx ->
            FrameLayout(ctx).also { container ->
                if (remoteUid != 0) {
                    AgoraManager.instance.setupRemoteVideo(ctx, container, remoteUid)
                }
            }
        },
        update = { container ->
            if (remoteUid != 0) {
                AgoraManager.instance.setupRemoteVideo(container.context, container, remoteUid)
            } else {
                container.removeAllViews()
            }
        },
        modifier = modifier
    )
}

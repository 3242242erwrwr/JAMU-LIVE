package com.example.jamu_live.ui.screens

import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.jamu_live.agora.AgoraLocalVideoView
import com.example.jamu_live.agora.AgoraManager
import com.example.jamu_live.agora.AgoraRemoteVideoView
import com.example.jamu_live.model.CallState
import com.example.jamu_live.model.User
import java.util.Locale

@Composable
fun VideoCallScreen(
    callState: CallState,
    currentUserId: String,
    onToggleMute: () -> Unit,
    onToggleCamera: () -> Unit,
    onSwitchCamera: () -> Unit,
    onEndCall: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when (callState) {
            is CallState.OutgoingCalling -> {
                OutgoingCallingView(
                    targetUser = callState.targetUser,
                    onEndCall = onEndCall
                )
            }

            is CallState.InCall -> {
                ActiveVideoCallView(
                    inCallState = callState,
                    currentUserId = currentUserId,
                    onToggleMute = onToggleMute,
                    onToggleCamera = onToggleCamera,
                    onSwitchCamera = onSwitchCamera,
                    onEndCall = onEndCall
                )
            }

            is CallState.CallEnded -> {
                CallEndedView(reason = callState.reason)
            }

            else -> {}
        }
    }
}

@Composable
fun OutgoingCallingView(
    targetUser: User,
    onEndCall: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF1F1C2C), Color(0xFF928DAB))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            Box(
                modifier = Modifier
                    .scale(scale)
                    .size(120.dp)
                    .background(Color(0xFF6C63FF).copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .background(Color(0xFF6C63FF), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = targetUser.name.take(1).uppercase(),
                        color = Color.White,
                        fontSize = 42.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = targetUser.name,
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Qo'ng'iroq qilinmoqda...",
                color = Color.LightGray,
                fontSize = 16.sp
            )

            Spacer(modifier = Modifier.height(60.dp))

            FilledIconButton(
                onClick = onEndCall,
                modifier = Modifier.size(72.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Color(0xFFF44336)
                )
            ) {
                Icon(
                    imageVector = Icons.Default.CallEnd,
                    contentDescription = "Tugatish",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

@Composable
fun ActiveVideoCallView(
    inCallState: CallState.InCall,
    currentUserId: String,
    onToggleMute: () -> Unit,
    onToggleCamera: () -> Unit,
    onSwitchCamera: () -> Unit,
    onEndCall: () -> Unit
) {
    val context = LocalContext.current
    val peer = inCallState.peerUser
    var remoteUid by remember { mutableIntStateOf(0) }

    LaunchedEffect(inCallState) {
        // Unbind CameraX so Agora RTC Engine gets exclusive access to camera hardware
        try {
            val cameraProvider = ProcessCameraProvider.getInstance(context).get()
            cameraProvider.unbindAll()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val agora = AgoraManager.instance
        agora.init(context, AgoraManager.AGORA_APP_ID)

        agora.onUserJoinedCallback = { uid ->
            remoteUid = uid
        }
        agora.onUserOfflineCallback = { _ ->
            remoteUid = 0
        }

        val myUid = (Math.abs(currentUserId.hashCode()) % 900000) + 10000
        val channelName = "jamu_call_${minOf(currentUserId, peer.id)}_${maxOf(currentUserId, peer.id)}"
        agora.joinChannel(channelName, myUid)
    }

    DisposableEffect(Unit) {
        onDispose {
            AgoraManager.instance.leaveChannel()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Main Full Screen View -> Remote User's Real Live Video
        if (remoteUid != 0) {
            AgoraRemoteVideoView(
                remoteUid = remoteUid,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Local video view or placeholder while peer is connecting
            if (inCallState.isCameraOn) {
                AgoraLocalVideoView(modifier = Modifier.fillMaxSize())
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color(0xFF1A1A24), Color(0xFF0F0F14))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(130.dp)
                                .background(Color(0xFF3F51B5), CircleShape)
                                .border(4.dp, Color(0xFF6C63FF), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = peer.name.take(1).uppercase(),
                                color = Color.White,
                                fontSize = 50.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = peer.name,
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Box(
                            modifier = Modifier
                                .background(Color(0xFF4CAF50).copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Ulanyapti...",
                                color = Color(0xFF4CAF50),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // Top gradient overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .align(Alignment.TopCenter)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)
                    )
                )
        )

        // Bottom gradient overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .align(Alignment.BottomCenter)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                    )
                )
        )

        // Top Header Info Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 48.dp, start = 20.dp, end = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color(0xFF3F51B5), CircleShape)
                        .border(2.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = peer.name.take(1).uppercase(),
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.size(10.dp))
                Column {
                    Text(
                        text = peer.name,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = formatDuration(inCallState.callDurationSeconds),
                        color = Color(0xFF4CAF50),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Box(
                modifier = Modifier
                    .background(Color.Red.copy(alpha = 0.85f), CircleShape)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "LIVE",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Floating Picture-In-Picture (PiP) Window -> Local Camera View
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 110.dp, end = 16.dp)
                .size(width = 115.dp, height = 155.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(2.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
                .background(Color.Black)
        ) {
            if (inCallState.isCameraOn && remoteUid != 0) {
                AgoraLocalVideoView(modifier = Modifier.fillMaxSize())
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF1E1E2C)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Siz",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (remoteUid != 0) "Jonli" else "Kutmoqda",
                            color = Color(0xFF4CAF50),
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }

        // Bottom Controls Action Bar
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 32.dp, start = 20.dp, end = 20.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E1E2C).copy(alpha = 0.95f), RoundedCornerShape(32.dp))
                    .padding(vertical = 14.dp, horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mute
                IconButton(
                    onClick = {
                        onToggleMute()
                        AgoraManager.instance.toggleMute(inCallState.isMuted)
                    },
                    modifier = Modifier
                        .size(50.dp)
                        .background(
                            if (inCallState.isMuted) Color(0xFFF44336) else Color(0xFF333344),
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = if (inCallState.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = "Mute",
                        tint = Color.White
                    )
                }

                // Camera Toggle
                IconButton(
                    onClick = {
                        onToggleCamera()
                        AgoraManager.instance.toggleCamera(!inCallState.isCameraOn)
                    },
                    modifier = Modifier
                        .size(50.dp)
                        .background(
                            if (!inCallState.isCameraOn) Color(0xFFF44336) else Color(0xFF333344),
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = if (inCallState.isCameraOn) Icons.Default.Videocam else Icons.Default.VideocamOff,
                        contentDescription = "Camera Toggle",
                        tint = Color.White
                    )
                }

                // Switch Camera
                IconButton(
                    onClick = {
                        onSwitchCamera()
                        AgoraManager.instance.switchCamera()
                    },
                    enabled = inCallState.isCameraOn,
                    modifier = Modifier
                        .size(50.dp)
                        .background(
                            if (inCallState.isCameraOn) Color(0xFF333344) else Color.Gray.copy(alpha = 0.3f),
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Cameraswitch,
                        contentDescription = "Switch Camera",
                        tint = Color.White
                    )
                }

                // End Call
                IconButton(
                    onClick = {
                        AgoraManager.instance.leaveChannel()
                        onEndCall()
                    },
                    modifier = Modifier
                        .size(56.dp)
                        .background(Color(0xFFF44336), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.CallEnd,
                        contentDescription = "End Call",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CallEndedView(reason: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF11111A)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.CallEnd,
                contentDescription = null,
                tint = Color(0xFFF44336),
                modifier = Modifier.size(60.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = reason,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun formatDuration(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", mins, secs)
}

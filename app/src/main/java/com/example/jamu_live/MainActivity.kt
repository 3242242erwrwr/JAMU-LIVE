package com.example.jamu_live

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.example.jamu_live.model.CallState
import com.example.jamu_live.ui.components.IncomingCallDialog
import com.example.jamu_live.ui.screens.FriendsListScreen
import com.example.jamu_live.ui.screens.LoginScreen
import com.example.jamu_live.ui.screens.VideoCallScreen
import com.example.jamu_live.ui.theme.JAMULIVETheme
import com.example.jamu_live.viewmodel.CallViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: CallViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            JAMULIVETheme {
                MainAppContent(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun MainAppContent(viewModel: CallViewModel) {
    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsState()
    val friends by viewModel.friendsList.collectAsState()
    val callState by viewModel.callState.collectAsState()

    // Permission launcher for Camera and Audio
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        if (cameraGranted && audioGranted) {
            // Permissions granted successfully
        }
    }

    fun requestPermissionsIfNeeded(): Boolean {
        val cameraCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
        val audioCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
        val allGranted = cameraCheck == PackageManager.PERMISSION_GRANTED && audioCheck == PackageManager.PERMISSION_GRANTED

        if (!allGranted) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO
                )
            )
        }
        return allGranted
    }

    LaunchedEffect(Unit) {
        requestPermissionsIfNeeded()
    }

    // Always check permissions when entering video call
    LaunchedEffect(callState) {
        if (callState is CallState.OutgoingCalling || callState is CallState.InCall) {
            requestPermissionsIfNeeded()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val user = currentUser
        if (user == null) {
            LoginScreen { name ->
                viewModel.login(name)
            }
        } else {
            when (val state = callState) {
                is CallState.Idle, is CallState.IncomingCall -> {
                    FriendsListScreen(
                        currentUser = user,
                        friends = friends,
                        onStartCall = { targetUser ->
                            requestPermissionsIfNeeded()
                            viewModel.startCall(targetUser)
                        },
                        onSimulateIncomingCall = { friend ->
                            viewModel.simulateIncomingCall(friend)
                        },
                        onAddFriend = { name ->
                            viewModel.addFriend(name)
                        },
                        onLogout = {
                            viewModel.logout()
                        }
                    )

                    if (state is CallState.IncomingCall) {
                        IncomingCallDialog(
                            caller = state.callerUser,
                            onAccept = {
                                requestPermissionsIfNeeded()
                                viewModel.acceptCall()
                            },
                            onDecline = { viewModel.declineCall() }
                        )
                    }
                }

                is CallState.OutgoingCalling, is CallState.InCall, is CallState.CallEnded -> {
                    VideoCallScreen(
                        callState = state,
                        currentUserId = user.id,
                        onToggleMute = { viewModel.toggleMute() },
                        onToggleCamera = { viewModel.toggleCamera() },
                        onSwitchCamera = { viewModel.switchCamera() },
                        onEndCall = { viewModel.endCall() }
                    )
                }
            }
        }
    }
}

package com.example.jamu_live

import android.Manifest
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
    val currentUser by viewModel.currentUser.collectAsState()
    val friends by viewModel.friendsList.collectAsState()
    val callState by viewModel.callState.collectAsState()

    // Request permissions launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        if (cameraGranted && audioGranted) {
            // Permissions granted
        }
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            )
        )
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
                            onAccept = { viewModel.acceptCall() },
                            onDecline = { viewModel.declineCall() }
                        )
                    }
                }

                is CallState.OutgoingCalling, is CallState.InCall, is CallState.CallEnded -> {
                    VideoCallScreen(
                        callState = state,
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

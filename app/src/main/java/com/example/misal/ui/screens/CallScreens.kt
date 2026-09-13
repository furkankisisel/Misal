package com.example.misal.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.misal.domain.model.Call
import com.example.misal.ui.viewmodel.CallViewModel
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

@Composable
fun IncomingCallScreen(
    call: Call,
    callerName: String,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E1E)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = if (call.type == "video") Icons.Default.Videocam else Icons.Default.Call,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = callerName,
                color = Color.White,
                fontSize = 24.sp
            )
            Text(
                text = "Gelen ${if (call.type == "video") "Görüntülü " else ""}Arama",
                color = Color.Gray,
                fontSize = 16.sp
            )

            Spacer(modifier = Modifier.height(64.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                FloatingActionButton(
                    onClick = onReject,
                    containerColor = Color.Red,
                    contentColor = Color.White,
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.CallEnd, contentDescription = "Reddet")
                }

                FloatingActionButton(
                    onClick = onAccept,
                    containerColor = Color.Green,
                    contentColor = Color.White,
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.Call, contentDescription = "Cevapla")
                }
            }
        }
    }
}

@Composable
fun ActiveCallScreen(
    callViewModel: CallViewModel,
    call: Call,
    onEndCall: () -> Unit
) {
    val remoteStream by callViewModel.remoteStream.collectAsState()
    var isMuted by remember { mutableStateOf(false) }
    var isVideoEnabled by remember { mutableStateOf(call.type == "video") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (call.type == "video") {
            // Remote Video Full Screen
            AndroidView(
                factory = { context ->
                    SurfaceViewRenderer(context).apply {
                        callViewModel.webRtcManager.initSurfaceView(this)
                        setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                    }
                },
                update = { renderer ->
                    val videoTrack = remoteStream?.videoTracks?.firstOrNull() as? VideoTrack
                    videoTrack?.addSink(renderer)
                },
                modifier = Modifier.fillMaxSize()
            )

            // Local Video (PiP) - just attaches renderer, capture already started
            if (isVideoEnabled) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .size(width = 100.dp, height = 150.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(Color.DarkGray)
                ) {
                    AndroidView(
                        factory = { context ->
                            SurfaceViewRenderer(context).apply {
                                setZOrderMediaOverlay(true)
                                setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                                callViewModel.webRtcManager.attachLocalRenderer(this)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        } else {
            // Audio Call UI
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.Call,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(80.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = if (call.status == "accepted") "Konuşuluyor..." else "Çalıyor...",
                    color = Color.White,
                    fontSize = 20.sp
                )
            }
        }

        // Call Controls
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 48.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            FloatingActionButton(
                onClick = { 
                    isMuted = !isMuted
                    callViewModel.webRtcManager.toggleMute(isMuted)
                },
                containerColor = if (isMuted) Color.White else Color.DarkGray,
                contentColor = if (isMuted) Color.Black else Color.White,
                shape = CircleShape
            ) {
                Icon(if (isMuted) Icons.Default.MicOff else Icons.Default.Mic, contentDescription = "Mute")
            }

            if (call.type == "video") {
                FloatingActionButton(
                    onClick = { 
                        isVideoEnabled = !isVideoEnabled
                        callViewModel.webRtcManager.toggleVideo(isVideoEnabled)
                    },
                    containerColor = if (!isVideoEnabled) Color.White else Color.DarkGray,
                    contentColor = if (!isVideoEnabled) Color.Black else Color.White,
                    shape = CircleShape
                ) {
                    Icon(if (!isVideoEnabled) Icons.Default.VideocamOff else Icons.Default.Videocam, contentDescription = "Video")
                }

                FloatingActionButton(
                    onClick = { 
                        callViewModel.webRtcManager.switchCamera()
                    },
                    containerColor = Color.DarkGray,
                    contentColor = Color.White,
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.Cameraswitch, contentDescription = "Switch Camera")
                }
            }

            FloatingActionButton(
                onClick = {
                    callViewModel.endCall()
                    onEndCall()
                },
                containerColor = Color.Red,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(Icons.Default.CallEnd, contentDescription = "Kapat")
            }
        }
    }
}

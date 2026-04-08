package com.qunes.app.presentation.call

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.qunes.app.presentation.ui.theme.*

@Composable
fun CallScreen(
    onCallEnded: () -> Unit,
    viewModel: CallViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Box(modifier = Modifier.fillMaxSize().background(BackgroundDeep)) {
        // Placeholder for Remote Video Surface (Mediasoup Producer)
        Box(modifier = Modifier.fillMaxSize()) {
            Text(
                "WAITING FOR MESH SIGNAL...",
                color = Color.DarkGray,
                modifier = Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.labelSmall
            )
        }

        // Quantum Protection UI Overlay
        Column(modifier = Modifier.fillMaxSize()) {
            // TOP BAR: Security Details (Step 32 Requirement)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(SecureGreen.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = SecureGreen,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        state.connectionStatus,
                        color = WhiteText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        "CRYPTO: ${state.protectionLevel} / E2EE: ACTIVE",
                        color = SecureGreen,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // PIP Preview
            Box(
                modifier = Modifier
                    .padding(16.dp)
                    .size(100.dp, 140.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceDark)
                    .align(Alignment.End),
                contentAlignment = Alignment.Center
            ) {
                if (state.isVideoEnabled) {
                    Text("PQC-V01", fontSize = 8.sp, color = MutedText)
                } else {
                    Icon(Icons.Default.VideocamOff, contentDescription = null, tint = GhostGrey)
                }
            }

            // Bottom Action Hub
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.8f))
                    .padding(vertical = 32.dp, horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { viewModel.toggleMute() },
                    modifier = Modifier.size(56.dp).clip(CircleShape).background(if (state.isMuted) Color.White else GhostGrey)
                ) {
                    Icon(
                        imageVector = if (state.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = null,
                        tint = if (state.isMuted) Color.Black else Color.White
                    )
                }

                FloatingActionButton(
                    onClick = { viewModel.endCall(); onCallEnded() },
                    containerColor = ErrorRed,
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier.size(72.dp)
                ) {
                    Icon(Icons.Default.CallEnd, contentDescription = "Terminate Tunnel", modifier = Modifier.size(32.dp))
                }

                IconButton(
                    onClick = { viewModel.toggleVideo() },
                    modifier = Modifier.size(56.dp).clip(CircleShape).background(if (!state.isVideoEnabled) Color.White else GhostGrey)
                ) {
                    Icon(
                        imageVector = if (state.isVideoEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                        contentDescription = null,
                        tint = if (!state.isVideoEnabled) Color.Black else Color.White
                    )
                }
            }
        }
    }
}
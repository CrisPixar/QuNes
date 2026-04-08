package com.qunes.app.presentation.auth

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.qunes.app.presentation.ui.theme.BackgroundDeep
import com.qunes.app.presentation.ui.theme.QuantumCyan
import com.qunes.app.presentation.ui.theme.SurfaceDark

@Composable
fun AuthScreen(
    onAuthorized: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    
    LaunchedEffect(state.authorized) {
        if (state.authorized) onAuthorized()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDeep),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            QuantumLogoAnimation(active = state.isGeneratingKeys)
            
            Spacer(modifier = Modifier.height(40.dp))
            
            Text(
                text = if (state.isGeneratingKeys) "GENERATING IDENTITY" else "QUNES MESH",
                style = MaterialTheme.typography.headlineMedium,
                color = QuantumCyan,
                letterSpacing = 4.sp
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = if (state.isGeneratingKeys) state.status else "SECURE DECENTRALIZED PROTOCOL",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(48.dp))

            if (!state.isGeneratingKeys) {
                Button(
                    onClick = { viewModel.startIdentityGenesis() },
                    colors = ButtonDefaults.buttonColors(containerColor = QuantumCyan),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text("ENTER TUNNEL", fontWeight = FontWeight.Bold, color = Color.Black)
                }
            } else {
                LinearProgressIndicator(
                    progress = state.progress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = QuantumCyan,
                    trackColor = SurfaceDark
                )
            }
            
            state.error?.let {
                Spacer(modifier = Modifier.height(16.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun QuantumLogoAnimation(active: Boolean) {
    val transition = rememberInfiniteTransition()
    val rotate by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation =tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    val pulse by transition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Canvas(modifier = Modifier.size(120.dp)) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(QuantumCyan.copy(alpha = 0.2f * pulse), Color.Transparent)
            ),
            radius = size.minDimension / 1.5f
        )
        
        rotate(rotate) {
            drawArc(
                color = QuantumCyan,
                startAngle = 0f,
                sweepAngle = 90f,
                useCenter = false,
                style = Stroke(width = 4.dp.toPx()),
                size = size
            )
            drawArc(
                color = QuantumCyan,
                startAngle = 180f,
                sweepAngle = 90f,
                useCenter = false,
                style = Stroke(width = 4.dp.toPx()),
                size = size
            )
        }
    }
}
package com.qunes.app.presentation.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.qunes.app.presentation.ui.theme.BackgroundDeep
import com.qunes.app.presentation.ui.theme.QuantumCyan
import com.qunes.app.presentation.ui.theme.SurfaceDark

@Composable
fun ProfileScreen(viewModel: ProfileViewModel = hiltViewModel()) {
    val ghostMode by viewModel.ghostMode.collectAsState()
    val hideLastSeen by viewModel.hideLastSeen.collectAsState()
    val currentAccent by viewModel.accentColor.collectAsState()

    val accentPalette = listOf(
        "#00E5FF", "#7C4DFF", "#00C853", "#FF3D00", "#FFAB00", "#F48FB1"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDeep)
            .padding(24.dp)
    ) {
        Text(
            text = "QUANTUM PROFILE",
            style = MaterialTheme.typography.headlineMedium,
            color = QuantumCyan,
            letterSpacing = 2.sp
        )
        
        Spacer(modifier = Modifier.height(32.dp))

        // Privacy Selection Section
        SectionHeader("SECURITY & PRIVACY")
        PrivacyToggle(
            "GHOST MODE",
            "SUPPRESSES ALL REAL-TIME SIGNALS",
            ghostMode,
            onToggle = { viewModel.toggleGhostMode(it) }
        )
        PrivacyToggle(
            "HIDE LAST SEEN",
            "DISBLES TIME SYNC IN MESH NODE",
            hideLastSeen,
            onToggle = { viewModel.toggleHideLastSeen(it) }
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Customization Section
        SectionHeader("UI CUSTOMIZATION")
        Text("ACCENT COLOR", style = MaterialTheme.typography.labelSmall, color = Color.Gray, modifier = Modifier.padding(bottom = 8.dp))
        
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(accentPalette) { hex ->
                val color = Color(android.graphics.Color.parseColor(hex))
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(
                            width = if (currentAccent == hex) 3.dp else 0.dp,
                            color = Color.White,
                            shape = CircleShape
                        )
                        .clickable { viewModel.setAccentColor(hex) }
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = Color.DarkGray,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 16.dp)
    )
}

@Composable
private fun PrivacyToggle(label: String, subLabel: String, enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceDark)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.Bold, color = Color.White)
            Text(subLabel, style = MaterialTheme.typography.labelSmall, color = Color.Gray, fontSize = 10.sp)
        }
        Switch(
            checked = enabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(checkedThumbColor = QuantumCyan)
        )
    }
}
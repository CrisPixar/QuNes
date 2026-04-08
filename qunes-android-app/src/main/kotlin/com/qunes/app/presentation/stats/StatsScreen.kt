package com.qunes.app.presentation.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.qunes.app.presentation.ui.theme.BackgroundDeep
import com.qunes.app.presentation.ui.theme.QuantumCyan
import com.qunes.app.presentation.ui.theme.SecureGreen
import com.qunes.app.presentation.ui.theme.SurfaceDark

@Composable
fun StatsScreen(viewModel: StatsViewModel = hiltViewModel()) {
    val history by viewModel.history.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDeep)
            .padding(16.dp)
    ) {
        Text(
            "TRAFFIC LOGS",
            style = MaterialTheme.typography.headlineMedium,
            color = QuantumCyan
        )
        Spacer(modifier = Modifier.height(24.dp))
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(SurfaceDark)
                .padding(8.dp)
        ) {
            TrafficChart(history)
        }

        Spacer(modifier = Modifier.height(16.dp))
        
        TrafficIndicator("UPLOADING", SecureGreen, history.lastOrNull()?.upload ?: 0L)
        TrafficIndicator("DOWNLOADING", QuantumCyan, history.lastOrNull()?.download ?: 0L)
    }
}

@Composable
fun TrafficChart(points: List<StatsViewModel.TrafficPoint>) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        if (points.isEmpty()) return@Canvas

        val max = (points.maxOf { maxOf(it.upload, it.download) }).coerceAtLeast(1024L)
        val width = size.width
        val height = size.height
        val step = width / 60

        fun drawLine(selector: (StatsViewModel.TrafficPoint) -> Long, color: Color) {
            val path = Path()
            points.forEachIndexed { i, p ->
                val x = width - (points.size - 1 - i) * step
                val y = height - (selector(p).toFloat() / max * height)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color, style = Stroke(width = 2.dp.toPx()))
        }

        drawLine({ it.upload }, SecureGreen)
        drawLine({ it.download }, QuantumCyan)
    }
}

@Composable
fun TrafficIndicator(label: String, color: Color, bytes: Long) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        Text("${bytes / 1024} KB/s", style = MaterialTheme.typography.labelSmall, color = color)
    }
}
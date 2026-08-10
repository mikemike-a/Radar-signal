package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.RadarViewModel
import com.example.ui.theme.*

@Composable
fun HeatmapWidget(viewModel: RadarViewModel) {
    val heatmapData by viewModel.heatmapData.collectAsStateWithLifecycle()
    val huntSignalLost by viewModel.huntSignalLost.collectAsStateWithLifecycle()
    val gridSize = 7 // 7x7 grid

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Carte Thermique (RSSI)", fontWeight = FontWeight.Bold, color = TextPrimary)
        Text("Touchez une case où vous vous trouvez", fontSize = 12.sp, color = TextSecondary)
        Spacer(modifier = Modifier.height(16.dp))
        
        Box(
            modifier = Modifier
                .border(1.dp, CardBorder, RoundedCornerShape(8.dp))
                .background(DarkSurface, RoundedCornerShape(8.dp))
                .padding(8.dp)
        ) {
            Column {
                for (y in 0 until gridSize) {
                    Row {
                        for (x in 0 until gridSize) {
                            val recordedRssi = heatmapData[Pair(x, y)]
                            val cellColor = when {
                                recordedRssi == null -> ObsidianBg
                                recordedRssi >= -60 -> WarmRed.copy(alpha = 0.8f) // Hot
                                recordedRssi >= -75 -> OrangeAccent.copy(alpha = 0.8f) // Warm
                                recordedRssi >= -90 -> NeonCyan.copy(alpha = 0.6f) // Cool
                                else -> Color.Blue.copy(alpha = 0.4f) // Cold
                            }
                            
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .padding(2.dp)
                                    .background(cellColor, RoundedCornerShape(4.dp))
                                    .border(0.5.dp, CardBorder, RoundedCornerShape(4.dp))
                                    .clickable(enabled = !huntSignalLost) {
                                        viewModel.recordHeatmapPoint(x, y)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (recordedRssi != null) {
                                    Text(
                                        text = recordedRssi.toString(),
                                        fontSize = 11.sp,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(
            onClick = { viewModel.clearHeatmap() },
            colors = ButtonDefaults.textButtonColors(contentColor = WarmRed)
        ) {
            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Effacer la carte")
        }
    }
}

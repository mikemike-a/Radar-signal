package com.example.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun ARFinderScreen(viewModel: RadarViewModel) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Placeholder for AR View (in a real app, this would be an ARScene)
        Text(text = "Interface de Recherche en Réalité Augmentée", color = Color.White, modifier = Modifier.align(Alignment.Center))
        
        IconButton(
            onClick = { viewModel.toggleArMode(false) },
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
        ) {
            Icon(Icons.Default.Close, contentDescription = "Quitter AR", tint = Color.White)
        }
    }
}

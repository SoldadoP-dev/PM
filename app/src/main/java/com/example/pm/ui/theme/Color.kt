package com.example.pm.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Paleta Principal (Vibrante)
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

// Fondo y Superficies (Dark Mode Premium)
val DeepSpace = Color(0xFF000000)
val GlassColor = Color(0xFF121212).copy(alpha = 0.7f)
val CardGray = Color(0xFF1A1A1A)

// Acentos
val NeonPurple = Color(0xFFBC00FF)
val NeonPink = Color(0xFFFF007F)
val SoftCyan = Color(0xFF00E5FF)

// Gradientes Estilo Instagram / Moderno
val InstaGradient = Brush.linearGradient(
    colors = listOf(
        Color(0xFF833AB4), // Morado
        Color(0xFFC13584), // Magenta
        Color(0xFFE1306C), // Rosa Intenso
        Color(0xFFFD1D1D), // Rojo
        Color(0xFFF77737)  // Naranja
    )
)

val PremiumGradient = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF2C3E50).copy(alpha = 0.8f),
        Color(0xFF000000)
    )
)

val ActionGradient = Brush.horizontalGradient(
    colors = listOf(NeonPurple, NeonPink)
)

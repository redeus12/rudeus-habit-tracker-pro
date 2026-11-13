package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import com.example.ui.theme.MossGreen
import com.example.ui.theme.TealBlue
import com.example.ui.theme.VioletPurple
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private data class ConfettiParticle(
    val initialXRatio: Float,
    val initialYRatio: Float,
    val initialVx: Float,
    val initialVy: Float,
    val width: Float,
    val height: Float,
    val color: Color,
    val rotationSpeed: Float,
    val wobbleFrequency: Float,
    val wobblePhase: Float,
    val isCircle: Boolean
)

@Composable
fun ConfettiOverlay(
    triggerKey: Long,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    var activeKey by remember { mutableStateOf(0L) }
    val progress = remember { Animatable(0f) }
    var particles by remember { mutableStateOf<List<ConfettiParticle>>(emptyList()) }

    val colors = remember(primary) {
        listOf(
            primary,
            primary.copy(alpha = 0.85f),
            Color(0xFFFFD54F),
            MossGreen,
            TealBlue,
            VioletPurple,
            Color(0xFFFF7043),
            Color(0xFFE0E0E0),
            Color(0xFF81C784)
        )
    }

    LaunchedEffect(triggerKey) {
        if (triggerKey > 0 && triggerKey != activeKey) {
            activeKey = triggerKey
            val random = Random(triggerKey)
            val newParticles = (0..80).map {
                ConfettiParticle(
                    initialXRatio = 0.5f + (random.nextFloat() - 0.5f) * 0.4f,
                    initialYRatio = 0.55f + (random.nextFloat() - 0.5f) * 0.2f,
                    initialVx = (random.nextFloat() - 0.5f) * 1100f,
                    initialVy = -700f - random.nextFloat() * 850f,
                    width = 12f + random.nextFloat() * 16f,
                    height = 8f + random.nextFloat() * 12f,
                    color = colors[random.nextInt(colors.size)],
                    rotationSpeed = (random.nextFloat() - 0.5f) * 720f,
                    wobbleFrequency = 4f + random.nextFloat() * 6f,
                    wobblePhase = random.nextFloat() * 6.28f,
                    isCircle = random.nextBoolean()
                )
            }
            particles = newParticles
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 2400, easing = LinearEasing)
            )
            particles = emptyList()
        }
    }

    if (particles.isNotEmpty() && progress.value < 1f) {
        val t = progress.value
        val alpha = if (t < 0.7f) 1f else (1f - (t - 0.7f) / 0.3f).coerceIn(0f, 1f)
        val gravity = 1800f

        Canvas(modifier = modifier.fillMaxSize()) {
            val canvasW = size.width
            val canvasH = size.height

            particles.forEach { p ->
                val timeSec = t * 2.4f
                val startX = p.initialXRatio * canvasW
                val startY = p.initialYRatio * canvasH

                val wobble = sin(timeSec * p.wobbleFrequency + p.wobblePhase) * 45f
                val currentX = startX + (p.initialVx * timeSec) + wobble
                val currentY = startY + (p.initialVy * timeSec) + (0.5f * gravity * timeSec * timeSec)

                if (currentY <= canvasH + 50 && currentX >= -50 && currentX <= canvasW + 50) {
                    val rotation = timeSec * p.rotationSpeed
                    val particleAlpha = alpha * (p.color.alpha)

                    rotate(degrees = rotation, pivot = Offset(currentX, currentY)) {
                        if (p.isCircle) {
                            drawCircle(
                                color = p.color.copy(alpha = particleAlpha),
                                radius = p.width / 2f,
                                center = Offset(currentX, currentY)
                            )
                        } else {
                            drawRect(
                                color = p.color.copy(alpha = particleAlpha),
                                topLeft = Offset(currentX - p.width / 2f, currentY - p.height / 2f),
                                size = Size(p.width, p.height)
                            )
                        }
                    }
                }
            }
        }
    }
}

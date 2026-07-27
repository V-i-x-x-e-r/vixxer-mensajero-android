package dev.vixxer.mensajero.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale

@Composable
fun Modifier.pulsable(
    habilitado: Boolean = true,
    hundido: Float = 0.97f,
    alPulsar: () -> Unit,
): Modifier
{
    val interaccion = remember { MutableInteractionSource() }
    val presionado by interaccion.collectIsPressedAsState()
    val escala by animateFloatAsState(
        targetValue = if (presionado) hundido else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh),
        label = "escalaPulsable",
    )
    return this
        .scale(escala)
        .clickable(
            enabled = habilitado,
            interactionSource = interaccion,
            indication = null,
            onClick = alPulsar,
        )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.pulsableLargo(
    hundido: Float = 0.985f,
    alMantener: () -> Unit,
    alPulsar: () -> Unit,
): Modifier
{
    val interaccion = remember { MutableInteractionSource() }
    val presionado by interaccion.collectIsPressedAsState()
    val escala by animateFloatAsState(
        targetValue = if (presionado) hundido else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh),
        label = "escalaPulsableLargo",
    )
    return this
        .scale(escala)
        .combinedClickable(
            interactionSource = interaccion,
            indication = null,
            onClick = alPulsar,
            onLongClick = alMantener,
        )
}

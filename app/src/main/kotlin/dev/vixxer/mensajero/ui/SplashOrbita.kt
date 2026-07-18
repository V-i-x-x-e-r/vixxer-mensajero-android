package dev.vixxer.mensajero.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val CX = 150f
private const val CY = 150f
private const val R = 76f
private const val SP = 50f

private val entradaSalidaCubica = Easing { t ->
    if (t < 0.5f) 4f * t * t * t
    else 1f - (-2f * t + 2f) * (-2f * t + 2f) * (-2f * t + 2f) / 2f
}

private fun DrawScope.orbe(indice: Int, avance: Float, radio: Float)
{
    val conv = 1f - (1f - avance) * (1f - avance) * (1f - avance)
    val grados = (indice * 72f) + 700f * (1f - (1f - avance) * (1f - avance))
    val angulo = grados * PI.toFloat() / 180f
    val ox = CX + R * cos(angulo)
    val oy = CY + R * sin(angulo)
    val lx = CX + (indice - 2) * SP
    val x = ox + (lx - ox) * conv
    val y = oy + (CY - oy) * conv
    bola(x, y, radio, central = indice == 2)
}

@Composable
fun SplashOrbita(fondo: Color = Color(0xFF0C1015), listoParaSalir: Boolean = true, alTerminar: () -> Unit)
{
    val avance = remember { Animatable(0f) }
    val opacidadV = remember { Animatable(0f) }
    val visible = remember { Animatable(1f) }
    val salir by rememberUpdatedState(listoParaSalir)

    LaunchedEffect(Unit)
    {
        launch { avance.animateTo(1f, tween(2200, easing = entradaSalidaCubica)) }
        launch {
            delay(1700)
            opacidadV.animateTo(1f, tween(520))
        }
        delay(2900)
        snapshotFlow { salir }.first { it }
        visible.animateTo(0f, tween(520))
        alTerminar()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(visible.value)
            .background(fondo),
        contentAlignment = Alignment.Center,
    )
    {
        Canvas(modifier = Modifier.size(220.dp))
        {
            val escala = size.width / 300f
            withTransform({ scale(escala, escala, pivot = Offset.Zero) })
            {
                orbe(0, avance.value, 19f)
                orbe(1, avance.value, 19f)
                orbe(3, avance.value, 19f)
                orbe(4, avance.value, 19f)
                orbe(2, avance.value, 20f)
            }
        }
        Box(
            modifier = Modifier
                .size(220.dp)
                .alpha(opacidadV.value),
            contentAlignment = Alignment.Center,
        )
        {
            Canvas(modifier = Modifier.fillMaxSize())
            {
                val escala = size.width / 300f
                withTransform({ scale(escala, escala, pivot = Offset.Zero) })
                {
                    vCincelada(CX, CY, 20f)
                }
            }
        }
    }
}

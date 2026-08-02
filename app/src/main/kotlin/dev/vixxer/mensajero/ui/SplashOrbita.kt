package dev.vixxer.mensajero.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
private const val SP = 40f
private const val LADO_SPLASH = 220f

private val entradaSalidaCubica = Easing { t ->
    if (t < 0.5f)
    {
        4f * t * t * t
    }
    else
    {
        1f - (-2f * t + 2f) * (-2f * t + 2f) * (-2f * t + 2f) / 2f
    }
}

private fun posicionOrbe(indice: Int, avance: Float): Offset
{
    val conv = 1f - (1f - avance) * (1f - avance) * (1f - avance)
    val grados = (indice * 72f) + 700f * (1f - (1f - avance) * (1f - avance))
    val angulo = grados * PI.toFloat() / 180f
    val ox = CX + R * cos(angulo)
    val oy = CY + R * sin(angulo)
    val lx = CX + (indice - 2) * SP
    val x = ox + (lx - ox) * conv
    val y = oy + (CY - oy) * conv
    return Offset(x, y)
}

@Composable
private fun BoxScope.OrbeSplash(indice: Int, avance: () -> Float)
{
    val central = indice == 2
    val radio = if (central) 20f else 19f
    val diametro = (LADO_SPLASH * radio * 2f / 300f).dp
    Canvas(
        modifier = Modifier
            .align(Alignment.Center)
            .size(diametro)
            .graphicsLayer
            {
                val posicion = posicionOrbe(indice, avance())
                val escala = LADO_SPLASH.dp.toPx() / 300f
                translationX = (posicion.x - CX) * escala
                translationY = (posicion.y - CY) * escala
            },
    )
    {
        val escala = size.width / (radio * 2f)
        withTransform({ scale(escala, escala, pivot = Offset.Zero) })
        {
            bola(
                radio,
                radio,
                radio,
                central = central,
                mostrarMarca = false,
            )
        }
    }
}

@Composable
private fun BoxScope.MarcaSplash(opacidad: () -> Float)
{
    val radio = 20f
    val diametro = (LADO_SPLASH * radio * 2f / 300f).dp
    Canvas(
        modifier = Modifier
            .align(Alignment.Center)
            .size(diametro)
            .graphicsLayer { alpha = opacidad() },
    )
    {
        val escala = size.width / (radio * 2f)
        withTransform({ scale(escala, escala, pivot = Offset.Zero) })
        {
            vCincelada(radio, radio, radio)
        }
    }
}

@Composable
fun SplashOrbita(
    fondo: Color = Color(0xFF0C1015),
    listoParaSalir: Boolean = true,
    alTerminar: () -> Unit,
)
{
    val avance = remember { Animatable(0f) }
    val opacidadV = remember { Animatable(0f) }
    val visible = remember { Animatable(1f) }
    val salir by rememberUpdatedState(listoParaSalir)

    LaunchedEffect(Unit)
    {
        launch {
            avance.animateTo(1f, tween(1100, easing = entradaSalidaCubica))
        }
        launch {
            delay(760)
            opacidadV.animateTo(1f, tween(280))
        }
        delay(1200)
        snapshotFlow { salir }.first { it }
        visible.animateTo(0f, tween(260))
        alTerminar()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = visible.value }
            .background(fondo),
        contentAlignment = Alignment.Center,
    )
    {
        Box(modifier = Modifier.size(LADO_SPLASH.dp))
        {
            for (indice in 0..4)
            {
                OrbeSplash(indice) { avance.value }
            }
            MarcaSplash { opacidadV.value }
        }
    }
}

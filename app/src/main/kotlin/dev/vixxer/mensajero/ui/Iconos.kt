package dev.vixxer.mensajero.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private fun DrawScope.trazo(datos: String, color: Color)
{
    drawPath(
        PathParser().parsePathString(datos).toPath(),
        color,
        style = Stroke(width = 2f, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round),
    )
}

@Composable
private fun Icono24(tamano: Dp, dibujar: DrawScope.() -> Unit)
{
    Canvas(modifier = Modifier.size(tamano)) {
        val e = size.width / 24f
        withTransform({ scale(e, e, pivot = Offset.Zero) }) {
            dibujar()
        }
    }
}

@Composable
fun Ojo(mostrando: Boolean, color: Color, tamano: Dp = 18.dp)
{
    Icono24(tamano) {
        if (mostrando)
        {
            trazo("M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24", color)
            drawLine(color, Offset(1f, 1f), Offset(23f, 23f), strokeWidth = 2f, cap = StrokeCap.Round)
        }
        else
        {
            trazo("M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z", color)
            drawCircle(color, radius = 3f, center = Offset(12f, 12f), style = Stroke(width = 2f))
        }
    }
}

@Composable
fun Sol(color: Color, tamano: Dp = 20.dp)
{
    Icono24(tamano) {
        drawCircle(color, radius = 4.2f, center = Offset(12f, 12f), style = Stroke(width = 2f))
        val rayos = listOf(
            Offset(12f, 1.5f) to Offset(12f, 4f),
            Offset(12f, 20f) to Offset(12f, 22.5f),
            Offset(3.6f, 3.6f) to Offset(5.4f, 5.4f),
            Offset(18.6f, 18.6f) to Offset(20.4f, 20.4f),
            Offset(1.5f, 12f) to Offset(4f, 12f),
            Offset(20f, 12f) to Offset(22.5f, 12f),
            Offset(3.6f, 20.4f) to Offset(5.4f, 18.6f),
            Offset(18.6f, 5.4f) to Offset(20.4f, 3.6f),
        )
        for ((inicio, fin) in rayos)
        {
            drawLine(color, inicio, fin, strokeWidth = 2f, cap = StrokeCap.Round)
        }
    }
}

@Composable
fun Luna(color: Color, tamano: Dp = 20.dp)
{
    Icono24(tamano) {
        drawPath(
            PathParser().parsePathString("M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z").toPath(),
            color,
        )
    }
}

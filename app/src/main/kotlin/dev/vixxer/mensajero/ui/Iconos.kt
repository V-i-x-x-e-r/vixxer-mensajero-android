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

@Composable
fun Engrane(color: Color, tamano: Dp = 22.dp)
{
    Icono24(tamano) {
        drawCircle(color, radius = 3f, center = Offset(12f, 12f), style = Stroke(width = 2f))
        trazo("M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z", color)
    }
}

@Composable
fun Pin(color: Color, tamano: Dp = 20.dp)
{
    Icono24(tamano) {
        trazo("M12 17v5", color)
        trazo("M9 10.76a2 2 0 0 1-1.11 1.79l-1.78.9A2 2 0 0 0 5 15.24V16a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-.76a2 2 0 0 0-1.11-1.79l-1.78-.9A2 2 0 0 1 15 10.76V7a1 1 0 0 1 1-1 2 2 0 0 0 0-4H8a2 2 0 0 0 0 4 1 1 0 0 1 1 1z", color)
    }
}

@Composable
fun Silencio(color: Color, tamano: Dp = 20.dp)
{
    Icono24(tamano) {
        trazo("M13.73 21a2 2 0 0 1-3.46 0", color)
        trazo("M18.63 13A17.89 17.89 0 0 1 18 8", color)
        trazo("M6.26 6.26A5.86 5.86 0 0 0 6 8c0 7-3 9-3 9h14", color)
        trazo("M18 8a6 6 0 0 0-9.33-5", color)
        drawLine(color, Offset(1f, 1f), Offset(23f, 23f), strokeWidth = 2f, cap = StrokeCap.Round)
    }
}

@Composable
fun Bote(color: Color, tamano: Dp = 20.dp)
{
    Icono24(tamano) {
        trazo("M3 6h18", color)
        trazo("M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2", color)
        trazo("M10 11v6", color)
        trazo("M14 11v6", color)
    }
}

@Composable
fun Archivar(color: Color, tamano: Dp = 20.dp)
{
    Icono24(tamano) {
        drawRoundRect(
            color,
            topLeft = Offset(2f, 3f),
            size = androidx.compose.ui.geometry.Size(20f, 5f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(1f),
            style = Stroke(width = 2f),
        )
        trazo("M4 8v11a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1V8", color)
        trazo("M10 12h4", color)
    }
}

@Composable
fun Estrella(color: Color, relleno: Color? = null, tamano: Dp = 14.dp)
{
    Icono24(tamano) {
        val camino = PathParser().parsePathString("M12 3l2.7 5.6 6.1.9-4.4 4.3 1.05 6.1L12 17.9l-5.45 2.9L7.6 13.8 3.2 9.5l6.1-.9z").toPath()
        if (relleno != null)
        {
            drawPath(camino, relleno)
        }
        drawPath(camino, color, style = Stroke(width = 2f, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }
}

@Composable
fun Lupa(color: Color, tamano: Dp = 20.dp)
{
    Icono24(tamano) {
        drawCircle(color, radius = 7f, center = Offset(11f, 11f), style = Stroke(width = 2f))
        drawLine(color, Offset(21f, 21f), Offset(16.7f, 16.7f), strokeWidth = 2f, cap = StrokeCap.Round)
    }
}

@Composable
fun IconoAmigos(color: Color, tamano: Dp = 16.dp)
{
    Icono24(tamano) {
        trazo("M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2", color)
        drawCircle(color, radius = 4f, center = Offset(9f, 7f), style = Stroke(width = 2f))
        trazo("M23 21v-2a4 4 0 0 0-3-3.87", color)
        trazo("M16 3.13a4 4 0 0 1 0 7.75", color)
    }
}

@Composable
fun IconoChat(color: Color, tamano: Dp = 16.dp)
{
    Icono24(tamano) {
        trazo("M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z", color)
    }
}

@Composable
fun IconoGrupos(color: Color, tamano: Dp = 16.dp)
{
    Icono24(tamano) {
        drawCircle(color, radius = 2.6f, center = Offset(12f, 6.5f), style = Stroke(width = 2f))
        drawCircle(color, radius = 2.2f, center = Offset(5f, 9.5f), style = Stroke(width = 2f))
        drawCircle(color, radius = 2.2f, center = Offset(19f, 9.5f), style = Stroke(width = 2f))
        trazo("M8 20v-1.5a4 4 0 0 1 8 0V20", color)
        trazo("M2 20v-1a3.4 3.4 0 0 1 4.2-3.3", color)
        trazo("M22 20v-1a3.4 3.4 0 0 0-4.2-3.3", color)
    }
}

@Composable
fun IconoImagen(color: Color, tamano: Dp = 20.dp)
{
    Icono24(tamano) {
        drawRoundRect(
            color,
            topLeft = Offset(3f, 3f),
            size = androidx.compose.ui.geometry.Size(18f, 18f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f),
            style = Stroke(width = 2f),
        )
        drawCircle(color, radius = 1.5f, center = Offset(8.5f, 8.5f), style = Stroke(width = 2f))
        trazo("M21 15l-5-5L5 21", color)
    }
}

@Composable
fun IconoVideo(color: Color, tamano: Dp = 20.dp)
{
    Icono24(tamano) {
        trazo("M23 7l-7 5 7 5V7z", color)
        drawRoundRect(
            color,
            topLeft = Offset(1f, 5f),
            size = androidx.compose.ui.geometry.Size(15f, 14f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f),
            style = Stroke(width = 2f),
        )
    }
}

@Composable
fun Microfono(color: Color, tamano: Dp = 20.dp)
{
    Icono24(tamano) {
        trazo("M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z", color)
        trazo("M19 10v2a7 7 0 0 1-14 0v-2", color)
        drawLine(color, Offset(12f, 19f), Offset(12f, 23f), strokeWidth = 2f, cap = StrokeCap.Round)
        drawLine(color, Offset(8f, 23f), Offset(16f, 23f), strokeWidth = 2f, cap = StrokeCap.Round)
    }
}

@Composable
fun Reproducir(color: Color, tamano: Dp = 20.dp)
{
    Icono24(tamano) {
        val camino = PathParser().parsePathString("M8 5v14l11-7z").toPath()
        drawPath(camino, color)
    }
}

@Composable
fun Pausa(color: Color, tamano: Dp = 20.dp)
{
    Icono24(tamano) {
        drawRoundRect(color, topLeft = Offset(7f, 5f), size = androidx.compose.ui.geometry.Size(3.6f, 14f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.4f))
        drawRoundRect(color, topLeft = Offset(13.4f, 5f), size = androidx.compose.ui.geometry.Size(3.6f, 14f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.4f))
    }
}

@Composable
fun Documento(color: Color, tamano: Dp = 22.dp)
{
    Icono24(tamano) {
        trazo("M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z", color)
        trazo("M14 2v6h6", color)
    }
}

@Composable
fun Clip(color: Color, tamano: Dp = 20.dp)
{
    Icono24(tamano) {
        trazo("M21.44 11.05l-9.19 9.19a6 6 0 0 1-8.49-8.49l9.19-9.19a4 4 0 0 1 5.66 5.66l-9.2 9.19a2 2 0 0 1-2.83-2.83l8.49-8.48", color)
    }
}

@Composable
fun Responder(color: Color, tamano: Dp = 18.dp)
{
    Icono24(tamano) {
        trazo("M9 14L4 9l5-5", color)
        trazo("M4 9h11a5 5 0 0 1 5 5v5", color)
    }
}

@Composable
fun Reenviar(color: Color, tamano: Dp = 18.dp)
{
    Icono24(tamano) {
        trazo("M15 14l5-5-5-5", color)
        trazo("M20 9H9a5 5 0 0 0-5 5v5", color)
    }
}

@Composable
fun Copiar(color: Color, tamano: Dp = 18.dp)
{
    Icono24(tamano) {
        drawRoundRect(
            color,
            topLeft = Offset(9f, 9f),
            size = androidx.compose.ui.geometry.Size(13f, 13f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f),
            style = Stroke(width = 2f),
        )
        trazo("M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1", color)
    }
}

@Composable
fun Lapiz(color: Color, tamano: Dp = 18.dp)
{
    Icono24(tamano) {
        trazo("M12 20h9", color)
        trazo("M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4 12.5-12.5z", color)
    }
}

@Composable
fun Check(color: Color, tamano: Dp = 18.dp)
{
    Icono24(tamano) {
        trazo("M20 6L9 17l-5-5", color)
    }
}

@Composable
fun Reloj(color: Color, tamano: Dp = 18.dp)
{
    Icono24(tamano) {
        drawCircle(color, radius = 10f, center = Offset(12f, 12f), style = Stroke(width = 2f))
        trazo("M12 7v5l3 2", color)
    }
}

@Composable
fun Kebab(color: Color, tamano: Dp = 20.dp)
{
    Icono24(tamano) {
        for (y in listOf(6f, 12f, 18f))
        {
            drawCircle(color, radius = 2.1f, center = Offset(12f, y))
        }
    }
}

@Composable
fun Visto(color: Color, dos: Boolean = false, tamano: Dp = 14.dp)
{
    if (dos)
    {
        Canvas(modifier = Modifier.size(tamano + tamano * (5f / 14f), tamano)) {
            val e = size.height / 16f
            withTransform({ scale(e, e, pivot = Offset.Zero) }) {
                trazo("M1 8.5l3.5 3.5L12 3", color)
                trazo("M8.5 12l0.8 0.8L20 3", color)
            }
        }
        return
    }
    Canvas(modifier = Modifier.size(tamano)) {
        val e = size.width / 16f
        withTransform({ scale(e, e, pivot = Offset.Zero) }) {
            trazo("M2 8.5l4 4 8-9", color)
        }
    }
}

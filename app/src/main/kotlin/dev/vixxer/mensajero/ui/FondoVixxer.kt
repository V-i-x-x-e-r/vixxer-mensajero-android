package dev.vixxer.mensajero.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.draw.drawWithCache

private val LUZ_FRIA = Color(0xFF4A78C2)

@Composable
fun Modifier.fondoVixxer(): Modifier
{
    val tema = LocalTema.current
    val colores = tema.colores
    val alphaAcento = if (tema.oscuro) 0.13f else 0.16f
    val alphaFrio = if (tema.oscuro) 0.10f else 0.12f

    return drawWithCache {
        val acento = Brush.linearGradient(
            colors = listOf(tema.acento.copy(alpha = alphaAcento), Color.Transparent),
            start = Offset.Zero,
            end = Offset(size.width * 0.78f, size.height * 0.42f),
        )
        val frio = Brush.linearGradient(
            colors = listOf(Color.Transparent, LUZ_FRIA.copy(alpha = alphaFrio)),
            start = Offset(size.width * 0.18f, size.height * 0.40f),
            end = Offset(size.width, size.height),
        )

        onDrawBehind {
            dibujarFondo(colores.fondo, acento, frio)
        }
    }
}

private fun DrawScope.dibujarFondo(base: Color, acento: Brush, frio: Brush)
{
    drawRect(base)
    drawRect(acento)
    drawRect(frio)
}

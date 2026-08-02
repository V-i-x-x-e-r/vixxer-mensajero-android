package dev.vixxer.mensajero.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.draw.drawWithCache

private val PAPEL_SUAVE = Color(0xFFCDD1D6)
private val TINTA_ELEVADA = Color(0xFF171A1E)

@Composable
fun Modifier.fondoVixxer(): Modifier
{
    val tema = LocalTema.current
    val colores = tema.colores

    return drawWithCache {
        val luz = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = if (tema.oscuro) 0.035f else 0.30f),
                Color.Transparent,
            ),
            start = Offset.Zero,
            end = Offset(size.width * 0.76f, size.height * 0.40f),
        )
        val profundidad = Brush.linearGradient(
            colors = listOf(
                Color.Transparent,
                if (tema.oscuro)
                {
                    TINTA_ELEVADA.copy(alpha = 0.72f)
                }
                else
                {
                    PAPEL_SUAVE.copy(alpha = 0.66f)
                },
            ),
            start = Offset(size.width * 0.18f, size.height * 0.40f),
            end = Offset(size.width, size.height),
        )

        onDrawBehind {
            dibujarFondo(colores.fondo, luz, profundidad)
        }
    }
}

private fun DrawScope.dibujarFondo(base: Color, luz: Brush, profundidad: Brush)
{
    drawRect(base)
    drawRect(luz)
    drawRect(profundidad)
}

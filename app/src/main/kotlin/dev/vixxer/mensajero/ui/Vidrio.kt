package dev.vixxer.mensajero.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object Vidrio
{
    val fondoPanel = Color(0xFF121212).copy(alpha = 0.48f)
    val fondoFuerte = Color(0xFF121212).copy(alpha = 0.74f)
    val fondoOsd = Color(0xFF121212).copy(alpha = 0.78f)
    val borde = Color(0x22FFFFFF)
    val bordeSuave = Color(0x14FFFFFF)
    val brillo = Color(0x2EF8F8F8)
    val sombra = Color(0xFF00040A)
    val activo = Color(0xE8F8F8F8)
    val ocupado = Color(0xA8D8D8D8)
    val vacio = Color(0x35FFFFFF)
    val radioVentana = 8.dp
    val radioPanel = 18.dp
    val radioPildora = 80.dp
    val anchoBorde = 0.7.dp
}

@Composable
fun Modifier.panelVidrio(radio: Dp = Vidrio.radioPanel, fuerte: Boolean = false): Modifier
{
    val tema = LocalTema.current
    val forma = RoundedCornerShape(radio)
    if (tema.oscuro)
    {
        return this
            .background(if (fuerte) Vidrio.fondoFuerte else Vidrio.fondoPanel, forma)
            .background(brilloVidrio(), forma)
            .border(Vidrio.anchoBorde, Vidrio.borde, forma)
    }
    val colores = tema.colores
    return this
        .shadow(if (fuerte) 5.dp else 3.dp, forma, ambientColor = Vidrio.sombra, spotColor = Vidrio.sombra)
        .background(colores.surface.copy(alpha = if (fuerte) 0.82f else 0.72f), forma)
        .background(brilloVidrioClaro(), forma)
        .border(Vidrio.anchoBorde, colores.borde, forma)
}

@Composable
fun Modifier.pildoraVidrio(): Modifier
{
    val tema = LocalTema.current
    val forma = RoundedCornerShape(Vidrio.radioPildora)
    if (tema.oscuro)
    {
        return this
            .background(Vidrio.fondoOsd, forma)
            .background(brilloVidrio(), forma)
            .border(Vidrio.anchoBorde, Vidrio.borde, forma)
    }
    val colores = tema.colores
    return this
        .shadow(6.dp, forma, ambientColor = Vidrio.sombra, spotColor = Vidrio.sombra)
        .background(colores.surface.copy(alpha = 0.78f), forma)
        .background(brilloVidrioClaro(), forma)
        .border(Vidrio.anchoBorde, colores.borde, forma)
}

@Composable
fun Modifier.vidrioFlotante(radio: Dp = 22.dp): Modifier
{
    val tema = LocalTema.current
    val forma = RoundedCornerShape(radio)
    if (tema.oscuro)
    {
        return this
            .background(Vidrio.fondoFuerte, forma)
            .background(brilloVidrio(), forma)
            .border(Vidrio.anchoBorde, Vidrio.borde, forma)
    }
    val colores = tema.colores
    return this
        .background(colores.surface.copy(alpha = 0.8f), forma)
        .background(brilloVidrioClaro(), forma)
        .border(Vidrio.anchoBorde, colores.borde, forma)
}

private fun brilloVidrio(): Brush =
    Brush.verticalGradient(
        0f to Color.White.copy(alpha = 0.07f),
        0.28f to Color.Transparent,
    )

private fun brilloVidrioClaro(): Brush =
    Brush.verticalGradient(
        0f to Color.White.copy(alpha = 0.55f),
        0.32f to Color.Transparent,
    )

@Composable
fun colorPestanaActiva(): Color
{
    val tema = LocalTema.current
    return if (tema.oscuro) Vidrio.activo else tema.colores.texto
}

@Composable
fun colorPestanaInactiva(): Color
{
    val tema = LocalTema.current
    return if (tema.oscuro) Vidrio.ocupado else tema.colores.muted
}

@Composable
fun colorBrilloPestana(): Color
{
    val tema = LocalTema.current
    return if (tema.oscuro) Vidrio.brillo else tema.colores.borde
}

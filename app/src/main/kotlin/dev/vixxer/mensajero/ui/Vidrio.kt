package dev.vixxer.mensajero.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

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

private fun Modifier.conBlur(estado: HazeState, forma: Shape, tinte: Color, radioBlur: Dp, borde: Color): Modifier =
    this
        .clip(forma)
        .hazeEffect(estado) {
            blurRadius = radioBlur
            tints = listOf(HazeTint(tinte))
        }
        .border(Vidrio.anchoBorde, borde, forma)

@Composable
fun Modifier.panelVidrio(radio: Dp = Vidrio.radioPanel, fuerte: Boolean = false, desenfocar: Boolean = false): Modifier
{
    val tema = LocalTema.current
    val forma = RoundedCornerShape(radio)
    val haze = LocalHazeState.current
    if (desenfocar && hayBlur && haze != null)
    {
        val tinte = if (tema.oscuro) Color(0xFF121212).copy(alpha = if (fuerte) 0.42f else 0.3f)
            else tema.colores.surface.copy(alpha = if (fuerte) 0.42f else 0.34f)
        val borde = if (tema.oscuro) Vidrio.borde else tema.colores.borde
        return this.conBlur(estado = haze, forma = forma, tinte = tinte, radioBlur = 22.dp, borde = borde)
    }
    if (tema.oscuro)
    {
        return this
            .background(if (fuerte) Vidrio.fondoFuerte else Vidrio.fondoPanel, forma)
            .brilloTope(forma)
            .border(Vidrio.anchoBorde, Vidrio.borde, forma)
    }
    val colores = tema.colores
    return this
        .shadow(if (fuerte) 5.dp else 3.dp, forma, ambientColor = Vidrio.sombra, spotColor = Vidrio.sombra)
        .background(colores.surface.copy(alpha = if (fuerte) 0.62f else 0.68f), forma)
        .brilloTope(forma)
        .border(Vidrio.anchoBorde, colores.borde, forma)
}

@Composable
fun Modifier.pildoraVidrio(): Modifier
{
    val tema = LocalTema.current
    val forma = RoundedCornerShape(Vidrio.radioPildora)
    val haze = LocalHazeState.current
    if (hayBlur && haze != null)
    {
        val tinte = if (tema.oscuro) Color(0xFF121212).copy(alpha = 0.46f) else tema.colores.surface.copy(alpha = 0.46f)
        val borde = if (tema.oscuro) Vidrio.borde else tema.colores.borde
        return this.conBlur(estado = haze, forma = forma, tinte = tinte, radioBlur = 26.dp, borde = borde)
    }
    if (tema.oscuro)
    {
        return this
            .background(Vidrio.fondoOsd, forma)
            .brilloTope(forma)
            .border(Vidrio.anchoBorde, Vidrio.borde, forma)
    }
    val colores = tema.colores
    return this
        .shadow(6.dp, forma, ambientColor = Vidrio.sombra, spotColor = Vidrio.sombra)
        .background(colores.surface.copy(alpha = 0.7f), forma)
        .brilloTope(forma)
        .border(Vidrio.anchoBorde, colores.borde, forma)
}

@Composable
fun Modifier.vidrioFlotante(radio: Dp = 22.dp): Modifier
{
    val tema = LocalTema.current
    val forma = RoundedCornerShape(radio)
    val haze = LocalHazeState.current
    if (hayBlur && haze != null)
    {
        val tinte = if (tema.oscuro) Color(0xFF121212).copy(alpha = 0.44f) else tema.colores.surface.copy(alpha = 0.42f)
        val borde = if (tema.oscuro) Vidrio.borde else tema.colores.borde
        return this.conBlur(estado = haze, forma = forma, tinte = tinte, radioBlur = 24.dp, borde = borde)
    }
    if (tema.oscuro)
    {
        return this
            .background(Vidrio.fondoFuerte, forma)
            .brilloTope(forma)
            .border(Vidrio.anchoBorde, Vidrio.borde, forma)
    }
    val colores = tema.colores
    return this
        .background(colores.surface.copy(alpha = 0.62f), forma)
        .brilloTope(forma)
        .border(Vidrio.anchoBorde, colores.borde, forma)
}

@Composable
private fun Modifier.brilloTope(forma: Shape): Modifier
{
    val oscuro = LocalTema.current.oscuro
    val alto = with(LocalDensity.current) { 2.dp.toPx() }
    val color = if (oscuro) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.55f)
    return this.background(
        Brush.verticalGradient(0f to color, 1f to Color.Transparent, startY = 0f, endY = alto),
        forma,
    )
}

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

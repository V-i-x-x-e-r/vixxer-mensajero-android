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
    val fondoPanel = Color(0xFF17191D).copy(alpha = 0.44f)
    val fondoFuerte = Color(0xFF17191D).copy(alpha = 0.58f)
    val fondoOsd = Color(0xFF17191D).copy(alpha = 0.64f)
    val solidoPanel = Color(0xFF23252A)
    val solidoFuerte = Color(0xFF2A2D33)
    val solidoOsd = Color(0xFF272A30)
    val bordeSolido = Color(0x2EFFFFFF)
    val borde = Color(0x24FFFFFF)
    val bordeSuave = Color(0x18FFFFFF)
    val brillo = Color(0x30FFFFFF)
    val sombra = Color(0xFF05070A)
    val activo = Color(0xFFF5F7FA)
    val ocupado = Color(0xFFAAAEB6)
    val vacio = Color(0x35FFFFFF)
    val radioVentana = 8.dp
    val radioPanel = 18.dp
    val radioPildora = 80.dp
    val anchoBorde = 0.7.dp
}

private enum class CapaVidrio
{
    PANEL,
    FUERTE,
    PILDORA,
    FLOTANTE,
}

private data class AparienciaVidrio(
    val solido: Color,
    val tinte: Color,
    val borde: Brush,
    val brillo: Color,
    val radioBlur: Dp,
    val elevacion: Dp,
)

@Composable
private fun aparienciaVidrio(capa: CapaVidrio): AparienciaVidrio
{
    val tema = LocalTema.current
    val oscuro = tema.oscuro
    val solido = if (oscuro)
    {
        when (capa)
        {
            CapaVidrio.PANEL -> Vidrio.solidoPanel
            CapaVidrio.FUERTE -> Vidrio.solidoFuerte
            CapaVidrio.PILDORA -> Vidrio.solidoOsd
            CapaVidrio.FLOTANTE -> Vidrio.solidoFuerte
        }
    }
    else
    {
        when (capa)
        {
            CapaVidrio.PANEL -> Color(0xFFFCFCFE)
            CapaVidrio.FUERTE -> Color.White
            CapaVidrio.PILDORA -> Color(0xFFFEFEFF)
            CapaVidrio.FLOTANTE -> Color.White
        }
    }
    val alphaTinte = when (capa)
    {
        CapaVidrio.PANEL -> if (oscuro) 0.44f else 0.56f
        CapaVidrio.FUERTE -> if (oscuro) 0.58f else 0.68f
        CapaVidrio.PILDORA -> if (oscuro) 0.56f else 0.72f
        CapaVidrio.FLOTANTE -> if (oscuro) 0.64f else 0.76f
    }
    val baseTinte = if (oscuro) Color(0xFF343840) else Color.White
    val bordeInicial = if (oscuro) Color.White.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.96f)
    val bordeMedio = if (oscuro) Vidrio.bordeSuave else tema.colores.borde.copy(alpha = 0.86f)
    val bordeAcento = tema.acento.copy(alpha = if (oscuro) 0.24f else 0.18f)
    val borde = Brush.linearGradient(listOf(bordeInicial, bordeMedio, bordeAcento, bordeMedio))
    val brillo = if (oscuro) Color.White.copy(alpha = 0.13f) else Color.White.copy(alpha = 0.72f)
    val radioBlur = when (capa)
    {
        CapaVidrio.PANEL -> 20.dp
        CapaVidrio.FUERTE -> 24.dp
        CapaVidrio.PILDORA -> 28.dp
        CapaVidrio.FLOTANTE -> 30.dp
    }
    val elevacion = when (capa)
    {
        CapaVidrio.PANEL -> 3.dp
        CapaVidrio.FUERTE -> 7.dp
        CapaVidrio.PILDORA -> 9.dp
        CapaVidrio.FLOTANTE -> 12.dp
    }
    return AparienciaVidrio(
        solido = solido,
        tinte = baseTinte.copy(alpha = alphaTinte),
        borde = borde,
        brillo = brillo,
        radioBlur = radioBlur,
        elevacion = elevacion,
    )
}

private fun Modifier.elevar(elevacion: Dp, forma: Shape): Modifier =
    shadow(
        elevation = elevacion,
        shape = forma,
        clip = false,
        ambientColor = Vidrio.sombra.copy(alpha = 0.22f),
        spotColor = Vidrio.sombra.copy(alpha = 0.28f),
    )

@Composable
private fun Modifier.conDesenfoque(
    estado: HazeState,
    forma: Shape,
    apariencia: AparienciaVidrio,
): Modifier =
    elevar(apariencia.elevacion, forma)
        .clip(forma)
        .hazeEffect(estado)
        {
            blurRadius = apariencia.radioBlur
            tints = listOf(HazeTint(apariencia.tinte))
        }
        .brilloTope(forma, apariencia.brillo)
        .border(Vidrio.anchoBorde, apariencia.borde, forma)

@Composable
private fun Modifier.sinDesenfoque(
    forma: Shape,
    apariencia: AparienciaVidrio,
): Modifier =
    elevar(apariencia.elevacion, forma)
        .background(apariencia.solido, forma)
        .brilloTope(forma, apariencia.brillo)
        .border(Vidrio.anchoBorde, apariencia.borde, forma)

@Composable
private fun Modifier.aplicarVidrio(
    capa: CapaVidrio,
    forma: Shape,
    desenfocar: Boolean,
): Modifier
{
    val apariencia = aparienciaVidrio(capa)
    val haze = LocalHazeState.current
    if (desenfocar && hayBlur && haze != null)
    {
        return conDesenfoque(haze, forma, apariencia)
    }
    return sinDesenfoque(forma, apariencia)
}

@Composable
fun Modifier.panelVidrio(
    radio: Dp = Vidrio.radioPanel,
    fuerte: Boolean = false,
    desenfocar: Boolean = false,
): Modifier = aplicarVidrio(
    capa = if (fuerte) CapaVidrio.FUERTE else CapaVidrio.PANEL,
    forma = RoundedCornerShape(radio),
    desenfocar = desenfocar,
)

@Composable
fun Modifier.pildoraVidrio(): Modifier = aplicarVidrio(
    capa = CapaVidrio.PILDORA,
    forma = RoundedCornerShape(Vidrio.radioPildora),
    desenfocar = true,
)

@Composable
fun Modifier.vidrioFlotante(radio: Dp = 22.dp): Modifier = aplicarVidrio(
    capa = CapaVidrio.FLOTANTE,
    forma = RoundedCornerShape(radio),
    desenfocar = true,
)

@Composable
private fun Modifier.brilloTope(forma: Shape, color: Color): Modifier
{
    val alto = with(LocalDensity.current) { 3.dp.toPx() }
    return background(
        Brush.verticalGradient(0f to color, 1f to Color.Transparent, startY = 0f, endY = alto),
        forma,
    )
}

@Composable
fun colorPestanaActiva(): Color
{
    val tema = LocalTema.current
    return if (tema.oscuro) Vidrio.activo else tema.colores.botonTexto
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
    return if (tema.oscuro) Vidrio.brillo else tema.colores.botonFondo
}

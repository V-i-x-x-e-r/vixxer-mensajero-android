package dev.vixxer.mensajero.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
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
    val fondoPanel = Color(0xFF171A1E).copy(alpha = 0.44f)
    val fondoFuerte = Color(0xFF171A1E).copy(alpha = 0.58f)
    val fondoOsd = Color(0xFF171A1E).copy(alpha = 0.64f)
    val solidoPanel = Color(0xFF171A1E)
    val solidoFuerte = Color(0xFF1C2025)
    val solidoOsd = Color(0xFF171A1E)
    val bordeSolido = Color(0x2EFFFFFF)
    val borde = Color(0x24FFFFFF)
    val bordeSuave = Color(0x18FFFFFF)
    val brillo = Color(0x30FFFFFF)
    val sombra = Color(0xFF08090B)
    val activo = Color(0xFFF5F6F7)
    val ocupado = Color(0xFFA9AFB8)
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
            CapaVidrio.PANEL -> Vidrio.solidoPanel.copy(alpha = 0.74f)
            CapaVidrio.FUERTE -> Vidrio.solidoFuerte.copy(alpha = 0.84f)
            CapaVidrio.PILDORA -> Vidrio.solidoOsd.copy(alpha = 0.80f)
            CapaVidrio.FLOTANTE -> Vidrio.solidoFuerte.copy(alpha = 0.90f)
        }
    }
    else
    {
        when (capa)
        {
            CapaVidrio.PANEL -> Color.White.copy(alpha = 0.68f)
            CapaVidrio.FUERTE -> Color.White.copy(alpha = 0.82f)
            CapaVidrio.PILDORA -> Color.White.copy(alpha = 0.76f)
            CapaVidrio.FLOTANTE -> Color.White.copy(alpha = 0.90f)
        }
    }
    val alphaTinte = when (capa)
    {
        CapaVidrio.PANEL -> if (oscuro) 0.32f else 0.40f
        CapaVidrio.FUERTE -> if (oscuro) 0.44f else 0.52f
        CapaVidrio.PILDORA -> if (oscuro) 0.40f else 0.46f
        CapaVidrio.FLOTANTE -> if (oscuro) 0.52f else 0.60f
    }
    val baseTinte = if (oscuro) Color(0xFF171A1E) else Color.White
    val bordeInicial = if (oscuro) Color.White.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.92f)
    val bordeMedio = if (oscuro) Vidrio.borde else tema.colores.borde
    val bordeFinal = if (oscuro) Color.White.copy(alpha = 0.08f) else Color(0xFF08090B).copy(alpha = 0.08f)
    val borde = Brush.linearGradient(listOf(bordeInicial, bordeMedio, bordeFinal, bordeMedio))
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
            noiseFactor = 0.08f
            fallbackTint = HazeTint(apariencia.solido)
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
    if (desenfocar && hayBlurNativo && haze != null)
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
fun Modifier.circuloVidrio(
    fuerte: Boolean = false,
    desenfocar: Boolean = true,
): Modifier = aplicarVidrio(
    capa = if (fuerte) CapaVidrio.FUERTE else CapaVidrio.PANEL,
    forma = CircleShape,
    desenfocar = desenfocar,
)

@Composable
fun Modifier.hojaVidrio(radio: Dp = 22.dp): Modifier = aplicarVidrio(
    capa = CapaVidrio.FLOTANTE,
    forma = RoundedCornerShape(topStart = radio, topEnd = radio),
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
    return if (tema.oscuro) Color.White.copy(alpha = 0.13f) else Color(0xFF08090B).copy(alpha = 0.07f)
}

@Composable
fun colorBordePestana(): Color
{
    val tema = LocalTema.current
    return if (tema.oscuro) Color.White.copy(alpha = 0.28f) else Color(0xFF08090B).copy(alpha = 0.22f)
}

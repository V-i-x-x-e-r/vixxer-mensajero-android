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
    val solidoPanel = Color(0xFF171A1E)
    val solidoFuerte = Color(0xFF1C2025)
    val solidoOsd = Color(0xFF171A1E)
    val sombra = Color(0xFF08090B)
    val activo = Color(0xFFF5F6F7)
    val ocupado = Color(0xFFA9AFB8)
    val radioPanel = 8.dp
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
    val reflejo: Brush,
    val rim: Brush?,
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
            CapaVidrio.PANEL -> Color(0xFFFCFDFE)
            CapaVidrio.FUERTE -> Color(0xFFFFFFFF)
            CapaVidrio.PILDORA -> Color(0xFFFDFEFF)
            CapaVidrio.FLOTANTE -> Color(0xFFFFFFFF)
        }
    }
    val alphaTinte = when (capa)
    {
        CapaVidrio.PANEL -> if (oscuro) 0.32f else 0.30f
        CapaVidrio.FUERTE -> if (oscuro) 0.44f else 0.40f
        CapaVidrio.PILDORA -> if (oscuro) 0.40f else 0.34f
        CapaVidrio.FLOTANTE -> if (oscuro) 0.52f else 0.50f
    }
    val baseTinte = if (oscuro) Color(0xFF171A1E) else Color(0xFFF2F5F8)
    val reflejo = if (oscuro)
    {
        Brush.linearGradient(listOf(Color.White.copy(alpha = 0.07f), Color.Transparent, Color.White.copy(alpha = 0.025f)))
    }
    else
    {
        Brush.verticalGradient(
            0f to Color.White.copy(alpha = 0.55f),
            0.45f to Color.White.copy(alpha = 0.05f),
            0.86f to Color.Transparent,
            1f to Color(0xFF08090B).copy(alpha = 0.05f),
        )
    }
    val rim = if (oscuro)
    {
        null
    }
    else
    {
        Brush.verticalGradient(
            0f to Color.White.copy(alpha = 0.38f),
            0.5f to Color.White.copy(alpha = 0.12f),
            1f to Color.White.copy(alpha = 0.24f),
        )
    }
    val brillo = if (oscuro) Color.White.copy(alpha = 0.07f) else Color.White.copy(alpha = 0.50f)
    val radioBlur = when (capa)
    {
        CapaVidrio.PANEL -> 20.dp
        CapaVidrio.FUERTE -> 24.dp
        CapaVidrio.PILDORA -> 28.dp
        CapaVidrio.FLOTANTE -> 30.dp
    }
    val elevacion = when (capa)
    {
        CapaVidrio.PANEL -> if (oscuro) 1.dp else 2.dp
        CapaVidrio.FUERTE -> if (oscuro) 2.dp else 3.dp
        CapaVidrio.PILDORA -> if (oscuro) 3.dp else 4.dp
        CapaVidrio.FLOTANTE -> if (oscuro) 6.dp else 7.dp
    }
    return AparienciaVidrio(
        solido = solido,
        tinte = baseTinte.copy(alpha = alphaTinte),
        reflejo = reflejo,
        rim = rim,
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
        .background(apariencia.reflejo, forma)
        .brilloTope(forma, apariencia.brillo)
        .conRim(apariencia.rim, forma)

@Composable
private fun Modifier.sinDesenfoque(
    forma: Shape,
    apariencia: AparienciaVidrio,
): Modifier =
    elevar(apariencia.elevacion, forma)
        .background(apariencia.solido, forma)
        .background(apariencia.reflejo, forma)
        .brilloTope(forma, apariencia.brillo)
        .conRim(apariencia.rim, forma)

private fun Modifier.conRim(rim: Brush?, forma: Shape): Modifier =
    if (rim == null) this else border(1.dp, rim, forma)

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
    return when
    {
        tema.oscuro -> Color.White.copy(alpha = 0.13f)
        tema.nombre == "colorido" -> tema.acento.copy(alpha = 0.28f)
        else -> Color(0xFF08090B).copy(alpha = 0.07f)
    }
}

@Composable
fun colorBordePestana(): Color
{
    val tema = LocalTema.current
    return when
    {
        tema.oscuro -> Color.White.copy(alpha = 0.28f)
        tema.nombre == "colorido" -> tema.acento.copy(alpha = 0.72f)
        else -> Color(0xFF08090B).copy(alpha = 0.22f)
    }
}

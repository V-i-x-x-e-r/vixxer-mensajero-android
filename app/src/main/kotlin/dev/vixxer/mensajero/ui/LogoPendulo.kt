package dev.vixxer.mensajero.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val LOGO_ANCHO = 300f
private const val LOGO_ALTO = 264f
private const val LOGIN_BY = 130f
private const val PIVOTE = 12f
private val LOGIN_CX = floatArrayOf(70f, 110f, 150f, 190f, 230f)

private val salidaCubica = Easing { t -> 1f - (1f - t) * (1f - t) * (1f - t) }
private val entradaCubica = Easing { t -> t * t * t }
private val salidaCuadrada = Easing { t -> 1f - (1f - t) * (1f - t) }
private val entradaCuadrada = Easing { t -> t * t }
private val salidaPendulo = Easing { t -> sin(t * PI.toFloat() / 2f) }
private val entradaPendulo = Easing { t -> 1f - cos(t * PI.toFloat() / 2f) }

private enum class MovimientoPendulo
{
    CICLO,
    GOLPE,
    CAOS,
}

private data class OrdenPendulo(
    val instante: Long,
    val movimiento: MovimientoPendulo,
)

private class SecuenciaTaps
{
    private var cantidad = 0
    private var ultimo = 0L

    fun registrar(ahora: Long): Boolean
    {
        cantidad = if (ahora - ultimo < 450) cantidad + 1 else 1
        ultimo = ahora
        if (cantidad < 3)
        {
            return false
        }
        cantidad = 0
        return true
    }
}

internal fun DrawScope.vCincelada(cx: Float, cy: Float, r: Float)
{
    val w = r * 0.74f
    val top = cy - r * 0.55f
    val bot = cy + r * 0.78f
    val iw = r * 0.4f
    val ib = cy + r * 0.3f

    fun mitad(lado: Float): Path
    {
        val trazo = Path()
        trazo.moveTo(cx + lado * w, top)
        trazo.lineTo(cx, bot)
        trazo.lineTo(cx, ib)
        trazo.lineTo(cx + lado * iw, top)
        trazo.close()
        return trazo
    }

    val izq = mitad(-1f)
    val der = mitad(1f)
    val dy = r * 0.055f
    val recorte = Path()
    recorte.addOval(Rect(center = Offset(cx, cy), radius = r * 0.985f))

    clipPath(recorte) {
        translate(0f, -dy) {
            drawPath(izq, Color(0xFF05080C), alpha = 0.55f)
            drawPath(der, Color(0xFF05080C), alpha = 0.55f)
        }
        translate(0f, dy) {
            drawPath(izq, Color(0xFFDCE3EC), alpha = 0.5f)
            drawPath(der, Color(0xFFDCE3EC), alpha = 0.5f)
        }
        drawPath(izq, Brush.linearGradient(
            listOf(Color(0xFF9AA3B0), Color(0xFF4E5661)),
            start = Offset(cx - w, top),
            end = Offset(cx - w + 0.25f * w, bot),
        ))
        drawPath(der, Brush.linearGradient(
            listOf(Color(0xFF8C95A2), Color(0xFF454D58)),
            start = Offset(cx, top),
            end = Offset(cx + 0.25f * w, bot),
        ))
    }
}

internal fun DrawScope.bola(
    x: Float,
    y: Float,
    r: Float,
    central: Boolean = false,
    mini: Boolean = false,
    mostrarMarca: Boolean = central,
)
{
    val fx = if (central) 0.38f else 0.36f
    val fy = if (central) 0.26f else 0.28f
    val fr = when
    {
        mini && central -> 0.82f
        mini -> 0.80f
        central -> 0.76f
        else -> 0.74f
    }
    val paradas = when
    {
        mini && central -> arrayOf(
            0f to Color(0xFFF7FAFD),
            0.22f to Color(0xFFC2CAD5),
            0.50f to Color(0xFF7B8594),
            0.80f to Color(0xFF39414D),
            1f to Color(0xFF11161C),
        )
        mini -> arrayOf(
            0f to Color(0xFFF2F6FA),
            0.20f to Color(0xFFB8C1CC),
            0.48f to Color(0xFF6F7A88),
            0.78f to Color(0xFF333B46),
            1f to Color(0xFF12161C),
        )
        central -> arrayOf(
            0f to Color(0xFFF2F6FB),
            0.15f to Color(0xFFA6AEB9),
            0.40f to Color(0xFF474E59),
            0.68f to Color(0xFF1E232A),
            1f to Color(0xFF07090C),
        )
        else -> arrayOf(
            0f to Color(0xFFEAF0F8),
            0.14f to Color(0xFF9BA3AE),
            0.38f to Color(0xFF525A66),
            0.66f to Color(0xFF252A31),
            1f to Color(0xFF090B0F),
        )
    }
    val centro = Offset(x - r + 2f * r * fx, y - r + 2f * r * fy)
    drawCircle(
        Brush.radialGradient(colorStops = paradas, center = centro, radius = 2f * r * fr),
        radius = r,
        center = Offset(x, y),
    )
    val s = r / 24f
    val rx = if (mini) 9f * s else 8f * s
    val ry = if (mini) 5.5f * s else 5f * s
    drawOval(
        Color.White,
        topLeft = Offset(x - 8f * s - rx, y - 10f * s - ry),
        size = Size(2f * rx, 2f * ry),
        alpha = if (mini) 0.45f else if (central) 0.3f else 0.26f,
    )
    if (mostrarMarca)
    {
        vCincelada(x, y, r)
    }
}

private fun DrawScope.hilos(x: Float, colorHilo: Color)
{
    drawLine(colorHilo, Offset(x - 5f, 6f), Offset(x, LOGIN_BY), strokeWidth = 1.3f, alpha = 0.55f)
    drawLine(colorHilo, Offset(x + 5f, 18f), Offset(x, LOGIN_BY), strokeWidth = 1.3f, alpha = 0.8f)
}

private fun brochaMarco(zona: Rect): Brush
{
    return Brush.linearGradient(
        colorStops = arrayOf(
            0f to Color(0xFF9AA3B2),
            0.22f to Color(0xFF606877),
            0.60f to Color(0xFF2C333D),
            1f to Color(0xFF0C1017),
        ),
        start = Offset(zona.left, zona.top),
        end = Offset(zona.left + 0.3f * zona.width, zona.top + zona.height),
    )
}

@Composable
fun LogoPenduloFila(alto: Dp)
{
    val ancho = alto * (244f / 56f)
    Canvas(modifier = Modifier.width(ancho).height(alto)) {
        val e = size.width / 244f
        withTransform({ scale(e, e, pivot = Offset.Zero) }) {
            bola(24f, 28f, 21f, mini = true)
            bola(70f, 28f, 21f, mini = true)
            bola(174f, 28f, 21f, mini = true)
            bola(220f, 28f, 21f, mini = true)
            bola(122f, 28f, 27f, central = true, mini = true)
        }
    }
}

@Composable
private fun CapaLogo(
    modifier: Modifier = Modifier,
    dibujar: DrawScope.() -> Unit,
)
{
    Canvas(modifier = Modifier.fillMaxSize().then(modifier))
    {
        val escala = size.width / LOGO_ANCHO
        withTransform({ scale(escala, escala, pivot = Offset.Zero) })
        {
            dibujar()
        }
    }
}

private suspend fun asentarPendulo(
    izquierda: Animatable<Float, *>,
    derecha: Animatable<Float, *>,
    giro: Animatable<Float, *>,
)
{
    coroutineScope {
        launch {
            if (izquierda.value != 0f)
            {
                izquierda.animateTo(0f, tween(120, easing = salidaCuadrada))
            }
        }
        launch {
            if (derecha.value != 0f)
            {
                derecha.animateTo(0f, tween(120, easing = salidaCuadrada))
            }
        }
        launch {
            if (giro.value != 0f)
            {
                giro.animateTo(360f, tween(160, easing = salidaCuadrada))
            }
            giro.snapTo(0f)
        }
    }
}

private suspend fun animarCiclo(
    izquierda: Animatable<Float, *>,
    derecha: Animatable<Float, *>,
    giro: Animatable<Float, *>,
    duracion: Int,
)
{
    asentarPendulo(izquierda, derecha, giro)
    val medioCiclo = duracion.coerceAtLeast(800) / 2
    val ida = (medioCiclo * 0.52f).toInt()
    val vuelta = medioCiclo - ida
    while (true)
    {
        izquierda.animateTo(-12f, tween(ida, easing = salidaPendulo))
        izquierda.animateTo(0f, tween(vuelta, easing = entradaPendulo))
        derecha.animateTo(12f, tween(ida, easing = salidaPendulo))
        derecha.animateTo(0f, tween(vuelta, easing = entradaPendulo))
    }
}

private suspend fun animarGolpe(
    izquierda: Animatable<Float, *>,
    derecha: Animatable<Float, *>,
    giro: Animatable<Float, *>,
)
{
    asentarPendulo(izquierda, derecha, giro)
    izquierda.animateTo(-19f, tween(280, easing = salidaPendulo))
    izquierda.animateTo(0f, tween(230, easing = entradaPendulo))
    derecha.animateTo(19f, tween(280, easing = salidaPendulo))
    derecha.animateTo(0f, tween(230, easing = entradaPendulo))
}

private suspend fun animarCaos(
    izquierda: Animatable<Float, *>,
    derecha: Animatable<Float, *>,
    giro: Animatable<Float, *>,
)
{
    asentarPendulo(izquierda, derecha, giro)
    coroutineScope {
        launch {
            izquierda.animateTo(-30f, tween(280, easing = salidaCubica))
            izquierda.animateTo(7f, tween(220, easing = entradaCubica))
            izquierda.animateTo(-11f, tween(200, easing = salidaCuadrada))
            izquierda.animateTo(0f, tween(200, easing = entradaCuadrada))
        }
        launch {
            derecha.animateTo(30f, tween(280, easing = salidaCubica))
            derecha.animateTo(-7f, tween(220, easing = entradaCubica))
            derecha.animateTo(11f, tween(200, easing = salidaCuadrada))
            derecha.animateTo(0f, tween(200, easing = entradaCuadrada))
        }
        launch {
            delay(140)
            giro.animateTo(360f, tween(950, easing = salidaCubica))
        }
    }
}

private fun DrawScope.fondoLogo()
{
    drawLine(Color(0xFF4A525E), Offset(84f, 240f), Offset(66f, 256f), strokeWidth = 11f, cap = StrokeCap.Round)
    drawLine(Color(0xFF4A525E), Offset(216f, 240f), Offset(234f, 256f), strokeWidth = 11f, cap = StrokeCap.Round)
    clipRect(0f, 0f, LOGO_ANCHO, 72f)
    {
        val zona = Rect(Offset(24f, 6f), Size(252f, 200f))
        drawRoundRect(
            brochaMarco(zona),
            topLeft = zona.topLeft,
            size = zona.size,
            cornerRadius = CornerRadius(52f),
            style = Stroke(width = 10f),
            alpha = 0.9f,
        )
    }
}

private fun DrawScope.pendulosCentrales(colorBarra: Color)
{
    hilos(LOGIN_CX[1], colorBarra)
    hilos(LOGIN_CX[2], colorBarra)
    hilos(LOGIN_CX[3], colorBarra)
    bola(LOGIN_CX[1], LOGIN_BY, 19f)
    bola(LOGIN_CX[3], LOGIN_BY, 19f)
}

private fun DrawScope.penduloExterior(indice: Int, colorBarra: Color)
{
    val x = LOGIN_CX[indice]
    hilos(x, colorBarra)
    bola(x, LOGIN_BY, 19f)
}

private fun DrawScope.frenteLogo()
{
    val zona = Rect(Offset(24f, 18f), Size(252f, 222f))
    drawRoundRect(
        brochaMarco(zona),
        topLeft = zona.topLeft,
        size = zona.size,
        cornerRadius = CornerRadius(52f),
        style = Stroke(width = 13f),
    )
    drawRoundRect(
        Color(0xFFB4BEC9),
        topLeft = Offset(30.5f, 24.5f),
        size = Size(239f, 209f),
        cornerRadius = CornerRadius(46f),
        style = Stroke(width = 1.3f),
        alpha = 0.5f,
    )
}

private fun origenGiro(x: Float, y: Float): TransformOrigin
{
    return TransformOrigin(x / LOGO_ANCHO, y / LOGO_ALTO)
}

@Composable
fun LogoPendulo(
    alto: Dp,
    colorTexto: Color,
    colorBarra: Color = Color(0xFF9AA2AD),
    duracionCiclo: Int = 1500,
)
{
    val ancho = alto * (LOGO_ANCHO / LOGO_ALTO)
    val izquierda = remember { Animatable(0f) }
    val derecha = remember { Animatable(0f) }
    val giro = remember { Animatable(0f) }
    var orden by remember {
        mutableStateOf(OrdenPendulo(System.nanoTime(), MovimientoPendulo.CICLO))
    }
    val taps = remember { SecuenciaTaps() }

    LaunchedEffect(orden, duracionCiclo)
    {
        when (orden.movimiento)
        {
            MovimientoPendulo.CICLO -> animarCiclo(izquierda, derecha, giro, duracionCiclo)
            MovimientoPendulo.GOLPE -> animarGolpe(izquierda, derecha, giro)
            MovimientoPendulo.CAOS -> animarCaos(izquierda, derecha, giro)
        }
        if (orden.movimiento != MovimientoPendulo.CICLO)
        {
            delay(260)
            orden = OrdenPendulo(System.nanoTime(), MovimientoPendulo.CICLO)
        }
    }

    fun tocar()
    {
        val ahora = System.nanoTime() / 1_000_000L
        val movimiento = if (taps.registrar(ahora))
        {
            MovimientoPendulo.CAOS
        }
        else
        {
            MovimientoPendulo.GOLPE
        }
        orden = OrdenPendulo(System.nanoTime(), movimiento)
    }

    Box(
        modifier = Modifier
            .width(ancho)
            .height(alto)
            .semantics { contentDescription = "Vixxer" }
            .pointerInput(Unit)
            {
                detectTapGestures { tocar() }
            },
    )
    {
        CapaLogo {
            fondoLogo()
        }
        CapaLogo {
            pendulosCentrales(colorBarra)
        }
        CapaLogo(
            modifier = Modifier.graphicsLayer
            {
                transformOrigin = origenGiro(LOGIN_CX[2], LOGIN_BY)
                rotationZ = -giro.value
            },
        )
        {
            bola(LOGIN_CX[2], LOGIN_BY, 20f, central = true)
        }
        CapaLogo(
            modifier = Modifier.graphicsLayer
            {
                transformOrigin = origenGiro(LOGIN_CX[0], PIVOTE)
                rotationZ = -izquierda.value
            },
        )
        {
            penduloExterior(0, colorBarra)
        }
        CapaLogo(
            modifier = Modifier.graphicsLayer
            {
                transformOrigin = origenGiro(LOGIN_CX[4], PIVOTE)
                rotationZ = -derecha.value
            },
        )
        {
            penduloExterior(4, colorBarra)
        }
        CapaLogo {
            frenteLogo()
        }
        Text(
            text = "VIXXER",
            color = colorTexto,
            fontSize = (alto.value * 0.092f).sp,
            letterSpacing = (alto.value * 0.045f).sp,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = alto * 0.727f),
        )
    }
}

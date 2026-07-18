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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val LOGIN_BY = 130f
private const val PIVOTE = 12f
private val LOGIN_CX = floatArrayOf(58f, 104f, 150f, 196f, 242f)

private val salidaCubica = Easing { t -> 1f - (1f - t) * (1f - t) * (1f - t) }
private val entradaCubica = Easing { t -> t * t * t }
private val salidaCuadrada = Easing { t -> 1f - (1f - t) * (1f - t) }
private val entradaCuadrada = Easing { t -> t * t }

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

internal fun DrawScope.bola(x: Float, y: Float, r: Float, central: Boolean = false, mini: Boolean = false)
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
    if (central)
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
fun LogoPendulo(alto: Dp, colorTexto: Color, colorBarra: Color = Color(0xFF9AA2AD), velocidad: Int = 1500)
{
    val ancho = alto * (300f / 264f)
    val izq = remember { Animatable(0f) }
    val der = remember { Animatable(0f) }
    val giro = remember { Animatable(0f) }
    var orden by remember { mutableStateOf(0L to "ciclo") }
    val taps = remember { longArrayOf(0L, 0L) }

    LaunchedEffect(orden, velocidad) {
        when (orden.second)
        {
            "golpe" ->
            {
                coroutineScope {
                    launch {
                        izq.animateTo(-19f, tween(300, easing = salidaCubica))
                        izq.animateTo(0f, tween(240, easing = entradaCubica))
                    }
                    launch {
                        delay(430)
                        der.animateTo(19f, tween(300, easing = salidaCubica))
                        der.animateTo(0f, tween(260, easing = entradaCubica))
                    }
                }
                delay(410)
                orden = System.currentTimeMillis() to "ciclo"
            }
            "caos" ->
            {
                coroutineScope {
                    launch {
                        izq.animateTo(-30f, tween(280, easing = salidaCubica))
                        izq.animateTo(7f, tween(220, easing = entradaCubica))
                        izq.animateTo(-11f, tween(200, easing = salidaCuadrada))
                        izq.animateTo(0f, tween(200, easing = entradaCuadrada))
                    }
                    launch {
                        der.animateTo(30f, tween(280, easing = salidaCubica))
                        der.animateTo(-7f, tween(220, easing = entradaCubica))
                        der.animateTo(11f, tween(200, easing = salidaCuadrada))
                        der.animateTo(0f, tween(200, easing = entradaCuadrada))
                    }
                    launch {
                        giro.snapTo(0f)
                        delay(140)
                        giro.animateTo(360f, tween(950, easing = salidaCubica))
                    }
                }
                delay(910)
                orden = System.currentTimeMillis() to "ciclo"
            }
            else ->
            {
                izq.snapTo(0f)
                der.snapTo(0f)
                giro.snapTo(0f)
                val a = (velocidad * 0.3f).toInt()
                val b = (velocidad * 0.28f).toInt()
                while (true)
                {
                    coroutineScope {
                        launch {
                            izq.animateTo(-12f, tween(a, easing = salidaCubica))
                            izq.animateTo(0f, tween(b, easing = entradaCubica))
                        }
                        launch {
                            delay((a + b).toLong())
                            der.animateTo(12f, tween(a, easing = salidaCubica))
                            der.animateTo(0f, tween(b, easing = entradaCubica))
                        }
                    }
                }
            }
        }
    }

    fun tocar()
    {
        val ahora = System.currentTimeMillis()
        taps[0] = if (ahora - taps[1] < 450) taps[0] + 1 else 1
        taps[1] = ahora
        if (taps[0] >= 3)
        {
            taps[0] = 0
            orden = ahora to "caos"
            return
        }
        orden = ahora to "golpe"
    }

    Box(
        modifier = Modifier
            .width(ancho)
            .height(alto)
            .pointerInput(Unit) {
                detectTapGestures { tocar() }
            },
    )
    {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val e = size.width / 300f
            withTransform({ scale(e, e, pivot = Offset.Zero) }) {
                drawLine(Color(0xFF4A525E), Offset(84f, 240f), Offset(66f, 256f), strokeWidth = 11f, cap = StrokeCap.Round)
                drawLine(Color(0xFF4A525E), Offset(216f, 240f), Offset(234f, 256f), strokeWidth = 11f, cap = StrokeCap.Round)
                clipRect(0f, 0f, 300f, 72f) {
                    val zonaTras = Rect(Offset(24f, 6f), Size(252f, 200f))
                    drawRoundRect(
                        brochaMarco(zonaTras),
                        topLeft = zonaTras.topLeft,
                        size = zonaTras.size,
                        cornerRadius = CornerRadius(52f),
                        style = Stroke(width = 10f),
                        alpha = 0.9f,
                    )
                }
                hilos(LOGIN_CX[1], colorBarra)
                hilos(LOGIN_CX[2], colorBarra)
                hilos(LOGIN_CX[3], colorBarra)
                bola(LOGIN_CX[1], LOGIN_BY, 19f)
                withTransform({ rotate(-giro.value, pivot = Offset(LOGIN_CX[2], LOGIN_BY)) }) {
                    bola(LOGIN_CX[2], LOGIN_BY, 20f, central = true)
                }
                bola(LOGIN_CX[3], LOGIN_BY, 19f)
                withTransform({ rotate(-izq.value, pivot = Offset(LOGIN_CX[0], PIVOTE)) }) {
                    hilos(LOGIN_CX[0], colorBarra)
                    bola(LOGIN_CX[0], LOGIN_BY, 19f)
                }
                withTransform({ rotate(-der.value, pivot = Offset(LOGIN_CX[4], PIVOTE)) }) {
                    hilos(LOGIN_CX[4], colorBarra)
                    bola(LOGIN_CX[4], LOGIN_BY, 19f)
                }
                val zonaFrente = Rect(Offset(24f, 18f), Size(252f, 222f))
                drawRoundRect(
                    brochaMarco(zonaFrente),
                    topLeft = zonaFrente.topLeft,
                    size = zonaFrente.size,
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

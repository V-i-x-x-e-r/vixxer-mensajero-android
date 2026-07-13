package dev.vixxer.mensajero.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlin.math.abs

private val PALETA = listOf(
    Color(0xFF35D487),
    Color(0xFF65A7FF),
    Color(0xFFFFD166),
    Color(0xFFFF6B5E),
    Color(0xFF8B7CFF),
    Color(0xFF22C55E),
)

private fun colorDe(nombre: String): Color
{
    var h = 0.0
    for (c in nombre)
    {
        val entero = (h.toLong() and 0xFFFFFFFFL).toInt()
        h = c.code + ((entero shl 5) - h)
    }
    return PALETA[(abs(h) % PALETA.size).toInt()]
}

@Composable
fun Avatar(nombre: String = "", uri: String? = null, tamano: Dp = 40.dp)
{
    if (!uri.isNullOrEmpty())
    {
        AsyncImage(
            model = uri,
            contentDescription = null,
            modifier = Modifier.size(tamano).clip(CircleShape),
        )
        return
    }
    val inicial = (nombre.trim().firstOrNull() ?: '?').uppercaseChar().toString()
    Box(
        modifier = Modifier
            .size(tamano)
            .background(colorDe(nombre), CircleShape),
        contentAlignment = Alignment.Center,
    )
    {
        Text(
            inicial,
            color = Color(0xFF0A0A0A),
            fontWeight = FontWeight.Bold,
            fontSize = (tamano.value * 0.42f).sp,
        )
    }
}

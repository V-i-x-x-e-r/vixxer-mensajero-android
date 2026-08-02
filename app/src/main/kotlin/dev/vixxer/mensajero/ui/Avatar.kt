package dev.vixxer.mensajero.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

private val AVATARES_CLAROS = listOf(
    Color(0xFFD8DBDF),
    Color(0xFFC9CDD2),
    Color(0xFFBBC0C6),
    Color(0xFFAEB4BB),
    Color(0xFFA1A8B0),
    Color(0xFF949CA5),
)

private val AVATARES_OSCUROS = listOf(
    Color(0xFF282C31),
    Color(0xFF30353B),
    Color(0xFF393F46),
    Color(0xFF424950),
    Color(0xFF4B535C),
    Color(0xFF555E68),
)

private fun indiceDe(nombre: String, cantidad: Int): Int
{
    var hash = 0
    for (c in nombre)
    {
        hash = 31 * hash + c.code
    }
    return (hash and Int.MAX_VALUE) % cantidad
}

@Composable
fun Avatar(
    nombre: String = "",
    uri: String? = null,
    tamano: Dp = 40.dp,
    alFallarCarga: (() -> Unit)? = null,
)
{
    val tema = LocalTema.current
    val paleta = if (tema.oscuro) AVATARES_OSCUROS else AVATARES_CLAROS
    val fondo = paleta[indiceDe(nombre, paleta.size)]
    val brillo = lerp(fondo, Color.White, if (tema.oscuro) 0.16f else 0.24f)
    val sombra = lerp(fondo, Color.Black, if (tema.oscuro) 0.18f else 0.12f)
    val relleno = Brush.linearGradient(listOf(brillo, fondo, sombra))
    val texto = if (tema.oscuro) Color(0xFFF5F6F7) else Color(0xFF121417)
    val borde = if (tema.oscuro) Color.White.copy(alpha = 0.16f) else Color(0xFF08090B).copy(alpha = 0.12f)
    val inicial = (nombre.trim().firstOrNull() ?: '?').uppercaseChar().toString()

    Box(
        modifier = Modifier
            .size(tamano)
            .clip(CircleShape)
            .background(relleno)
            .border(Vidrio.anchoBorde, borde, CircleShape),
        contentAlignment = Alignment.Center,
    )
    {
        Text(
            inicial,
            color = texto,
            fontWeight = FontWeight.SemiBold,
            fontSize = (tamano.value * 0.38f).sp,
        )
        if (!uri.isNullOrEmpty())
        {
            AsyncImage(
                model = uri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onError = { alFallarCarga?.invoke() },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

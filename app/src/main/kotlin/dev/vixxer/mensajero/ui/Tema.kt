package dev.vixxer.mensajero.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import dev.vixxer.mensajero.R
import dev.vixxer.mensajero.nucleo.Almacen

data class Paleta(
    val fondo: Color,
    val surface: Color,
    val texto: Color,
    val muted: Color,
    val borde: Color,
    val bordeFoco: Color,
    val botonFondo: Color,
    val botonTexto: Color,
    val enlace: Color,
    val placeholder: Color,
    val error: Color,
)

val CLARO = Paleta(
    fondo = Color(0xFFFFFFFF),
    surface = Color(0xFFF5F5F7),
    texto = Color(0xFF1D1D1F),
    muted = Color(0xFF86868B),
    borde = Color(0xFFE8E8ED),
    bordeFoco = Color(0xFF1D1D1F),
    botonFondo = Color(0xFF1D1D1F),
    botonTexto = Color(0xFFFFFFFF),
    enlace = Color(0xFF1D1D1F),
    placeholder = Color(0xFFA1A1A6),
    error = Color(0xFFDC2626),
)

val OSCURO = Paleta(
    fondo = Color(0xFF0D0F13),
    surface = Color(0xFF171A20),
    texto = Color(0xFFEDEFF3),
    muted = Color(0xFF7C8492),
    borde = Color(0xFF242A33),
    bordeFoco = Color(0xFFEDEFF3),
    botonFondo = Color(0xFFFFFFFF),
    botonTexto = Color(0xFF0D0F13),
    enlace = Color(0xFFEDEFF3),
    placeholder = Color(0xFF5E6673),
    error = Color(0xFFF87171),
)

val COLORIDO = Paleta(
    fondo = Color(0xFFFAFAF7),
    surface = Color(0xFFF0F0EA),
    texto = Color(0xFF1C1B18),
    muted = Color(0xFF78766D),
    borde = Color(0xFFE2E1D8),
    bordeFoco = Color(0xFF14B8A6),
    botonFondo = Color(0xFF14B8A6),
    botonTexto = Color(0xFFFFFFFF),
    enlace = Color(0xFF14B8A6),
    placeholder = Color(0xFFA3A198),
    error = Color(0xFFE0356B),
)

val ACENTOS = listOf(
    Color(0xFF14B8A6),
    Color(0xFF3B82F6),
    Color(0xFF6C5CE7),
    Color(0xFFF43F5E),
    Color(0xFFF59E0B),
    Color(0xFF22C55E),
)

private val CLAVE_TEMA = "vixxer_tema"
private val CLAVE_ACENTO = "vixxer_tema_acento"
private val paletas = mapOf("claro" to CLARO, "oscuro" to OSCURO, "colorido" to COLORIDO)

class EstadoTema(private val almacen: Almacen, oscuroSistema: Boolean)
{
    var nombre by mutableStateOf(if (oscuroSistema) "oscuro" else "claro")
        private set
    var acento by mutableStateOf(ACENTOS[0])
        private set

    init
    {
        val guardado = almacen.leer(CLAVE_TEMA)
        if (paletas.containsKey(guardado))
        {
            nombre = guardado!!
        }
        val acentoGuardado = almacen.leer(CLAVE_ACENTO)?.let { crudo ->
            ACENTOS.firstOrNull { aHex(it) == crudo }
        }
        if (acentoGuardado != null)
        {
            acento = acentoGuardado
        }
    }

    val oscuro: Boolean
        get() = nombre == "oscuro"

    val colores: Paleta
        get() = if (nombre == "colorido")
        {
            COLORIDO.copy(botonFondo = acento, bordeFoco = acento, enlace = acento)
        }
        else
        {
            paletas.getValue(nombre)
        }

    val coloresAuth: Paleta
        get() = if (nombre == "colorido") CLARO else colores

    fun elegirTema(nuevo: String)
    {
        if (!paletas.containsKey(nuevo))
        {
            return
        }
        nombre = nuevo
        almacen.escribir(CLAVE_TEMA, nuevo)
    }

    fun elegirAcento(nuevo: Color)
    {
        if (nuevo !in ACENTOS)
        {
            return
        }
        acento = nuevo
        almacen.escribir(CLAVE_ACENTO, aHex(nuevo))
    }

    fun alternar()
    {
        elegirTema(if (nombre == "oscuro") "claro" else "oscuro")
    }

    private fun aHex(color: Color): String
    {
        val valor = color.value shr 32
        return "#%06X".format(valor.toLong() and 0xFFFFFF)
    }
}

val LocalTema = staticCompositionLocalOf<EstadoTema> { error("EstadoTema sin proveer") }

val FuenteOutfit = FontFamily(
    Font(R.font.outfit_regular, FontWeight.Normal),
    Font(R.font.outfit_medium, FontWeight.Medium),
    Font(R.font.outfit_semibold, FontWeight.SemiBold),
    Font(R.font.outfit_bold, FontWeight.Bold),
)

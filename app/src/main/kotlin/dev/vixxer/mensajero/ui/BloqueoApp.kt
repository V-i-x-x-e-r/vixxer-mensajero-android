package dev.vixxer.mensajero.ui

import android.app.Activity
import android.content.Context
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import dev.vixxer.mensajero.AplicacionVixxer
import dev.vixxer.mensajero.nucleo.Almacen
import java.security.MessageDigest
import java.security.SecureRandom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object Seguridad
{
    private const val CAPTURAS = "vixxer_bloquear_capturas"
    private const val ACTIVO = "vixxer_bloqueo_activo"
    private const val BIOMETRICO = "vixxer_biometrico"
    private const val PIN = "vixxer_pin"
    const val LARGO_PIN = 4

    fun capturasBloqueadas(estado: Almacen): Boolean = estado.leer(CAPTURAS) == "1"

    fun ponerCapturas(estado: Almacen, activo: Boolean)
    {
        estado.escribir(CAPTURAS, if (activo) "1" else "0")
    }

    fun bloqueoActivo(estado: Almacen): Boolean = estado.leer(ACTIVO) == "1"

    fun ponerBloqueoActivo(estado: Almacen, activo: Boolean)
    {
        estado.escribir(ACTIVO, if (activo) "1" else "0")
    }

    fun biometricoActivo(estado: Almacen): Boolean = estado.leer(BIOMETRICO) == "1"

    fun ponerBiometrico(estado: Almacen, activo: Boolean)
    {
        estado.escribir(BIOMETRICO, if (activo) "1" else "0")
    }

    fun pinConfigurado(boveda: Almacen): Boolean = !boveda.leer(PIN).isNullOrEmpty()

    fun candadoHabilitado(boveda: Almacen, estado: Almacen): Boolean = pinConfigurado(boveda) && bloqueoActivo(estado)

    fun guardarPin(boveda: Almacen, pin: String)
    {
        val sal = ByteArray(16)
        SecureRandom().nextBytes(sal)
        val salHex = aHex(sal)
        boveda.escribir(PIN, "$salHex:${hashear(pin, salHex)}")
    }

    fun verificarPin(boveda: Almacen, pin: String): Boolean
    {
        val guardado = boveda.leer(PIN) ?: return false
        val partes = guardado.split(":")
        if (partes.size != 2)
        {
            return false
        }
        val esperado = partes[1].toByteArray(Charsets.UTF_8)
        val calculado = hashear(pin, partes[0]).toByteArray(Charsets.UTF_8)
        return MessageDigest.isEqual(esperado, calculado)
    }

    fun quitarPin(boveda: Almacen, estado: Almacen)
    {
        boveda.borrar(PIN)
        ponerBloqueoActivo(estado, false)
        ponerBiometrico(estado, false)
    }

    private fun hashear(pin: String, salHex: String): String
    {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(deHex(salHex))
        return aHex(md.digest(pin.toByteArray(Charsets.UTF_8)))
    }

    private fun aHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    private fun deHex(hex: String): ByteArray = ByteArray(hex.length / 2) { i ->
        hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
    }
}

fun aplicarCapturas(actividad: Activity, bloquear: Boolean)
{
    if (bloquear)
    {
        actividad.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
    else
    {
        actividad.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}

fun biometricoDisponible(contexto: Context): Boolean =
    BiometricManager.from(contexto).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS

private fun pedirBiometrico(actividad: FragmentActivity, alExito: () -> Unit)
{
    val ejecutor = ContextCompat.getMainExecutor(actividad)
    val prompt = BiometricPrompt(actividad, ejecutor, object : BiometricPrompt.AuthenticationCallback()
    {
        override fun onAuthenticationSucceeded(resultado: BiometricPrompt.AuthenticationResult)
        {
            alExito()
        }
    })
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Desbloquear Vixxer")
        .setSubtitle("Usa tu huella o rostro")
        .setNegativeButtonText("Usar PIN")
        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
        .build()
    prompt.authenticate(info)
}

@Composable
fun PantallaBloqueo(app: AplicacionVixxer, alDesbloquear: () -> Unit)
{
    val colores = LocalTema.current.colores
    val contexto = LocalContext.current
    val biometrico = remember { Seguridad.biometricoActivo(app.estado) && biometricoDisponible(contexto) }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    fun agregar(digito: String)
    {
        if (pin.length >= Seguridad.LARGO_PIN)
        {
            return
        }
        error = false
        val nuevo = pin + digito
        pin = nuevo
        if (nuevo.length == Seguridad.LARGO_PIN)
        {
            if (Seguridad.verificarPin(app.boveda, nuevo))
            {
                alDesbloquear()
            }
            else
            {
                error = true
                pin = ""
            }
        }
    }

    LaunchedEffect(Unit) {
        if (biometrico)
        {
            (contexto as? FragmentActivity)?.let { pedirBiometrico(it, alDesbloquear) }
        }
    }
    BackHandler(enabled = true) {}

    LienzoBloqueo(
        colores = colores,
        titulo = "Vixxer bloqueado",
        subtitulo = if (error) "PIN incorrecto, intenta de nuevo" else "Ingresa tu PIN",
        error = error,
        largo = pin.length,
        biometrico = biometrico,
        alDigito = { agregar(it) },
        alBorrar = { if (pin.isNotEmpty()) pin = pin.dropLast(1) },
        alBiometrico = { (contexto as? FragmentActivity)?.let { pedirBiometrico(it, alDesbloquear) } },
    )
}

@Composable
fun ConfigurarPin(app: AplicacionVixxer, alTerminar: (Boolean) -> Unit)
{
    val colores = LocalTema.current.colores
    val alcance = rememberCoroutineScope()
    var fase by remember { mutableStateOf("crear") }
    var primero by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var guardando by remember { mutableStateOf(false) }

    fun agregar(digito: String)
    {
        if (guardando) return
        if (pin.length >= Seguridad.LARGO_PIN)
        {
            return
        }
        error = false
        val nuevo = pin + digito
        pin = nuevo
        if (nuevo.length < Seguridad.LARGO_PIN)
        {
            return
        }
        if (fase == "crear")
        {
            primero = nuevo
            pin = ""
            fase = "confirmar"
        }
        else if (nuevo == primero)
        {
            guardando = true
            alcance.launch {
                val guardado = withContext(Dispatchers.IO) {
                    runCatching { Seguridad.guardarPin(app.boveda, nuevo) }.isSuccess
                }
                if (guardado)
                {
                    Seguridad.ponerBloqueoActivo(app.estado, true)
                    alTerminar(true)
                }
                else
                {
                    guardando = false
                    error = true
                    pin = ""
                    primero = ""
                    fase = "crear"
                }
            }
        }
        else
        {
            error = true
            pin = ""
            primero = ""
            fase = "crear"
        }
    }

    BackHandler(enabled = !guardando) { alTerminar(false) }

    LienzoBloqueo(
        colores = colores,
        titulo = if (fase == "crear") "Crea un PIN" else "Confirma tu PIN",
        subtitulo = when
        {
            error -> "No coincidió, empieza de nuevo"
            fase == "crear" -> "4 dígitos para proteger la app"
            else -> "Vuelve a escribirlo"
        },
        error = error,
        largo = pin.length,
        biometrico = false,
        alDigito = { agregar(it) },
        alBorrar = { if (pin.isNotEmpty()) pin = pin.dropLast(1) },
        alBiometrico = {},
        alCancelar = { if (!guardando) alTerminar(false) },
    )
}

@Composable
private fun LienzoBloqueo(
    colores: Paleta,
    titulo: String,
    subtitulo: String,
    error: Boolean,
    largo: Int,
    biometrico: Boolean,
    alDigito: (String) -> Unit,
    alBorrar: () -> Unit,
    alBiometrico: () -> Unit,
    alCancelar: (() -> Unit)? = null,
)
{
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colores.fondo)
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    )
    {
        Text(titulo, fontSize = 22.sp, fontFamily = FuenteOutfit, fontWeight = FontWeight.SemiBold, color = colores.texto)
        Spacer(modifier = Modifier.height(6.dp))
        Text(subtitulo, fontSize = 13.sp, color = if (error) colores.error else colores.muted, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(28.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            for (i in 0 until Seguridad.LARGO_PIN)
            {
                val lleno = i < largo
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .background(
                            if (lleno) (if (error) colores.error else colores.texto) else Color.Transparent,
                            CircleShape,
                        )
                        .border(1.5.dp, if (error) colores.error else colores.borde, CircleShape),
                )
            }
        }
        Spacer(modifier = Modifier.height(40.dp))
        TecladoPin(colores, biometrico, alDigito, alBorrar, alBiometrico)
        if (alCancelar != null)
        {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "Cancelar",
                fontSize = 15.sp,
                color = colores.muted,
                modifier = Modifier.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { alCancelar() },
            )
        }
    }
}

@Composable
private fun TecladoPin(
    colores: Paleta,
    biometrico: Boolean,
    alDigito: (String) -> Unit,
    alBorrar: () -> Unit,
    alBiometrico: () -> Unit,
)
{
    val filas = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf(if (biometrico) "bio" else "", "0", "borrar"),
    )
    Column(verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        for (fila in filas)
        {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                for (tecla in fila)
                {
                    when (tecla)
                    {
                        "" -> Spacer(modifier = Modifier.size(72.dp))
                        "borrar" -> TeclaEspecial(colores, "⌫", alBorrar)
                        "bio" -> TeclaEspecial(colores, "☉", alBiometrico)
                        else -> TeclaDigito(colores, tecla) { alDigito(tecla) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TeclaDigito(colores: Paleta, digito: String, alPulsar: () -> Unit)
{
    Box(
        modifier = Modifier
            .size(72.dp)
            .background(colores.surface, CircleShape)
            .border(Vidrio.anchoBorde, colores.borde, CircleShape)
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { alPulsar() },
        contentAlignment = Alignment.Center,
    )
    {
        Text(digito, fontSize = 26.sp, fontFamily = FuenteOutfit, fontWeight = FontWeight.Medium, color = colores.texto)
    }
}

@Composable
private fun TeclaEspecial(colores: Paleta, simbolo: String, alPulsar: () -> Unit)
{
    Box(
        modifier = Modifier
            .size(72.dp)
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { alPulsar() },
        contentAlignment = Alignment.Center,
    )
    {
        Text(simbolo, fontSize = 24.sp, color = colores.texto)
    }
}

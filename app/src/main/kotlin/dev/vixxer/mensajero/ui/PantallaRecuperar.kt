package dev.vixxer.mensajero.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vixxer.mensajero.AplicacionVixxer
import dev.vixxer.mensajero.nucleo.Identidad
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Composable
fun PantallaRecuperar(
    app: AplicacionVixxer,
    codigoLeido: String? = null,
    alEscanear: () -> Unit = {},
    alNavegar: (String) -> Unit,
)
{
    val tema = LocalTema.current
    val colores = tema.coloresAuth
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()
    var codigo by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var cargando by remember { mutableStateOf(false) }
    var nuevoCodigo by remember { mutableStateOf(app.identidad.codigoPendiente().orEmpty()) }
    var confirmarNuevo by remember { mutableStateOf(false) }
    var archivo by remember { mutableStateOf<JSONObject?>(null) }

    suspend fun publicarIdentidad(identidad: Identidad.Nueva)
    {
        val firma = app.identidad.prepararFirma()
        app.identidad.confirmarIdentidad(identidad)
        app.identidad.confirmarFirma(firma)
        app.api.publicarIdentidad(identidad.publicKey, firma.publicKey, identidad.respaldo)
        app.identidad.confirmarRespaldoSubido()
    }

    val selectorArchivo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null)
        {
            alcance.launch {
                val leido = withContext(Dispatchers.IO) {
                    runCatching {
                        contexto.contentResolver.openInputStream(uri)?.use { flujo ->
                            app.identidad.leerRespaldoArchivo(flujo.readBytes().toString(Charsets.UTF_8))
                        }
                    }.getOrNull()
                }
                if (leido != null)
                {
                    archivo = leido
                    error = ""
                }
            }
        }
    }

    fun recuperar(codigoQR: String? = null)
    {
        val cod = (codigoQR ?: codigo).trim()
        if (cod.isEmpty())
        {
            error = "Escribe tu código de recuperación"
            return
        }
        error = ""
        cargando = true
        alcance.launch {
            try
            {
                val restaurada = withContext(Dispatchers.IO) {
                    val respaldo = archivo ?: app.api.obtenerRespaldo() as? JSONObject
                    app.identidad.prepararRestauracion(respaldo, cod)
                }
                if (restaurada == null)
                {
                    error = "Código incorrecto. Revísalo e intenta de nuevo."
                    return@launch
                }
                withContext(Dispatchers.IO) {
                    publicarIdentidad(restaurada)
                    app.identidad.confirmarCodigoGuardado()
                }
                alNavegar("chats")
            }
            catch (e: Exception)
            {
                if (app.identidad.respaldoPendiente() != null)
                {
                    withContext(Dispatchers.IO) { app.cerrarSesionLocal() }
                    alNavegar("login")
                    return@launch
                }
                error = "No se pudo recuperar. Revisa tu conexión."
            }
            finally
            {
                cargando = false
            }
        }
    }

    fun empezarDeNuevo()
    {
        confirmarNuevo = false
        error = ""
        cargando = true
        alcance.launch {
            try
            {
                val creado = withContext(Dispatchers.IO) {
                    val identidad = app.identidad.prepararIdentidad()
                    publicarIdentidad(identidad)
                    identidad.codigo
                }
                nuevoCodigo = creado
            }
            catch (e: Exception)
            {
                if (app.identidad.respaldoPendiente() != null)
                {
                    withContext(Dispatchers.IO) { app.cerrarSesionLocal() }
                    alNavegar("login")
                    return@launch
                }
                error = "No se pudo crear una identidad nueva."
            }
            finally
            {
                cargando = false
            }
        }
    }

    LaunchedEffect(codigoLeido)
    {
        if (!codigoLeido.isNullOrBlank())
        {
            if (!codigoLeido.startsWith(PREFIJO_VINCULO))
            {
                error = "Ese QR no es de vincular dispositivo"
            }
            else
            {
                val leido = codigoLeido.removePrefix(PREFIJO_VINCULO)
                codigo = leido
                recuperar(leido)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colores.fondo),
    )
    {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 16.dp, start = 28.dp, end = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        )
        {
            LogoPenduloFila(alto = 22.dp)
            Text("Vixxer", fontSize = 18.sp, fontFamily = FuenteOutfit, fontWeight = FontWeight.SemiBold, color = colores.texto)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp)
                .padding(top = 90.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.Center,
        )
        {
            Column(modifier = Modifier.padding(bottom = 36.dp)) {
                Text(
                    "Recuperar tus chats",
                    fontSize = 24.sp,
                    fontFamily = FuenteOutfit,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.5).sp,
                    color = colores.texto,
                )
                Text(
                    "Escribe el código de recuperación que guardaste al crear tu cuenta.",
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = colores.muted,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Campo(valor = codigo, alCambiar = { codigo = it }, placeholder = "Código de recuperación", enMayusculas = true)
                if (error.isNotEmpty())
                {
                    Text(error, fontSize = 13.sp, color = colores.error)
                }
                Boton(titulo = "Recuperar", alPulsar = { recuperar() }, cargando = cargando)
                Column(modifier = Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Opcion(
                        texto = "Escanear desde tu otro teléfono",
                        color = colores.texto,
                        borde = colores.borde,
                    ) { alEscanear() }
                    Opcion(
                        texto = if (archivo != null) "Archivo cargado ✓ — escribe tu código" else "Restaurar desde un archivo",
                        color = if (archivo != null) colores.botonFondo else colores.texto,
                        borde = colores.borde,
                    ) { selectorArchivo.launch("*/*") }
                    Opcion(
                        texto = "No tengo el código — empezar de nuevo",
                        color = colores.texto,
                        borde = colores.borde,
                    ) { confirmarNuevo = true }
                }
                Text(
                    "Empezar de nuevo descarta el historial cifrado anterior.",
                    fontSize = 12.sp,
                    color = colores.muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                )
            }
        }

        RespaldoCodigo(
            visible = nuevoCodigo.isNotEmpty(),
            codigo = nuevoCodigo,
            alCerrar = {
                alcance.launch {
                    withContext(Dispatchers.IO) { app.identidad.confirmarCodigoGuardado() }
                    alNavegar("chats")
                }
            },
        )

        Confirmacion(
            visible = confirmarNuevo,
            titulo = "Empezar de nuevo",
            mensaje = "Sin tu código de recuperación perderás para siempre el acceso a los mensajes anteriores, tuyos y los de tus chats. Esto no se puede deshacer.",
            textoConfirmar = "Crear identidad nueva",
            destructivo = true,
            alConfirmar = { empezarDeNuevo() },
            alCancelar = { confirmarNuevo = false },
        )
    }
}

@Composable
private fun Opcion(texto: String, color: androidx.compose.ui.graphics.Color, borde: androidx.compose.ui.graphics.Color, alPulsar: () -> Unit)
{
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borde, RoundedCornerShape(12.dp))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { alPulsar() }
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center,
    )
    {
        Text(texto, fontSize = 14.sp, fontFamily = FuenteOutfit, fontWeight = FontWeight.Medium, color = color)
    }
}

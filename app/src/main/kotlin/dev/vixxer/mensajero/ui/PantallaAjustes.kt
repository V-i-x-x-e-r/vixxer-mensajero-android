package dev.vixxer.mensajero.ui

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vixxer.mensajero.AplicacionVixxer
import dev.vixxer.mensajero.nucleo.ClavesSeguras
import dev.vixxer.mensajero.nucleo.ConexionSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Composable
fun PantallaAjustes(app: AplicacionVixxer, alNavegar: (String) -> Unit)
{
    val tema = LocalTema.current
    val colores = tema.colores
    val alcance = rememberCoroutineScope()
    val portapapeles = LocalClipboardManager.current
    var usuario by remember { mutableStateOf("") }
    var codigo by remember { mutableStateOf("") }
    var avatar by remember { mutableStateOf<String?>(null) }
    var copiado by remember { mutableStateOf(false) }
    var prefs by remember { mutableStateOf<JSONObject?>(null) }
    var confirmarSalir by remember { mutableStateOf(false) }
    var mostrarQr by remember { mutableStateOf(false) }
    var subiendoFoto by remember { mutableStateOf(false) }
    val contexto = androidx.compose.ui.platform.LocalContext.current
    var capturas by remember { mutableStateOf(Seguridad.capturasBloqueadas(app.estado)) }
    var pinPuesto by remember { mutableStateOf(Seguridad.pinConfigurado(app.boveda)) }
    var biometrico by remember { mutableStateOf(Seguridad.biometricoActivo(app.estado)) }
    var configurandoPin by remember { mutableStateOf(false) }
    val hayBiometrico = remember { biometricoDisponible(contexto) }

    val selectorFoto = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null)
        {
            subiendoFoto = true
            alcance.launch {
                val nueva = withContext(Dispatchers.IO) {
                    runCatching {
                        val imagen = comprimirImagen(contexto, uri) ?: return@runCatching null
                        val b64 = android.util.Base64.encodeToString(imagen.bytes, android.util.Base64.NO_WRAP)
                        val r = app.api.subirAvatar(b64, "image/jpeg") as JSONObject
                        r.textoO("avatar_url").ifEmpty { null }
                    }.getOrNull()
                }
                subiendoFoto = false
                if (nueva != null)
                {
                    avatar = nueva
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        val cache = withContext(Dispatchers.IO) { app.estado.leer("vixxer_perfil_cache") }
        if (cache != null)
        {
            runCatching {
                val p = JSONObject(cache)
                usuario = p.optString("usuario")
                codigo = p.optString("codigo")
                avatar = p.optString("avatar").ifEmpty { null }
            }
        }
        withContext(Dispatchers.IO) {
            runCatching {
                val d = app.api.miCodigo() as JSONObject
                usuario = d.optString("usuario")
                codigo = d.optString("codigo")
                avatar = d.textoO("avatar_url").ifEmpty { null }
                app.estado.escribir("vixxer_perfil_cache", JSONObject()
                    .put("usuario", usuario)
                    .put("codigo", codigo)
                    .put("avatar", avatar ?: "")
                    .toString())
            }
            runCatching { prefs = app.api.preferencias() as? JSONObject }
        }
    }

    fun cambiarPreferencia(clave: String, valor: Boolean)
    {
        val nuevas = JSONObject(prefs?.toString() ?: "{}").put(clave, valor)
        prefs = nuevas
        alcance.launch(Dispatchers.IO) {
            runCatching { app.api.actualizarPreferencias(nuevas) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colores.fondo)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .padding(bottom = 24.dp),
    )
    {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        )
        {
            Text(
                "‹",
                fontSize = 26.sp,
                color = colores.texto,
                modifier = Modifier.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { alNavegar("chats") },
            )
            Text("Ajustes", fontSize = 18.sp, fontFamily = FuenteOutfit, fontWeight = FontWeight.SemiBold, color = colores.texto)
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        )
        {
            Box(
                modifier = Modifier.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                    app.saltarBloqueo = true
                    selectorFoto.launch(androidx.activity.result.PickVisualMediaRequest(
                        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly,
                    ))
                },
            )
            {
                Avatar(nombre = usuario, uri = avatar, tamano = 92.dp)
            }
            Text(usuario.ifEmpty { "…" }, fontSize = 20.sp, fontFamily = FuenteOutfit, fontWeight = FontWeight.SemiBold, color = colores.texto)
            Text(
                if (subiendoFoto) "subiendo foto…" else "toca la foto para cambiarla",
                fontSize = 12.sp,
                color = colores.muted,
            )
        }

        Seccion("TU CÓDIGO DE AMIGO", colores)
        Tarjeta {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                        portapapeles.setText(AnnotatedString(codigo))
                        copiado = true
                    }
                    .padding(vertical = 16.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            )
            {
                Text(codigo.ifEmpty { "…" }, fontSize = 17.sp, fontFamily = FuenteOutfit, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = colores.texto)
                Text(if (copiado) "copiado ✓" else "toca para copiar", fontSize = 12.sp, color = colores.muted)
            }
            Separador(colores)
            FilaNav("Mostrar código QR", colores) { mostrarQr = true }
        }

        Seccion("APARIENCIA", colores)
        Tarjeta {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            )
            {
                Text("Tema", fontSize = 15.sp, color = colores.texto)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for ((clave, etiqueta) in listOf("claro" to "Claro", "oscuro" to "Oscuro", "colorido" to "Colorido"))
                    {
                        val activo = tema.nombre == clave
                        Text(
                            etiqueta,
                            fontSize = 12.sp,
                            color = if (activo) colores.botonTexto else colores.texto,
                            modifier = Modifier
                                .background(if (activo) colores.botonFondo else Color.Transparent, RoundedCornerShape(Vidrio.radioPildora))
                                .border(Vidrio.anchoBorde, colores.borde, RoundedCornerShape(Vidrio.radioPildora))
                                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { tema.elegirTema(clave) }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
            }
            if (tema.nombre == "colorido")
            {
                Separador(colores)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                )
                {
                    Text("Color", fontSize = 15.sp, color = colores.texto)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (c in ACENTOS)
                        {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(c, CircleShape)
                                    .then(if (tema.acento == c) Modifier.border(2.5.dp, colores.texto, CircleShape) else Modifier)
                                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { tema.elegirAcento(c) },
                            )
                        }
                    }
                }
            }
        }

        Seccion("PRIVACIDAD", colores)
        Tarjeta {
            FilaSwitch("Mostrar mi conexión", prefs?.optBoolean("mostrar_conexion", true) ?: true, colores) {
                cambiarPreferencia("mostrar_conexion", it)
            }
            Separador(colores)
            FilaSwitch("Acuses de lectura", prefs?.optBoolean("mostrar_acuses", true) ?: true, colores) {
                cambiarPreferencia("mostrar_acuses", it)
            }
            Separador(colores)
            FilaNav("Usuarios bloqueados", colores) { alNavegar("bloqueados") }
        }

        Seccion("SEGURIDAD", colores)
        Tarjeta {
            FilaSwitch("Bloquear capturas de pantalla", capturas, colores) { activo ->
                capturas = activo
                Seguridad.ponerCapturas(app.estado, activo)
                (contexto as? android.app.Activity)?.let { aplicarCapturas(it, activo) }
            }
            Separador(colores)
            FilaSwitch("Bloqueo con PIN", pinPuesto, colores) { activo ->
                if (activo)
                {
                    configurandoPin = true
                }
                else
                {
                    Seguridad.quitarPin(app.boveda, app.estado)
                    pinPuesto = false
                    biometrico = false
                }
            }
            if (pinPuesto && hayBiometrico)
            {
                Separador(colores)
                FilaSwitch("Desbloqueo biométrico", biometrico, colores) { activo ->
                    biometrico = activo
                    Seguridad.ponerBiometrico(app.estado, activo)
                }
            }
        }

        Seccion("CUENTA", colores)
        Tarjeta {
            FilaNav("Cambiar contraseña", colores) { alNavegar("cambiar-contrasena") }
            Separador(colores)
            Text(
                "Cerrar sesión",
                fontSize = 15.sp,
                color = colores.error,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { confirmarSalir = true }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            )
        }

        Text(
            "Vixxer 0.2.0-f2",
            fontSize = 12.sp,
            color = colores.muted,
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }

    if (mostrarQr && codigo.isNotEmpty())
    {
        CodigoQr(codigo = codigo, colores = colores) { mostrarQr = false }
    }

    if (configurandoPin)
    {
        ConfigurarPin(app) { exito ->
            configurandoPin = false
            if (exito)
            {
                pinPuesto = true
            }
        }
    }

    Confirmacion(
        visible = confirmarSalir,
        titulo = "Cerrar sesión",
        mensaje = "Tus llaves se conservan en este dispositivo. Podrás entrar de nuevo con tu usuario y contraseña.",
        textoConfirmar = "Cerrar sesión",
        destructivo = true,
        alConfirmar = {
            confirmarSalir = false
            alcance.launch {
                withContext(Dispatchers.IO) {
                    app.boveda.borrar(ClavesSeguras.TOKEN)
                    app.boveda.borrar(ClavesSeguras.MI_ID)
                    ConexionSocket.desconectar()
                }
                alNavegar("login")
            }
        },
        alCancelar = { confirmarSalir = false },
    )
}

@Composable
private fun Seccion(titulo: String, colores: Paleta)
{
    Text(
        titulo,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
        color = colores.muted,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun Tarjeta(contenido: @Composable () -> Unit)
{
    Column(modifier = Modifier.fillMaxWidth().panelVidrio()) {
        contenido()
    }
}

@Composable
private fun Separador(colores: Paleta)
{
    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(colores.borde))
}

@Composable
private fun FilaNav(etiqueta: String, colores: Paleta, alPulsar: () -> Unit)
{
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { alPulsar() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    )
    {
        Text(etiqueta, fontSize = 15.sp, color = colores.texto)
        Text("›", fontSize = 18.sp, color = colores.muted)
    }
}

@Composable
private fun FilaSwitch(etiqueta: String, valor: Boolean, colores: Paleta, alCambio: (Boolean) -> Unit)
{
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    )
    {
        Text(etiqueta, fontSize = 15.sp, color = colores.texto)
        Switch(
            checked = valor,
            onCheckedChange = alCambio,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colores.botonTexto,
                checkedTrackColor = colores.botonFondo,
                uncheckedThumbColor = colores.muted,
                uncheckedTrackColor = colores.surface,
                uncheckedBorderColor = colores.borde,
            ),
        )
    }
}

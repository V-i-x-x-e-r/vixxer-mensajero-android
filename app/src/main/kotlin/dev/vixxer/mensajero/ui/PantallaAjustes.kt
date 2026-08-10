package dev.vixxer.mensajero.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vixxer.mensajero.AplicacionVixxer
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
    var errorPreferencias by remember { mutableStateOf("") }
    var guardandoPreferencia by remember { mutableStateOf(false) }
    var confirmarSalir by remember { mutableStateOf(false) }
    var borrando by remember { mutableStateOf(false) }
    var borrarClave by remember { mutableStateOf("") }
    var borrarEstado by remember { mutableStateOf("") }
    var borrarEnCurso by remember { mutableStateOf(false) }
    var mostrarQr by remember { mutableStateOf(false) }
    var subiendoFoto by remember { mutableStateOf(false) }
    var errorFoto by remember { mutableStateOf("") }
    val contexto = androidx.compose.ui.platform.LocalContext.current
    var capturas by remember { mutableStateOf(Seguridad.capturasBloqueadas(app.estado)) }
    var pinPuesto by remember { mutableStateOf(Seguridad.pinConfigurado(app.boveda)) }
    var biometrico by remember { mutableStateOf(Seguridad.biometricoActivo(app.estado)) }
    var configurandoPin by remember { mutableStateOf(false) }
    var cambiandoPin by remember { mutableStateOf(false) }
    val hayBiometrico = remember { biometricoDisponible(contexto) }
    var respaldoCfg by remember { mutableStateOf(RespaldoConfig.leer(app.estado)) }
    var respaldando by remember { mutableStateOf(false) }
    var nuevoCodigo by remember { mutableStateOf("") }
    var importando by remember { mutableStateOf(false) }
    var importArchivo by remember { mutableStateOf<JSONObject?>(null) }
    var importCodigo by remember { mutableStateOf("") }
    var importEstado by remember { mutableStateOf("") }

    val selectorRespaldo = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null)
        {
            importEstado = ""
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
                    importArchivo = leido
                    importEstado = ""
                }
                else
                {
                    importEstado = "Archivo no válido."
                }
            }
        }
    }

    val selectorFoto = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null)
        {
            subiendoFoto = true
            errorFoto = ""
            alcance.launch {
                val resultado = withContext(Dispatchers.IO) {
                    runCatching {
                        val imagen = comprimirAvatar(contexto, uri)
                        val b64 = android.util.Base64.encodeToString(imagen.bytes, android.util.Base64.NO_WRAP)
                        val respuesta = app.api.subirAvatar(b64, "image/jpeg") as? JSONObject
                            ?: error("El servidor no confirmó la foto")
                        respuesta.textoO("avatar_url").ifEmpty {
                            error("El servidor no devolvió la foto")
                        }
                    }
                }
                subiendoFoto = false
                val nueva = resultado.getOrNull()
                if (nueva != null)
                {
                    avatar = nueva
                    withContext(Dispatchers.IO) {
                        guardarCachePerfil(app, usuario, codigo, nueva)
                    }
                }
                else
                {
                    errorFoto = mensajeErrorAvatar(resultado.exceptionOrNull())
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
        if (guardandoPreferencia) return
        val nuevas = JSONObject(prefs?.toString() ?: "{}").put(clave, valor)
        guardandoPreferencia = true
        errorPreferencias = ""
        alcance.launch {
            val guardada = withContext(Dispatchers.IO) {
                runCatching { app.api.actualizarPreferencias(nuevas) }.isSuccess
            }
            if (guardada)
            {
                prefs = nuevas
            }
            else
            {
                errorPreferencias = "No se pudo guardar la preferencia. Revisa tu conexión."
            }
            guardandoPreferencia = false
        }
    }

    fun aplicarCfg(nueva: RespaldoConfig)
    {
        respaldoCfg = nueva
        RespaldoConfig.guardar(app.estado, nueva)
    }

    fun hacerCopiaAhora()
    {
        if (respaldando) return
        respaldando = true
        app.saltarBloqueo = true
        alcance.launch {
            val listo = withContext(Dispatchers.IO) {
                runCatching {
                    val preparado = app.identidad.prepararRespaldoActual() ?: return@runCatching null
                    if (respaldoCfg.destino != "local")
                    {
                        app.api.subirRespaldo(preparado.respaldo)
                    }
                    preparado
                }.getOrNull()
            }
            if (listo != null)
            {
                if (respaldoCfg.destino == "local")
                {
                    runCatching { exportarRespaldoArchivo(contexto, listo.respaldo) }
                }
                aplicarCfg(respaldoCfg.copy(ultimo = System.currentTimeMillis()))
                nuevoCodigo = listo.codigo
            }
            respaldando = false
        }
    }

    fun importarLlaveAnterior()
    {
        importEstado = ""
        val archivo = importArchivo
        if (archivo == null || importCodigo.trim().isEmpty())
        {
            importEstado = "Elige el archivo y escribe su código."
            return
        }
        alcance.launch {
            val ok = withContext(Dispatchers.IO) {
                app.identidad.importarLlaveAnterior(archivo, importCodigo.trim())
            }
            importEstado = if (ok) "listo" else "El código no abre ese respaldo."
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .fondoVixxer()
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
                modifier = Modifier.pulsable { alNavegar("chats") },
            )
            Text("Ajustes", fontSize = 18.sp, fontFamily = FuenteOutfit, fontWeight = FontWeight.SemiBold, color = colores.texto)
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        )
        {
            Box(
                modifier = Modifier
                    .pulsable(habilitado = !subiendoFoto) {
                        app.saltarBloqueo = true
                        selectorFoto.launch(androidx.activity.result.PickVisualMediaRequest(
                            androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly,
                        ))
                    }
                    .semantics
                    {
                        contentDescription = "Cambiar foto de perfil"
                        role = Role.Button
                    },
            )
            {
                Avatar(
                    nombre = usuario,
                    uri = avatar,
                    tamano = 80.dp,
                    alFallarCarga = {
                        if (errorFoto.isEmpty())
                        {
                            errorFoto = "La foto está guardada, pero no se pudo mostrar."
                        }
                    },
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(28.dp)
                        .circuloVidrio(fuerte = true, desenfocar = false),
                    contentAlignment = Alignment.Center,
                )
                {
                    if (subiendoFoto)
                    {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = colores.texto,
                            strokeWidth = 1.5.dp,
                        )
                    }
                    else
                    {
                        Lapiz(color = colores.texto, tamano = 13.dp)
                    }
                }
            }
            Text(usuario.ifEmpty { "…" }, fontSize = 19.sp, fontFamily = FuenteOutfit, fontWeight = FontWeight.SemiBold, color = colores.texto)
            if (subiendoFoto || errorFoto.isNotEmpty())
            {
                Text(
                    if (subiendoFoto) "Actualizando foto…" else errorFoto,
                    fontSize = 12.sp,
                    color = if (errorFoto.isEmpty()) colores.muted else colores.error,
                )
            }
        }

        Seccion("TU CÓDIGO DE AMIGO", colores)
        Tarjeta {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .pulsable {
                        portapapeles.setText(AnnotatedString(codigo))
                        copiado = true
                    }
                    .padding(vertical = 12.dp, horizontal = 16.dp),
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
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
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
                                .pulsable { tema.elegirTema(clave) }
                                .background(if (activo) colores.botonFondo else Color.Transparent, RoundedCornerShape(Vidrio.radioPildora))
                                .border(Vidrio.anchoBorde, colores.borde, RoundedCornerShape(Vidrio.radioPildora))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
            }
            if (tema.nombre == "colorido")
            {
                Separador(colores)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
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
                                    .pulsable { tema.elegirAcento(c) }
                                    .size(24.dp)
                                    .background(c, CircleShape)
                                    .then(if (tema.acento == c) Modifier.border(2.5.dp, colores.texto, CircleShape) else Modifier),
                            )
                        }
                    }
                }
            }
        }

        Seccion("PRIVACIDAD", colores)
        Tarjeta {
            FilaSwitch("Mostrar mi conexión", prefs?.optBoolean("mostrar_conexion", true) ?: true, colores, !guardandoPreferencia) {
                cambiarPreferencia("mostrar_conexion", it)
            }
            Separador(colores)
            FilaSwitch("Acuses de lectura", prefs?.optBoolean("mostrar_acuses", true) ?: true, colores, !guardandoPreferencia) {
                cambiarPreferencia("mostrar_acuses", it)
            }
            Separador(colores)
            FilaNav("Usuarios bloqueados", colores) { alNavegar("bloqueados") }
        }
        if (errorPreferencias.isNotEmpty())
        {
            Text(errorPreferencias, fontSize = 13.sp, color = colores.error)
        }

        Seccion("CERCANÍA", colores)
        Tarjeta {
            FilaNav("Ver radar de cercanía", colores) { alNavegar("cercania") }
            Text(
                "La ruta se elige junto al buscador de Chats: internet si hay, y si no, tus mensajes saltan por Bluetooth entre vixxers cercanos.",
                fontSize = 12.sp,
                color = colores.muted,
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp),
            )
        }

        Seccion("SEGURIDAD", colores)
        Tarjeta {
            FilaSwitch("Bloquear capturas de pantalla", capturas, colores) { activo ->
                capturas = activo
                Seguridad.ponerCapturas(app.estado, activo)
                (contexto as? android.app.Activity)?.let { aplicarCapturas(it, activo) }
            }
            Separador(colores)
            FilaSwitch("Bloqueo con PIN", pinPuesto, colores, !cambiandoPin) { activo ->
                if (activo)
                {
                    configurandoPin = true
                }
                else
                {
                    cambiandoPin = true
                    alcance.launch {
                        val quitado = withContext(Dispatchers.IO) {
                            runCatching { Seguridad.quitarPin(app.boveda, app.estado) }.isSuccess
                        }
                        if (quitado)
                        {
                            pinPuesto = false
                            biometrico = false
                        }
                        cambiandoPin = false
                    }
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

        Seccion("COPIA DE SEGURIDAD", colores)
        Tarjeta {
            FilaValor("Destino", if (respaldoCfg.destino == "nube") "Servidor (cifrada)" else "Archivo", colores) {
                aplicarCfg(respaldoCfg.copy(destino = if (respaldoCfg.destino == "nube") "local" else "nube"))
            }
            Separador(colores)
            FilaValor("Frecuencia", RespaldoConfig.etiquetaFrecuencia(respaldoCfg.frecuencia), colores) {
                val i = RespaldoConfig.FRECUENCIAS.indexOf(respaldoCfg.frecuencia)
                aplicarCfg(respaldoCfg.copy(frecuencia = RespaldoConfig.FRECUENCIAS[(i + 1) % RespaldoConfig.FRECUENCIAS.size]))
            }
            Separador(colores)
            FilaValor("Hora", "%02d:00".format(respaldoCfg.hora), colores, apagada = respaldoCfg.frecuencia == "nunca") {
                aplicarCfg(respaldoCfg.copy(hora = (respaldoCfg.hora + 1) % 24))
            }
            Separador(colores)
            FilaNav(if (respaldando) "Respaldando…" else "Hacer copia ahora", colores) { hacerCopiaAhora() }
        }
        Text(
            (respaldoCfg.ultimo?.let { "Última copia: ${textoFecha(it)}. " } ?: "Aún no has hecho una copia. ") +
                "Tu llave se respalda cifrada; solo tu código de recuperación la abre.",
            fontSize = 12.sp,
            color = colores.muted,
            modifier = Modifier.padding(top = 8.dp).padding(horizontal = 4.dp),
        )

        Seccion("CUENTA", colores)
        Tarjeta {
            FilaNav("Importar llave anterior", colores) { importando = true }
            Separador(colores)
            FilaNav("Cambiar contraseña", colores) { alNavegar("cambiar-contrasena") }
            Separador(colores)
            Text(
                "Cerrar sesión",
                fontSize = 15.sp,
                color = colores.error,
                modifier = Modifier
                    .fillMaxWidth()
                    .pulsable { confirmarSalir = true }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            )
            Separador(colores)
            Text(
                "Eliminar mi cuenta",
                fontSize = 15.sp,
                color = colores.error,
                modifier = Modifier
                    .fillMaxWidth()
                    .pulsable { borrando = true }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            )
        }

        Text(
            "Vixxer ${dev.vixxer.mensajero.BuildConfig.VERSION_NAME}",
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

    RespaldoCodigo(visible = nuevoCodigo.isNotEmpty(), codigo = nuevoCodigo) { nuevoCodigo = "" }

    if (importando)
    {
        ImportarLlave(
            colores = colores,
            archivoCargado = importArchivo != null,
            codigo = importCodigo,
            estado = importEstado,
            alElegirArchivo = {
                app.saltarBloqueo = true
                selectorRespaldo.launch("application/json")
            },
            alCambiarCodigo = { importCodigo = it },
            alImportar = { importarLlaveAnterior() },
            alCerrar = {
                importando = false
                importArchivo = null
                importCodigo = ""
                importEstado = ""
            },
        )
    }

    if (borrando)
    {
        BorrarCuenta(
            colores = colores,
            contrasena = borrarClave,
            estado = borrarEstado,
            enCurso = borrarEnCurso,
            alCambiarContrasena = { borrarClave = it },
            alBorrar = {
                borrarEnCurso = true
                borrarEstado = ""
                alcance.launch {
                    val error = withContext(Dispatchers.IO)
                    {
                        runCatching { app.api.borrarCuenta(borrarClave) }
                            .fold({ null }, { it.message ?: "No se pudo eliminar la cuenta" })
                    }
                    if (error != null)
                    {
                        borrarEnCurso = false
                        borrarEstado = error
                        return@launch
                    }
                    withContext(Dispatchers.IO) { app.borrarDatosLocales() }
                    borrando = false
                    borrarClave = ""
                    borrarEnCurso = false
                    alNavegar("login")
                }
            },
            alCerrar = {
                borrando = false
                borrarClave = ""
                borrarEstado = ""
            },
        )
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
                withContext(Dispatchers.IO) { app.cerrarSesionLocal() }
                alNavegar("login")
            }
        },
        alCancelar = { confirmarSalir = false },
    )
}

private fun guardarCachePerfil(
    app: AplicacionVixxer,
    usuario: String,
    codigo: String,
    avatar: String,
)
{
    val guardado = app.estado.leer("vixxer_perfil_cache")
    val cache = runCatching { JSONObject(guardado ?: "{}") }.getOrElse { JSONObject() }
    if (usuario.isNotEmpty())
    {
        cache.put("usuario", usuario)
    }
    if (codigo.isNotEmpty())
    {
        cache.put("codigo", codigo)
    }
    cache.put("avatar", avatar)
    app.estado.escribir("vixxer_perfil_cache", cache.toString())
}

@Composable
internal fun Seccion(titulo: String, colores: Paleta)
{
    Text(
        titulo,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
        color = colores.muted,
        modifier = Modifier.padding(top = 20.dp, bottom = 6.dp),
    )
}

@Composable
internal fun Tarjeta(contenido: @Composable () -> Unit)
{
    Column(modifier = Modifier.fillMaxWidth().panelVidrio()) {
        contenido()
    }
}

@Composable
internal fun Separador(colores: Paleta)
{
    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(colores.borde))
}

internal val ALTO_FILA = 48.dp

@Composable
internal fun FilaNav(etiqueta: String, colores: Paleta, alPulsar: () -> Unit)
{
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pulsable { alPulsar() }
            .heightIn(min = ALTO_FILA)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    )
    {
        Text(etiqueta, fontSize = 15.sp, color = colores.texto)
        Chevron(colores.muted)
    }
}

@Composable
internal fun FilaSwitch(
    etiqueta: String,
    valor: Boolean,
    colores: Paleta,
    habilitado: Boolean = true,
    alCambio: (Boolean) -> Unit,
)
{
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ALTO_FILA)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    )
    {
        Text(etiqueta, fontSize = 15.sp, color = colores.texto)
        Switch(
            checked = valor,
            onCheckedChange = alCambio,
            enabled = habilitado,
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

@Composable
internal fun FilaValor(
    etiqueta: String,
    valor: String,
    colores: Paleta,
    apagada: Boolean = false,
    alPulsar: () -> Unit,
)
{
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (apagada) Modifier
                else Modifier.pulsable { alPulsar() },
            )
            .heightIn(min = ALTO_FILA)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    )
    {
        Text(etiqueta, fontSize = 15.sp, color = if (apagada) colores.muted else colores.texto)
        Text(valor, fontSize = 14.sp, color = colores.muted)
    }
}

@Composable
private fun BorrarCuenta(
    colores: Paleta,
    contrasena: String,
    estado: String,
    enCurso: Boolean,
    alCambiarContrasena: (String) -> Unit,
    alBorrar: () -> Unit,
    alCerrar: () -> Unit,
)
{
    androidx.compose.ui.window.Dialog(onDismissRequest = alCerrar)
    {
        ContenidoBorrarCuenta(
            colores = colores,
            contrasena = contrasena,
            estado = estado,
            enCurso = enCurso,
            alCambiarContrasena = alCambiarContrasena,
            alBorrar = alBorrar,
            alCerrar = alCerrar,
        )
    }
}

@Composable
internal fun ContenidoBorrarCuenta(
    colores: Paleta,
    contrasena: String,
    estado: String,
    enCurso: Boolean,
    alCambiarContrasena: (String) -> Unit,
    alBorrar: () -> Unit,
    alCerrar: () -> Unit,
)
{
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colores.surface, RoundedCornerShape(16.dp))
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        )
        {
            Text(
                "Eliminar mi cuenta",
                fontSize = 17.sp,
                fontFamily = FuenteOutfit,
                fontWeight = FontWeight.SemiBold,
                color = colores.error,
            )
            Text(
                "Se borran tu cuenta, tus llaves, tu respaldo, tu foto, tus amigos y tus " +
                    "conversaciones, también del lado de quien te escribió. Esto no se puede deshacer " +
                    "y no hay forma de recuperar nada después.",
                fontSize = 13.sp,
                color = colores.muted,
            )
            Campo(
                valor = contrasena,
                alCambiar = alCambiarContrasena,
                placeholder = "Escribe tu contraseña para confirmar",
                esContrasena = true,
            )
            if (estado.isNotEmpty())
            {
                Text(estado, fontSize = 13.sp, color = colores.error)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            )
            {
                Text(
                    "Cancelar",
                    fontSize = 15.sp,
                    color = colores.muted,
                    modifier = Modifier
                        .weight(1f)
                        .pulsable { alCerrar() }
                        .padding(vertical = 12.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Text(
                    if (enCurso) "Eliminando..." else "Eliminar",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (contrasena.isEmpty() || enCurso) colores.muted else colores.error,
                    modifier = Modifier
                        .weight(1f)
                        .pulsable {
                            if (contrasena.isNotEmpty() && !enCurso)
                            {
                                alBorrar()
                            }
                        }
                        .padding(vertical = 12.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
}

@Composable
private fun ImportarLlave(
    colores: Paleta,
    archivoCargado: Boolean,
    codigo: String,
    estado: String,
    alElegirArchivo: () -> Unit,
    alCambiarCodigo: (String) -> Unit,
    alImportar: () -> Unit,
    alCerrar: () -> Unit,
)
{
    val listo = estado == "listo"
    androidx.compose.ui.window.Dialog(onDismissRequest = alCerrar)
    {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colores.surface, RoundedCornerShape(16.dp))
                .border(1.dp, colores.borde, RoundedCornerShape(16.dp))
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        )
        {
            Text("Importar llave anterior", fontSize = 17.sp, fontFamily = FuenteOutfit, fontWeight = FontWeight.SemiBold, color = colores.texto)
            Text(
                "Si tienes el archivo de respaldo y el código de una identidad vieja, podrás volver a leer esos chats sin perder los actuales.",
                fontSize = 13.sp,
                color = colores.muted,
            )
            Row(
                modifier = Modifier
                    .pulsable { alElegirArchivo() }
                    .fillMaxWidth()
                    .border(1.dp, if (archivoCargado) colores.botonFondo else colores.borde, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            )
            {
                Text(
                    if (archivoCargado) "Respaldo cargado ✓  (toca para cambiar)" else "Toca para elegir el archivo .json del respaldo",
                    fontSize = 14.sp,
                    color = if (archivoCargado) colores.texto else colores.muted,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
            Campo(valor = codigo, alCambiar = alCambiarCodigo, placeholder = "Código de recuperación de esa llave", enMayusculas = true)
            if (listo)
            {
                Text("Llave importada. Tus chats viejos vuelven a leerse.", fontSize = 13.sp, color = colores.texto)
            }
            else if (estado.isNotEmpty())
            {
                Text(estado, fontSize = 13.sp, color = colores.error)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            )
            {
                Text(
                    if (listo) "Cerrar" else "Cancelar",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colores.texto,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .pulsable { alCerrar() }
                        .weight(1f)
                        .border(Vidrio.anchoBorde, colores.borde, RoundedCornerShape(Vidrio.radioPildora))
                        .padding(vertical = 12.dp),
                )
                if (!listo)
                {
                    Text(
                        "Importar",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colores.botonTexto,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier
                            .pulsable { alImportar() }
                            .weight(1f)
                            .background(colores.botonFondo, RoundedCornerShape(Vidrio.radioPildora))
                            .padding(vertical = 12.dp),
                    )
                }
            }
        }
    }
}

private fun textoFecha(millis: Long): String =
    java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(millis))

private fun exportarRespaldoArchivo(contexto: android.content.Context, respaldo: JSONObject)
{
    val carpeta = java.io.File(contexto.cacheDir, "respaldos")
    carpeta.mkdirs()
    val archivo = java.io.File(carpeta, "vixxer-respaldo.json")
    archivo.writeText(JSONObject(respaldo.toString()).put("v", 1).toString())
    val uri = androidx.core.content.FileProvider.getUriForFile(contexto, dev.vixxer.mensajero.BuildConfig.APPLICATION_ID + ".archivos", archivo)
    val envio = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(android.content.Intent.EXTRA_STREAM, uri)
        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    contexto.startActivity(android.content.Intent.createChooser(envio, "Guardar respaldo de Vixxer"))
}

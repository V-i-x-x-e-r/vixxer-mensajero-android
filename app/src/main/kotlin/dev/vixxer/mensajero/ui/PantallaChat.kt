package dev.vixxer.mensajero.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import dev.vixxer.mensajero.AplicacionVixxer
import dev.vixxer.mensajero.DrenadorOutbox
import dev.vixxer.mensajero.ble.GestorCercania
import dev.vixxer.mensajero.llamadas.GestorLlamadas
import dev.vixxer.mensajero.nucleo.ClavesSeguras
import dev.vixxer.mensajero.nucleo.ConexionSocket
import dev.vixxer.mensajero.nucleo.Cripto
import dev.vixxer.mensajero.nucleo.DiagnosticoMesh
import dev.vixxer.mensajero.nucleo.Efimero
import dev.vixxer.mensajero.nucleo.EnvioDirecto
import dev.vixxer.mensajero.nucleo.Fechas
import dev.vixxer.mensajero.nucleo.IdMensaje
import dev.vixxer.mensajero.nucleo.Fijados
import dev.vixxer.mensajero.nucleo.Medios
import dev.vixxer.mensajero.nucleo.Ocultos
import dev.vixxer.mensajero.nucleo.Outbox
import dev.vixxer.mensajero.nucleo.Resumen
import dev.vixxer.mensajero.nucleo.TemporizadorEfimero
import io.socket.client.Socket
import io.socket.emitter.Emitter
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class Mensaje(
    val id: String,
    val remitenteId: String,
    val texto: String?,
    val enviadoEn: String,
    val entregado: Boolean = false,
    val leido: Boolean = false,
    val estado: String? = null,
    val editado: Boolean = false,
    val borrado: Boolean = false,
    val clienteId: String? = null,
    val respuestaA: String? = null,
    val respuestaTexto: String? = null,
    val reacciones: Map<String, String> = emptyMap(),
)

private fun mensajeDeJson(m: JSONObject): Mensaje = Mensaje(
    id = m.optString("id"),
    remitenteId = m.optString("remitente_id"),
    texto = if (m.isNull("texto")) null else m.optString("texto"),
    enviadoEn = m.optString("enviado_en"),
    entregado = !m.isNull("entregado_en"),
    leido = !m.isNull("leido_en"),
    estado = if (m.isNull("estado")) null else m.optString("estado"),
    editado = m.optBoolean("editado"),
    borrado = m.optString("contenido_cifrado") == "BORRADO",
    clienteId = if (m.isNull("cliente_id")) null else m.optString("cliente_id"),
    respuestaA = if (m.isNull("respuesta_a")) null else m.optString("respuesta_a"),
    respuestaTexto = if (m.isNull("respuestaTexto")) null else m.optString("respuestaTexto"),
)

private fun mensajeAJson(m: Mensaje): JSONObject
{
    val obj = JSONObject()
    obj.put("id", m.id)
    obj.put("remitente_id", m.remitenteId)
    obj.put("texto", m.texto ?: JSONObject.NULL)
    obj.put("enviado_en", m.enviadoEn)
    if (m.entregado)
    {
        obj.put("entregado_en", m.enviadoEn)
    }
    if (m.leido)
    {
        obj.put("leido_en", m.enviadoEn)
    }
    if (m.estado != null)
    {
        obj.put("estado", m.estado)
    }
    obj.put("editado", m.editado)
    if (m.borrado)
    {
        obj.put("contenido_cifrado", "BORRADO")
    }
    obj.put("cliente_id", m.clienteId ?: JSONObject.NULL)
    obj.put("respuesta_a", m.respuestaA ?: JSONObject.NULL)
    if (m.respuestaTexto != null)
    {
        obj.put("respuestaTexto", m.respuestaTexto)
    }
    return obj
}

private fun textoVisible(texto: String?): String
{
    if (texto == null)
    {
        return "No se pudo descifrar este mensaje"
    }
    if (texto.startsWith("{"))
    {
        val efimero = Efimero.leerEfimero(texto)
        if (efimero != null)
        {
            return efimero.m
        }
        return Resumen.resumenMensaje(texto)
    }
    return texto
}

private fun reaccionesDe(data: JSONObject): Map<String, String>
{
    val obj = data.optJSONObject("reacciones") ?: return emptyMap()
    return obj.keys().asSequence().associateWith { obj.optString(it) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PantallaChat(app: AplicacionVixxer, amigo: Amigo, alNavegar: (String) -> Unit = {}, alVolver: () -> Unit)
{
    val tema = LocalTema.current
    val colores = tema.colores
    val alcance = rememberCoroutineScope()
    val portapapeles = LocalClipboardManager.current
    val vibrador = androidx.compose.ui.platform.LocalHapticFeedback.current
    val otroId = amigo.id
    var mensajes by remember { mutableStateOf(listOf<Mensaje>()) }
    var texto by remember { mutableStateOf("") }
    var escribiendo by remember { mutableStateOf(false) }
    var presencia by remember { mutableStateOf<JSONObject?>(null) }
    var aliasNombre by remember { mutableStateOf<String?>(null) }
    var temporizador by remember { mutableStateOf(0) }
    var hayMas by remember { mutableStateOf(true) }
    var masCargando by remember { mutableStateOf(false) }
    var miId by remember { mutableStateOf("") }
    var sel by remember { mutableStateOf<AccionesDe<Mensaje>?>(null) }
    var respondiendo by remember { mutableStateOf<Mensaje?>(null) }
    var editando by remember { mutableStateOf<Mensaje?>(null) }
    var reenviando by remember { mutableStateOf<Mensaje?>(null) }
    var seleccionando by remember { mutableStateOf(false) }
    var seleccionados by remember { mutableStateOf(listOf<String>()) }
    var buscando by remember { mutableStateOf(false) }
    var consulta by remember { mutableStateOf("") }
    var fijados by remember { mutableStateOf(listOf<Fijados.Fijado>()) }
    var indiceFijado by remember { mutableStateOf(0) }
    var ocultos by remember { mutableStateOf(setOf<String>()) }
    var pickerTemp by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var silenciado by remember { mutableStateOf(app.estadosChat.leerEstados().silenciados.contains(otroId)) }
    var confirmarKebab by remember { mutableStateOf<String?>(null) }
    var nuevosAbajo by remember { mutableStateOf(0) }
    var visor by remember { mutableStateOf<File?>(null) }
    var subiendo by remember { mutableStateOf(false) }
    var progresoSubida by remember { mutableStateOf(0f) }
    var adjuntando by remember { mutableStateOf(false) }
    var visorVideo by remember { mutableStateOf<MediaMensaje?>(null) }
    var grabando by remember { mutableStateOf(false) }
    var segundosGrabando by remember { mutableStateOf(0) }
    var grabacionPausada by remember { mutableStateOf(false) }
    val grabadora = remember { arrayOf<Grabadora?>(null) }
    var previos by remember { mutableStateOf(listOf<PrevioEnvio>()) }
    var mostrandoStickers by remember { mutableStateOf(false) }
    val contexto = LocalContext.current
    val cicloVida = LocalLifecycleOwner.current
    val enfoque = androidx.compose.ui.platform.LocalFocusManager.current
    val envioMedia = remember { EnvioMedia(app, contexto) }
    val listaEstado = rememberLazyListState()
    val escribiendoJob = remember { arrayOf<Job?>(null) }
    val apagarEscribiendo = remember { arrayOf<Job?>(null) }
    val purgados = remember { HashSet<String>() }
    val selectorFotoRef = remember { arrayOf<(() -> Unit)?>(null) }
    val selectorDocumentoRef = remember { arrayOf<(() -> Unit)?>(null) }
    val selectorVideoRef = remember { arrayOf<(() -> Unit)?>(null) }
    val permisoMicRef = remember { arrayOf<(() -> Unit)?>(null) }
    val camaraRef = remember { arrayOf<(() -> Unit)?>(null) }
    val fotoCamara = remember { arrayOf<android.net.Uri?>(null) }
    val claveBorrador = "chat-$otroId"

    val visibles = remember(mensajes, ocultos) {
        if (ocultos.isEmpty()) mensajes else mensajes.filter { !ocultos.contains(it.id) }
    }
    val mensajesPorId = remember(mensajes) { mensajes.associateBy { it.id } }
    val coincidencias = remember(visibles, consulta, buscando) {
        val q = consulta.trim().lowercase()
        if (buscando && q.isNotEmpty())
        {
            visibles.mapIndexedNotNull { i, m ->
                val t = m.texto
                if (t != null && !t.startsWith("{") && t.lowercase().contains(q)) i else null
            }
        }
        else
        {
            emptyList()
        }
    }
    var indiceCoincidencia by remember { mutableStateOf(0) }
    LaunchedEffect(coincidencias) {
        indiceCoincidencia = if (coincidencias.isEmpty()) 0 else coincidencias.lastIndex
    }
    LaunchedEffect(indiceCoincidencia, coincidencias) {
        coincidencias.getOrNull(indiceCoincidencia)?.let { listaEstado.animateScrollToItem(it) }
    }
    val tecladoVisible = WindowInsets.isImeVisible
    LaunchedEffect(tecladoVisible) {
        if (tecladoVisible && !buscando && visibles.isNotEmpty())
        {
            listaEstado.animateScrollToItem(visibles.size)
            delay(260)
            listaEstado.animateScrollToItem(visibles.size)
        }
    }

    fun guardarCache(lista: List<Mensaje>)
    {
        val arreglo = JSONArray()
        for (m in lista)
        {
            arreglo.put(mensajeAJson(m))
        }
        app.cacheChats.guardarChat(otroId, arreglo)
    }

    fun marcarLeidos(filas: List<Mensaje>)
    {
        val ids = filas.filter { it.remitenteId == otroId && it.estado == null }.map { it.id }
        if (ids.isEmpty())
        {
            return
        }
        val socket = ConexionSocket.obtener() ?: return
        socket.emit("mensaje:leido", JSONObject().put("ids", JSONArray(ids)))
    }

    suspend fun abrir(cifrado: String, nonce: String): String?
    {
        return withContext(Dispatchers.IO) {
            val pub = app.llaves.llavePublicaDe(otroId)
            app.identidad.descifrarConHistoricas(cifrado, nonce, pub)
                ?: app.identidad.descifrarConHistoricas(
                    cifrado,
                    nonce,
                    app.llaves.llavePublicaDe(otroId, forzar = true),
                )
        }
    }

    fun descifrarLote(filas: JSONArray): List<Mensaje>
    {
        var pub = app.llaves.llavePublicaDe(otroId)
        val textos = arrayOfNulls<String>(filas.length())
        var falto = false
        for (i in 0 until filas.length())
        {
            val f = filas.getJSONObject(i)
            if (f.optString("contenido_cifrado") == "BORRADO")
            {
                continue
            }
            textos[i] = app.identidad.descifrarConHistoricas(
                f.getString("contenido_cifrado"),
                f.getString("nonce"),
                pub,
            )
            if (textos[i] == null)
            {
                falto = true
            }
        }
        if (falto)
        {
            pub = app.llaves.llavePublicaDe(otroId, forzar = true)
        }
        val salida = ArrayList<Mensaje>()
        for (i in 0 until filas.length())
        {
            val f = filas.getJSONObject(i)
            val borrado = f.optString("contenido_cifrado") == "BORRADO"
            val claro = if (borrado) null else textos[i]
                ?: app.identidad.descifrarConHistoricas(
                    f.getString("contenido_cifrado"),
                    f.getString("nonce"),
                    pub,
                )
            salida.add(Mensaje(
                id = f.optString("id"),
                remitenteId = f.optString("remitente_id"),
                texto = if (borrado) null else claro ?: "No se pudo descifrar este mensaje",
                enviadoEn = f.optString("enviado_en"),
                entregado = !f.isNull("entregado_en"),
                leido = !f.isNull("leido_en"),
                editado = f.optBoolean("editado"),
                borrado = borrado,
                clienteId = if (f.isNull("cliente_id")) null else f.optString("cliente_id"),
                respuestaA = if (f.isNull("respuesta_a")) null else f.optString("respuesta_a"),
                reacciones = reaccionesDe(f),
            ))
        }
        return salida
    }

    fun intentarEnviar(item: JSONObject)
    {
        val cuentaId = miId
        if (cuentaId.isBlank())
        {
            return
        }
        alcance.launch {
            DrenadorOutbox.enviar(
                app,
                cuentaId,
                Outbox.Pendiente(Outbox.Tipo.DIRECTO, otroId, item),
            )
        }
    }

    fun vaciarOutbox()
    {
        alcance.launch {
            if (miId.isNotBlank())
            {
                val ids = withContext(Dispatchers.IO) {
                    app.outbox.leer(otroId).map { it.optString("localId") }.toSet()
                }
                mensajes = mensajes.map { if (it.id in ids) it.copy(estado = "enviando") else it }
                DrenadorOutbox.drenar(app, miId, Outbox.Tipo.DIRECTO, otroId, forzar = true)
            }
        }
    }

    suspend fun sincronizar()
    {
        try
        {
            val inicioSync = System.currentTimeMillis()
            val descifrados = withContext(Dispatchers.IO) {
                val filas = app.api.historial(otroId) as JSONArray
                Pair(filas.length(), descifrarLote(filas))
            }
            android.util.Log.d(
                "VxPerf",
                "chat servidor: ${descifrados.first} mensajes descifrados en ${System.currentTimeMillis() - inicioSync} ms",
            )
            val extras = mensajes.filter { m ->
                m.estado != null && descifrados.second.none { servidor ->
                    servidor.id == m.id || servidor.clienteId == m.id
                }
            }
            val lista = if (extras.isNotEmpty()) (descifrados.second + extras).sortedBy { it.enviadoEn } else descifrados.second
            mensajes = lista
            withContext(Dispatchers.IO) { guardarCache(lista) }
            hayMas = descifrados.first >= 50
            marcarLeidos(lista)
        }
        catch (e: Exception)
        {
        }
    }

    fun escribir(t: String)
    {
        texto = t
        val socket = ConexionSocket.obtener() ?: return
        socket.emit("usuario:escribiendo", JSONObject().put("para", otroId).put("activo", true))
        escribiendoJob[0]?.cancel()
        escribiendoJob[0] = alcance.launch {
            delay(1500)
            socket.emit("usuario:escribiendo", JSONObject().put("para", otroId).put("activo", false))
        }
    }

    suspend fun crearPendienteDirecto(
        destinoId: String,
        plano: String,
        respuestaA: String? = null,
        respuestaTexto: String? = null,
    ): JSONObject = withContext(Dispatchers.IO) {
        val nuevo = EnvioDirecto.crearPendiente(
            texto = plano,
            enviadoEn = Instant.now().toString(),
            respuestaA = respuestaA,
            respuestaTexto = respuestaTexto,
        )
        app.outbox.agregar(destinoId, nuevo)
        nuevo
    }

    fun mandar(plano: String)
    {
        val resp = respondiendo
        alcance.launch {
            val item = crearPendienteDirecto(
                destinoId = otroId,
                plano = plano,
                respuestaA = resp?.id,
                respuestaTexto = resp?.texto?.let { Resumen.resumenMensaje(it) },
            )
            mensajes = mensajes + Mensaje(
                id = item.getString("localId"),
                remitenteId = miId,
                texto = plano,
                enviadoEn = item.getString("enviado_en"),
                estado = "enviando",
                respuestaA = resp?.id,
                respuestaTexto = if (item.isNull("respuestaTexto")) null else item.optString("respuestaTexto"),
            )
            respondiendo = null
            listaEstado.animateScrollToItem(maxOf(0, mensajes.size - 1))
            intentarEnviar(item)
        }
    }

    fun enviar()
    {
        val limpio = texto.trim()
        if (limpio.isEmpty())
        {
            return
        }
        val objetivo = editando
        if (objetivo != null)
        {
            alcance.launch {
                val socket = ConexionSocket.obtener() ?: return@launch
                withContext(Dispatchers.IO) {
                    val priv = app.boveda.leer(ClavesSeguras.CLAVE_PRIVADA) ?: return@withContext
                    val pub = app.llaves.llavePublicaDe(otroId)
                    val (contenidoCifrado, nonce) = Cripto.cifrarTexto(limpio, pub, priv)
                    socket.emit("mensaje:editar", JSONObject()
                        .put("id", objetivo.id)
                        .put("destinatarioId", otroId)
                        .put("contenidoCifrado", contenidoCifrado)
                        .put("nonce", nonce))
                }
                mensajes = mensajes.map { if (it.id == objetivo.id) it.copy(texto = limpio, editado = true) else it }
                editando = null
                texto = ""
            }
            return
        }
        escribiendoJob[0]?.cancel()
        ConexionSocket.obtener()?.emit("usuario:escribiendo", JSONObject().put("para", otroId).put("activo", false))
        texto = ""
        mandar(if (temporizador > 0) Efimero.envolver(limpio, temporizador) else limpio)
    }

    fun rutaCercania(): Boolean =
        ConexionSocket.obtener()?.connected() != true &&
            GestorCercania.corriendo &&
            GestorCercania.amigoVisible(otroId)

    fun avisoCercania(aviso: String)
    {
        android.widget.Toast.makeText(contexto, aviso, android.widget.Toast.LENGTH_SHORT).show()
    }

    suspend fun despacharMediaCercania(par: Pair<String, ByteArray>?): Boolean
    {
        if (par == null)
        {
            return false
        }
        val (plano, datos) = par
        val localId = withContext(Dispatchers.IO) {
            val privada = app.boveda.leer(ClavesSeguras.CLAVE_PRIVADA) ?: return@withContext null
            val publica = runCatching { app.llaves.llavePublicaDe(otroId) }.getOrNull()
                ?: return@withContext null
            val claro = JSONObject()
                .put("t", "cercania-media")
                .put("plano", plano)
                .put("datos", Cripto.aBase64(datos))
                .toString()
            val sellado = runCatching { Cripto.cifrarTexto(claro, publica, privada) }.getOrNull()
                ?: return@withContext null
            val id = IdMensaje.nuevo()
            val resultado = GestorCercania.mensajeria(app).enviarDirectoA(
                GestorCercania.macsDeAmigo(otroId),
                otroId,
                sellado.first,
                sellado.second,
                id,
            )
            if (resultado.entregados > 0) id else null
        }
        if (localId == null)
        {
            return false
        }
        val nuevo = Mensaje(
            id = localId,
            remitenteId = miId,
            texto = plano,
            enviadoEn = Instant.now().toString(),
            estado = "cercania",
            clienteId = localId,
        )
        mensajes = mensajes + nuevo
        withContext(Dispatchers.IO) { guardarCache(mensajes) }
        listaEstado.animateScrollToItem(maxOf(0, mensajes.size - 1))
        return true
    }

    suspend fun despacharFotoWifi(imagen: ImagenLista, cap: String?): Boolean
    {
        val entregadoId = withContext(Dispatchers.IO) {
            val par = envioMedia.prepararImagenCompletaCercania(imagen, cap) ?: return@withContext null
            val (plano, bytes) = par
            val privada = app.boveda.leer(ClavesSeguras.CLAVE_PRIVADA) ?: return@withContext null
            val publica = runCatching { app.llaves.llavePublicaDe(otroId) }.getOrNull()
                ?: return@withContext null
            val nonce = ByteArray(Cripto.TAMANO_NONCE)
            java.security.SecureRandom().nextBytes(nonce)
            val caja = runCatching {
                Cripto.cifrar(bytes, nonce, Cripto.deBase64(publica), Cripto.deBase64(privada))
            }.getOrNull() ?: return@withContext null
            val id = IdMensaje.nuevo()
            val inicioWifi = System.nanoTime()
            registrarWifi(app, id, DiagnosticoMesh.Etapa.INTENTO)
            val sesion = dev.vixxer.mensajero.ble.RadioWifi.servir(contexto, caja)
            if (sesion == null)
            {
                registrarWifi(
                    app,
                    id,
                    DiagnosticoMesh.Etapa.ERROR,
                    inicioWifi,
                    DiagnosticoMesh.CodigoError.WIFI,
                )
                return@withContext null
            }
            val control = JSONObject()
                .put("t", "cercania-wifi")
                .put("ssid", sesion.ssid)
                .put("pass", sesion.pass)
                .put("puerto", sesion.puerto)
                .put("nonce", Cripto.aBase64(nonce))
                .put("plano", plano)
                .put("peso", caja.size)
                .toString()
            val sellado = runCatching { Cripto.cifrarTexto(control, publica, privada) }.getOrNull()
                ?: run {
                    sesion.cerrar()
                    registrarWifi(
                        app,
                        id,
                        DiagnosticoMesh.Etapa.ERROR,
                        inicioWifi,
                        DiagnosticoMesh.CodigoError.PREPARACION,
                    )
                    return@withContext null
                }
            val resultado = GestorCercania.mensajeria(app).enviarDirectoA(
                GestorCercania.macsDeAmigo(otroId),
                otroId,
                sellado.first,
                sellado.second,
                id,
            )
            if (resultado.entregados == 0)
            {
                sesion.cerrar()
                registrarWifi(
                    app,
                    id,
                    DiagnosticoMesh.Etapa.ERROR,
                    inicioWifi,
                    DiagnosticoMesh.CodigoError.WIFI,
                )
                return@withContext null
            }
            val entregado = sesion.esperarEntrega(90)
            registrarWifi(
                app,
                id,
                if (entregado) DiagnosticoMesh.Etapa.ENVIADO else DiagnosticoMesh.Etapa.ERROR,
                inicioWifi,
                if (entregado) null else DiagnosticoMesh.CodigoError.WIFI,
            )
            if (entregado) Pair(id, plano) else null
        }
        if (entregadoId == null)
        {
            return false
        }
        val nuevo = Mensaje(
            id = entregadoId.first,
            remitenteId = miId,
            texto = entregadoId.second,
            enviadoEn = Instant.now().toString(),
            estado = "cercania",
            clienteId = entregadoId.first,
        )
        mensajes = mensajes + nuevo
        withContext(Dispatchers.IO) { guardarCache(mensajes) }
        listaEstado.animateScrollToItem(maxOf(0, mensajes.size - 1))
        return true
    }

    fun enviarSticker(archivo: java.io.File)
    {
        mostrandoStickers = false
        if (rutaCercania())
        {
            avisoCercania("Sin internet: por Bluetooth solo viajan fotos ligeras y notas de voz")
            return
        }
        subiendo = true
        progresoSubida = 0f
        alcance.launch {
            val plano = withContext(Dispatchers.IO) {
                val datos = Stickers.leer(archivo) ?: return@withContext null
                envioMedia.prepararSticker(datos.first, datos.second, datos.third) { avance ->
                    alcance.launch { progresoSubida = avance.toFloat().coerceIn(0f, 1f) }
                }
            }
            subiendo = false
            if (plano != null)
            {
                mandar(plano)
            }
        }
    }

    fun enviarLote(lista: List<Pair<PrevioEnvio, String?>>)
    {
        if (lista.isEmpty())
        {
            return
        }
        subiendo = true
        progresoSubida = 0f
        alcance.launch {
            val porCercania = rutaCercania()
            for ((indice, par) in lista.withIndex())
            {
                val (item, cap) = par
                if (porCercania)
                {
                    if (item.esVideo)
                    {
                        avisoCercania("Sin internet: los videos no viajan por Bluetooth")
                    }
                    else
                    {
                        val imagen = item.imagen
                        var mandado = false
                        if (
                            imagen != null &&
                            dev.vixxer.mensajero.ble.RadioWifi.soportado(contexto) &&
                            GestorCercania.amigoSoportaWifi(otroId)
                        )
                        {
                            avisoCercania("Enviando foto completa por Wi-Fi directo…")
                            mandado = despacharFotoWifi(imagen, cap)
                            if (!mandado)
                            {
                                avisoCercania("Wi-Fi directo no disponible: va la miniatura")
                            }
                        }
                        if (!mandado)
                        {
                            val listo = withContext(Dispatchers.IO) {
                                imagen?.let { envioMedia.prepararImagenCercania(it, cap) }
                            }
                            if (!despacharMediaCercania(listo))
                            {
                                avisoCercania("No se pudo enviar por cercanía")
                            }
                        }
                    }
                    progresoSubida = (indice + 1f) / lista.size
                    continue
                }
                val plano = withContext(Dispatchers.IO) {
                    val progreso: (Double) -> Unit = { avance ->
                        val total = (indice + avance.coerceIn(0.0, 1.0)) / lista.size
                        alcance.launch { progresoSubida = total.toFloat() }
                    }
                    if (item.esVideo) envioMedia.prepararVideo(item.uri, cap, progreso)
                    else item.imagen?.let { envioMedia.prepararImagen(it, cap, progreso) }
                }
                progresoSubida = (indice + 1f) / lista.size
                if (plano != null)
                {
                    mandar(plano)
                }
            }
            subiendo = false
        }
    }

    fun enviarAudio(archivo: java.io.File, dur: Int, ondas: List<Float>)
    {
        subiendo = true
        progresoSubida = 0f
        alcance.launch {
            if (rutaCercania())
            {
                val listo = withContext(Dispatchers.IO) {
                    envioMedia.prepararAudioCercania(archivo, dur, ondas)
                }
                subiendo = false
                runCatching { archivo.delete() }
                if (listo == null)
                {
                    avisoCercania("La nota es muy larga para enviarse por Bluetooth")
                }
                else if (!despacharMediaCercania(listo))
                {
                    avisoCercania("No se pudo enviar por cercanía")
                }
                return@launch
            }
            val plano = withContext(Dispatchers.IO) {
                envioMedia.prepararAudio(archivo, dur, ondas) { avance ->
                    alcance.launch { progresoSubida = avance.toFloat().coerceIn(0f, 1f) }
                }
            }
            subiendo = false
            runCatching { archivo.delete() }
            if (plano != null)
            {
                mandar(plano)
            }
        }
    }

    fun terminarGrabacion(enviar: Boolean)
    {
        val activa = grabadora[0] ?: return
        grabadora[0] = null
        grabando = false
        val archivo = activa.terminar()
        if (enviar && archivo != null && segundosGrabando >= 1)
        {
            enviarAudio(archivo, segundosGrabando, activa.ondas())
        }
        else
        {
            runCatching { archivo?.delete() }
        }
    }

    DisposableEffect(cicloVida) {
        val observador = LifecycleEventObserver { _, evento ->
            if (evento == Lifecycle.Event.ON_STOP)
            {
                terminarGrabacion(false)
            }
        }
        cicloVida.lifecycle.addObserver(observador)
        onDispose {
            cicloVida.lifecycle.removeObserver(observador)
            terminarGrabacion(false)
        }
    }

    fun enviarDocumento(uri: android.net.Uri)
    {
        if (rutaCercania())
        {
            avisoCercania("Sin internet: por Bluetooth solo viajan fotos ligeras y notas de voz")
            return
        }
        subiendo = true
        progresoSubida = 0f
        alcance.launch {
            val plano = withContext(Dispatchers.IO) {
                envioMedia.prepararDocumento(uri) { avance ->
                    alcance.launch { progresoSubida = avance.toFloat().coerceIn(0f, 1f) }
                }
            }
            subiendo = false
            if (plano != null)
            {
                mandar(plano)
            }
        }
    }

    fun reintentar(mensaje: Mensaje)
    {
        alcance.launch {
            val item = withContext(Dispatchers.IO) { app.outbox.leer(otroId).find { it.optString("localId") == mensaje.id } } ?: return@launch
            mensajes = mensajes.map { if (it.id == mensaje.id) it.copy(estado = "enviando") else it }
            intentarEnviar(item)
        }
    }

    fun reaccionar(mensaje: Mensaje, emoji: String)
    {
        sel = null
        ConexionSocket.obtener()?.emit("mensaje:reaccionar", JSONObject().put("id", mensaje.id).put("emoji", emoji))
        if (mensajes.takeLast(3).any { it.id == mensaje.id })
        {
            alcance.launch {
                delay(80)
                listaEstado.animateScrollToItem(maxOf(0, mensajes.size - 1))
            }
        }
        mensajes = mensajes.map { m ->
            if (m.id != mensaje.id)
            {
                m
            }
            else
            {
                val r = m.reacciones.toMutableMap()
                if (r[miId] == emoji)
                {
                    r.remove(miId)
                }
                else
                {
                    r[miId] = emoji
                }
                m.copy(reacciones = r)
            }
        }
    }

    fun copiar(mensaje: Mensaje)
    {
        sel = null
        val texto = mensaje.texto ?: return
        val efimero = Efimero.leerEfimero(texto)
        portapapeles.setText(AnnotatedString(efimero?.m ?: texto))
    }

    fun borrar(mensaje: Mensaje)
    {
        sel = null
        ConexionSocket.obtener()?.emit("mensaje:borrar", JSONObject().put("id", mensaje.id))
        mensajes = mensajes.map { if (it.id == mensaje.id) it.copy(texto = null, borrado = true) else it }
        if (fijados.any { it.id == mensaje.id })
        {
            fijados = Fijados(app.estado).quitar(otroId, mensaje.id)
        }
    }

    fun borrarLocal(mensaje: Mensaje)
    {
        sel = null
        alcance.launch {
            ocultos = withContext(Dispatchers.IO) { Ocultos(app.estado).ocultar(otroId, mensaje.id) }
        }
    }

    fun fijar(mensaje: Mensaje)
    {
        sel = null
        fijados = Fijados(app.estado).alternar(otroId, Fijados.Fijado(mensaje.id, mensaje.texto, mensaje.remitenteId))
    }

    fun hacerReenvio(destino: Amigo)
    {
        val objetivo = reenviando ?: return
        reenviando = null
        val plano = objetivo.texto ?: return
        alcance.launch {
            val item = crearPendienteDirecto(destino.id, plano)
            DrenadorOutbox.enviar(
                app,
                miId,
                Outbox.Pendiente(Outbox.Tipo.DIRECTO, destino.id, item),
            )
        }
    }

    fun irAFijado()
    {
        if (fijados.isEmpty())
        {
            return
        }
        val actual = fijados[indiceFijado % fijados.size]
        val idx = visibles.indexOfFirst { it.id == actual.id }
        if (idx >= 0)
        {
            alcance.launch { listaEstado.animateScrollToItem(idx) }
        }
        if (fijados.size > 1)
        {
            indiceFijado = (indiceFijado + 1) % fijados.size
        }
    }

    LaunchedEffect(otroId) {
        val inicioCarga = System.currentTimeMillis()
        val datos = withContext(Dispatchers.IO) {
            val mi = app.boveda.leer(ClavesSeguras.MI_ID) ?: ""
            val cache = app.cacheChats.leerChat(otroId)
            val borrador = app.borradores.leer(claveBorrador).optString("texto")
            val aliasGuardado = app.aliasLocal.de(otroId)
            val temp = TemporizadorEfimero(app.estado).leer(otroId)
            val pendientes = app.outbox.leer(otroId)
            val fij = Fijados(app.estado).leer(otroId)
            val ocul = Ocultos(app.estado).leer(otroId)
            Triple(Triple(mi, cache, borrador), Triple(aliasGuardado, temp, pendientes), Pair(fij, ocul))
        }
        miId = datos.first.first
        aliasNombre = datos.second.first
        temporizador = datos.second.second
        fijados = datos.third.first
        ocultos = datos.third.second
        if (datos.first.third.isNotEmpty())
        {
            texto = datos.first.third
        }
        val cache = datos.first.second
        if (cache != null)
        {
            mensajes = (0 until cache.length()).map { mensajeDeJson(cache.getJSONObject(it)) }
            android.util.Log.d(
                "VxPerf",
                "chat cache: ${mensajes.size} mensajes en ${System.currentTimeMillis() - inicioCarga} ms",
            )
        }
        val pendientes = datos.second.third
        if (pendientes.isNotEmpty())
        {
            val existentes = mensajes.flatMap { mensaje ->
                listOfNotNull(mensaje.id, mensaje.clienteId)
            }.toSet()
            mensajes = mensajes + pendientes.filter { !existentes.contains(it.optString("localId")) }.map {
                Mensaje(
                    id = it.getString("localId"),
                    remitenteId = miId,
                    texto = it.optString("texto"),
                    enviadoEn = it.optString("enviado_en"),
                    estado = "enviando",
                    respuestaA = if (it.isNull("respuestaA")) null else it.optString("respuestaA"),
                    respuestaTexto = if (it.isNull("respuestaTexto")) null else it.optString("respuestaTexto"),
                )
            }
            pendientes.forEach { intentarEnviar(it) }
        }
        launch(Dispatchers.IO) {
            runCatching { presencia = app.api.presencia(otroId) as? JSONObject }
        }
        sincronizar()
        if (mensajes.isNotEmpty())
        {
            listaEstado.scrollToItem(maxOf(0, mensajes.size - 1))
        }
    }

    LaunchedEffect(otroId) {
        while (true)
        {
            val ahora = System.currentTimeMillis()
            val vivos = mensajes.filter { m ->
                val limite = Efimero.expiraEn(m.texto ?: "", m.enviadoEn)
                if (limite == null || limite > ahora)
                {
                    true
                }
                else
                {
                    if (m.remitenteId == miId && m.estado == null && !purgados.contains(m.id))
                    {
                        purgados.add(m.id)
                        ConexionSocket.obtener()?.emit("mensaje:borrar", JSONObject().put("id", m.id))
                    }
                    false
                }
            }
            if (vivos.size != mensajes.size)
            {
                mensajes = vivos
                withContext(Dispatchers.IO) { guardarCache(vivos) }
            }
            delay(5000)
        }
    }

    DisposableEffect(otroId) {
        val socket = ConexionSocket.obtener()
        val alRecibir = Emitter.Listener { args ->
            val fila = args.getOrNull(0) as? JSONObject
            if (fila != null && fila.optString("remitente_id") == otroId)
            {
                alcance.launch {
                    val claro = abrir(fila.getString("contenido_cifrado"), fila.getString("nonce"))
                    val nuevo = Mensaje(
                        id = fila.optString("id"),
                        remitenteId = fila.optString("remitente_id"),
                        texto = claro ?: "No se pudo descifrar este mensaje",
                        enviadoEn = fila.optString("enviado_en"),
                        clienteId = if (fila.isNull("cliente_id")) null else fila.optString("cliente_id"),
                        respuestaA = if (fila.isNull("respuesta_a")) null else fila.optString("respuesta_a"),
                    )
                    val optimista = nuevo.clienteId?.let { clienteId ->
                        mensajes.find { it.id == clienteId || it.clienteId == clienteId }
                    }
                    if (optimista != null)
                    {
                        mensajes = mensajes.map { mensaje ->
                            if (mensaje === optimista)
                            {
                                nuevo.copy(respuestaTexto = optimista.respuestaTexto)
                            }
                            else
                            {
                                mensaje
                            }
                        }
                        withContext(Dispatchers.IO) { guardarCache(mensajes) }
                    }
                    else if (mensajes.none { it.id == nuevo.id })
                    {
                        mensajes = mensajes + nuevo
                        marcarLeidos(listOf(nuevo))
                        val info = listaEstado.layoutInfo
                        val lejos = info.visibleItemsInfo.lastOrNull()?.index ?: 0 < mensajes.size - 4
                        if (lejos)
                        {
                            nuevosAbajo += 1
                        }
                        else
                        {
                            listaEstado.animateScrollToItem(maxOf(0, mensajes.size - 1))
                        }
                    }
                }
            }
        }
        val alEscribir = Emitter.Listener { args ->
            val data = args.getOrNull(0) as? JSONObject
            if (data != null && data.optString("de") == otroId)
            {
                alcance.launch {
                    escribiendo = data.optBoolean("activo")
                    apagarEscribiendo[0]?.cancel()
                    if (escribiendo)
                    {
                        apagarEscribiendo[0] = launch {
                            delay(3000)
                            escribiendo = false
                        }
                    }
                }
            }
        }
        val alEstado = Emitter.Listener { args ->
            val data = args.getOrNull(0) as? JSONObject
            if (data != null)
            {
                alcance.launch {
                    mensajes = mensajes.map {
                        if (it.id == data.optString("id"))
                        {
                            it.copy(
                                entregado = it.entregado || !data.isNull("entregado_en"),
                                leido = it.leido || !data.isNull("leido_en"),
                            )
                        }
                        else
                        {
                            it
                        }
                    }
                }
            }
        }
        val alEditado = Emitter.Listener { args ->
            val data = args.getOrNull(0) as? JSONObject
            if (data != null)
            {
                alcance.launch {
                    val claro = abrir(data.getString("contenido_cifrado"), data.getString("nonce"))
                    mensajes = mensajes.map {
                        if (it.id == data.optString("id")) it.copy(texto = claro, editado = true) else it
                    }
                }
            }
        }
        val alBorrado = Emitter.Listener { args ->
            val data = args.getOrNull(0) as? JSONObject
            if (data != null)
            {
                alcance.launch {
                    mensajes = mensajes.map {
                        if (it.id == data.optString("id")) it.copy(texto = null, borrado = true) else it
                    }
                }
            }
        }
        val alReaccion = Emitter.Listener { args ->
            val data = args.getOrNull(0) as? JSONObject
            if (data != null)
            {
                alcance.launch {
                    mensajes = mensajes.map {
                        if (it.id == data.optString("id")) it.copy(reacciones = reaccionesDe(data)) else it
                    }
                }
            }
        }
        val dejarDeObservarOutbox = DrenadorOutbox.observar { resultado ->
            if (
                resultado.cuentaId == miId &&
                resultado.tipo == Outbox.Tipo.DIRECTO &&
                resultado.destinoId == otroId
            )
            {
                alcance.launch {
                    val actualizados = mensajes.map { mensaje ->
                        if (
                            mensaje.id != resultado.clienteId &&
                            mensaje.clienteId != resultado.clienteId
                        )
                        {
                            mensaje
                        }
                        else if (resultado.exitoso)
                        {
                            mensaje.copy(
                                id = resultado.idServidor ?: mensaje.id,
                                clienteId = resultado.clienteId,
                                estado = null,
                            )
                        }
                        else if (mensaje.id == resultado.clienteId)
                        {
                            mensaje.copy(estado = if (resultado.porCercania) "cercania" else "fallido")
                        }
                        else
                        {
                            mensaje
                        }
                    }
                    mensajes = actualizados
                    if (resultado.exitoso)
                    {
                        withContext(Dispatchers.IO) { guardarCache(actualizados) }
                    }
                }
            }
        }
        val alConectar = Emitter.Listener { vaciarOutbox() }
        val mensajeriaBle = GestorCercania.mensajeria(app)
        mensajeriaBle.chatVisible = otroId
        val dejarDeEscucharBle = mensajeriaBle.alEntrante { obj ->
            if (obj.optString("remitente_id") == otroId && !obj.has("grupo_id"))
            {
                alcance.launch {
                    val nuevo = mensajeDeJson(obj)
                    val repetido = mensajes.any {
                        it.id == nuevo.id || (nuevo.clienteId != null && it.clienteId == nuevo.clienteId)
                    }
                    if (!repetido)
                    {
                        mensajes = mensajes + nuevo
                        listaEstado.animateScrollToItem(maxOf(0, mensajes.size - 1))
                    }
                }
            }
        }
        socket?.on("mensaje:recibido", alRecibir)
        socket?.on("usuario:escribiendo", alEscribir)
        socket?.on("mensaje:entregado", alEstado)
        socket?.on("mensaje:leido", alEstado)
        socket?.on("mensaje:editado", alEditado)
        socket?.on("mensaje:borrado", alBorrado)
        socket?.on("mensaje:reaccion", alReaccion)
        socket?.on(Socket.EVENT_CONNECT, alConectar)
        onDispose {
            socket?.off("mensaje:recibido", alRecibir)
            socket?.off("usuario:escribiendo", alEscribir)
            socket?.off("mensaje:entregado", alEstado)
            socket?.off("mensaje:leido", alEstado)
            socket?.off("mensaje:editado", alEditado)
            socket?.off("mensaje:borrado", alBorrado)
            socket?.off("mensaje:reaccion", alReaccion)
            socket?.off(Socket.EVENT_CONNECT, alConectar)
            if (mensajeriaBle.chatVisible == otroId)
            {
                mensajeriaBle.chatVisible = null
            }
            dejarDeEscucharBle()
            dejarDeObservarOutbox()
        }
    }

    LaunchedEffect(grabando) {
        var pulsos = 0
        while (grabando)
        {
            delay(150)
            grabadora[0]?.muestrear()
            if (grabadora[0]?.pausada != true)
            {
                pulsos += 1
                segundosGrabando = pulsos * 150 / 1000
            }
        }
    }

    LaunchedEffect(texto) {
        if (editando == null)
        {
            delay(250)
            withContext(Dispatchers.IO) {
                app.borradores.guardar(claveBorrador, JSONObject().put("texto", texto).put("audio", JSONObject.NULL))
            }
        }
    }

    LaunchedEffect(listaEstado) {
        snapshotFlow { listaEstado.firstVisibleItemIndex }.collect { indice ->
            val ultimo = listaEstado.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            if (ultimo >= mensajes.size - 2)
            {
                nuevosAbajo = 0
            }
            if (indice <= 1 && hayMas && !masCargando && mensajes.isNotEmpty() && !buscando)
            {
                masCargando = true
                runCatching {
                    val primero = mensajes.first().enviadoEn
                    val lote = withContext(Dispatchers.IO) {
                        val filas = app.api.historial(otroId, primero) as JSONArray
                        Pair(filas.length(), descifrarLote(filas))
                    }
                    if (lote.first == 0)
                    {
                        hayMas = false
                    }
                    else
                    {
                        mensajes = lote.second + mensajes
                        if (lote.first < 50)
                        {
                            hayMas = false
                        }
                    }
                }
                masCargando = false
            }
        }
    }

    androidx.activity.compose.BackHandler(
        enabled = sel != null || adjuntando || previos.isNotEmpty() || grabando || buscando || seleccionando || pickerTemp || reenviando != null,
    )
    {
        when
        {
            sel != null -> sel = null
            adjuntando -> adjuntando = false
            previos.isNotEmpty() -> previos = emptyList()
            grabando -> terminarGrabacion(false)
            pickerTemp -> pickerTemp = false
            reenviando != null -> reenviando = null
            seleccionando ->
            {
                seleccionando = false
                seleccionados = emptyList()
            }
            buscando ->
            {
                buscando = false
                consulta = ""
            }
        }
    }

    val nombre = aliasNombre ?: amigo.usuario
    val sub = when
    {
        escribiendo -> "escribiendo…"
        presencia?.optBoolean("en_linea") == true -> "en línea"
        presencia?.isNull("ultima_conexion") == false -> "últ. vez ${Fechas.hora(presencia?.optString("ultima_conexion"))}"
        else -> null
    }
    val fijadoActual = if (fijados.isEmpty()) null else fijados[indiceFijado % fijados.size]

    Box(modifier = Modifier.fillMaxSize().fondoVixxer()) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
            if (buscando)
            {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                )
                {
                    Text(
                        "‹",
                        fontSize = 26.sp,
                        color = colores.texto,
                        modifier = Modifier.pulsable {
                            buscando = false
                            consulta = ""
                        },
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .panelVidrio(radio = 20.dp, desenfocar = true)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                    {
                        if (consulta.isEmpty())
                        {
                            Text("Buscar en el chat", fontSize = 14.sp, color = colores.placeholder)
                        }
                        BasicTextField(
                            value = consulta,
                            onValueChange = { consulta = it },
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 14.sp, color = colores.texto),
                            cursorBrush = SolidColor(colores.texto),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (consulta.trim().isNotEmpty())
                    {
                        Text(
                            if (coincidencias.isEmpty()) "0/0" else "${indiceCoincidencia + 1}/${coincidencias.size}",
                            fontSize = 12.sp,
                            color = colores.muted,
                        )
                        Text(
                            "‹",
                            fontSize = 22.sp,
                            color = if (indiceCoincidencia > 0) colores.texto else colores.placeholder,
                            modifier = Modifier
                                .rotate(90f)
                                .pulsable {
                                    if (indiceCoincidencia > 0) indiceCoincidencia--
                                },
                        )
                        Text(
                            "‹",
                            fontSize = 22.sp,
                            color = if (indiceCoincidencia < coincidencias.lastIndex) colores.texto else colores.placeholder,
                            modifier = Modifier
                                .rotate(-90f)
                                .pulsable {
                                    if (indiceCoincidencia < coincidencias.lastIndex) indiceCoincidencia++
                                },
                        )
                    }
                }
            }
            else
            {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                )
                {
                    Text(
                        "‹",
                        fontSize = 26.sp,
                        color = colores.texto,
                        modifier = Modifier.pulsable { alVolver() },
                    )
                    Avatar(nombre = nombre, uri = amigo.avatarUrl.ifEmpty { null }, tamano = 32.dp)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .pulsable { alNavegar("perfil") },
                    ) {
                        Text(nombre, fontSize = 16.sp, fontFamily = FuenteOutfit, fontWeight = FontWeight.SemiBold, color = colores.texto)
                        if (sub != null)
                        {
                            Text(sub, fontSize = 12.sp, color = colores.muted)
                        }
                    }
                    if (GestorLlamadas.llamadasDisponibles())
                    {
                        Box(modifier = Modifier.pulsable { alNavegar("llamada/$otroId|$nombre|0|0") }) {
                            Telefono(color = colores.texto, tamano = 20.dp)
                        }
                        Box(modifier = Modifier.pulsable { alNavegar("llamada/$otroId|$nombre|1|0") }) {
                            IconoVideo(color = colores.texto, tamano = 20.dp)
                        }
                    }
                    Box(modifier = Modifier.pulsable { buscando = true }) {
                        Lupa(color = colores.texto, tamano = 20.dp)
                    }
                    Box(modifier = Modifier.pulsable { menu = true }) {
                        Kebab(color = if (temporizador > 0) colores.botonFondo else colores.texto)
                    }
                }
            }

            if (fijadoActual != null && !buscando)
            {
                Row(
                    modifier = Modifier
                        .pulsable { irAFijado() }
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .panelVidrio(radio = 12.dp, desenfocar = true)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                )
                {
                    Pin(color = colores.muted, tamano = 16.dp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Mensaje fijado${if (fijados.size > 1) " · ${(indiceFijado % fijados.size) + 1}/${fijados.size}" else ""}",
                            fontSize = 11.sp,
                            fontFamily = FuenteOutfit,
                            fontWeight = FontWeight.Medium,
                            color = colores.muted,
                        )
                        Text(
                            textoVisible(fijadoActual.texto),
                            fontSize = 13.sp,
                            color = colores.texto,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            LazyColumn(
                state = listaEstado,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            )
            {
                itemsIndexed(visibles, key = { _, m -> m.id }) { i, m ->
                    val mio = m.remitenteId == miId
                    val cita = m.respuestaTexto ?: m.respuestaA?.let { respuestaId ->
                        mensajesPorId[respuestaId]?.texto?.let { Resumen.resumenMensaje(it) } ?: "Mensaje"
                    }
                    val resaltado = buscando && coincidencias.getOrNull(indiceCoincidencia) == i
                    var limites by remember { mutableStateOf(Rect.Zero) }
                    Box(
                        modifier = Modifier
                            .animateItem()
                            .then(if (resaltado) Modifier.background(colores.botonFondo.copy(alpha = 0.12f)) else Modifier)
                            .onGloballyPositioned { limites = it.boundsInRoot() },
                    ) {
                        Burbuja(
                            m = if (cita == m.respuestaTexto) m else m.copy(respuestaTexto = cita),
                            mio = mio,
                            colores = colores,
                            app = app,
                            alAbrirImagen = { visor = it },
                            alAbrirVideo = { visorVideo = it },
                            miId = miId,
                            seleccionando = seleccionando,
                            seleccionado = seleccionados.contains(m.id),
                            alReintentar = { reintentar(m) },
                            alPulsar = {
                                if (seleccionando)
                                {
                                    seleccionados = if (seleccionados.contains(m.id)) seleccionados - m.id else seleccionados + m.id
                                }
                            },
                            alMantener = {
                                if (!seleccionando && !m.borrado && (m.estado == null || m.estado == "cercania"))
                                {
                                    vibrador.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                    sel = AccionesDe(m, limites)
                                }
                            },
                        )
                    }
                }
                item {
                    Box(modifier = Modifier.padding(bottom = 2.dp))
                }
            }

            if (seleccionando)
            {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                )
                {
                    Text(
                        "✕",
                        fontSize = 18.sp,
                        color = colores.texto,
                        modifier = Modifier.pulsable {
                            seleccionando = false
                            seleccionados = emptyList()
                        },
                    )
                    Text(
                        "${seleccionados.size}",
                        fontSize = 16.sp,
                        fontFamily = FuenteOutfit,
                        fontWeight = FontWeight.SemiBold,
                        color = colores.texto,
                        modifier = Modifier.weight(1f),
                    )
                    Box(modifier = Modifier.pulsable(habilitado = seleccionados.isNotEmpty()) {
                        alcance.launch {
                            withContext(Dispatchers.IO) {
                                val almacen = Ocultos(app.estado)
                                var set = ocultos
                                for (id in seleccionados)
                                {
                                    set = almacen.ocultar(otroId, id)
                                }
                                ocultos = set
                            }
                            seleccionando = false
                            seleccionados = emptyList()
                        }
                    }) {
                        Bote(color = colores.error, tamano = 22.dp)
                    }
                }
            }
            else if (!buscando)
            {
                Column {
                    val resp = respondiendo
                    if (resp != null)
                    {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .panelVidrio(radio = 12.dp, desenfocar = true)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        )
                        {
                            Text(
                                "Respondiendo: ${Resumen.resumenMensaje(resp.texto)}",
                                fontSize = 13.sp,
                                color = colores.muted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "✕",
                                fontSize = 16.sp,
                                color = colores.muted,
                                modifier = Modifier.pulsable { respondiendo = null },
                            )
                        }
                    }
                    if (editando != null)
                    {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .panelVidrio(radio = 12.dp, desenfocar = true)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        )
                        {
                            Text("Editando mensaje", fontSize = 13.sp, color = colores.muted)
                            Text(
                                "✕",
                                fontSize = 16.sp,
                                color = colores.muted,
                                modifier = Modifier.pulsable {
                                    editando = null
                                    texto = ""
                                },
                            )
                        }
                    }
                    val selectorFoto = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
                        if (uris.isNotEmpty())
                        {
                            alcance.launch {
                                val lista = withContext(Dispatchers.IO) {
                                    uris.mapNotNull { u -> comprimirImagen(contexto, u)?.let { PrevioEnvio(u, it, null, esVideo = false) } }
                                }
                                if (lista.isNotEmpty())
                                {
                                    previos = lista
                                }
                            }
                        }
                    }
                    val selectorDocumento = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                        if (uri != null)
                        {
                            enviarDocumento(uri)
                        }
                    }
                    val selectorVideo = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
                        if (uris.isNotEmpty())
                        {
                            alcance.launch {
                                val lista = withContext(Dispatchers.IO) {
                                    uris.map { u -> PrevioEnvio(u, null, miniaturaVideo(contexto, u).first, esVideo = true) }
                                }
                                previos = lista
                            }
                        }
                    }
                    val permisoMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { dado ->
                        if (dado)
                        {
                            val nueva = Grabadora(contexto)
                            if (nueva.iniciar())
                            {
                                grabadora[0] = nueva
                                segundosGrabando = 0
                                grabacionPausada = false
                                grabando = true
                            }
                        }
                    }
                    selectorFotoRef[0] = { app.saltarBloqueo = true; selectorFoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                    selectorDocumentoRef[0] = { app.saltarBloqueo = true; selectorDocumento.launch("*/*") }
                    selectorVideoRef[0] = { app.saltarBloqueo = true; selectorVideo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) }
                    permisoMicRef[0] = { permisoMic.launch(android.Manifest.permission.RECORD_AUDIO) }
                    val camara = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
                        val uri = fotoCamara[0]
                        if (ok && uri != null)
                        {
                            alcance.launch {
                                val imagen = withContext(Dispatchers.IO) { comprimirImagen(contexto, uri) }
                                if (imagen != null)
                                {
                                    previos = listOf(PrevioEnvio(uri, imagen, null, esVideo = false))
                                }
                            }
                        }
                    }
                    camaraRef[0] = {
                        val dir = java.io.File(contexto.cacheDir, "capturas")
                        dir.mkdirs()
                        val destino = java.io.File(dir, "captura-${System.currentTimeMillis()}.jpg")
                        val uri = androidx.core.content.FileProvider.getUriForFile(contexto, dev.vixxer.mensajero.BuildConfig.APPLICATION_ID + ".archivos", destino)
                        fotoCamara[0] = uri
                        app.saltarBloqueo = true
                        camara.launch(uri)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Bottom,
                    )
                    {
                        Box(
                            modifier = Modifier
                                .pulsable(habilitado = !subiendo) {
                                    enfoque.clearFocus()
                                    adjuntando = true
                                }
                                .size(44.dp),
                            contentAlignment = Alignment.Center,
                        )
                        {
                            if (subiendo)
                            {
                                androidx.compose.material3.CircularProgressIndicator(
                                    progress = { progresoSubida },
                                    modifier = Modifier.size(18.dp),
                                    color = colores.muted,
                                    strokeWidth = 2.dp,
                                )
                            }
                            else
                            {
                                Clip(color = colores.muted)
                            }
                        }
                        CampoMensaje(
                            valor = texto,
                            alCambiar = { escribir(it) },
                            modifier = Modifier.weight(1f),
                        )
                        if (texto.isBlank() && editando == null)
                        {
                            BotonCircularVidrio(
                                descripcion = "Grabar nota de voz",
                                habilitado = !subiendo,
                                alPulsar = { permisoMicRef[0]?.invoke() },
                            )
                            {
                                Microfono(color = colores.texto, tamano = 20.dp)
                            }
                        }
                        else
                        {
                            BotonCircularPrimario(
                                descripcion = "Enviar mensaje",
                                alPulsar = { enviar() },
                            )
                            {
                                Enviar(color = colores.botonTexto, tamano = 19.dp)
                            }
                        }
                    }
                }
            }
        }

        if (nuevosAbajo > 0)
        {
            Text(
                "↓ $nuevosAbajo",
                fontSize = 13.sp,
                fontFamily = FuenteOutfit,
                fontWeight = FontWeight.SemiBold,
                color = colores.botonTexto,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 100.dp)
                    .background(colores.botonFondo, RoundedCornerShape(20.dp))
                    .pulsable {
                        nuevosAbajo = 0
                        alcance.launch { listaEstado.animateScrollToItem(maxOf(0, visibles.size - 1)) }
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }

        AccionesMensaje(
            sel = sel,
            esMio = sel?.mensaje?.remitenteId == miId,
            fijado = sel?.mensaje?.let { m -> fijados.any { it.id == m.id } } ?: false,
            alReaccionar = { m, e -> reaccionar(m, e) },
            alResponder = { m ->
                sel = null
                respondiendo = m
                editando = null
            },
            alReenviar = { m ->
                sel = null
                reenviando = m
            },
            alSeleccionar = { m ->
                sel = null
                seleccionando = true
                seleccionados = listOf(m.id)
            },
            alCopiar = { copiar(it) },
            alEditar = { m ->
                sel = null
                editando = m
                respondiendo = null
                texto = Efimero.leerEfimero(m.texto)?.m ?: (m.texto ?: "")
            },
            alFijar = { fijar(it) },
            alBorrar = { borrar(it) },
            alBorrarLocal = { borrarLocal(it) },
            alCerrar = { sel = null },
        )

        if (adjuntando)
        {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.25f))
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { adjuntando = false },
                contentAlignment = Alignment.BottomStart,
            )
            {
                Row(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(start = 12.dp, bottom = 68.dp)
                        .panelVidrio(radio = 22.dp, fuerte = true, desenfocar = true)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                )
                {
                    for ((etiqueta, icono, accion) in listOf(
                        Triple("Imagen", 0) { adjuntando = false; selectorFotoRef[0]?.invoke() },
                        Triple("Video", 1) { adjuntando = false; selectorVideoRef[0]?.invoke() },
                        Triple("Archivo", 2) { adjuntando = false; selectorDocumentoRef[0]?.invoke() },
                        Triple("Cámara", 3) { adjuntando = false; camaraRef[0]?.invoke() },
                        Triple("Sticker", 4) { adjuntando = false; mostrandoStickers = true },
                    ))
                    {
                        Column(
                            modifier = Modifier.pulsable { accion() },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        )
                        {
                            when (icono)
                            {
                                0 -> IconoImagen(color = colores.texto, tamano = 22.dp)
                                1 -> IconoVideo(color = colores.texto, tamano = 22.dp)
                                2 -> Documento(color = colores.texto, tamano = 22.dp)
                                3 -> IconoCamara(color = colores.texto, tamano = 22.dp)
                                else -> IconoSticker(color = colores.texto, tamano = 22.dp)
                            }
                            Text(etiqueta, fontSize = 11.sp, color = colores.muted)
                        }
                    }
                }
            }
        }

        if (previos.isNotEmpty())
        {
            PrevioMediaMulti(
                items = previos,
                colores = colores,
                onCancelar = { previos = emptyList() },
                onEnviar = { lista ->
                    previos = emptyList()
                    enviarLote(lista)
                },
            )
        }

        SelectorSticker(app = app, visible = mostrandoStickers, alElegir = { enviarSticker(it) }, alCerrar = { mostrandoStickers = false })

        if (grabando)
        {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {},
                contentAlignment = Alignment.BottomCenter,
            )
            {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(12.dp)
                    .panelVidrio(radio = 22.dp, fuerte = true, desenfocar = true)
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            )
            {
                Box(modifier = Modifier.size(10.dp).background(colores.error, CircleShape))
                Text(
                    "%d:%02d".format(segundosGrabando / 60, segundosGrabando % 60),
                    fontSize = 15.sp,
                    fontFamily = FuenteOutfit,
                    fontWeight = FontWeight.Medium,
                    color = colores.texto,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "Cancelar",
                    fontSize = 14.sp,
                    color = colores.muted,
                    modifier = Modifier.pulsable {
                        terminarGrabacion(false)
                    },
                )
                BotonCircularVidrio(
                    descripcion = if (grabacionPausada) "Continuar grabación" else "Pausar grabación",
                    tamano = 34.dp,
                    alPulsar = {
                        val activa = grabadora[0]
                        if (activa != null)
                        {
                            if (activa.pausada) activa.continuar() else activa.pausar()
                            grabacionPausada = activa.pausada
                        }
                    },
                )
                {
                    if (grabacionPausada)
                    {
                        Reproducir(color = colores.texto, tamano = 16.dp)
                    }
                    else
                    {
                        Pausa(color = colores.texto, tamano = 16.dp)
                    }
                }
                BotonCircularPrimario(
                    descripcion = "Enviar nota de voz",
                    tamano = 40.dp,
                    alPulsar = { terminarGrabacion(true) },
                )
                {
                    Enviar(color = colores.botonTexto, tamano = 17.dp)
                }
            }
            }
        }

        VisorVideo(app = app, media = visorVideo, alCerrar = { visorVideo = null })

        VisorImagen(archivo = visor, alCerrar = { visor = null })

        SelectorContacto(
            app = app,
            visible = reenviando != null,
            titulo = "Reenviar a",
            alElegir = { hacerReenvio(it) },
            alCerrar = { reenviando = null },
        )

        if (pickerTemp)
        {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.35f))
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { pickerTemp = false },
                contentAlignment = Alignment.BottomCenter,
            )
            {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .panelVidrio(radio = 20.dp, fuerte = true, desenfocar = true)
                        .navigationBarsPadding()
                        .padding(top = 8.dp, bottom = 28.dp),
                )
                {
                    Text(
                        "MENSAJES TEMPORALES",
                        fontSize = 12.sp,
                        fontFamily = FuenteOutfit,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp,
                        color = colores.muted,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                    for (opcion in Efimero.OPCIONES)
                    {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .pulsable {
                                    pickerTemp = false
                                    if (opcion.valor != temporizador)
                                    {
                                        temporizador = opcion.valor
                                        alcance.launch(Dispatchers.IO) {
                                            TemporizadorEfimero(app.estado).guardar(otroId, opcion.valor)
                                        }
                                        mandar(Efimero.envolverAviso(opcion.valor))
                                    }
                                }
                                .padding(vertical = 14.dp, horizontal = 24.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        )
                        {
                            Text(opcion.etiqueta, fontSize = 16.sp, color = colores.texto)
                            if (temporizador == opcion.valor)
                            {
                                Check(color = colores.botonFondo, tamano = 18.dp)
                            }
                        }
                    }
                }
            }
        }
        if (menu)
        {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { menu = false },
                contentAlignment = Alignment.TopEnd,
            )
            {
                Column(
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(top = 46.dp, end = 10.dp)
                        .width(230.dp)
                        .panelVidrio(radio = 14.dp, desenfocar = true)
                        .padding(vertical = 6.dp),
                )
                {
                    ItemKebab(
                        "Mensajes temporales",
                        { Reloj(it, 18.dp) },
                        if (temporizador > 0) colores.botonFondo else colores.texto,
                        if (temporizador > 0) Efimero.etiquetaDuracion(temporizador) else "Desactivado",
                        colores,
                    ) { menu = false; pickerTemp = true }
                    DivisorKebab(colores)
                    ItemKebab(
                        if (silenciado) "Activar sonido" else "Silenciar",
                        { Silencio(it, 18.dp) },
                        if (silenciado) colores.botonFondo else colores.texto,
                        null,
                        colores,
                    ) {
                        app.estadosChat.alternarSilenciado(otroId)
                        silenciado = !silenciado
                        menu = false
                    }
                    ItemKebab("Ver contacto", { Ojo(true, it, 18.dp) }, colores.texto, null, colores) { menu = false; alNavegar("perfil") }
                    ItemKebab("Vaciar chat", { Bote(it, 18.dp) }, colores.texto, null, colores) { menu = false; confirmarKebab = "vaciar" }
                    ItemKebab("Bloquear", null, colores.error, null, colores) { menu = false; confirmarKebab = "bloquear" }
                }
            }
        }
        Confirmacion(
            visible = confirmarKebab == "vaciar",
            titulo = "Vaciar chat",
            mensaje = "Se borrará el historial de esta conversación en el servidor.",
            textoConfirmar = "Vaciar",
            destructivo = true,
            alConfirmar = {
                confirmarKebab = null
                mensajes = emptyList()
                alcance.launch {
                    withContext(Dispatchers.IO) {
                        runCatching { app.api.limpiarConversacion(otroId) }
                        runCatching { app.cacheChats.guardarChat(otroId, JSONArray()) }
                    }
                }
            },
            alCancelar = { confirmarKebab = null },
        )
        Confirmacion(
            visible = confirmarKebab == "bloquear",
            titulo = "Bloquear",
            mensaje = "No podrá escribirte y se quitará de tus chats. Puedes desbloquearle desde Ajustes.",
            textoConfirmar = "Bloquear",
            destructivo = true,
            alConfirmar = {
                confirmarKebab = null
                alcance.launch {
                    withContext(Dispatchers.IO) { runCatching { app.api.bloquear(otroId) } }
                    alVolver()
                }
            },
            alCancelar = { confirmarKebab = null },
        )
    }
}

private fun registrarWifi(
    app: AplicacionVixxer,
    mensajeId: String,
    etapa: DiagnosticoMesh.Etapa,
    inicio: Long? = null,
    error: DiagnosticoMesh.CodigoError? = null,
)
{
    app.diagnosticoMesh.registrar(
        mensajeId = mensajeId,
        etapa = etapa,
        transporte = DiagnosticoMesh.Transporte.WIFI,
        enlace = "wifi_direct",
        duracionMs = inicio?.let {
            (System.nanoTime() - it).coerceAtLeast(0L) / 1_000_000L
        },
        intento = if (etapa == DiagnosticoMesh.Etapa.INTENTO) 1 else null,
        error = error,
    )
}

@Composable
private fun ItemKebab(
    texto: String,
    icono: (@Composable (Color) -> Unit)?,
    color: Color,
    sub: String?,
    colores: Paleta,
    alPulsar: () -> Unit,
)
{
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pulsable { alPulsar() }
            .padding(horizontal = 16.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    )
    {
        if (icono != null)
        {
            icono(color)
        }
        Column(modifier = Modifier.weight(1f))
        {
            Text(texto, fontSize = 14.sp, fontFamily = FuenteOutfit, fontWeight = FontWeight.Medium, color = color)
            if (sub != null)
            {
                Text(sub, fontSize = 11.sp, color = colores.muted)
            }
        }
    }
}

@Composable
private fun DivisorKebab(colores: Paleta)
{
    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(1.dp).background(colores.borde))
}

@Composable
internal fun Burbuja(
    m: Mensaje,
    mio: Boolean,
    colores: Paleta,
    app: AplicacionVixxer,
    alAbrirImagen: (File) -> Unit,
    alAbrirVideo: (MediaMensaje) -> Unit,
    miId: String,
    seleccionando: Boolean,
    seleccionado: Boolean,
    alReintentar: () -> Unit,
    alPulsar: () -> Unit,
    alMantener: () -> Unit,
)
{
    val aviso = Efimero.leerAviso(m.texto)
    if (aviso != null)
    {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                Efimero.textoAviso(aviso.d),
                fontSize = 12.sp,
                color = colores.muted,
                modifier = Modifier
                    .background(colores.surface, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            )
        }
        return
    }
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val anchoMax = maxWidth * 0.8f
        Column(
            modifier = Modifier
                .align(if (mio) Alignment.CenterEnd else Alignment.CenterStart)
                .background(if (seleccionado) colores.borde else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(12.dp)),
        )
        {
            val mediaBurbuja = remember(m.texto, m.borrado) { if (m.borrado) null else leerMedia(m.texto) }
            val mediaVisual = mediaBurbuja != null && mediaBurbuja.t in listOf("img", "video", "sticker")
            Column(
                modifier = Modifier
                    .widthIn(max = anchoMax)
                    .background(if (mio) colores.botonFondo else colores.surface, RoundedCornerShape(16.dp))
                    .then(if (mio) Modifier else Modifier.border(Vidrio.anchoBorde, colores.borde, RoundedCornerShape(16.dp)))
                    .combinedClickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = { alPulsar() },
                        onLongClick = { alMantener() },
                    )
                    .padding(
                        horizontal = if (mediaVisual && m.respuestaTexto == null) 3.dp else 14.dp,
                        vertical = if (mediaVisual && m.respuestaTexto == null) 3.dp else 9.dp,
                    ),
            )
            {
                if (m.respuestaTexto != null)
                {
                    val tintaCita = if (mio) colores.botonTexto else colores.texto
                    Row(
                        modifier = Modifier
                            .padding(bottom = 6.dp)
                            .height(IntrinsicSize.Min)
                            .background(tintaCita.copy(alpha = 0.07f), RoundedCornerShape(6.dp)),
                    )
                    {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .fillMaxHeight()
                                .background(tintaCita.copy(alpha = 0.55f), RoundedCornerShape(6.dp)),
                        )
                        Text(
                            m.respuestaTexto,
                            fontSize = 13.sp,
                            color = tintaCita.copy(alpha = 0.75f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 5.dp, bottom = 5.dp),
                        )
                    }
                }
                val media = mediaBurbuja
                if (m.borrado)
                {
                    Text(
                        "Este mensaje fue eliminado",
                        fontSize = 15.sp,
                        fontStyle = FontStyle.Italic,
                        color = (if (mio) colores.botonTexto else colores.muted).copy(alpha = 0.8f),
                    )
                }
                else if (media != null && (media.t == "img" || media.t == "sticker"))
                {
                    AdjuntoImagen(app = app, media = media, colores = colores, alAbrir = alAbrirImagen, alMantener = alMantener)
                    if (media.cap != null)
                    {
                        Text(
                            media.cap,
                            fontSize = 14.sp,
                            color = if (mio) colores.botonTexto else colores.texto,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
                else if (media != null && media.t == "file")
                {
                    AdjuntoArchivo(app = app, media = media, mio = mio, colores = colores)
                }
                else if (media != null && media.t == "video")
                {
                    AdjuntoVideo(media = media, colores = colores, alReproducir = { alAbrirVideo(media) }, alMantener = alMantener)
                    if (media.cap != null)
                    {
                        Text(
                            media.cap,
                            fontSize = 14.sp,
                            color = if (mio) colores.botonTexto else colores.texto,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
                else if (media != null && media.t == "audio")
                {
                    AdjuntoAudio(app = app, media = media, mio = mio, colores = colores)
                }
                else if (media != null)
                {
                    Text(
                        Resumen.resumenMensaje(m.texto),
                        fontSize = 15.sp,
                        fontStyle = FontStyle.Italic,
                        color = (if (mio) colores.botonTexto else colores.texto).copy(alpha = 0.85f),
                    )
                }
                else
                {
                    Text(
                        textoVisible(m.texto),
                        fontSize = 15.sp,
                        color = if (mio) colores.botonTexto else colores.texto,
                    )
                    val urlEnlace = dev.vixxer.mensajero.nucleo.Enlaces.extraerUrl(m.texto)
                    if (urlEnlace != null)
                    {
                        TarjetaEnlace(url = urlEnlace, mio = mio, colores = colores)
                    }
                }
                Row(
                    modifier = Modifier.align(Alignment.End).padding(top = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                )
                {
                    if (m.editado)
                    {
                        Text(
                            "editado",
                            fontSize = 10.sp,
                            fontStyle = FontStyle.Italic,
                            color = (if (mio) colores.botonTexto else colores.texto).copy(alpha = 0.7f),
                        )
                    }
                    Text(
                        Fechas.hora(m.enviadoEn),
                        fontSize = 10.sp,
                        color = (if (mio) colores.botonTexto else colores.texto).copy(alpha = 0.7f),
                    )
                    if (mio)
                    {
                        when (m.estado)
                        {
                            "enviando" -> Text("…", fontSize = 10.sp, color = colores.botonTexto.copy(alpha = 0.7f))
                            "fallido" -> Text(
                                "Reintentar",
                                fontSize = 10.sp,
                                fontFamily = FuenteOutfit,
                                fontWeight = FontWeight.Medium,
                                color = colores.error,
                                modifier = Modifier.pulsable { alReintentar() },
                            )
                            else -> Visto(
                                color = if (m.leido) colores.botonTexto else colores.botonTexto.copy(alpha = 0.5f),
                                dos = m.entregado || m.leido,
                                tamano = 13.dp,
                            )
                        }
                    }
                }
            }
            if (m.reacciones.isNotEmpty())
            {
                val grupos = m.reacciones.values.groupingBy { it }.eachCount()
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.align(if (mio) Alignment.End else Alignment.Start).padding(top = 3.dp),
                )
                {
                    for ((emoji, n) in grupos)
                    {
                        Text(
                            if (n > 1) "$emoji $n" else emoji,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .background(colores.surface, RoundedCornerShape(12.dp))
                                .border(Vidrio.anchoBorde, colores.borde, RoundedCornerShape(12.dp))
                                .padding(horizontal = 7.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

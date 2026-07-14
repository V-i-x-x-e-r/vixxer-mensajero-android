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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import java.io.File
import dev.vixxer.mensajero.AplicacionVixxer
import dev.vixxer.mensajero.nucleo.ClavesSeguras
import dev.vixxer.mensajero.nucleo.ConexionSocket
import dev.vixxer.mensajero.nucleo.Cripto
import dev.vixxer.mensajero.nucleo.Efimero
import dev.vixxer.mensajero.nucleo.Fechas
import dev.vixxer.mensajero.nucleo.Fijados
import dev.vixxer.mensajero.nucleo.Medios
import dev.vixxer.mensajero.nucleo.Ocultos
import dev.vixxer.mensajero.nucleo.Resumen
import dev.vixxer.mensajero.nucleo.TemporizadorEfimero
import io.socket.client.Ack
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

@Composable
fun PantallaChat(app: AplicacionVixxer, amigo: Amigo, alVolver: () -> Unit)
{
    val tema = LocalTema.current
    val colores = tema.colores
    val alcance = rememberCoroutineScope()
    val portapapeles = LocalClipboardManager.current
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
    var nuevosAbajo by remember { mutableStateOf(0) }
    var visor by remember { mutableStateOf<File?>(null) }
    var subiendo by remember { mutableStateOf(false) }
    var adjuntando by remember { mutableStateOf(false) }
    var previo by remember { mutableStateOf<Pair<android.net.Uri, ImagenLista>?>(null) }
    var caption by remember { mutableStateOf("") }
    val contexto = LocalContext.current
    val listaEstado = rememberLazyListState()
    val escribiendoJob = remember { arrayOf<Job?>(null) }
    val apagarEscribiendo = remember { arrayOf<Job?>(null) }
    val purgados = remember { HashSet<String>() }
    val selectorFotoRef = remember { arrayOf<(() -> Unit)?>(null) }
    val selectorDocumentoRef = remember { arrayOf<(() -> Unit)?>(null) }
    val claveBorrador = "chat-$otroId"

    val visibles = remember(mensajes, ocultos, buscando, consulta) {
        val sinOcultos = if (ocultos.isEmpty()) mensajes else mensajes.filter { !ocultos.contains(it.id) }
        if (buscando && consulta.trim().isNotEmpty())
        {
            sinOcultos.filter { m ->
                val t = m.texto ?: return@filter false
                !t.startsWith("{") && t.lowercase().contains(consulta.trim().lowercase())
            }
        }
        else
        {
            sinOcultos
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
        val ids = filas.filter { it.remitenteId == otroId && !it.id.startsWith("local-") }.map { it.id }
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
            val priv = app.boveda.leer(ClavesSeguras.CLAVE_PRIVADA) ?: return@withContext null
            val pub = app.llaves.llavePublicaDe(otroId)
            Cripto.descifrarTexto(cifrado, nonce, pub, priv)
                ?: Cripto.descifrarTexto(cifrado, nonce, app.llaves.llavePublicaDe(otroId, forzar = true), priv)
        }
    }

    fun descifrarLote(filas: JSONArray): List<Mensaje>
    {
        val priv = app.boveda.leer(ClavesSeguras.CLAVE_PRIVADA) ?: ""
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
            textos[i] = Cripto.descifrarTexto(f.getString("contenido_cifrado"), f.getString("nonce"), pub, priv)
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
                ?: Cripto.descifrarTexto(f.getString("contenido_cifrado"), f.getString("nonce"), pub, priv)
            salida.add(Mensaje(
                id = f.optString("id"),
                remitenteId = f.optString("remitente_id"),
                texto = if (borrado) null else claro ?: "No se pudo descifrar este mensaje",
                enviadoEn = f.optString("enviado_en"),
                entregado = !f.isNull("entregado_en"),
                leido = !f.isNull("leido_en"),
                editado = f.optBoolean("editado"),
                borrado = borrado,
                reacciones = reaccionesDe(f),
            ))
        }
        return salida
    }

    fun intentarEnviar(item: JSONObject)
    {
        val socket = ConexionSocket.obtener()
        val localId = item.getString("localId")
        if (socket == null || !socket.connected())
        {
            mensajes = mensajes.map { if (it.id == localId) it.copy(estado = "fallido") else it }
            return
        }
        val limite = alcance.launch {
            delay(8000)
            mensajes = mensajes.map { if (it.id == localId) it.copy(estado = "fallido") else it }
        }
        val cuerpo = JSONObject()
            .put("destinatarioId", otroId)
            .put("contenidoCifrado", item.getString("contenidoCifrado"))
            .put("nonce", item.getString("nonce"))
            .put("respuestaA", item.opt("respuestaA") ?: JSONObject.NULL)
            .put("clienteId", localId)
        socket.emit("mensaje:enviar", arrayOf<Any>(cuerpo), Ack { args ->
            limite.cancel()
            val r = args.getOrNull(0) as? JSONObject
            if (r != null && r.optBoolean("ok"))
            {
                mensajes = mensajes.map { if (it.id == localId) it.copy(id = r.optString("id"), estado = "enviado") else it }
                alcance.launch(Dispatchers.IO) { app.outbox.quitar(otroId, localId) }
            }
            else
            {
                mensajes = mensajes.map { if (it.id == localId) it.copy(estado = "fallido") else it }
            }
        })
    }

    fun vaciarOutbox()
    {
        alcance.launch {
            val items = withContext(Dispatchers.IO) { app.outbox.leer(otroId) }
            for (item in items)
            {
                val localId = item.getString("localId")
                mensajes = mensajes.map { if (it.id == localId) it.copy(estado = "enviando") else it }
                intentarEnviar(item)
            }
        }
    }

    suspend fun sincronizar()
    {
        try
        {
            val descifrados = withContext(Dispatchers.IO) {
                val filas = app.api.historial(otroId) as JSONArray
                Pair(filas.length(), descifrarLote(filas))
            }
            val extras = mensajes.filter { m -> m.id.startsWith("local-") && descifrados.second.none { it.id == m.id } }
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

    fun mandar(plano: String)
    {
        val resp = respondiendo
        alcance.launch {
            val item = withContext(Dispatchers.IO) {
                val priv = app.boveda.leer(ClavesSeguras.CLAVE_PRIVADA) ?: return@withContext null
                val pub = app.llaves.llavePublicaDe(otroId)
                val (contenidoCifrado, nonce) = Cripto.cifrarTexto(plano, pub, priv)
                val nuevo = JSONObject()
                    .put("localId", "local-${System.currentTimeMillis()}")
                    .put("contenidoCifrado", contenidoCifrado)
                    .put("nonce", nonce)
                    .put("respuestaA", resp?.id ?: JSONObject.NULL)
                    .put("texto", plano)
                    .put("respuestaTexto", resp?.texto?.let { Resumen.resumenMensaje(it) } ?: JSONObject.NULL)
                    .put("enviado_en", Instant.now().toString())
                app.outbox.agregar(otroId, nuevo)
                nuevo
            } ?: return@launch
            mensajes = mensajes + Mensaje(
                id = item.getString("localId"),
                remitenteId = miId,
                texto = plano,
                enviadoEn = item.getString("enviado_en"),
                estado = "enviando",
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

    fun enviarImagen(imagen: ImagenLista, cap: String?)
    {
        subiendo = true
        alcance.launch {
            val plano = withContext(Dispatchers.IO) {
                val cifrado = Medios.cifrarArchivo(imagen.bytes)
                val respuesta = runCatching { app.api.subirMediaConProgreso(cifrado.datos) as JSONObject }.getOrNull()
                    ?: return@withContext null
                val path = respuesta.getString("path")
                CacheMedia.guardar(contexto, path, imagen.bytes)
                val obj = JSONObject()
                    .put("t", "img")
                    .put("path", path)
                    .put("mime", "image/jpeg")
                    .put("k", cifrado.clave)
                    .put("n", cifrado.nonce)
                    .put("w", imagen.ancho)
                    .put("h", imagen.alto)
                if (!cap.isNullOrBlank())
                {
                    obj.put("cap", cap.trim())
                }
                obj.toString()
            }
            subiendo = false
            if (plano != null)
            {
                mandar(plano)
            }
        }
    }

    fun enviarDocumento(uri: android.net.Uri)
    {
        subiendo = true
        alcance.launch {
            val plano = withContext(Dispatchers.IO) {
                val archivo = leerArchivo(contexto, uri) ?: return@withContext null
                if (archivo.bytes.size > 25 * 1024 * 1024)
                {
                    return@withContext null
                }
                val cifrado = Medios.cifrarArchivo(archivo.bytes)
                val respuesta = runCatching { app.api.subirMediaConProgreso(cifrado.datos) as JSONObject }.getOrNull()
                    ?: return@withContext null
                val path = respuesta.getString("path")
                CacheMedia.guardar(contexto, path, archivo.bytes)
                JSONObject()
                    .put("t", "file")
                    .put("path", path)
                    .put("mime", archivo.mime)
                    .put("k", cifrado.clave)
                    .put("n", cifrado.nonce)
                    .put("nombre", archivo.nombre)
                    .put("peso", archivo.peso)
                    .toString()
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
            withContext(Dispatchers.IO) {
                runCatching {
                    val priv = app.boveda.leer(ClavesSeguras.CLAVE_PRIVADA) ?: return@runCatching
                    val pub = app.llaves.llavePublicaDe(destino.id)
                    val (contenidoCifrado, nonce) = Cripto.cifrarTexto(plano, pub, priv)
                    ConexionSocket.obtener()?.emit("mensaje:enviar", JSONObject()
                        .put("destinatarioId", destino.id)
                        .put("contenidoCifrado", contenidoCifrado)
                        .put("nonce", nonce)
                        .put("respuestaA", JSONObject.NULL))
                }
            }
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
        }
        val pendientes = datos.second.third
        if (pendientes.isNotEmpty())
        {
            val existentes = mensajes.map { it.id }.toSet()
            mensajes = mensajes + pendientes.filter { !existentes.contains(it.optString("localId")) }.map {
                Mensaje(
                    id = it.getString("localId"),
                    remitenteId = miId,
                    texto = it.optString("texto"),
                    enviadoEn = it.optString("enviado_en"),
                    estado = "enviando",
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
                    if (m.remitenteId == miId && !m.id.startsWith("local-") && !purgados.contains(m.id))
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
                    )
                    if (mensajes.none { it.id == nuevo.id })
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
                escribiendo = data.optBoolean("activo")
                apagarEscribiendo[0]?.cancel()
                if (escribiendo)
                {
                    apagarEscribiendo[0] = alcance.launch {
                        delay(3000)
                        escribiendo = false
                    }
                }
            }
        }
        val alEstado = Emitter.Listener { args ->
            val data = args.getOrNull(0) as? JSONObject
            if (data != null)
            {
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
                mensajes = mensajes.map {
                    if (it.id == data.optString("id")) it.copy(texto = null, borrado = true) else it
                }
            }
        }
        val alReaccion = Emitter.Listener { args ->
            val data = args.getOrNull(0) as? JSONObject
            if (data != null)
            {
                mensajes = mensajes.map {
                    if (it.id == data.optString("id")) it.copy(reacciones = reaccionesDe(data)) else it
                }
            }
        }
        val alConectar = Emitter.Listener { vaciarOutbox() }
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

    val nombre = aliasNombre ?: amigo.usuario
    val sub = when
    {
        escribiendo -> "escribiendo…"
        presencia?.optBoolean("en_linea") == true -> "en línea"
        presencia?.isNull("ultima_conexion") == false -> "últ. vez ${Fechas.hora(presencia?.optString("ultima_conexion"))}"
        else -> null
    }
    val fijadoActual = if (fijados.isEmpty()) null else fijados[indiceFijado % fijados.size]

    Box(modifier = Modifier.fillMaxSize().background(colores.fondo)) {
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
                        modifier = Modifier.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                            buscando = false
                            consulta = ""
                        },
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(colores.surface, RoundedCornerShape(20.dp))
                            .border(Vidrio.anchoBorde, colores.borde, RoundedCornerShape(20.dp))
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
                        modifier = Modifier.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { alVolver() },
                    )
                    Avatar(nombre = nombre, uri = amigo.avatarUrl.ifEmpty { null }, tamano = 32.dp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(nombre, fontSize = 16.sp, fontFamily = FuenteOutfit, fontWeight = FontWeight.SemiBold, color = colores.texto)
                        if (sub != null)
                        {
                            Text(sub, fontSize = 12.sp, color = colores.muted)
                        }
                    }
                    Box(modifier = Modifier.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { buscando = true }) {
                        Lupa(color = colores.texto, tamano = 20.dp)
                    }
                    Box(modifier = Modifier.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { pickerTemp = true }) {
                        Kebab(color = if (temporizador > 0) colores.botonFondo else colores.texto)
                    }
                }
            }

            if (fijadoActual != null && !buscando)
            {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .panelVidrio(radio = 12.dp)
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { irAFijado() }
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
                itemsIndexed(visibles, key = { _, m -> m.id }) { _, m ->
                    val mio = m.remitenteId == miId
                    var limites by remember { mutableStateOf(Rect.Zero) }
                    Box(modifier = Modifier.onGloballyPositioned { limites = it.boundsInRoot() }) {
                        Burbuja(
                            m = m,
                            mio = mio,
                            colores = colores,
                            app = app,
                            alAbrirImagen = { visor = it },
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
                                if (!seleccionando && !m.borrado)
                                {
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
                        modifier = Modifier.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
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
                    Box(modifier = Modifier.clickable(enabled = seleccionados.isNotEmpty(), indication = null, interactionSource = remember { MutableInteractionSource() }) {
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
                                .panelVidrio(radio = 12.dp)
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
                                modifier = Modifier.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { respondiendo = null },
                            )
                        }
                    }
                    if (editando != null)
                    {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .panelVidrio(radio = 12.dp)
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
                                modifier = Modifier.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                                    editando = null
                                    texto = ""
                                },
                            )
                        }
                    }
                    val selectorFoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                        if (uri != null)
                        {
                            alcance.launch {
                                val imagen = withContext(Dispatchers.IO) { comprimirImagen(contexto, uri) }
                                if (imagen != null)
                                {
                                    caption = ""
                                    previo = Pair(uri, imagen)
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
                    selectorFotoRef[0] = { selectorFoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                    selectorDocumentoRef[0] = { selectorDocumento.launch("*/*") }
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
                                .size(44.dp)
                                .clickable(enabled = !subiendo, indication = null, interactionSource = remember { MutableInteractionSource() }) { adjuntando = true },
                            contentAlignment = Alignment.Center,
                        )
                        {
                            if (subiendo)
                            {
                                androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(18.dp), color = colores.muted, strokeWidth = 2.dp)
                            }
                            else
                            {
                                Clip(color = colores.muted)
                            }
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(colores.surface, RoundedCornerShape(22.dp))
                                .border(Vidrio.anchoBorde, colores.borde, RoundedCornerShape(22.dp))
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                        {
                            if (texto.isEmpty())
                            {
                                Text("Mensaje", fontSize = 15.sp, color = colores.placeholder)
                            }
                            BasicTextField(
                                value = texto,
                                onValueChange = { escribir(it) },
                                textStyle = TextStyle(fontSize = 15.sp, color = colores.texto),
                                cursorBrush = SolidColor(colores.texto),
                                maxLines = 5,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(colores.botonFondo, CircleShape)
                                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { enviar() },
                            contentAlignment = Alignment.Center,
                        )
                        {
                            Text("➤", fontSize = 18.sp, color = colores.botonTexto)
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
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
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
                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.35f))
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { adjuntando = false },
                contentAlignment = Alignment.BottomCenter,
            )
            {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .panelVidrio(radio = 20.dp, fuerte = true)
                        .navigationBarsPadding()
                        .padding(top = 8.dp, bottom = 28.dp),
                )
                {
                    Text(
                        "ADJUNTAR",
                        fontSize = 12.sp,
                        fontFamily = FuenteOutfit,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp,
                        color = colores.muted,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                    Text(
                        "Foto",
                        fontSize = 16.sp,
                        color = colores.texto,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                                adjuntando = false
                                selectorFotoRef[0]?.invoke()
                            }
                            .padding(vertical = 14.dp, horizontal = 24.dp),
                    )
                    Text(
                        "Documento",
                        fontSize = 16.sp,
                        color = colores.texto,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                                adjuntando = false
                                selectorDocumentoRef[0]?.invoke()
                            }
                            .padding(vertical = 14.dp, horizontal = 24.dp),
                    )
                }
            }
        }

        val previoActual = previo
        if (previoActual != null)
        {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.94f))
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding(),
            )
            {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        "✕",
                        fontSize = 22.sp,
                        color = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { previo = null },
                    )
                }
                coil.compose.AsyncImage(
                    model = previoActual.first,
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom,
                )
                {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(colores.surface, RoundedCornerShape(22.dp))
                            .border(Vidrio.anchoBorde, colores.borde, RoundedCornerShape(22.dp))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                    {
                        if (caption.isEmpty())
                        {
                            Text("Añade un comentario…", fontSize = 15.sp, color = colores.placeholder)
                        }
                        BasicTextField(
                            value = caption,
                            onValueChange = { caption = it },
                            textStyle = TextStyle(fontSize = 15.sp, color = colores.texto),
                            cursorBrush = SolidColor(colores.texto),
                            maxLines = 3,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(colores.botonFondo, CircleShape)
                            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                                val listo = previoActual.second
                                previo = null
                                enviarImagen(listo, caption)
                            },
                        contentAlignment = Alignment.Center,
                    )
                    {
                        Text("➤", fontSize = 18.sp, color = colores.botonTexto)
                    }
                }
            }
        }

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
                        .panelVidrio(radio = 20.dp, fuerte = true)
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
                                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
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
    }
}

@Composable
private fun Burbuja(
    m: Mensaje,
    mio: Boolean,
    colores: Paleta,
    app: AplicacionVixxer,
    alAbrirImagen: (File) -> Unit,
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
                    .padding(horizontal = 14.dp, vertical = 9.dp),
            )
            {
                if (m.respuestaTexto != null)
                {
                    Text(
                        m.respuestaTexto,
                        fontSize = 13.sp,
                        color = if (mio) colores.botonTexto.copy(alpha = 0.8f) else colores.muted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 8.dp, bottom = 6.dp),
                    )
                }
                val media = if (m.borrado) null else leerMedia(m.texto)
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
                    AdjuntoImagen(app = app, media = media, colores = colores, alAbrir = alAbrirImagen)
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
                                modifier = Modifier.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { alReintentar() },
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

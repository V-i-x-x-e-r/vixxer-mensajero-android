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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vixxer.mensajero.AplicacionVixxer
import dev.vixxer.mensajero.DrenadorOutbox
import dev.vixxer.mensajero.nucleo.ClavesSeguras
import dev.vixxer.mensajero.nucleo.ConexionSocket
import dev.vixxer.mensajero.nucleo.Cripto
import dev.vixxer.mensajero.nucleo.ErrorApi
import dev.vixxer.mensajero.nucleo.Fechas
import dev.vixxer.mensajero.nucleo.Fijados
import dev.vixxer.mensajero.nucleo.GrupoVisto
import dev.vixxer.mensajero.nucleo.IdMensaje
import dev.vixxer.mensajero.nucleo.Ocultos
import dev.vixxer.mensajero.nucleo.Outbox
import dev.vixxer.mensajero.nucleo.Resumen
import io.socket.client.Socket
import io.socket.emitter.Emitter
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class MensajeGrupo(
    val id: String,
    val remitenteId: String,
    val autor: String?,
    val texto: String?,
    val enviadoEn: String,
    val borrado: Boolean = false,
    val editado: Boolean = false,
    val estado: String? = null,
    val clienteId: String? = null,
    val reacciones: Map<String, String> = emptyMap(),
    val respuestaA: String? = null,
    val respuestaTexto: String? = null,
    val leidoPor: Map<String, String> = emptyMap(),
)

data class MiembroGrupo(val id: String, val usuario: String, val avatarUrl: String?)

private data class DatosGrupo(
    val nombre: String,
    val miembros: List<MiembroGrupo>,
    val publicas: Map<String, String>,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PantallaChatGrupo(app: AplicacionVixxer, grupoId: String, nombreInicial: String, alNavegar: (String) -> Unit)
{
    val colores = LocalTema.current.colores
    val alcance = rememberCoroutineScope()
    var mensajes by remember { mutableStateOf(listOf<MensajeGrupo>()) }
    var texto by remember { mutableStateOf("") }
    var nombreGrupo by remember { mutableStateOf(nombreInicial) }
    var numMiembros by remember { mutableStateOf(0) }
    var miembros by remember { mutableStateOf(listOf<MiembroGrupo>()) }
    var infoDe by remember { mutableStateOf<MensajeGrupo?>(null) }
    var escribiendoDe by remember { mutableStateOf<String?>(null) }
    var hayMas by remember { mutableStateOf(true) }
    var masCargando by remember { mutableStateOf(false) }
    var miId by remember { mutableStateOf("") }
    var sel by remember { mutableStateOf<AccionesDe<MensajeGrupo>?>(null) }
    var respondiendo by remember { mutableStateOf<MensajeGrupo?>(null) }
    var editando by remember { mutableStateOf<MensajeGrupo?>(null) }
    var reenviando by remember { mutableStateOf<MensajeGrupo?>(null) }
    var fijados by remember { mutableStateOf(listOf<Fijados.Fijado>()) }
    var indiceFijado by remember { mutableStateOf(0) }
    var ocultos by remember { mutableStateOf(setOf<String>()) }
    val portapapeles = LocalClipboardManager.current
    val contexto = androidx.compose.ui.platform.LocalContext.current
    val cicloVida = LocalLifecycleOwner.current
    val enfoque = androidx.compose.ui.platform.LocalFocusManager.current
    val envioMedia = remember { EnvioMedia(app, contexto) }
    var subiendo by remember { mutableStateOf(false) }
    var progresoSubida by remember { mutableStateOf(0f) }
    var adjuntando by remember { mutableStateOf(false) }
    var previos by remember { mutableStateOf(listOf<PrevioEnvio>()) }
    var mostrandoStickers by remember { mutableStateOf(false) }
    var visor by remember { mutableStateOf<java.io.File?>(null) }
    var visorVideo by remember { mutableStateOf<MediaMensaje?>(null) }
    var grabando by remember { mutableStateOf(false) }
    var segundosGrabando by remember { mutableStateOf(0) }
    var grabacionPausada by remember { mutableStateOf(false) }
    val grabadora = remember { arrayOf<Grabadora?>(null) }
    val selectorFotoRef = remember { arrayOf<(() -> Unit)?>(null) }
    val selectorVideoRef = remember { arrayOf<(() -> Unit)?>(null) }
    val selectorDocumentoRef = remember { arrayOf<(() -> Unit)?>(null) }
    val permisoMicRef = remember { arrayOf<(() -> Unit)?>(null) }
    val camaraRef = remember { arrayOf<(() -> Unit)?>(null) }
    val fotoCamara = remember { arrayOf<android.net.Uri?>(null) }
    val vibrador = androidx.compose.ui.platform.LocalHapticFeedback.current
    val pubs = remember { ConcurrentHashMap<String, String>() }
    val nombres = remember { ConcurrentHashMap<String, String>() }
    val marcados = remember { HashSet<String>() }
    val listaEstado = rememberLazyListState()
    val tecladoVisible = WindowInsets.isImeVisible
    LaunchedEffect(tecladoVisible) {
        if (tecladoVisible && mensajes.isNotEmpty())
        {
            listaEstado.animateScrollToItem(mensajes.size)
            delay(260)
            listaEstado.animateScrollToItem(mensajes.size)
        }
    }
    val tecleandoJob = remember { arrayOf<Job?>(null) }
    val apagarEscribiendo = remember { arrayOf<Job?>(null) }
    val claveBorrador = "grupo-$grupoId"
    val claveCache = "g-$grupoId"

    fun guardarCache(lista: List<MensajeGrupo>)
    {
        val arreglo = JSONArray()
        for (m in lista)
        {
            val obj = JSONObject()
            obj.put("id", m.id)
            obj.put("remitente_id", m.remitenteId)
            obj.put("autor", m.autor ?: JSONObject.NULL)
            obj.put("texto", m.texto ?: JSONObject.NULL)
            obj.put("enviado_en", m.enviadoEn)
            obj.put("borrado", m.borrado)
            obj.put("editado", m.editado)
            obj.put("estado", m.estado ?: JSONObject.NULL)
            obj.put("cliente_id", m.clienteId ?: JSONObject.NULL)
            obj.put("respuesta_a", m.respuestaA ?: JSONObject.NULL)
            obj.put("respuestaTexto", m.respuestaTexto ?: JSONObject.NULL)
            arreglo.put(obj)
        }
        app.cacheChats.guardarChat(claveCache, arreglo)
    }

    fun descifrarFila(f: JSONObject): MensajeGrupo
    {
        val remitente = f.optString("remitente_id")
        val pub = pubs[remitente]
        val borrado = f.optBoolean("borrado")
        val reacciones = f.optJSONObject("reacciones")?.let { obj ->
            obj.keys().asSequence().associateWith { obj.optString(it) }
        } ?: emptyMap()
        val leidoPor = f.optJSONObject("leido_por")?.let { obj ->
            obj.keys().asSequence().associateWith { obj.optString(it) }
        } ?: emptyMap()
        return MensajeGrupo(
            id = f.optString("id"),
            remitenteId = remitente,
            autor = nombres[remitente],
            texto = if (borrado || pub == null) null
                else app.identidad.descifrarConHistoricas(
                    f.getString("contenido_cifrado"),
                    f.getString("nonce"),
                    pub,
                ) ?: "No se pudo descifrar",
            enviadoEn = f.optString("enviado_en"),
            borrado = borrado,
            editado = f.optBoolean("editado"),
            clienteId = if (f.isNull("cliente_id")) null else f.optString("cliente_id"),
            reacciones = reacciones,
            respuestaA = if (f.isNull("respuesta_a")) null else f.optString("respuesta_a"),
            leidoPor = leidoPor,
        )
    }

    fun reportarLeidos(lista: List<MensajeGrupo>)
    {
        val ids = lista
            .filter { it.remitenteId != miId && it.estado == null && !marcados.contains(it.id) }
            .map { it.id }
        if (ids.isEmpty())
        {
            return
        }
        ids.forEach { marcados.add(it) }
        alcance.launch(Dispatchers.IO) {
            runCatching { app.api.marcarLeidosGrupo(grupoId, ids.take(200)) }
        }
    }

    fun cifrarParaTodos(plano: String, priv: String): JSONArray
    {
        val copias = JSONArray()
        for ((idMiembro, llave) in pubs)
        {
            val (cifrado, nonce) = Cripto.cifrarTexto(plano, llave, priv)
            copias.put(JSONObject()
                .put("destinatario_id", idMiembro)
                .put("contenido_cifrado", cifrado)
                .put("nonce", nonce))
        }
        return copias
    }

    suspend fun refrescarGrupo(): Boolean
    {
        val datos = withContext(Dispatchers.IO) {
            runCatching {
                val grupo = app.api.infoGrupo(grupoId) as JSONObject
                val lista = grupo.optJSONArray("miembros") ?: JSONArray()
                val miembrosNuevos = ArrayList<MiembroGrupo>()
                val publicas = LinkedHashMap<String, String>()
                for (i in 0 until lista.length())
                {
                    val miembro = lista.getJSONObject(i)
                    val id = miembro.getString("id")
                    publicas[id] = miembro.textoO("llave_publica")
                    miembrosNuevos.add(MiembroGrupo(
                        id,
                        miembro.optString("usuario"),
                        miembro.textoO("avatar_url").ifEmpty { null },
                    ))
                }
                DatosGrupo(
                    nombre = grupo.optString("nombre").ifEmpty { nombreInicial },
                    miembros = miembrosNuevos,
                    publicas = publicas,
                )
            }.getOrNull()
        } ?: return false
        nombreGrupo = datos.nombre
        miembros = datos.miembros
        numMiembros = datos.miembros.size
        pubs.clear()
        pubs.putAll(datos.publicas)
        nombres.clear()
        datos.miembros.forEach { nombres[it.id] = it.usuario }
        return true
    }

    fun mandarPlano(plano: String, respuestaA: String? = null, respuestaTexto: String? = null)
    {
        alcance.launch {
            val item = withContext(Dispatchers.IO) {
                val nuevo = JSONObject()
                    .put("localId", IdMensaje.nuevo())
                    .put("texto", plano)
                    .put("respuestaA", respuestaA ?: JSONObject.NULL)
                    .put("respuestaTexto", respuestaTexto ?: JSONObject.NULL)
                    .put("enviado_en", Instant.now().toString())
                app.outbox.agregarGrupo(grupoId, nuevo)
                nuevo
            }
            val clienteId = item.getString("localId")
            mensajes = mensajes + MensajeGrupo(
                id = clienteId,
                remitenteId = miId,
                autor = null,
                texto = plano,
                enviadoEn = item.getString("enviado_en"),
                estado = "enviando",
                respuestaA = respuestaA,
                respuestaTexto = respuestaTexto,
            )
            listaEstado.animateScrollToItem(maxOf(0, mensajes.size - 1))
            DrenadorOutbox.enviar(
                app,
                miId,
                Outbox.Pendiente(Outbox.Tipo.GRUPO, grupoId, item),
            )
        }
    }

    fun enviarStickerGrupo(archivo: java.io.File)
    {
        mostrandoStickers = false
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
                mandarPlano(plano)
            }
        }
    }

    fun enviarLoteGrupo(lista: List<Pair<PrevioEnvio, String?>>)
    {
        if (lista.isEmpty())
        {
            return
        }
        subiendo = true
        progresoSubida = 0f
        alcance.launch {
            for ((indice, par) in lista.withIndex())
            {
                val (item, cap) = par
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
                    mandarPlano(plano)
                }
            }
            subiendo = false
        }
    }

    fun enviarDocumentoGrupo(uri: android.net.Uri)
    {
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
                mandarPlano(plano)
            }
        }
    }

    fun enviarAudioGrupo(archivo: java.io.File, dur: Int, ondas: List<Float>)
    {
        subiendo = true
        progresoSubida = 0f
        alcance.launch {
            val plano = withContext(Dispatchers.IO) {
                envioMedia.prepararAudio(archivo, dur, ondas) { avance ->
                    alcance.launch { progresoSubida = avance.toFloat().coerceIn(0f, 1f) }
                }
            }
            subiendo = false
            runCatching { archivo.delete() }
            if (plano != null)
            {
                mandarPlano(plano)
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
            enviarAudioGrupo(archivo, segundosGrabando, activa.ondas())
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

    suspend fun editarMensaje(objetivo: MensajeGrupo, plano: String): Boolean
    {
        for (intento in 0..1)
        {
            if (!refrescarGrupo())
            {
                return false
            }
            val totalMiembros = miembros.size
            try
            {
                withContext(Dispatchers.IO) {
                    val privada = app.boveda.leer(ClavesSeguras.CLAVE_PRIVADA)
                        ?: error("No hay llave privada")
                    val cifrados = cifrarParaTodos(plano, privada)
                    require(cifrados.length() == totalMiembros && cifrados.length() > 0)
                    app.api.editarMensajeGrupo(grupoId, objetivo.id, cifrados)
                }
                return true
            }
            catch (error: ErrorApi)
            {
                if (error.status != 409 || intento == 1)
                {
                    return false
                }
            }
            catch (_: Exception)
            {
                return false
            }
        }
        return false
    }

    fun enviar()
    {
        val limpio = texto.trim()
        if (limpio.isEmpty())
        {
            return
        }
        tecleandoJob[0]?.cancel()
        ConexionSocket.obtener()?.emit("grupo:escribiendo", JSONObject().put("grupo", grupoId).put("activo", false))
        val objetivo = editando
        if (objetivo != null)
        {
            alcance.launch {
                if (!editarMensaje(objetivo, limpio))
                {
                    return@launch
                }
                mensajes = mensajes.map { if (it.id == objetivo.id) it.copy(texto = limpio, editado = true) else it }
                editando = null
                texto = ""
            }
            return
        }
        texto = ""
        val resp = respondiendo
        respondiendo = null
        mandarPlano(limpio, resp?.id, resp?.texto?.let { Resumen.resumenMensaje(it) })
    }

    fun reintentar(mensaje: MensajeGrupo)
    {
        alcance.launch {
            val item = withContext(Dispatchers.IO) {
                app.outbox.leerGrupo(grupoId).find { it.optString("localId") == mensaje.id }
            } ?: return@launch
            mensajes = mensajes.map { if (it.id == mensaje.id) it.copy(estado = "enviando") else it }
            DrenadorOutbox.enviar(
                app,
                miId,
                Outbox.Pendiente(Outbox.Tipo.GRUPO, grupoId, item),
            )
        }
    }

    fun reaccionar(mensaje: MensajeGrupo, emoji: String)
    {
        sel = null
        alcance.launch(Dispatchers.IO) {
            runCatching { app.api.reaccionarGrupo(grupoId, mensaje.id, emoji) }
        }
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

    fun borrar(mensaje: MensajeGrupo)
    {
        sel = null
        alcance.launch(Dispatchers.IO) {
            runCatching { app.api.borrarMensajeGrupo(grupoId, mensaje.id) }
        }
        mensajes = mensajes.map { if (it.id == mensaje.id) it.copy(texto = null, borrado = true) else it }
        if (fijados.any { it.id == mensaje.id })
        {
            fijados = Fijados(app.estado).quitar(claveCache, mensaje.id)
        }
    }

    fun copiar(mensaje: MensajeGrupo)
    {
        sel = null
        mensaje.texto?.let { portapapeles.setText(AnnotatedString(it)) }
    }

    fun borrarLocal(mensaje: MensajeGrupo)
    {
        sel = null
        alcance.launch {
            ocultos = withContext(Dispatchers.IO) { Ocultos(app.estado).ocultar(claveCache, mensaje.id) }
        }
    }

    fun fijar(mensaje: MensajeGrupo)
    {
        sel = null
        fijados = Fijados(app.estado).alternar(claveCache, Fijados.Fijado(mensaje.id, mensaje.texto, mensaje.remitenteId))
    }

    fun hacerReenvio(destino: Amigo)
    {
        val objetivo = reenviando ?: return
        reenviando = null
        val plano = objetivo.texto ?: return
        alcance.launch {
            val item = withContext(Dispatchers.IO) {
                val nuevo = JSONObject()
                    .put("localId", IdMensaje.nuevo())
                    .put("respuestaA", JSONObject.NULL)
                    .put("texto", plano)
                    .put("respuestaTexto", JSONObject.NULL)
                    .put("enviado_en", Instant.now().toString())
                app.outbox.agregar(destino.id, nuevo)
                nuevo
            }
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
        val idx = mensajes.indexOfFirst { it.id == actual.id }
        if (idx >= 0)
        {
            alcance.launch { listaEstado.animateScrollToItem(idx) }
        }
        if (fijados.size > 1)
        {
            indiceFijado = (indiceFijado + 1) % fijados.size
        }
    }

    fun escribir(t: String)
    {
        texto = t
        val socket = ConexionSocket.obtener() ?: return
        if (socket.connected())
        {
            socket.emit("grupo:escribiendo", JSONObject().put("grupo", grupoId).put("activo", true))
            tecleandoJob[0]?.cancel()
            tecleandoJob[0] = alcance.launch {
                delay(2200)
                socket.emit("grupo:escribiendo", JSONObject().put("grupo", grupoId).put("activo", false))
            }
        }
    }

    LaunchedEffect(grupoId) {
        val datos = withContext(Dispatchers.IO) {
            val mi = app.boveda.leer(ClavesSeguras.MI_ID) ?: ""
            val cache = app.cacheChats.leerChat(claveCache)
            val borrador = app.borradores.leer(claveBorrador).optString("texto")
            val pendientes = app.outbox.leerGrupo(grupoId)
            Triple(mi, cache, Pair(borrador, pendientes))
        }
        miId = datos.first
        if (datos.third.first.isNotEmpty())
        {
            texto = datos.third.first
        }
        val persistentes = withContext(Dispatchers.IO) {
            Triple(
                Fijados(app.estado).leer(claveCache),
                Ocultos(app.estado).leer(claveCache),
                datos.third.second,
            )
        }
        fijados = persistentes.first
        ocultos = persistentes.second
        val locales = persistentes.third
        val cache = datos.second
        if (cache != null)
        {
            mensajes = (0 until cache.length()).map { i ->
                val m = cache.getJSONObject(i)
                MensajeGrupo(
                    id = m.optString("id"),
                    remitenteId = m.optString("remitente_id"),
                    autor = if (m.isNull("autor")) null else m.optString("autor"),
                    texto = if (m.isNull("texto")) null else m.optString("texto"),
                    enviadoEn = m.optString("enviado_en"),
                    borrado = m.optBoolean("borrado"),
                    editado = m.optBoolean("editado"),
                    estado = if (m.isNull("estado")) null else m.optString("estado"),
                    clienteId = if (m.isNull("cliente_id")) null else m.optString("cliente_id"),
                    respuestaA = if (m.isNull("respuesta_a")) null else m.optString("respuesta_a"),
                    respuestaTexto = if (m.isNull("respuestaTexto")) null else m.optString("respuestaTexto"),
                )
            }
        }
        if (locales.isNotEmpty())
        {
            val existentes = mensajes.flatMap { mensaje ->
                listOfNotNull(mensaje.id, mensaje.clienteId)
            }.toSet()
            mensajes = mensajes + locales.filter { it.optString("localId") !in existentes }.map { item ->
                MensajeGrupo(
                    id = item.getString("localId"),
                    remitenteId = miId,
                    autor = null,
                    texto = item.optString("texto"),
                    enviadoEn = item.optString("enviado_en"),
                    estado = "enviando",
                    respuestaA = if (item.isNull("respuestaA")) null else item.optString("respuestaA"),
                    respuestaTexto = if (item.isNull("respuestaTexto")) null else item.optString("respuestaTexto"),
                )
            }
        }
        if (refrescarGrupo())
        {
            val historial = withContext(Dispatchers.IO) {
                runCatching {
                    val filas = app.api.historialGrupo(grupoId) as JSONArray
                    Pair(filas.length(), (0 until filas.length()).map { descifrarFila(filas.getJSONObject(it)) })
                }.getOrNull()
            }
            if (historial != null)
            {
                hayMas = historial.first >= 50
                val descifrados = historial.second
                if (descifrados.isNotEmpty() || cache == null)
                {
                    val pendientes = mensajes.filter { mensaje ->
                        mensaje.estado != null && descifrados.none { servidor ->
                            servidor.id == mensaje.id || servidor.clienteId == mensaje.id
                        }
                    }
                    mensajes = (descifrados + pendientes).sortedBy { it.enviadoEn }
                    withContext(Dispatchers.IO) { guardarCache(mensajes) }
                }
            }
        }
        withContext(Dispatchers.IO) { GrupoVisto(app.estado).marcarVisto(grupoId) }
        reportarLeidos(mensajes)
        if (miId.isNotBlank())
        {
            DrenadorOutbox.drenar(app, miId, Outbox.Tipo.GRUPO, grupoId, forzar = true)
        }
        if (mensajes.isNotEmpty())
        {
            listaEstado.scrollToItem(maxOf(0, mensajes.size - 1))
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

    androidx.activity.compose.BackHandler(
        enabled = sel != null || adjuntando || previos.isNotEmpty() || grabando || reenviando != null,
    )
    {
        when
        {
            sel != null -> sel = null
            adjuntando -> adjuntando = false
            previos.isNotEmpty() -> previos = emptyList()
            grabando -> terminarGrabacion(false)
            reenviando != null -> reenviando = null
        }
    }

    LaunchedEffect(texto) {
        delay(250)
        withContext(Dispatchers.IO) {
            app.borradores.guardar(claveBorrador, JSONObject().put("texto", texto).put("audio", JSONObject.NULL))
        }
    }

    LaunchedEffect(listaEstado) {
        snapshotFlow { listaEstado.firstVisibleItemIndex }.collect { indice ->
            if (indice <= 1 && hayMas && !masCargando && mensajes.isNotEmpty())
            {
                masCargando = true
                runCatching {
                    val primero = mensajes.first().enviadoEn
                    val lote = withContext(Dispatchers.IO) {
                        val filas = app.api.historialGrupo(grupoId, primero) as JSONArray
                        Pair(filas.length(), (0 until filas.length()).map { descifrarFila(filas.getJSONObject(it)) })
                    }
                    if (lote.first < 50)
                    {
                        hayMas = false
                    }
                    if (lote.first > 0)
                    {
                        mensajes = lote.second + mensajes
                    }
                }
                masCargando = false
            }
        }
    }

    DisposableEffect(grupoId) {
        val socket = ConexionSocket.obtener()
        val alMensaje = Emitter.Listener { args ->
            val data = args.getOrNull(0) as? JSONObject ?: return@Listener
            if (data.optString("grupo_id") != grupoId)
            {
                return@Listener
            }
            alcance.launch {
                if (!pubs.containsKey(data.optString("remitente_id")))
                {
                    refrescarGrupo()
                }
                val nuevo = withContext(Dispatchers.IO) {
                    descifrarFila(data)
                }
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
                    withContext(Dispatchers.IO) {
                        guardarCache(mensajes)
                        GrupoVisto(app.estado).marcarVisto(grupoId)
                    }
                    reportarLeidos(listOf(nuevo))
                    listaEstado.animateScrollToItem(maxOf(0, mensajes.size - 1))
                }
            }
        }
        val alEscribiendo = Emitter.Listener { args ->
            val data = args.getOrNull(0) as? JSONObject ?: return@Listener
            if (data.optString("grupo") != grupoId || data.optString("de") == miId)
            {
                return@Listener
            }
            alcance.launch {
                apagarEscribiendo[0]?.cancel()
                if (data.optBoolean("activo"))
                {
                    escribiendoDe = nombres[data.optString("de")] ?: "Alguien"
                    apagarEscribiendo[0] = launch {
                        delay(3000)
                        escribiendoDe = null
                    }
                }
                else
                {
                    escribiendoDe = null
                }
            }
        }
        val alBorrado = Emitter.Listener { args ->
            val data = args.getOrNull(0) as? JSONObject ?: return@Listener
            if (data.optString("grupo_id") == grupoId)
            {
                alcance.launch {
                    mensajes = mensajes.map { if (it.id == data.optString("id")) it.copy(texto = null, borrado = true) else it }
                }
            }
        }
        val alEditado = Emitter.Listener { args ->
            val data = args.getOrNull(0) as? JSONObject ?: return@Listener
            if (data.optString("grupo_id") != grupoId)
            {
                return@Listener
            }
            alcance.launch {
                val idMensaje = data.optString("id")
                val objetivo = mensajes.find { it.id == idMensaje } ?: return@launch
                val clave = pubs[objetivo.remitenteId] ?: return@launch
                val claro = withContext(Dispatchers.IO) {
                    app.identidad.descifrarConHistoricas(
                        data.getString("contenido_cifrado"),
                        data.getString("nonce"),
                        clave,
                    )
                }
                mensajes = mensajes.map {
                    if (it.id == idMensaje) it.copy(texto = claro ?: it.texto, editado = true) else it
                }
            }
        }
        val alReaccion = Emitter.Listener { args ->
            val data = args.getOrNull(0) as? JSONObject ?: return@Listener
            if (data.optString("grupo_id") == grupoId)
            {
                val obj = data.optJSONObject("reacciones")
                val reacciones = obj?.keys()?.asSequence()?.associateWith { obj.optString(it) } ?: emptyMap()
                alcance.launch {
                    mensajes = mensajes.map { if (it.id == data.optString("id")) it.copy(reacciones = reacciones) else it }
                }
            }
        }
        val alLeido = Emitter.Listener { args ->
            val data = args.getOrNull(0) as? JSONObject ?: return@Listener
            if (data.optString("grupo_id") != grupoId)
            {
                return@Listener
            }
            val lecturas = data.optJSONArray("lecturas") ?: return@Listener
            val porMensaje = HashMap<String, Map<String, String>>()
            for (i in 0 until lecturas.length())
            {
                val l = lecturas.getJSONObject(i)
                val lp = l.optJSONObject("leido_por") ?: continue
                porMensaje[l.optString("id")] = lp.keys().asSequence().associateWith { lp.optString(it) }
            }
            alcance.launch {
                mensajes = mensajes.map { porMensaje[it.id]?.let { nuevo -> it.copy(leidoPor = nuevo) } ?: it }
            }
        }
        val alActualizado = Emitter.Listener { args ->
            val data = args.getOrNull(0) as? JSONObject ?: return@Listener
            if (data.optString("id") == grupoId)
            {
                alcance.launch {
                    refrescarGrupo()
                    if (miId.isNotBlank())
                    {
                        DrenadorOutbox.drenar(app, miId, Outbox.Tipo.GRUPO, grupoId, forzar = true)
                    }
                }
            }
        }
        val alConectar = Emitter.Listener {
            alcance.launch {
                if (miId.isNotBlank())
                {
                    DrenadorOutbox.drenar(app, miId, Outbox.Tipo.GRUPO, grupoId, forzar = true)
                }
            }
        }
        val dejarDeObservarOutbox = DrenadorOutbox.observar { resultado ->
            if (
                resultado.cuentaId == miId &&
                resultado.tipo == Outbox.Tipo.GRUPO &&
                resultado.destinoId == grupoId
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
                            mensaje.copy(estado = "fallido")
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
        socket?.on("grupo:mensaje", alMensaje)
        socket?.on("grupo:escribiendo", alEscribiendo)
        socket?.on("grupo:borrado", alBorrado)
        socket?.on("grupo:editado", alEditado)
        socket?.on("grupo:reaccion", alReaccion)
        socket?.on("grupo:leido", alLeido)
        socket?.on("grupo:actualizado", alActualizado)
        socket?.on(Socket.EVENT_CONNECT, alConectar)
        onDispose {
            socket?.off("grupo:mensaje", alMensaje)
            socket?.off("grupo:escribiendo", alEscribiendo)
            socket?.off("grupo:borrado", alBorrado)
            socket?.off("grupo:editado", alEditado)
            socket?.off("grupo:reaccion", alReaccion)
            socket?.off("grupo:leido", alLeido)
            socket?.off("grupo:actualizado", alActualizado)
            socket?.off(Socket.EVENT_CONNECT, alConectar)
            dejarDeObservarOutbox()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(colores.fondo)) {
    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { alNavegar("grupo-info/$grupoId") }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        )
        {
            Text(
                "‹",
                fontSize = 26.sp,
                color = colores.texto,
                modifier = Modifier.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { alNavegar("grupos") },
            )
            Avatar(nombre = nombreGrupo, tamano = 32.dp)
            Column {
                Text(nombreGrupo, fontSize = 16.sp, fontFamily = FuenteOutfit, fontWeight = FontWeight.SemiBold, color = colores.texto)
                Text(
                    escribiendoDe?.let { "$it escribe…" } ?: if (numMiembros > 0) "$numMiembros miembros" else "",
                    fontSize = 12.sp,
                    color = if (escribiendoDe != null) colores.botonFondo else colores.muted,
                )
            }
        }

        val fijadoActual = if (fijados.isEmpty()) null else fijados[indiceFijado % fijados.size]
        if (fijadoActual != null)
        {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .panelVidrio(radio = 12.dp, desenfocar = true)
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { irAFijado() }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            )
            {
                Pin(color = colores.muted, tamano = 16.dp)
                Text(
                    Resumen.resumenMensaje(fijadoActual.texto),
                    fontSize = 13.sp,
                    color = colores.texto,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        val visibles = if (ocultos.isEmpty()) mensajes else mensajes.filter { !ocultos.contains(it.id) }
        val porId = remember(mensajes) { mensajes.associateBy { it.id } }
        LazyColumn(
            state = listaEstado,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        )
        {
            items(visibles, key = { it.id }) { m ->
                var limites by remember { mutableStateOf(Rect.Zero) }
                Box(modifier = Modifier.animateItem().onGloballyPositioned { limites = it.boundsInRoot() }) {
                    BurbujaGrupo(
                        m = m,
                        mio = m.remitenteId == miId,
                        numMiembros = numMiembros,
                        colores = colores,
                        app = app,
                        alAbrirImagen = { visor = it },
                        alAbrirVideo = { visorVideo = it },
                        cita = m.respuestaTexto ?: m.respuestaA?.let { respuestaId ->
                            porId[respuestaId]?.let { Resumen.resumenMensaje(it.texto) } ?: "Mensaje"
                        },
                        alReintentar = { reintentar(m) },
                        alMantener = {
                            if (!m.borrado && m.estado == null)
                            {
                                vibrador.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                sel = AccionesDe(m, limites)
                            }
                        },
                    )
                }
            }
        }

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
                    modifier = Modifier.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
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
        val selectorDocumento = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null)
            {
                enviarDocumentoGrupo(uri)
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
        selectorFotoRef[0] = { app.saltarBloqueo = true; selectorFoto.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
        selectorVideoRef[0] = { app.saltarBloqueo = true; selectorVideo.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) }
        selectorDocumentoRef[0] = { app.saltarBloqueo = true; selectorDocumento.launch("*/*") }
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
            val uri = androidx.core.content.FileProvider.getUriForFile(contexto, "dev.vixxer.mensajero.nativo.archivos", destino)
            fotoCamara[0] = uri
            app.saltarBloqueo = true
            camara.launch(uri)
        }
        val mencionParcial = Regex("(^|\\s)@([\\w.-]*)$").find(texto)?.groupValues?.get(2)?.lowercase()
        val sugerencias = if (mencionParcial != null)
            miembros.filter { it.id != miId && it.usuario.lowercase().startsWith(mencionParcial) }.take(5)
        else emptyList()
        if (sugerencias.isNotEmpty())
        {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .panelVidrio(radio = 12.dp, desenfocar = true),
            )
            {
                for (mb in sugerencias)
                {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                                texto = texto.replace(Regex("@[\\w.-]*$"), "@${mb.usuario} ")
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    )
                    {
                        Avatar(nombre = mb.usuario, uri = mb.avatarUrl, tamano = 26.dp)
                        Text("@${mb.usuario}", fontSize = 14.sp, color = colores.texto)
                    }
                }
            }
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
                    .size(44.dp)
                    .clickable(enabled = !subiendo, indication = null, interactionSource = remember { MutableInteractionSource() }) {
                        enfoque.clearFocus()
                        adjuntando = true
                    },
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
            if (texto.isBlank() && editando == null)
            {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(colores.surface, CircleShape)
                        .border(Vidrio.anchoBorde, colores.borde, CircleShape)
                        .clickable(enabled = !subiendo, indication = null, interactionSource = remember { MutableInteractionSource() }) {
                            permisoMicRef[0]?.invoke()
                        },
                    contentAlignment = Alignment.Center,
                )
                {
                    Microfono(color = colores.texto, tamano = 20.dp)
                }
            }
            else
            {
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
                    .vidrioFlotante(radio = 22.dp)
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
                        modifier = Modifier.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { accion() },
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
                enviarLoteGrupo(lista)
            },
        )
    }

    SelectorSticker(app = app, visible = mostrandoStickers, alElegir = { enviarStickerGrupo(it) }, alCerrar = { mostrandoStickers = false })

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
                .vidrioFlotante(radio = 22.dp)
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
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(colores.surface, CircleShape)
                    .border(Vidrio.anchoBorde, colores.borde, CircleShape)
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                        val activa = grabadora[0]
                        if (activa != null)
                        {
                            if (activa.pausada) activa.continuar() else activa.pausar()
                            grabacionPausada = activa.pausada
                        }
                    },
                contentAlignment = Alignment.Center,
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
            Text(
                "Cancelar",
                fontSize = 14.sp,
                color = colores.muted,
                modifier = Modifier.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                    terminarGrabacion(false)
                },
            )
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(colores.botonFondo, CircleShape)
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                        terminarGrabacion(true)
                    },
                contentAlignment = Alignment.Center,
            )
            {
                Text("➤", fontSize = 16.sp, color = colores.botonTexto)
            }
        }
        }
    }

    VisorVideo(app = app, media = visorVideo, alCerrar = { visorVideo = null })

    VisorImagen(archivo = visor, alCerrar = { visor = null })

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
        alCopiar = { copiar(it) },
        alEditar = { m ->
            sel = null
            editando = m
            respondiendo = null
            texto = m.texto ?: ""
        },
        alFijar = { fijar(it) },
        alInfo = { m ->
            sel = null
            infoDe = m
        },
        alBorrar = { borrar(it) },
        alBorrarLocal = { borrarLocal(it) },
        alCerrar = { sel = null },
    )

    HojaVistos(infoDe, miembros, miId, numMiembros, colores) { infoDe = null }

    SelectorContacto(
        app = app,
        visible = reenviando != null,
        titulo = "Reenviar a",
        alElegir = { hacerReenvio(it) },
        alCerrar = { reenviando = null },
    )
    }
}

@Composable
private fun BurbujaGrupo(
    m: MensajeGrupo,
    mio: Boolean,
    numMiembros: Int,
    colores: Paleta,
    app: AplicacionVixxer,
    alAbrirImagen: (java.io.File) -> Unit,
    alAbrirVideo: (MediaMensaje) -> Unit,
    cita: String? = null,
    alReintentar: () -> Unit = {},
    alMantener: () -> Unit = {},
)
{
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val anchoMax = maxWidth * 0.8f
        Column(modifier = Modifier.align(if (mio) Alignment.CenterEnd else Alignment.CenterStart)) {
            if (!mio && m.autor != null)
            {
                Text(
                    m.autor,
                    fontSize = 12.sp,
                    fontFamily = FuenteOutfit,
                    fontWeight = FontWeight.SemiBold,
                    color = colores.botonFondo,
                    modifier = Modifier.padding(start = 6.dp, bottom = 2.dp),
                )
            }
            val mediaBurbuja = if (m.borrado) null else leerMedia(m.texto)
            val mediaVisual = mediaBurbuja != null && mediaBurbuja.t in listOf("img", "video", "sticker")
            Column(
                modifier = Modifier
                    .widthIn(max = anchoMax)
                    .background(if (mio) colores.botonFondo else colores.surface, RoundedCornerShape(16.dp))
                    .then(if (mio) Modifier else Modifier.border(Vidrio.anchoBorde, colores.borde, RoundedCornerShape(16.dp)))
                    .combinedClickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = { if (m.estado == "fallido") alReintentar() },
                        onLongClick = { alMantener() },
                    )
                    .padding(
                        horizontal = if (mediaVisual && cita == null) 3.dp else 14.dp,
                        vertical = if (mediaVisual && cita == null) 3.dp else 9.dp,
                    ),
            )
            {
                if (cita != null)
                {
                    Text(
                        cita,
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
                else if (media != null && media.t == "video")
                {
                    AdjuntoVideo(media = media, colores = colores, alReproducir = { alAbrirVideo(media) }, alMantener = alMantener)
                }
                else if (media != null && media.t == "audio")
                {
                    AdjuntoAudio(app = app, media = media, mio = mio, colores = colores)
                }
                else if (media != null && media.t == "file")
                {
                    AdjuntoArchivo(app = app, media = media, mio = mio, colores = colores)
                }
                else
                {
                    Text(
                        textoVisibleGrupo(m.texto),
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
                    if (mio && m.estado == "enviando")
                    {
                        Text("…", fontSize = 10.sp, color = colores.botonTexto.copy(alpha = 0.7f))
                    }
                    if (mio && m.estado == "fallido")
                    {
                        Text("No se envió", fontSize = 10.sp, color = colores.error)
                    }
                    if (mio && m.estado != "enviando" && m.estado != "fallido")
                    {
                        val lecturas = m.leidoPor.size
                        val todos = numMiembros > 1 && lecturas >= numMiembros - 1
                        Visto(
                            color = if (todos) colores.botonTexto else colores.botonTexto.copy(alpha = 0.5f),
                            dos = lecturas > 0,
                            tamano = 12.dp,
                        )
                    }
                }
            }
            if (m.reacciones.isNotEmpty())
            {
                val grupos = m.reacciones.values.groupingBy { it }.eachCount()
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 3.dp)) {
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

private fun textoVisibleGrupo(texto: String?): String
{
    if (texto == null)
    {
        return "No se pudo descifrar"
    }
    if (texto.startsWith("{"))
    {
        return dev.vixxer.mensajero.nucleo.Resumen.resumenMensaje(texto)
    }
    return texto
}

@Composable
private fun HojaVistos(
    mensaje: MensajeGrupo?,
    miembros: List<MiembroGrupo>,
    miId: String,
    numMiembros: Int,
    colores: Paleta,
    alCerrar: () -> Unit,
)
{
    if (mensaje == null)
    {
        return
    }
    val leidoPor = mensaje.leidoPor
    val vistos = miembros
        .filter { it.id != miId && leidoPor.containsKey(it.id) }
        .sortedBy { leidoPor[it.id] }
    androidx.compose.ui.window.Dialog(onDismissRequest = alCerrar) {
        Column(
            modifier = Modifier
                .widthIn(max = 360.dp)
                .background(colores.surface, RoundedCornerShape(16.dp))
                .border(Vidrio.anchoBorde, colores.borde, RoundedCornerShape(16.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        )
        {
            Text(
                "Visto por ${vistos.size} de ${maxOf(0, numMiembros - 1)}",
                fontSize = 16.sp,
                fontFamily = FuenteOutfit,
                fontWeight = FontWeight.SemiBold,
                color = colores.texto,
            )
            if (vistos.isEmpty())
            {
                Text("Nadie lo ha visto todavía.", fontSize = 13.sp, color = colores.muted)
            }
            Column(
                modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            )
            {
                for (mb in vistos)
                {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    )
                    {
                        Avatar(nombre = mb.usuario, uri = mb.avatarUrl, tamano = 30.dp)
                        Text(mb.usuario, fontSize = 14.sp, color = colores.texto, modifier = Modifier.weight(1f))
                        Text(Fechas.hora(leidoPor[mb.id] ?: ""), fontSize = 12.sp, color = colores.muted)
                    }
                }
            }
        }
    }
}

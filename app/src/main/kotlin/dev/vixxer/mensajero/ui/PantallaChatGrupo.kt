package dev.vixxer.mensajero.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vixxer.mensajero.AplicacionVixxer
import dev.vixxer.mensajero.nucleo.ClavesSeguras
import dev.vixxer.mensajero.nucleo.ConexionSocket
import dev.vixxer.mensajero.nucleo.Cripto
import dev.vixxer.mensajero.nucleo.Fechas
import dev.vixxer.mensajero.nucleo.GrupoVisto
import io.socket.emitter.Emitter
import java.time.Instant
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
    val reacciones: Map<String, String> = emptyMap(),
)

@Composable
fun PantallaChatGrupo(app: AplicacionVixxer, grupoId: String, nombreInicial: String, alNavegar: (String) -> Unit)
{
    val colores = LocalTema.current.colores
    val alcance = rememberCoroutineScope()
    var mensajes by remember { mutableStateOf(listOf<MensajeGrupo>()) }
    var texto by remember { mutableStateOf("") }
    var nombreGrupo by remember { mutableStateOf(nombreInicial) }
    var numMiembros by remember { mutableStateOf(0) }
    var escribiendoDe by remember { mutableStateOf<String?>(null) }
    var hayMas by remember { mutableStateOf(true) }
    var masCargando by remember { mutableStateOf(false) }
    var miId by remember { mutableStateOf("") }
    val pubs = remember { HashMap<String, String>() }
    val nombres = remember { HashMap<String, String>() }
    val marcados = remember { HashSet<String>() }
    val listaEstado = rememberLazyListState()
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
            arreglo.put(obj)
        }
        app.cacheChats.guardarChat(claveCache, arreglo)
    }

    fun descifrarFila(f: JSONObject, priv: String): MensajeGrupo
    {
        val remitente = f.optString("remitente_id")
        val pub = pubs[remitente]
        val borrado = f.optBoolean("borrado")
        val reacciones = f.optJSONObject("reacciones")?.let { obj ->
            obj.keys().asSequence().associateWith { obj.optString(it) }
        } ?: emptyMap()
        return MensajeGrupo(
            id = f.optString("id"),
            remitenteId = remitente,
            autor = nombres[remitente],
            texto = if (borrado || pub == null) null
                else Cripto.descifrarTexto(f.getString("contenido_cifrado"), f.getString("nonce"), pub, priv) ?: "No se pudo descifrar",
            enviadoEn = f.optString("enviado_en"),
            borrado = borrado,
            editado = f.optBoolean("editado"),
            reacciones = reacciones,
        )
    }

    fun reportarLeidos(lista: List<MensajeGrupo>)
    {
        val ids = lista
            .filter { it.remitenteId != miId && !it.id.startsWith("local-") && !marcados.contains(it.id) }
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

    fun enviar()
    {
        val limpio = texto.trim()
        if (limpio.isEmpty())
        {
            return
        }
        tecleandoJob[0]?.cancel()
        ConexionSocket.obtener()?.emit("grupo:escribiendo", JSONObject().put("grupo", grupoId).put("activo", false))
        texto = ""
        val clienteId = "local-${System.currentTimeMillis()}"
        mensajes = mensajes + MensajeGrupo(
            id = clienteId,
            remitenteId = miId,
            autor = null,
            texto = limpio,
            enviadoEn = Instant.now().toString(),
            estado = "enviando",
        )
        alcance.launch {
            listaEstado.animateScrollToItem(maxOf(0, mensajes.size - 1))
            val resultado = withContext(Dispatchers.IO) {
                runCatching {
                    val priv = app.boveda.leer(ClavesSeguras.CLAVE_PRIVADA) ?: return@runCatching null
                    app.api.enviarGrupo(grupoId, clienteId, cifrarParaTodos(limpio, priv)) as? JSONObject
                }.getOrNull()
            }
            mensajes = if (resultado != null && resultado.optBoolean("ok"))
            {
                mensajes.map { if (it.id == clienteId) it.copy(id = resultado.optString("id"), estado = "enviado") else it }
            }
            else
            {
                mensajes.map { if (it.id == clienteId) it.copy(estado = "fallido") else it }
            }
            withContext(Dispatchers.IO) { guardarCache(mensajes) }
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
            val priv = app.boveda.leer(ClavesSeguras.CLAVE_PRIVADA) ?: ""
            val cache = app.cacheChats.leerChat(claveCache)
            val borrador = app.borradores.leer(claveBorrador).optString("texto")
            Triple(mi, priv, Pair(cache, borrador))
        }
        miId = datos.first
        if (datos.third.second.isNotEmpty())
        {
            texto = datos.third.second
        }
        val cache = datos.third.first
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
                )
            }
        }
        withContext(Dispatchers.IO) {
            runCatching {
                val g = app.api.infoGrupo(grupoId) as JSONObject
                nombreGrupo = g.optString("nombre").ifEmpty { nombreInicial }
                val miembros = g.optJSONArray("miembros") ?: JSONArray()
                numMiembros = miembros.length()
                for (i in 0 until miembros.length())
                {
                    val m = miembros.getJSONObject(i)
                    pubs[m.getString("id")] = m.textoO("llave_publica")
                    nombres[m.getString("id")] = m.optString("usuario")
                }
            }
            runCatching {
                val filas = app.api.historialGrupo(grupoId) as JSONArray
                hayMas = filas.length() >= 50
                val desc = (0 until filas.length()).map { descifrarFila(filas.getJSONObject(it), datos.second) }
                if (desc.isNotEmpty() || cache == null)
                {
                    val locales = mensajes.filter { m -> m.id.startsWith("local-") && desc.none { it.id == m.id } }
                    val junto = (desc + locales).sortedBy { it.enviadoEn }
                    mensajes = junto
                    guardarCache(junto)
                }
                GrupoVisto(app.estado).marcarVisto(grupoId)
                reportarLeidos(mensajes)
            }
        }
        if (mensajes.isNotEmpty())
        {
            listaEstado.scrollToItem(maxOf(0, mensajes.size - 1))
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
                        val priv = app.boveda.leer(ClavesSeguras.CLAVE_PRIVADA) ?: ""
                        val filas = app.api.historialGrupo(grupoId, primero) as JSONArray
                        Pair(filas.length(), (0 until filas.length()).map { descifrarFila(filas.getJSONObject(it), priv) })
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
                val nuevo = withContext(Dispatchers.IO) {
                    val priv = app.boveda.leer(ClavesSeguras.CLAVE_PRIVADA) ?: ""
                    descifrarFila(data, priv)
                }
                if (mensajes.none { it.id == nuevo.id })
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
                mensajes = mensajes.map { if (it.id == data.optString("id")) it.copy(texto = null, borrado = true) else it }
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
                    val priv = app.boveda.leer(ClavesSeguras.CLAVE_PRIVADA) ?: ""
                    Cripto.descifrarTexto(data.getString("contenido_cifrado"), data.getString("nonce"), clave, priv)
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
                mensajes = mensajes.map { if (it.id == data.optString("id")) it.copy(reacciones = reacciones) else it }
            }
        }
        socket?.on("grupo:mensaje", alMensaje)
        socket?.on("grupo:escribiendo", alEscribiendo)
        socket?.on("grupo:borrado", alBorrado)
        socket?.on("grupo:editado", alEditado)
        socket?.on("grupo:reaccion", alReaccion)
        onDispose {
            socket?.off("grupo:mensaje", alMensaje)
            socket?.off("grupo:escribiendo", alEscribiendo)
            socket?.off("grupo:borrado", alBorrado)
            socket?.off("grupo:editado", alEditado)
            socket?.off("grupo:reaccion", alReaccion)
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(colores.fondo).statusBarsPadding().imePadding()) {
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

        LazyColumn(
            state = listaEstado,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        )
        {
            items(mensajes, key = { it.id }) { m ->
                BurbujaGrupo(m, m.remitenteId == miId, colores)
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

@Composable
private fun BurbujaGrupo(m: MensajeGrupo, mio: Boolean, colores: Paleta)
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
            Column(
                modifier = Modifier
                    .widthIn(max = anchoMax)
                    .background(if (mio) colores.botonFondo else colores.surface, RoundedCornerShape(16.dp))
                    .then(if (mio) Modifier else Modifier.border(Vidrio.anchoBorde, colores.borde, RoundedCornerShape(16.dp)))
                    .padding(horizontal = 14.dp, vertical = 9.dp),
            )
            {
                if (m.borrado)
                {
                    Text(
                        "Este mensaje fue eliminado",
                        fontSize = 15.sp,
                        fontStyle = FontStyle.Italic,
                        color = (if (mio) colores.botonTexto else colores.muted).copy(alpha = 0.8f),
                    )
                }
                else
                {
                    Text(
                        textoVisibleGrupo(m.texto),
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
                    if (mio && m.estado == "enviando")
                    {
                        Text("…", fontSize = 10.sp, color = colores.botonTexto.copy(alpha = 0.7f))
                    }
                    if (mio && m.estado == "fallido")
                    {
                        Text("No se envió", fontSize = 10.sp, color = colores.error)
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

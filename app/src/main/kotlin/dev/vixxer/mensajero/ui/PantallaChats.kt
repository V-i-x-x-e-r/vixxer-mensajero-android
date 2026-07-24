package dev.vixxer.mensajero.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vixxer.mensajero.AplicacionVixxer
import dev.vixxer.mensajero.Config
import dev.vixxer.mensajero.nucleo.ClavesSeguras
import dev.vixxer.mensajero.nucleo.ConexionSocket
import dev.vixxer.mensajero.nucleo.EstadosChat
import dev.vixxer.mensajero.nucleo.Fechas
import io.socket.client.Socket
import io.socket.emitter.Emitter
import java.text.DateFormat
import java.time.ZoneId
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private val DORADO = Color(0xFFF5B301)

data class Amigo(val id: String, val usuario: String, val avatarUrl: String)

data class Conv(
    val preview: String,
    val enviadoEn: String,
    val noLeidos: Int = 0,
    val mio: Boolean = false,
    val entregado: Boolean = false,
    val leido: Boolean = false,
    val borrador: Boolean = false,
)

private fun cuando(iso: String): String
{
    if (iso.isEmpty())
    {
        return ""
    }
    val instante = Fechas.aInstante(iso)
    val zona = ZoneId.systemDefault()
    return if (Fechas.mismoDia(iso, null, zona))
    {
        Fechas.hora(iso, zona)
    }
    else
    {
        DateFormat.getDateInstance(DateFormat.SHORT).format(Date.from(instante))
    }
}

private fun previewDe(texto: String): String
{
    if (texto.isEmpty() || texto[0] != '{')
    {
        return texto
    }
    return when
    {
        texto.contains("\"t\":\"img\"") -> "Foto"
        texto.contains("\"t\":\"video\"") -> "Video"
        texto.contains("\"t\":\"audio\"") -> "Audio"
        texto.contains("\"t\":\"sticker\"") -> "Sticker"
        texto.contains("\"t\":\"file\"") -> "Documento"
        texto.contains("\"t\":\"tmpaviso\"") -> "Mensajes temporales"
        texto.contains("\"t\":\"tmp\"") -> runCatching { JSONObject(texto).getString("m") }.getOrDefault("Mensaje")
        else -> texto
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PantallaChats(app: AplicacionVixxer, alNavegar: (String) -> Unit, alAbrirChat: (Amigo) -> Unit)
{
    val tema = LocalTema.current
    val colores = tema.colores
    val alcance = rememberCoroutineScope()
    var amigos by remember { mutableStateOf(listOf<Amigo>()) }
    var convs by remember { mutableStateOf(mapOf<String, Conv>()) }
    var estados by remember { mutableStateOf(EstadosChat.Estados(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())) }
    var verArchivados by remember { mutableStateOf(false) }
    var cargando by remember { mutableStateOf(true) }
    var refrescando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var estadoConexion by remember { mutableStateOf("conectando…") }
    var sel by remember { mutableStateOf<String?>(null) }
    var tecleando by remember { mutableStateOf(mapOf<String, Boolean>()) }
    var borrando by remember { mutableStateOf(false) }
    var busqueda by remember { mutableStateOf("") }
    var alias by remember { mutableStateOf(mapOf<String, String>()) }
    val trabajosTecleo = remember { HashMap<String, Job>() }
    val recarga = remember { arrayOf<Job?>(null) }
    var socketActivo by remember { mutableStateOf(ConexionSocket.obtener()) }

    suspend fun cargar()
    {
        error = false
        try
        {
            val resultado = withContext(Dispatchers.IO) {
                val e = app.estadosChat.leerEstados()
                val mapaAlias = app.aliasLocal.leerTodos()
                val lista = app.api.amigos() as JSONArray
                val conversaciones = app.api.conversaciones() as JSONArray
                val miId = app.boveda.leer(ClavesSeguras.MI_ID)
                val mapa = HashMap<String, Conv>()
                for (i in 0 until conversaciones.length())
                {
                    val c = conversaciones.getJSONObject(i)
                    val otroId = c.getString("otro_id")
                    if (c.optString("ultimo_cifrado") == "BORRADO")
                    {
                        mapa[otroId] = Conv("Mensaje eliminado", c.optString("enviado_en"), c.optInt("no_leidos"))
                        continue
                    }
                    app.llaves.sembrar(otroId, c.textoO("llave_publica").ifEmpty { null })
                    val pub = c.textoO("llave_publica").ifEmpty { app.llaves.llavePublicaDe(otroId) }
                    var claro = app.identidad.descifrarConHistoricas(
                        c.getString("ultimo_cifrado"),
                        c.getString("ultimo_nonce"),
                        pub,
                    )
                    if (claro == null)
                    {
                        val fresca = app.llaves.llavePublicaDe(otroId, forzar = true)
                        claro = app.identidad.descifrarConHistoricas(
                            c.getString("ultimo_cifrado"),
                            c.getString("ultimo_nonce"),
                            fresca,
                        )
                    }
                    val texto = previewDe(claro ?: "Mensaje cifrado")
                    val mio = c.optString("ultimo_remitente_id") == miId
                    mapa[otroId] = Conv(
                        preview = if (mio) "Tú: $texto" else texto,
                        enviadoEn = c.optString("enviado_en"),
                        noLeidos = c.optInt("no_leidos"),
                        mio = mio,
                        entregado = !c.isNull("ultimo_entregado_en"),
                        leido = !c.isNull("ultimo_leido_en"),
                    )
                }
                val todos = (0 until lista.length()).map { i ->
                    val a = lista.getJSONObject(i)
                    Amigo(a.getString("id"), a.optString("usuario"), a.textoO("avatar_url"))
                }
                for (a in todos)
                {
                    val b = app.borradores.leer("chat-${a.id}")
                    val textoBorrador = b.optString("texto")
                    if (textoBorrador.isNotEmpty() || !b.isNull("audio") && b.has("audio"))
                    {
                        val previa = mapa[a.id]
                        mapa[a.id] = Conv(
                            preview = if (textoBorrador.isNotEmpty()) "Borrador: $textoBorrador" else "Borrador: nota de voz",
                            enviadoEn = previa?.enviadoEn ?: java.time.Instant.now().toString(),
                            noLeidos = previa?.noLeidos ?: 0,
                            mio = previa?.mio ?: false,
                            entregado = previa?.entregado ?: false,
                            leido = previa?.leido ?: false,
                            borrador = true,
                        )
                    }
                }
                val visibles = todos.filter { mapa.containsKey(it.id) && !e.ocultos.contains(it.id) }
                    .sortedByDescending { mapa[it.id]?.enviadoEn ?: "" }
                val ordenados = visibles.filter { e.fijados.contains(it.id) } + visibles.filter { !e.fijados.contains(it.id) }
                val cache = JSONObject()
                val cacheAmigos = JSONArray()
                for (a in ordenados)
                {
                    cacheAmigos.put(JSONObject().put("id", a.id).put("usuario", a.usuario).put("avatar_url", a.avatarUrl))
                }
                val cacheConvs = JSONObject()
                for ((id, cv) in mapa)
                {
                    cacheConvs.put(id, JSONObject()
                        .put("preview", cv.preview)
                        .put("enviado_en", cv.enviadoEn)
                        .put("noLeidos", cv.noLeidos)
                        .put("mio", cv.mio)
                        .put("entregado", cv.entregado)
                        .put("leido", cv.leido)
                        .put("borrador", cv.borrador))
                }
                cache.put("amigos", cacheAmigos)
                cache.put("convs", cacheConvs)
                app.cacheChats.guardarLista(cache)
                Triple(e to mapaAlias, ordenados, mapa)
            }
            estados = resultado.first.first
            alias = resultado.first.second
            amigos = resultado.second
            convs = resultado.third
        }
        catch (e: Exception)
        {
            error = true
        }
        finally
        {
            cargando = false
        }
    }

    fun programarCarga()
    {
        if (recarga[0]?.isActive == true)
        {
            return
        }
        recarga[0] = alcance.launch {
            delay(250)
            cargar()
        }
    }

    LaunchedEffect(Unit) {
        val cache = withContext(Dispatchers.IO) { app.cacheChats.leerLista() }
        if (cache != null)
        {
            val listaCache = cache.optJSONArray("amigos") ?: JSONArray()
            val convsCache = cache.optJSONObject("convs") ?: JSONObject()
            amigos = (0 until listaCache.length()).map { i ->
                val a = listaCache.getJSONObject(i)
                Amigo(a.getString("id"), a.optString("usuario"), a.textoO("avatar_url"))
            }
            val mapa = HashMap<String, Conv>()
            for (id in convsCache.keys())
            {
                val cv = convsCache.getJSONObject(id)
                mapa[id] = Conv(
                    cv.optString("preview"),
                    cv.optString("enviado_en"),
                    cv.optInt("noLeidos"),
                    cv.optBoolean("mio"),
                    cv.optBoolean("entregado"),
                    cv.optBoolean("leido"),
                    cv.optBoolean("borrador"),
                )
            }
            convs = mapa
            cargando = false
        }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { respaldoAutomatico(app) }
    }

    LaunchedEffect(Unit) {
        val token = withContext(Dispatchers.IO) { app.boveda.leer(ClavesSeguras.TOKEN) }
        if (token == null)
        {
            alNavegar("login")
            return@LaunchedEffect
        }
        val socket = withContext(Dispatchers.IO) { ConexionSocket.conectar(app.urlServidor(), token) }
        dev.vixxer.mensajero.NotificadorMensajes.enganchar(app, socket)
        socketActivo = socket
        estadoConexion = if (socket.connected()) "conectado" else "conectando…"
        socket.emit("entregar:pendientes")
        cargar()
    }

    DisposableEffect(socketActivo) {
        val socket = socketActivo
        val alConectar = Emitter.Listener {
            alcance.launch { estadoConexion = "conectado" }
        }
        val alDesconectar = Emitter.Listener {
            alcance.launch { estadoConexion = "sin conexión" }
        }
        val alErrorConexion = Emitter.Listener {
            alcance.launch { estadoConexion = "sin conexión" }
        }
        val alMensaje = Emitter.Listener { args ->
            val fila = args.getOrNull(0) as? JSONObject ?: return@Listener
            socket?.emit("mensaje:entregado", JSONObject().put("id", fila.opt("id")))
            alcance.launch {
                app.estadosChat.mostrar(fila.optString("remitente_id"))
                programarCarga()
            }
        }
        val alEscribiendo = Emitter.Listener { args ->
            val data = args.getOrNull(0) as? JSONObject ?: return@Listener
            val de = data.optString("de")
            if (de.isEmpty())
            {
                return@Listener
            }
            alcance.launch {
                trabajosTecleo[de]?.cancel()
                if (data.optBoolean("activo"))
                {
                    tecleando = tecleando + (de to true)
                    trabajosTecleo[de] = launch {
                        delay(3000)
                        tecleando = tecleando + (de to false)
                    }
                }
                else
                {
                    tecleando = tecleando + (de to false)
                }
            }
        }
        val dejarDeEscucharBle = dev.vixxer.mensajero.ble.GestorCercania.mensajeria(app).alEntrante { obj ->
            val de = obj.optString("remitente_id")
            if (de.isNotEmpty() && !obj.has("grupo_id"))
            {
                alcance.launch {
                    val previa = convs[de]
                    val nuevas = convs + (de to Conv(
                        preview = previewDe(obj.optString("texto")),
                        enviadoEn = obj.optString("enviado_en"),
                        noLeidos = (previa?.noLeidos ?: 0) + 1,
                    ))
                    convs = nuevas
                    val fijados = estados.fijados
                    val base = if (amigos.none { it.id == de })
                    {
                        val nombre = dev.vixxer.mensajero.ble.GestorCercania.nombreDe(de) ?: "Vixxer"
                        amigos + Amigo(de, nombre, "")
                    }
                    else
                    {
                        amigos
                    }
                    amigos = base.sortedWith(
                        compareByDescending<Amigo> { fijados.contains(it.id) }
                            .thenByDescending { nuevas[it.id]?.enviadoEn ?: "" },
                    )
                }
            }
        }
        socket?.on(Socket.EVENT_CONNECT, alConectar)
        socket?.on(Socket.EVENT_DISCONNECT, alDesconectar)
        socket?.on(Socket.EVENT_CONNECT_ERROR, alErrorConexion)
        socket?.on("mensaje:recibido", alMensaje)
        socket?.on("usuario:escribiendo", alEscribiendo)
        onDispose {
            socket?.off(Socket.EVENT_CONNECT, alConectar)
            socket?.off(Socket.EVENT_DISCONNECT, alDesconectar)
            socket?.off(Socket.EVENT_CONNECT_ERROR, alErrorConexion)
            socket?.off("mensaje:recibido", alMensaje)
            socket?.off("usuario:escribiendo", alEscribiendo)
            dejarDeEscucharBle()
            recarga[0]?.cancel()
            trabajosTecleo.values.forEach { it.cancel() }
        }
    }

    val conectado = estadoConexion == "conectado"
    val archivados = estados.archivados
    val enSeccion = if (verArchivados) amigos.filter { archivados.contains(it.id) } else amigos.filter { !archivados.contains(it.id) }
    val mostrados = if (busqueda.trim().isNotEmpty())
    {
        enSeccion.filter { (alias[it.id] ?: it.usuario).lowercase().contains(busqueda.trim().lowercase()) }
    }
    else
    {
        enSeccion
    }
    val numArchivados = amigos.count { archivados.contains(it.id) }

    LaunchedEffect(verArchivados, numArchivados) {
        if (verArchivados && numArchivados == 0)
        {
            verArchivados = false
        }
    }

    fun accion(bloque: suspend () -> Unit)
    {
        alcance.launch {
            withContext(Dispatchers.IO) { bloque() }
            cargar()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(colores.fondo)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        )
        {
            val seleccionado = sel
            if (seleccionado != null)
            {
                Row(
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 28.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                )
                {
                    Text(
                        "✕",
                        fontSize = 22.sp,
                        color = colores.texto,
                        modifier = Modifier.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { sel = null },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        val esFavorito = estados.favoritos.contains(seleccionado)
                        val esFijado = estados.fijados.contains(seleccionado)
                        val esSilenciado = estados.silenciados.contains(seleccionado)
                        val esArchivado = estados.archivados.contains(seleccionado)
                        Box(modifier = Modifier.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                            accion { app.estadosChat.alternarFavorito(seleccionado) }
                        }) {
                            Estrella(color = if (esFavorito) DORADO else colores.muted, relleno = if (esFavorito) DORADO else null, tamano = 20.dp)
                        }
                        Box(modifier = Modifier.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                            accion { app.estadosChat.alternarFijado(seleccionado) }
                        }) {
                            Pin(color = if (esFijado) colores.texto else colores.muted)
                        }
                        Box(modifier = Modifier.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                            accion { app.estadosChat.alternarSilenciado(seleccionado) }
                        }) {
                            Silencio(color = if (esSilenciado) colores.texto else colores.muted)
                        }
                        Box(modifier = Modifier.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                            sel = null
                            accion { app.estadosChat.alternarArchivado(seleccionado) }
                        }) {
                            Archivar(color = if (esArchivado) colores.texto else colores.muted)
                        }
                        Box(modifier = Modifier.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { borrando = true }) {
                            Bote(color = colores.error)
                        }
                    }
                }
            }
            else if (verArchivados)
            {
                Row(
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 28.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                )
                {
                    Text(
                        "‹",
                        fontSize = 22.sp,
                        color = colores.texto,
                        modifier = Modifier.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                            verArchivados = false
                            busqueda = ""
                        },
                    )
                    Text("Archivados", fontSize = 18.sp, fontFamily = FuenteOutfit, fontWeight = FontWeight.SemiBold, color = colores.texto)
                }
            }
            else
            {
                Row(
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 28.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                )
                {
                    LogoPenduloFila(alto = 22.dp)
                    Box(modifier = Modifier.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { alNavegar("ajustes") }) {
                        Engrane(color = colores.texto)
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).background(if (conectado) Color(0xFF22C55E) else colores.muted, CircleShape))
                Text(estadoConexion, fontSize = 12.sp, color = colores.muted)
            }

            if (amigos.isNotEmpty() && sel == null)
            {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .background(colores.surface, RoundedCornerShape(12.dp))
                        .border(1.dp, colores.borde, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                )
                {
                    Lupa(color = colores.muted, tamano = 16.dp)
                    Box(modifier = Modifier.weight(1f)) {
                        if (busqueda.isEmpty())
                        {
                            Text("Buscar", fontSize = 15.sp, color = colores.placeholder)
                        }
                        BasicTextField(
                            value = busqueda,
                            onValueChange = { busqueda = it },
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 15.sp, color = colores.texto),
                            cursorBrush = SolidColor(colores.texto),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (busqueda.isNotEmpty())
                    {
                        Text(
                            "✕",
                            fontSize = 15.sp,
                            color = colores.muted,
                            modifier = Modifier.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { busqueda = "" },
                        )
                    }
                }
            }

            PullToRefreshBox(
                isRefreshing = refrescando,
                onRefresh = {
                    alcance.launch {
                        refrescando = true
                        cargar()
                        refrescando = false
                    }
                },
                modifier = Modifier.weight(1f),
            )
            {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    if (!verArchivados && sel == null && busqueda.isEmpty() && numArchivados > 0)
                    {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { verArchivados = true }
                                    .padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            )
                            {
                                Box(
                                    modifier = Modifier.size(44.dp).background(colores.surface, CircleShape),
                                    contentAlignment = Alignment.Center,
                                )
                                {
                                    Archivar(color = colores.muted, tamano = 20.dp)
                                }
                                Text("Archivados", fontSize = 16.sp, color = colores.texto, modifier = Modifier.weight(1f))
                                Text("$numArchivados", fontSize = 11.sp, color = colores.muted)
                            }
                        }
                    }
                    if (mostrados.isEmpty())
                    {
                        item {
                            EstadoLista(
                                cargando = cargando && busqueda.isEmpty(),
                                error = error,
                                vacio = if (busqueda.isNotEmpty()) "Sin resultados." else "Aún no tienes conversaciones. Ve a Amigos para empezar una.",
                                alReintentar = {
                                    cargando = true
                                    alcance.launch { cargar() }
                                },
                                esqueleto = { ListaChatsEsqueleto() },
                            )
                        }
                    }
                    items(mostrados, key = { it.id }) { item ->
                        val c = convs[item.id]
                        val fijado = estados.fijados.contains(item.id)
                        val favorito = estados.favoritos.contains(item.id)
                        val silenciado = estados.silenciados.contains(item.id)
                        val nombre = alias[item.id] ?: item.usuario
                        val elegido = sel == item.id
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (elegido) colores.surface else Color.Transparent, RoundedCornerShape(12.dp))
                                    .combinedClickable(
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() },
                                        onClick = { if (sel != null) sel = item.id else alAbrirChat(item) },
                                        onLongClick = { sel = item.id },
                                    )
                                    .padding(vertical = 12.dp, horizontal = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            )
                            {
                                Avatar(nombre = nombre, uri = item.avatarUrl.ifEmpty { null }, tamano = 44.dp)
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        if (fijado)
                                        {
                                            Pin(color = colores.muted, tamano = 13.dp)
                                        }
                                        if (favorito)
                                        {
                                            Estrella(color = DORADO, relleno = DORADO, tamano = 13.dp)
                                        }
                                        Text(nombre, fontSize = 16.sp, color = colores.texto, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                        if (silenciado)
                                        {
                                            Silencio(color = colores.muted, tamano = 13.dp)
                                        }
                                    }
                                    if (tecleando[item.id] == true)
                                    {
                                        Text("escribiendo…", fontSize = 13.sp, color = colores.botonFondo, maxLines = 1)
                                    }
                                    else if (c != null)
                                    {
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                            if (c.mio)
                                            {
                                                Visto(color = if (c.leido) colores.botonFondo else Color(0xFF8E8E93), dos = c.entregado || c.leido, tamano = 13.dp)
                                            }
                                            Text(
                                                c.preview,
                                                fontSize = 13.sp,
                                                color = if (c.borrador) colores.error else colores.muted,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }
                                if (c != null)
                                {
                                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(cuando(c.enviadoEn), fontSize = 11.sp, color = colores.muted)
                                        if (c.noLeidos > 0)
                                        {
                                            Box(
                                                modifier = Modifier
                                                    .defaultMinSize(minWidth = 20.dp)
                                                    .height(20.dp)
                                                    .background(if (silenciado) colores.muted else colores.botonFondo, RoundedCornerShape(10.dp))
                                                    .padding(horizontal = 6.dp),
                                                contentAlignment = Alignment.Center,
                                            )
                                            {
                                                Text(
                                                    "${c.noLeidos}",
                                                    fontSize = 12.sp,
                                                    fontFamily = FuenteOutfit,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = if (silenciado) colores.fondo else colores.botonTexto,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            Box(modifier = Modifier.fillMaxWidth().padding(start = 66.dp).height(1.dp).background(colores.borde))
                        }
                    }
                    item {
                        Box(modifier = Modifier.height(90.dp))
                    }
                }
            }
        }

        if (borrando)
        {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { borrando = false },
                contentAlignment = Alignment.BottomCenter,
            )
            {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colores.surface.copy(alpha = 0.98f), RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                        .border(1.dp, colores.borde, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                        .navigationBarsPadding()
                        .padding(top = 8.dp, bottom = 32.dp),
                )
                {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                                val id = sel
                                borrando = false
                                sel = null
                                if (id != null)
                                {
                                    accion { app.estadosChat.ocultar(id) }
                                }
                            }
                            .padding(vertical = 14.dp, horizontal = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    )
                    {
                        Text("Quitar de la lista", fontSize = 16.sp, color = colores.texto)
                        Text("La conversación se conserva", fontSize = 12.sp, color = colores.muted)
                    }
                    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(colores.borde))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                                val id = sel
                                borrando = false
                                sel = null
                                if (id != null)
                                {
                                    accion { runCatching { app.api.limpiarConversacion(id) } }
                                }
                            }
                            .padding(vertical = 14.dp, horizontal = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    )
                    {
                        Text("Borrar conversación", fontSize = 16.sp, color = colores.error)
                        Text("Se borra solo para ti", fontSize = 12.sp, color = colores.muted)
                    }
                }
            }
        }
    }
}

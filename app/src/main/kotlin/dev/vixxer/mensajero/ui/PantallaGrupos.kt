package dev.vixxer.mensajero.ui

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vixxer.mensajero.AplicacionVixxer
import dev.vixxer.mensajero.nucleo.ClavesSeguras
import dev.vixxer.mensajero.nucleo.ConexionSocket
import dev.vixxer.mensajero.nucleo.Cripto
import dev.vixxer.mensajero.nucleo.GrupoVisto
import io.socket.emitter.Emitter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class Grupo(
    val id: String,
    val nombre: String,
    val avatarUrl: String,
    val miembros: Int,
    val preview: String?,
    val hora: String?,
    val nuevo: Boolean,
    val borrador: Boolean = false,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaGrupos(app: AplicacionVixxer, alNavegar: (String) -> Unit)
{
    val colores = LocalTema.current.colores
    val alcance = rememberCoroutineScope()
    var grupos by remember { mutableStateOf(listOf<Grupo>()) }
    var cargando by remember { mutableStateOf(true) }
    var refrescando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var sel by remember { mutableStateOf<Grupo?>(null) }
    var confirmarSalir by remember { mutableStateOf(false) }
    var escribiendo by remember { mutableStateOf(mapOf<String, Boolean>()) }
    val trabajos = remember { HashMap<String, Job>() }
    val recarga = remember { arrayOf<Job?>(null) }

    fun grupoAJson(g: Grupo): JSONObject = JSONObject()
        .put("id", g.id)
        .put("nombre", g.nombre)
        .put("avatar_url", g.avatarUrl)
        .put("miembros", g.miembros)
        .put("preview", g.preview ?: JSONObject.NULL)
        .put("hora", g.hora ?: JSONObject.NULL)
        .put("nuevo", g.nuevo)
        .put("borrador", g.borrador)

    fun grupoDeJson(g: JSONObject): Grupo = Grupo(
        id = g.optString("id"),
        nombre = g.optString("nombre"),
        avatarUrl = g.textoO("avatar_url"),
        miembros = g.optInt("miembros"),
        preview = if (g.isNull("preview")) null else g.optString("preview"),
        hora = if (g.isNull("hora")) null else g.optString("hora"),
        nuevo = g.optBoolean("nuevo"),
        borrador = g.optBoolean("borrador"),
    )

    suspend fun cargar()
    {
        error = false
        try
        {
            val lista = withContext(Dispatchers.IO) {
                val data = app.api.misGrupos() as JSONArray
                val vistos = GrupoVisto(app.estado).leerVistos()
                val miId = app.boveda.leer(ClavesSeguras.MI_ID)
                val priv = app.boveda.leer(ClavesSeguras.CLAVE_PRIVADA) ?: ""
                val salida = ArrayList<Grupo>()
                for (i in 0 until data.length())
                {
                    val g = data.getJSONObject(i)
                    val ultimo = g.optJSONObject("ultimo")
                    if (ultimo == null)
                    {
                        salida.add(Grupo(g.getString("id"), g.optString("nombre"), g.textoO("avatar_url"), g.optInt("miembros"), null, null, false))
                        continue
                    }
                    val claro = Cripto.descifrarTexto(ultimo.getString("contenido_cifrado"), ultimo.getString("nonce"), ultimo.textoO("llave_publica"), priv)
                    val mio = ultimo.optString("remitente_id") == miId
                    val quien = if (mio) "Tú" else ultimo.optString("remitente")
                    val visto = vistos[g.getString("id")]
                    val enviadoEn = ultimo.optString("enviado_en")
                    val cuerpo = previewDeGrupo(claro)
                    salida.add(Grupo(
                        id = g.getString("id"),
                        nombre = g.optString("nombre"),
                        avatarUrl = g.textoO("avatar_url"),
                        miembros = g.optInt("miembros"),
                        preview = "$quien: $cuerpo",
                        hora = enviadoEn,
                        nuevo = !mio && (visto == null || enviadoEn > visto),
                    ))
                }
                val conBorradores = salida.map { g ->
                    val b = app.borradores.leer("grupo-${g.id}")
                    val textoBorrador = b.optString("texto")
                    if (textoBorrador.isNotEmpty())
                    {
                        g.copy(preview = "Borrador: $textoBorrador", borrador = true)
                    }
                    else
                    {
                        g
                    }
                }
                val cache = JSONArray()
                for (g in conBorradores)
                {
                    cache.put(grupoAJson(g))
                }
                app.estado.escribir("vixxer_lista_grupos", cache.toString())
                conBorradores
            }
            grupos = lista
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
        val cache = withContext(Dispatchers.IO) { app.estado.leer("vixxer_lista_grupos") }
        if (cache != null)
        {
            runCatching {
                val datos = JSONArray(cache)
                grupos = (0 until datos.length()).map { grupoDeJson(datos.getJSONObject(it)) }
                cargando = false
            }
        }
        cargar()
    }

    DisposableEffect(Unit) {
        val socket = ConexionSocket.obtener()
        val alCambio = Emitter.Listener { programarCarga() }
        val alEscribiendo = Emitter.Listener { args ->
            val data = args.getOrNull(0) as? JSONObject
            val grupo = data?.optString("grupo").orEmpty()
            if (grupo.isNotEmpty())
            {
                alcance.launch {
                    trabajos[grupo]?.cancel()
                    if (data!!.optBoolean("activo"))
                    {
                        escribiendo = escribiendo + (grupo to true)
                        trabajos[grupo] = launch {
                            delay(3000)
                            escribiendo = escribiendo + (grupo to false)
                        }
                    }
                    else
                    {
                        escribiendo = escribiendo + (grupo to false)
                    }
                }
            }
        }
        socket?.on("grupo:nuevo", alCambio)
        socket?.on("grupo:mensaje", alCambio)
        socket?.on("grupo:actualizado", alCambio)
        socket?.on("grupo:escribiendo", alEscribiendo)
        onDispose {
            socket?.off("grupo:nuevo", alCambio)
            socket?.off("grupo:mensaje", alCambio)
            socket?.off("grupo:actualizado", alCambio)
            socket?.off("grupo:escribiendo", alEscribiendo)
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
            Row(
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 28.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            )
            {
                Text("Grupos", fontSize = 18.sp, fontFamily = FuenteOutfit, fontWeight = FontWeight.SemiBold, color = colores.texto)
                Box(
                    modifier = Modifier
                        .background(colores.botonFondo, RoundedCornerShape(20.dp))
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { alNavegar("grupo-crear") }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
                {
                    Text("+ Nuevo", fontSize = 13.sp, fontFamily = FuenteOutfit, fontWeight = FontWeight.SemiBold, color = colores.botonTexto)
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
                    if (grupos.isEmpty())
                    {
                        item {
                            EstadoLista(
                                cargando = cargando,
                                error = error,
                                vacio = "Aún no tienes grupos. Crea uno con el botón de arriba.",
                                alReintentar = {
                                    cargando = true
                                    alcance.launch { cargar() }
                                },
                            )
                        }
                    }
                    items(grupos, key = { it.id }) { item ->
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() },
                                        onClick = { alNavegar("grupo/${item.id}") },
                                        onLongClick = { sel = item },
                                    )
                                    .padding(vertical = 12.dp, horizontal = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            )
                            {
                                if (item.avatarUrl.isNotEmpty())
                                {
                                    Avatar(nombre = item.nombre, uri = item.avatarUrl, tamano = 44.dp)
                                }
                                else
                                {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .background(colores.surface, CircleShape)
                                            .border(Vidrio.anchoBorde, colores.borde, CircleShape),
                                        contentAlignment = Alignment.Center,
                                    )
                                    {
                                        IconoGrupos(color = colores.muted, tamano = 20.dp)
                                    }
                                }
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(item.nombre, fontSize = 16.sp, color = colores.texto, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (escribiendo[item.id] == true)
                                    {
                                        Text("escribiendo…", fontSize = 13.sp, color = colores.botonFondo, maxLines = 1)
                                    }
                                    else
                                    {
                                        Text(
                                            item.preview ?: "${item.miembros} miembros",
                                            fontSize = 13.sp,
                                            color = if (item.borrador) colores.error else colores.muted,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (item.hora != null)
                                    {
                                        Text(cuandoGrupo(item.hora), fontSize = 11.sp, color = colores.muted)
                                    }
                                    if (item.nuevo)
                                    {
                                        Box(modifier = Modifier.size(10.dp).background(colores.botonFondo, CircleShape))
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

        val grupoSel = sel
        if (grupoSel != null && !confirmarSalir)
        {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { sel = null },
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
                        grupoSel.nombre.uppercase(),
                        fontSize = 12.sp,
                        fontFamily = FuenteOutfit,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp,
                        color = colores.muted,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                    Text(
                        "Ver info y miembros",
                        fontSize = 16.sp,
                        color = colores.texto,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                                val g = grupoSel
                                sel = null
                                alNavegar("grupo-info/${g.id}")
                            }
                            .padding(vertical = 14.dp, horizontal = 24.dp),
                    )
                    Text(
                        "Salir del grupo",
                        fontSize = 16.sp,
                        color = colores.error,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { confirmarSalir = true }
                            .padding(vertical = 14.dp, horizontal = 24.dp),
                    )
                }
            }
        }
    }

    Confirmacion(
        visible = confirmarSalir,
        titulo = "Salir del grupo",
        mensaje = "Dejarás de recibir sus mensajes. Podrán volver a agregarte más adelante.",
        textoConfirmar = "Salir",
        destructivo = true,
        alConfirmar = {
            val grupo = sel
            confirmarSalir = false
            sel = null
            if (grupo != null)
            {
                alcance.launch {
                    withContext(Dispatchers.IO) { runCatching { app.api.salirGrupo(grupo.id) } }
                    cargar()
                }
            }
        },
        alCancelar = {
            confirmarSalir = false
            sel = null
        },
    )
}

private fun previewDeGrupo(texto: String?): String
{
    if (texto == null)
    {
        return "Mensaje cifrado"
    }
    if (texto.startsWith("{"))
    {
        return when
        {
            texto.contains("\"t\":\"img\"") -> "Foto"
            texto.contains("\"t\":\"video\"") -> "Video"
            texto.contains("\"t\":\"audio\"") -> "Audio"
            texto.contains("\"t\":\"sticker\"") -> "Sticker"
            texto.contains("\"t\":\"file\"") -> "Documento"
            else -> texto
        }
    }
    return texto
}

private fun cuandoGrupo(iso: String): String
{
    if (iso.isEmpty())
    {
        return ""
    }
    val zona = java.time.ZoneId.systemDefault()
    return if (dev.vixxer.mensajero.nucleo.Fechas.mismoDia(iso, null, zona))
    {
        dev.vixxer.mensajero.nucleo.Fechas.hora(iso, zona)
    }
    else
    {
        java.text.DateFormat.getDateInstance(java.text.DateFormat.SHORT)
            .format(java.util.Date.from(dev.vixxer.mensajero.nucleo.Fechas.aInstante(iso)))
    }
}

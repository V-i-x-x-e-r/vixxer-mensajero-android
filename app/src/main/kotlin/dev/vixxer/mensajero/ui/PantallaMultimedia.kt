package dev.vixxer.mensajero.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import dev.vixxer.mensajero.AplicacionVixxer
import dev.vixxer.mensajero.nucleo.ClavesSeguras
import dev.vixxer.mensajero.nucleo.Cripto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File

@Composable
fun PantallaMultimedia(app: AplicacionVixxer, amigoId: String, alVolver: () -> Unit)
{
    val colores = LocalTema.current.colores
    val contexto = LocalContext.current
    var items by remember { mutableStateOf(listOf<Pair<String, MediaMensaje>>()) }
    var cargando by remember { mutableStateOf(true) }
    var hayMas by remember { mutableStateOf(true) }
    var filtro by remember { mutableStateOf("todo") }
    var visor by remember { mutableStateOf<File?>(null) }
    var visorVideo by remember { mutableStateOf<MediaMensaje?>(null) }
    val corte = remember { arrayOf<String?>(null) }
    val ocupado = remember { arrayOf(false) }
    val estadoGrid = rememberLazyGridState()

    suspend fun cargarPagina()
    {
        if (ocupado[0] || !hayMas)
        {
            return
        }
        ocupado[0] = true
        val nuevos = withContext(Dispatchers.IO) {
            runCatching {
                val priv = app.boveda.leer(ClavesSeguras.CLAVE_PRIVADA) ?: return@runCatching emptyList<Pair<String, MediaMensaje>>()
                val pub = app.llaves.llavePublicaDe(amigoId)
                val filas = app.api.historial(amigoId, corte[0]) as JSONArray
                if (filas.length() < 50)
                {
                    hayMas = false
                }
                if (filas.length() > 0)
                {
                    corte[0] = filas.getJSONObject(0).optString("enviado_en")
                }
                val salida = ArrayList<Pair<String, MediaMensaje>>()
                for (i in 0 until filas.length())
                {
                    val f = filas.getJSONObject(i)
                    if (f.optString("contenido_cifrado") == "BORRADO")
                    {
                        continue
                    }
                    val claro = Cripto.descifrarTexto(f.getString("contenido_cifrado"), f.getString("nonce"), pub, priv)
                    val m = leerMedia(claro)
                    if (m != null && (m.t == "img" || m.t == "video" || m.t == "file"))
                    {
                        salida.add(f.optString("id") to m)
                    }
                }
                salida.reversed()
            }.getOrDefault(emptyList())
        }
        items = items + nuevos
        cargando = false
        ocupado[0] = false
    }

    LaunchedEffect(Unit) { cargarPagina() }

    val filtrados = when (filtro)
    {
        "img" -> items.filter { it.second.t == "img" }
        "video" -> items.filter { it.second.t == "video" }
        "docs" -> items.filter { it.second.t == "file" }
        else -> items
    }
    val esDocs = filtro == "docs"

    LaunchedEffect(estadoGrid, filtrados.size) {
        snapshotFlow { estadoGrid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.collect { ultimo ->
            if (hayMas && !ocupado[0] && ultimo >= filtrados.size - 4)
            {
                cargarPagina()
            }
        }
    }

    BackHandler(enabled = visor != null || visorVideo != null) {
        visor = null
        visorVideo = null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colores.fondo)
            .statusBarsPadding(),
    )
    {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        )
        {
            Text(
                "‹",
                fontSize = 26.sp,
                color = colores.texto,
                modifier = Modifier.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { alVolver() },
            )
            Text("Multimedia", fontSize = 18.sp, fontFamily = FuenteOutfit, fontWeight = FontWeight.SemiBold, color = colores.texto)
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        )
        {
            for ((clave, etiqueta) in listOf("todo" to "Todo", "img" to "Fotos", "video" to "Videos", "docs" to "Docs"))
            {
                val activo = filtro == clave
                Text(
                    etiqueta,
                    fontSize = 13.sp,
                    fontFamily = FuenteOutfit,
                    color = if (activo) colores.botonTexto else colores.texto,
                    modifier = Modifier
                        .background(if (activo) colores.botonFondo else Color.Transparent, RoundedCornerShape(Vidrio.radioPildora))
                        .border(Vidrio.anchoBorde, colores.borde, RoundedCornerShape(Vidrio.radioPildora))
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { filtro = clave }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }
        }

        LazyVerticalGrid(
            state = estadoGrid,
            columns = GridCells.Fixed(if (esDocs) 1 else 3),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        )
        {
            items(filtrados, key = { it.first }) { par ->
                val m = par.second
                when (m.t)
                {
                    "file" -> AdjuntoArchivo(app = app, media = m, mio = false, colores = colores)
                    "video" -> CeldaVideo(prev = m.prev, colores = colores) { visorVideo = m }
                    else -> CeldaImagen(app = app, media = m, colores = colores) { visor = it }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center,
                )
                {
                    if (cargando)
                    {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), color = colores.muted, strokeWidth = 2.dp)
                    }
                    else if (filtrados.isEmpty())
                    {
                        Text("Aún no hay nada aquí.", fontSize = 14.sp, color = colores.muted, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }

    VisorImagen(archivo = visor) { visor = null }
    VisorVideo(app = app, media = visorVideo, alCerrar = { visorVideo = null })
}

@Composable
private fun CeldaImagen(app: AplicacionVixxer, media: MediaMensaje, colores: Paleta, alAbrir: (File) -> Unit)
{
    val contexto = LocalContext.current
    var archivo by remember(media.path) { mutableStateOf<File?>(null) }
    LaunchedEffect(media.path) {
        archivo = withContext(Dispatchers.IO) { CacheMedia.obtener(contexto, app, media) }
    }
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(4.dp))
            .background(colores.surface)
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { archivo?.let(alAbrir) },
    )
    {
        val actual = archivo
        if (actual != null)
        {
            AsyncImage(
                model = actual,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun CeldaVideo(prev: String?, colores: Paleta, alAbrir: () -> Unit)
{
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Black)
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { alAbrir() },
        contentAlignment = Alignment.Center,
    )
    {
        if (prev != null)
        {
            AsyncImage(
                model = prev,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            modifier = Modifier.size(36.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center,
        )
        {
            Reproducir(color = Color.White, tamano = 18.dp)
        }
    }
}

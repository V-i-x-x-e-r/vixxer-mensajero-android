package dev.vixxer.mensajero.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import dev.vixxer.mensajero.AplicacionVixxer
import dev.vixxer.mensajero.nucleo.Medios
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URL
import java.security.MessageDigest
import org.json.JSONObject
import android.content.ContentValues
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding

data class MediaMensaje(
    val t: String,
    val path: String,
    val mime: String,
    val k: String,
    val n: String,
    val w: Int,
    val h: Int,
    val cap: String?,
    val nombre: String?,
    val peso: Long,
    val dur: Int,
    val prev: String?,
    val wf: List<Float>?,
)

fun leerMedia(texto: String?): MediaMensaje?
{
    if (texto == null || !texto.startsWith("{"))
    {
        return null
    }
    val obj = runCatching { JSONObject(texto) }.getOrNull() ?: return null
    val t = obj.optString("t")
    if (t !in listOf("img", "video", "audio", "file", "sticker"))
    {
        return null
    }
    if (obj.textoO("path").isEmpty())
    {
        return null
    }
    return MediaMensaje(
        t = t,
        path = obj.getString("path"),
        mime = obj.textoO("mime"),
        k = obj.textoO("k"),
        n = obj.textoO("n"),
        w = obj.optInt("w"),
        h = obj.optInt("h"),
        cap = obj.textoO("cap").ifEmpty { null },
        nombre = obj.textoO("nombre").ifEmpty { null },
        peso = obj.optLong("peso"),
        dur = obj.optInt("dur"),
        prev = obj.textoO("prev").ifEmpty { null },
        wf = obj.optJSONArray("wf")?.let { arreglo ->
            (0 until arreglo.length()).map { arreglo.optDouble(it, 0.0).toFloat() }
        },
    )
}

object CacheMedia
{
    private fun carpeta(contexto: Context): File
    {
        val dir = File(contexto.cacheDir, "media")
        dir.mkdirs()
        return dir
    }

    fun archivoDe(contexto: Context, path: String): File
    {
        val hash = MessageDigest.getInstance("SHA-1").digest(path.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(carpeta(contexto), hash)
    }

    fun guardar(contexto: Context, path: String, bytes: ByteArray)
    {
        runCatching { archivoDe(contexto, path).writeBytes(bytes) }
    }

    fun obtener(contexto: Context, app: AplicacionVixxer, media: MediaMensaje): File?
    {
        val destino = archivoDe(contexto, media.path)
        if (destino.exists() && destino.length() > 0)
        {
            return destino
        }
        return runCatching {
            val respuesta = app.api.urlMedia(media.path) as JSONObject
            val url = respuesta.getString("url")
            val temporal = File(destino.parentFile, "${destino.name}.tmp")
            URL(url).openStream().use { entrada ->
                temporal.outputStream().buffered().use { salida ->
                    if (!Medios.descifrarFlujo(entrada, media.k, media.n, salida))
                    {
                        temporal.delete()
                        return@runCatching null
                    }
                }
            }
            temporal.renameTo(destino)
            destino
        }.getOrNull()
    }
}

data class ImagenLista(val bytes: ByteArray, val ancho: Int, val alto: Int)

fun comprimirImagen(contexto: Context, uri: Uri): ImagenLista?
{
    return runCatching {
        val bytes = contexto.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeStream(ByteArrayInputStream(bytes), null, limites)
        var muestra = 1
        val mayor = maxOf(limites.outWidth, limites.outHeight)
        while (mayor / muestra > 1920 * 2)
        {
            muestra *= 2
        }
        val opciones = BitmapFactory.Options().apply { inSampleSize = muestra }
        var mapa = BitmapFactory.decodeStream(ByteArrayInputStream(bytes), null, opciones) ?: return null
        val maxLado = maxOf(mapa.width, mapa.height)
        if (maxLado > 1920)
        {
            val factor = 1920f / maxLado
            mapa = Bitmap.createScaledBitmap(mapa, (mapa.width * factor).toInt(), (mapa.height * factor).toInt(), true)
        }
        val salida = ByteArrayOutputStream()
        mapa.compress(Bitmap.CompressFormat.JPEG, 82, salida)
        ImagenLista(salida.toByteArray(), mapa.width, mapa.height)
    }.getOrNull()
}

@Composable
fun AdjuntoImagen(app: AplicacionVixxer, media: MediaMensaje, colores: Paleta, alAbrir: (File) -> Unit, alMantener: () -> Unit = {})
{
    val contexto = androidx.compose.ui.platform.LocalContext.current
    var archivo by remember(media.path) { mutableStateOf<File?>(null) }
    var fallo by remember(media.path) { mutableStateOf(false) }

    LaunchedEffect(media.path) {
        val listo = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            CacheMedia.obtener(contexto, app, media)
        }
        if (listo != null)
        {
            archivo = listo
        }
        else
        {
            fallo = true
        }
    }

    val proporcion = if (media.w > 0 && media.h > 0) media.w.toFloat() / media.h.toFloat() else 1f
    Box(
        modifier = Modifier
            .widthIn(min = 200.dp, max = 260.dp)
            .heightIn(max = 340.dp)
            .aspectRatio(proporcion.coerceIn(0.5f, 2.2f))
            .clip(RoundedCornerShape(16.dp))
            .background(colores.surface),
        contentAlignment = Alignment.Center,
    )
    {
        val listo = archivo
        when
        {
            listo != null -> AsyncImage(
                model = listo,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .combinedClickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = { alAbrir(listo) },
                        onLongClick = { alMantener() },
                    ),
            )
            fallo -> Text("No se pudo cargar", fontSize = 12.sp, color = colores.muted)
            else -> CircularProgressIndicator(modifier = Modifier.size(22.dp), color = colores.muted, strokeWidth = 2.dp)
        }
    }
}

@Composable
fun VisorImagen(archivo: File?, alCerrar: () -> Unit)
{
    if (archivo == null)
    {
        return
    }
    var escala by remember { mutableStateOf(1f) }
    var despX by remember { mutableStateOf(0f) }
    var despY by remember { mutableStateOf(0f) }
    var medida by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val transformar = rememberTransformableState { zoom, arrastre, _ ->
        escala = (escala * zoom).coerceIn(1f, 5f)
        val topeX = (escala - 1f) * medida.width / 2f
        val topeY = (escala - 1f) * medida.height / 2f
        despX = (despX + arrastre.x * escala).coerceIn(-topeX, topeX)
        despY = (despY + arrastre.y * escala).coerceIn(-topeY, topeY)
    }
    androidx.activity.compose.BackHandler { alCerrar() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.96f))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { alCerrar() }
            .transformable(transformar)
            .onSizeChanged { medida = it },
        contentAlignment = Alignment.Center,
    )
    {
        AsyncImage(
            model = archivo,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = escala
                    scaleY = escala
                    translationX = despX
                    translationY = despY
                },
        )
    }
}


data class ArchivoElegido(val nombre: String, val peso: Long, val mime: String, val bytes: ByteArray)

fun leerArchivo(contexto: Context, uri: Uri): ArchivoElegido?
{
    return runCatching {
        var nombre = "archivo"
        var peso = 0L
        contexto.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst())
            {
                val iNombre = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val iPeso = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (iNombre >= 0)
                {
                    nombre = cursor.getString(iNombre) ?: nombre
                }
                if (iPeso >= 0)
                {
                    peso = cursor.getLong(iPeso)
                }
            }
        }
        val mime = contexto.contentResolver.getType(uri) ?: "application/octet-stream"
        val bytes = contexto.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        ArchivoElegido(nombre, if (peso > 0) peso else bytes.size.toLong(), mime, bytes)
    }.getOrNull()
}

fun guardarEnDescargas(contexto: Context, archivo: File, nombre: String, mime: String): Boolean
{
    return runCatching {
        if (Build.VERSION.SDK_INT >= 29)
        {
            val valores = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, nombre)
                put(MediaStore.Downloads.MIME_TYPE, mime)
            }
            val destino = contexto.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, valores)
                ?: return false
            contexto.contentResolver.openOutputStream(destino)?.use { salida ->
                archivo.inputStream().use { it.copyTo(salida) }
            } ?: return false
            true
        }
        else
        {
            val carpeta = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            carpeta.mkdirs()
            archivo.copyTo(File(carpeta, nombre), overwrite = true)
            true
        }
    }.getOrDefault(false)
}

private fun pesoLegible(peso: Long): String
{
    if (peso <= 0)
    {
        return ""
    }
    if (peso < 1024 * 1024)
    {
        return "%.0f KB".format(peso / 1024f)
    }
    return "%.1f MB".format(peso / (1024f * 1024f))
}

@Composable
fun AdjuntoArchivo(app: AplicacionVixxer, media: MediaMensaje, mio: Boolean, colores: Paleta)
{
    val contexto = androidx.compose.ui.platform.LocalContext.current
    var estado by remember(media.path) { mutableStateOf("") }
    val colorTexto = if (mio) colores.botonTexto else colores.texto

    Row(
        modifier = Modifier
            .widthIn(max = 260.dp)
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                if (estado.isEmpty())
                {
                    estado = "descargando"
                }
            },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    )
    {
        Documento(color = colorTexto, tamano = 26.dp)
        Column {
            Text(
                media.nombre ?: "Documento",
                fontSize = 14.sp,
                color = colorTexto,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Text(
                when (estado)
                {
                    "descargando" -> "descargando…"
                    "listo" -> "guardado en Descargas"
                    "fallo" -> "no se pudo descargar"
                    else -> pesoLegible(media.peso).ifEmpty { "tocar para descargar" }
                },
                fontSize = 11.sp,
                color = colorTexto.copy(alpha = 0.7f),
            )
        }
    }

    LaunchedEffect(estado) {
        if (estado == "descargando")
        {
            val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val archivo = CacheMedia.obtener(contexto, app, media) ?: return@withContext false
                guardarEnDescargas(contexto, archivo, media.nombre ?: "documento", media.mime.ifEmpty { "application/octet-stream" })
            }
            estado = if (ok) "listo" else "fallo"
        }
    }
}


fun miniaturaVideo(contexto: Context, uri: Uri): Triple<String?, Pair<Int, Int>, Int>
{
    return runCatching {
        val lector = android.media.MediaMetadataRetriever()
        lector.setDataSource(contexto, uri)
        val dur = (lector.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) / 1000
        val cuadro = lector.getFrameAtTime(0)
        lector.release()
        if (cuadro == null)
        {
            return Triple(null, Pair(0, 0), dur.toInt())
        }
        val maxLado = maxOf(cuadro.width, cuadro.height)
        val factor = if (maxLado > 480) 480f / maxLado else 1f
        val chico = Bitmap.createScaledBitmap(cuadro, (cuadro.width * factor).toInt(), (cuadro.height * factor).toInt(), true)
        val salida = ByteArrayOutputStream()
        chico.compress(Bitmap.CompressFormat.JPEG, 60, salida)
        val b64 = android.util.Base64.encodeToString(salida.toByteArray(), android.util.Base64.NO_WRAP)
        Triple("data:image/jpeg;base64,$b64", Pair(cuadro.width, cuadro.height), dur.toInt())
    }.getOrDefault(Triple(null, Pair(0, 0), 0))
}

private fun duracionLegible(segundos: Int): String
{
    val m = segundos / 60
    val s = segundos % 60
    return "%d:%02d".format(m, s)
}

@Composable
fun AdjuntoVideo(media: MediaMensaje, colores: Paleta, alReproducir: () -> Unit, alMantener: () -> Unit = {})
{
    val proporcion = if (media.w > 0 && media.h > 0) media.w.toFloat() / media.h.toFloat() else 16f / 9f
    Box(
        modifier = Modifier
            .widthIn(max = 260.dp)
            .heightIn(max = 340.dp)
            .aspectRatio(proporcion.coerceIn(0.5f, 2.2f))
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black)
            .combinedClickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = { alReproducir() },
                onLongClick = { alMantener() },
            ),
        contentAlignment = Alignment.Center,
    )
    {
        if (media.prev != null)
        {
            AsyncImage(
                model = media.prev,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(Color.Black.copy(alpha = 0.55f), androidx.compose.foundation.shape.CircleShape),
            contentAlignment = Alignment.Center,
        )
        {
            Reproducir(color = Color.White, tamano = 24.dp)
        }
        if (media.dur > 0)
        {
            Text(
                duracionLegible(media.dur),
                fontSize = 11.sp,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
fun VisorVideo(app: AplicacionVixxer, media: MediaMensaje?, alCerrar: () -> Unit)
{
    if (media == null)
    {
        return
    }
    val contexto = androidx.compose.ui.platform.LocalContext.current
    var archivo by remember(media.path) { mutableStateOf<File?>(null) }
    var fallo by remember(media.path) { mutableStateOf(false) }

    androidx.activity.compose.BackHandler { alCerrar() }

    LaunchedEffect(media.path) {
        val listo = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            CacheMedia.obtener(contexto, app, media)
        }
        if (listo != null)
        {
            archivo = listo
        }
        else
        {
            fallo = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.97f)),
        contentAlignment = Alignment.Center,
    )
    {
        val listo = archivo
        when
        {
            listo != null ->
            {
                val reproductor = remember(listo) {
                    androidx.media3.exoplayer.ExoPlayer.Builder(contexto).build().apply {
                        setMediaItem(androidx.media3.common.MediaItem.fromUri(Uri.fromFile(listo)))
                        prepare()
                        playWhenReady = true
                    }
                }
                androidx.compose.runtime.DisposableEffect(listo) {
                    onDispose { reproductor.release() }
                }
                androidx.compose.ui.viewinterop.AndroidView(
                    factory = { c ->
                        androidx.media3.ui.PlayerView(c).apply {
                            player = reproductor
                            setShowNextButton(false)
                            setShowPreviousButton(false)
                            setShowFastForwardButton(false)
                            setShowRewindButton(false)
                            setShowSubtitleButton(false)
                            setShowShuffleButton(false)
                            findViewById<android.view.View>(androidx.media3.ui.R.id.exo_settings)?.visibility = android.view.View.GONE
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            fallo -> Text("No se pudo cargar el video", fontSize = 13.sp, color = Color.White)
            else -> CircularProgressIndicator(modifier = Modifier.size(26.dp), color = Color.White, strokeWidth = 2.dp)
        }
        Text(
            "✕",
            fontSize = 24.sp,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { alCerrar() },
        )
    }
}

@Composable
fun AdjuntoAudio(app: AplicacionVixxer, media: MediaMensaje, mio: Boolean, colores: Paleta)
{
    val contexto = androidx.compose.ui.platform.LocalContext.current
    var reproduciendo by remember(media.path) { mutableStateOf(false) }
    var cargando by remember(media.path) { mutableStateOf(false) }
    val reproductor = remember(media.path) { arrayOf<android.media.MediaPlayer?>(null) }
    val colorTexto = if (mio) colores.botonTexto else colores.texto

    androidx.compose.runtime.DisposableEffect(media.path) {
        onDispose {
            reproductor[0]?.release()
            reproductor[0] = null
        }
    }

    fun alternar()
    {
        val actual = reproductor[0]
        if (actual != null)
        {
            if (actual.isPlaying)
            {
                actual.pause()
                reproduciendo = false
            }
            else
            {
                actual.start()
                reproduciendo = true
            }
            return
        }
        cargando = true
    }

    LaunchedEffect(cargando) {
        if (cargando)
        {
            val archivo = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                CacheMedia.obtener(contexto, app, media)
            }
            cargando = false
            if (archivo != null)
            {
                val mp = android.media.MediaPlayer()
                mp.setDataSource(archivo.absolutePath)
                mp.prepare()
                mp.setOnCompletionListener {
                    reproduciendo = false
                    it.seekTo(0)
                }
                reproductor[0] = mp
                mp.start()
                reproduciendo = true
            }
        }
    }

    Row(
        modifier = Modifier.widthIn(max = 240.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    )
    {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(colorTexto.copy(alpha = 0.15f), androidx.compose.foundation.shape.CircleShape)
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { alternar() },
            contentAlignment = Alignment.Center,
        )
        {
            when
            {
                cargando -> CircularProgressIndicator(modifier = Modifier.size(16.dp), color = colorTexto, strokeWidth = 2.dp)
                reproduciendo -> Pausa(color = colorTexto, tamano = 18.dp)
                else -> Reproducir(color = colorTexto, tamano = 18.dp)
            }
        }
        val barras = media.wf ?: List(28) { 0.4f }
        androidx.compose.foundation.Canvas(modifier = Modifier.size(width = 130.dp, height = 28.dp)) {
            val n = barras.size
            val paso = size.width / n
            for (i in 0 until n)
            {
                val alto = (barras[i].coerceIn(0.08f, 1f)) * size.height
                drawRoundRect(
                    color = colorTexto.copy(alpha = 0.85f),
                    topLeft = androidx.compose.ui.geometry.Offset(i * paso + paso * 0.2f, (size.height - alto) / 2f),
                    size = androidx.compose.ui.geometry.Size(paso * 0.6f, alto),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f),
                )
            }
        }
        if (media.dur > 0)
        {
            Text(duracionLegible(media.dur), fontSize = 11.sp, color = colorTexto.copy(alpha = 0.8f))
        }
    }
}

class Grabadora(private val contexto: Context)
{
    private var grabador: android.media.MediaRecorder? = null
    private var archivo: File? = null
    var pausada = false
        private set
    val muestras = ArrayList<Int>()

    fun pausar()
    {
        runCatching {
            grabador?.pause()
            pausada = true
        }
    }

    fun continuar()
    {
        runCatching {
            grabador?.resume()
            pausada = false
        }
    }

    fun iniciar(): Boolean
    {
        return runCatching {
            val destino = File(contexto.cacheDir, "nota-${System.currentTimeMillis()}.m4a")
            val mr = android.media.MediaRecorder()
            mr.setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
            mr.setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4)
            mr.setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC)
            mr.setAudioEncodingBitRate(96000)
            mr.setAudioSamplingRate(44100)
            mr.setOutputFile(destino.absolutePath)
            mr.prepare()
            mr.start()
            grabador = mr
            archivo = destino
            muestras.clear()
            true
        }.getOrDefault(false)
    }

    fun muestrear()
    {
        if (pausada)
        {
            return
        }
        val amplitud = runCatching { grabador?.maxAmplitude ?: 0 }.getOrDefault(0)
        muestras.add(amplitud)
    }

    fun terminar(): File?
    {
        val destino = archivo
        runCatching {
            grabador?.stop()
            grabador?.release()
        }
        grabador = null
        archivo = null
        return destino
    }

    fun ondas(n: Int = 32): List<Float>
    {
        if (muestras.isEmpty())
        {
            return List(n) { 0.08f }
        }
        val paso = muestras.size.toFloat() / n
        val crudos = (0 until n).map { i ->
            val inicio = (i * paso).toInt()
            val fin = maxOf(((i + 1) * paso).toInt(), inicio + 1).coerceAtMost(muestras.size)
            muestras.subList(inicio, fin).max().toFloat()
        }
        val tope = crudos.max().coerceAtLeast(1f)
        return crudos.map { (it / tope).coerceIn(0.08f, 1f) }
    }
}

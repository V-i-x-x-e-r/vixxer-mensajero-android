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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.border
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import dev.vixxer.mensajero.AplicacionVixxer
import dev.vixxer.mensajero.nucleo.ErrorApi
import dev.vixxer.mensajero.nucleo.Medios
import dev.vixxer.mensajero.nucleo.RedMedia
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import android.content.ContentValues
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
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
    val pesoConocido: Boolean,
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
        pesoConocido = obj.has("peso") && !obj.isNull("peso"),
        dur = obj.optInt("dur"),
        prev = obj.textoO("prev").ifEmpty { null },
        wf = obj.optJSONArray("wf")?.let { arreglo ->
            (0 until arreglo.length()).map { arreglo.optDouble(it, 0.0).toFloat() }
        },
    )
}

object CacheMedia
{
    private const val LIMITE = 150L * 1024 * 1024
    private const val LIMITE_CIFRADO = 65L * 1024 * 1024
    private const val TIMEOUT_CONEXION_MS = 12_000
    private const val TIMEOUT_LECTURA_MS = 30_000
    private data class Candado(val mutex: Mutex = Mutex(), var usuarios: Int = 0)
    private val monitorCandados = Any()
    private val candados = HashMap<String, Candado>()
    private var generacion = 0L

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

    fun guardar(contexto: Context, path: String, pesoEsperado: Long, abrir: () -> InputStream?)
    {
        if (pesoEsperado !in 0..Medios.LIMITE_DESCIFRADO) return
        val generacionInicial = generacionActual()
        val destino = archivoDe(contexto, path)
        val temporal = runCatching { File.createTempFile("${destino.name}-", ".tmp", destino.parentFile) }.getOrNull()
            ?: return
        try
        {
            val copiados = abrir()?.buffered()?.use { entrada ->
                temporal.outputStream().buffered().use { salida -> copiarLimitado(entrada, salida, pesoEsperado) }
            } ?: return
            if (copiados != pesoEsperado || !moverSiVigente(temporal, destino, generacionInicial)) return
            podar(contexto)
        }
        catch (_: Exception)
        {
            return
        }
        finally
        {
            temporal.delete()
        }
    }

    suspend fun obtener(contexto: Context, app: AplicacionVixxer, media: MediaMensaje): File?
    {
        val candado = synchronized(monitorCandados) {
            candados.getOrPut(media.path) { Candado() }.also { it.usuarios += 1 }
        }
        return try
        {
            candado.mutex.withLock { obtenerSinDuplicar(contexto, app, media) }
        }
        finally
        {
            synchronized(monitorCandados) {
                candado.usuarios -= 1
                if (candado.usuarios == 0) candados.remove(media.path, candado)
            }
        }
    }

    private suspend fun obtenerSinDuplicar(contexto: Context, app: AplicacionVixxer, media: MediaMensaje): File?
    {
        val generacionInicial = generacionActual()
        val destino = archivoDe(contexto, media.path)
        if (destino.isFile && if (media.pesoConocido) destino.length() == media.peso else destino.length() > 0)
        {
            destino.setLastModified(System.currentTimeMillis())
            return destino
        }
        if (destino.exists()) destino.delete()
        val contextoCorrutina = currentCoroutineContext()
        var conexion: HttpURLConnection? = null
        var temporal: File? = null
        var alCancelar: kotlinx.coroutines.DisposableHandle? = null
        try
        {
            val respuesta = app.api.urlMedia(media.path) as JSONObject
            contextoCorrutina.ensureActive()
            val url = URL(respuesta.getString("url"))
            if (url.protocol !in setOf("http", "https")) return null
            temporal = File.createTempFile("${destino.name}-", ".tmp", destino.parentFile)
            conexion = url.openConnection() as? HttpURLConnection ?: return null
            conexion.connectTimeout = TIMEOUT_CONEXION_MS
            conexion.readTimeout = TIMEOUT_LECTURA_MS
            conexion.instanceFollowRedirects = true
            conexion.setRequestProperty("Accept-Encoding", "identity")
            val conexionActiva = conexion
            alCancelar = contextoCorrutina[Job]?.invokeOnCompletion { causa ->
                if (causa is CancellationException) conexionActiva.disconnect()
            }
            val codigo = conexion.responseCode
            if (codigo !in 200..299) return null
            val anunciada = conexion.contentLengthLong
            if (anunciada > LIMITE_CIFRADO) return null
            val esperado = media.peso.takeIf { media.pesoConocido }
            conexion.inputStream.buffered().use { cruda ->
                val entrada = EntradaLimitada(cruda, LIMITE_CIFRADO) { contextoCorrutina.ensureActive() }
                temporal.outputStream().buffered().use { salida ->
                    if (!Medios.descifrarFlujo(entrada, media.k, media.n, salida, esperado))
                    {
                        return null
                    }
                }
            }
            contextoCorrutina.ensureActive()
            if (!moverSiVigente(temporal, destino, generacionInicial)) return null
            podar(contexto)
            return destino
        }
        catch (e: CancellationException)
        {
            throw e
        }
        catch (_: Exception)
        {
            return null
        }
        finally
        {
            alCancelar?.dispose()
            conexion?.disconnect()
            temporal?.delete()
        }
    }

    fun limpiar(contexto: Context)
    {
        synchronized(monitorCandados) {
            generacion += 1
            carpeta(contexto).listFiles()?.forEach { it.deleteRecursively() }
        }
    }

    private fun generacionActual(): Long = synchronized(monitorCandados) { generacion }

    private fun moverSiVigente(origen: File, destino: File, generacionInicial: Long): Boolean
    {
        return synchronized(monitorCandados) {
            generacion == generacionInicial && moverVerificado(origen, destino)
        }
    }

    private fun copiarLimitado(entrada: InputStream, salida: java.io.OutputStream, limite: Long): Long
    {
        val bufer = ByteArray(64 * 1024)
        var total = 0L
        while (true)
        {
            val leidos = entrada.read(bufer)
            if (leidos < 0) break
            total += leidos
            if (total > limite) throw IOException("El archivo cambio de tamano")
            salida.write(bufer, 0, leidos)
        }
        return total
    }

    private fun moverVerificado(origen: File, destino: File): Boolean
    {
        val peso = origen.length()
        return try
        {
            try
            {
                Files.move(
                    origen.toPath(),
                    destino.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
            catch (_: AtomicMoveNotSupportedException)
            {
                Files.move(origen.toPath(), destino.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            val valido = destino.isFile && destino.length() == peso
            if (!valido) destino.delete()
            valido
        }
        catch (_: Exception)
        {
            false
        }
    }

    private class EntradaLimitada(
        entrada: InputStream,
        private val limite: Long,
        private val comprobarCancelacion: () -> Unit,
    ) : FilterInputStream(entrada)
    {
        private var total = 0L

        override fun read(): Int
        {
            comprobarCancelacion()
            val valor = super.read()
            if (valor >= 0) sumar(1)
            return valor
        }

        override fun read(buffer: ByteArray, offset: Int, longitud: Int): Int
        {
            comprobarCancelacion()
            val leidos = super.read(buffer, offset, longitud)
            if (leidos > 0) sumar(leidos)
            return leidos
        }

        private fun sumar(cantidad: Int)
        {
            total += cantidad
            if (total > limite) throw IOException("La descarga excede el limite permitido")
        }
    }

    private fun podar(contexto: Context)
    {
        val archivos = carpeta(contexto).listFiles()?.filter { it.isFile && !it.name.endsWith(".tmp") } ?: return
        var total = archivos.sumOf { it.length() }
        if (total <= LIMITE)
        {
            return
        }
        for (archivo in archivos.sortedBy { it.lastModified() })
        {
            if (total <= LIMITE * 9 / 10)
            {
                break
            }
            val peso = archivo.length()
            if (archivo.delete())
            {
                total -= peso
            }
        }
    }
}

data class ImagenLista(val bytes: ByteArray, val ancho: Int, val alto: Int)

data class PrevioEnvio(val uri: Uri, val imagen: ImagenLista?, val miniatura: String?, val esVideo: Boolean)

fun miniaturaDe(imagen: ImagenLista, ladoMax: Int = 480, topeBytes: Int = 80 * 1024): ImagenLista?
{
    var original: Bitmap? = null
    var escalado: Bitmap? = null
    return try
    {
        val base = BitmapFactory.decodeByteArray(imagen.bytes, 0, imagen.bytes.size) ?: return null
        original = base
        val mayor = maxOf(base.width, base.height)
        val mapa = if (mayor > ladoMax)
        {
            val factor = ladoMax.toFloat() / mayor
            Bitmap.createScaledBitmap(
                base,
                (base.width * factor).toInt().coerceAtLeast(1),
                (base.height * factor).toInt().coerceAtLeast(1),
                true,
            )
        }
        else
        {
            base
        }
        escalado = mapa
        for (calidad in listOf(60, 45, 32))
        {
            val salida = ByteArrayOutputStream()
            if (!mapa.compress(Bitmap.CompressFormat.JPEG, calidad, salida))
            {
                return null
            }
            if (salida.size() <= topeBytes)
            {
                return ImagenLista(salida.toByteArray(), mapa.width, mapa.height)
            }
        }
        null
    }
    catch (_: Throwable)
    {
        null
    }
    finally
    {
        escalado?.takeIf { it !== original && !it.isRecycled }?.recycle()
        original?.takeIf { !it.isRecycled }?.recycle()
    }
}

fun comprimirImagen(contexto: Context, uri: Uri): ImagenLista?
{
    var mapa: Bitmap? = null
    return try
    {
        val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contexto.contentResolver.openInputStream(uri)?.buffered()?.use {
            BitmapFactory.decodeStream(it, null, limites)
        } ?: return null
        if (limites.outWidth <= 0 || limites.outHeight <= 0) return null
        var muestra = 1
        val mayor = maxOf(limites.outWidth, limites.outHeight)
        while (mayor / muestra > 1920 * 2)
        {
            muestra *= 2
        }
        var actual: Bitmap? = null
        while (muestra <= 32)
        {
            try
            {
                val opciones = BitmapFactory.Options().apply { inSampleSize = muestra }
                actual = contexto.contentResolver.openInputStream(uri)?.buffered()?.use {
                    BitmapFactory.decodeStream(it, null, opciones)
                }
                break
            }
            catch (_: OutOfMemoryError)
            {
                muestra *= 2
            }
        }
        if (actual == null) return null
        mapa = actual
        val maxLado = maxOf(actual.width, actual.height)
        if (maxLado > 1920)
        {
            val factor = 1920f / maxLado
            val original = actual
            actual = Bitmap.createScaledBitmap(original, (original.width * factor).toInt(), (original.height * factor).toInt(), true)
            mapa = actual
            if (actual !== original) original.recycle()
        }
        val salida = ByteArrayOutputStream()
        if (!actual.compress(Bitmap.CompressFormat.JPEG, 82, salida)) return null
        ImagenLista(salida.toByteArray(), actual.width, actual.height)
    }
    catch (_: Throwable)
    {
        null
    }
    finally
    {
        mapa?.takeIf { !it.isRecycled }?.recycle()
    }
}

fun comprimirAvatar(contexto: Context, uri: Uri): ImagenLista?
{
    val original = comprimirImagen(contexto, uri) ?: return null
    return miniaturaDe(
        imagen = original,
        ladoMax = 720,
        topeBytes = 700 * 1024,
    )
}

internal fun mensajeErrorAvatar(error: Throwable?): String = when (error)
{
    is ErrorApi -> when (error.status)
    {
        0 -> "No hay conexión para actualizar la foto."
        413 -> "La imagen elegida es demasiado grande."
        else -> error.message ?: "No pudimos actualizar la foto."
    }
    else -> "No pudimos procesar la imagen elegida."
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
            .widthIn(max = 260.dp)
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


data class ArchivoElegido(val nombre: String, val peso: Long, val mime: String)

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
        ArchivoElegido(nombre, peso.coerceAtLeast(0), mime)
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
            .pulsable {
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
    val lector = android.media.MediaMetadataRetriever()
    var cuadro: Bitmap? = null
    var chico: Bitmap? = null
    return try
    {
        lector.setDataSource(contexto, uri)
        val dur = (lector.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) / 1000
        val original = lector.getFrameAtTime(0) ?: return Triple(null, Pair(0, 0), dur.toInt())
        cuadro = original
        val ancho = original.width
        val alto = original.height
        val maxLado = maxOf(ancho, alto)
        val factor = if (maxLado > 480) 480f / maxLado else 1f
        val reducido = Bitmap.createScaledBitmap(original, (ancho * factor).toInt(), (alto * factor).toInt(), true)
        chico = reducido
        val salida = ByteArrayOutputStream()
        if (!reducido.compress(Bitmap.CompressFormat.JPEG, 60, salida)) return Triple(null, Pair(ancho, alto), dur.toInt())
        val b64 = android.util.Base64.encodeToString(salida.toByteArray(), android.util.Base64.NO_WRAP)
        Triple("data:image/jpeg;base64,$b64", Pair(ancho, alto), dur.toInt())
    }
    catch (_: Exception)
    {
        Triple(null, Pair(0, 0), 0)
    }
    finally
    {
        if (chico !== cuadro) chico?.takeIf { !it.isRecycled }?.recycle()
        cuadro?.takeIf { !it.isRecycled }?.recycle()
        runCatching { lector.release() }
    }
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
            .pulsableLargo(
                alMantener = { alMantener() },
                alPulsar = { alReproducir() },
            )
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black),
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
    var controles by remember { mutableStateOf(true) }
    var pausado by remember { mutableStateOf(false) }
    var pos by remember { mutableStateOf(0L) }
    var dur by remember { mutableStateOf(0L) }
    var arrastre by remember { mutableStateOf<Float?>(null) }
    var anchoBarra by remember { mutableStateOf(1f) }
    val reproductor = remember { arrayOf<androidx.media3.exoplayer.ExoPlayer?>(null) }

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

    LaunchedEffect(archivo) {
        while (archivo != null)
        {
            val actual = reproductor[0]
            if (actual != null)
            {
                pos = actual.currentPosition
                dur = actual.duration.coerceAtLeast(0L)
                pausado = !actual.isPlaying
            }
            kotlinx.coroutines.delay(100)
        }
    }

    LaunchedEffect(controles, pausado, arrastre) {
        if (controles && !pausado && arrastre == null)
        {
            kotlinx.coroutines.delay(3000)
            controles = false
        }
    }

    fun alternar()
    {
        val actual = reproductor[0] ?: return
        if (actual.isPlaying)
        {
            actual.pause()
            controles = true
        }
        else
        {
            if (dur > 0 && pos >= dur - 200)
            {
                actual.seekTo(0)
            }
            actual.play()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        val listo = archivo
        when
        {
            listo != null ->
            {
                val jugador = remember(listo) {
                    androidx.media3.exoplayer.ExoPlayer.Builder(contexto).build().apply {
                        setMediaItem(androidx.media3.common.MediaItem.fromUri(Uri.fromFile(listo)))
                        prepare()
                        playWhenReady = true
                        reproductor[0] = this
                    }
                }
                androidx.compose.runtime.DisposableEffect(listo) {
                    onDispose {
                        jugador.release()
                        reproductor[0] = null
                    }
                }
                androidx.compose.ui.viewinterop.AndroidView(
                    factory = { c ->
                        androidx.media3.ui.PlayerView(c).apply {
                            player = jugador
                            useController = false
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                            controles = true
                            alternar()
                        },
                )
            }
            fallo -> Text("No se pudo cargar el video", fontSize = 13.sp, color = Color.White, modifier = Modifier.align(Alignment.Center))
            else -> CircularProgressIndicator(modifier = Modifier.size(26.dp).align(Alignment.Center), color = Color.White, strokeWidth = 2.dp)
        }

        if (controles)
        {
            Box(
                modifier = Modifier
                    .pulsable { alCerrar() }
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 12.dp, end = 20.dp)
                    .size(38.dp)
                    .background(Color.White.copy(alpha = 0.12f), androidx.compose.foundation.shape.CircleShape)
                    .border(0.5.dp, Color.White.copy(alpha = 0.25f), androidx.compose.foundation.shape.CircleShape),
                contentAlignment = Alignment.Center,
            )
            {
                Text("✕", fontSize = 17.sp, color = Color.White)
            }

            if (pausado && archivo != null)
            {
                Box(
                    modifier = Modifier
                        .pulsable { alternar() }
                        .align(Alignment.Center)
                        .size(68.dp)
                        .background(Color.White.copy(alpha = 0.14f), androidx.compose.foundation.shape.CircleShape)
                        .border(0.5.dp, Color.White.copy(alpha = 0.3f), androidx.compose.foundation.shape.CircleShape),
                    contentAlignment = Alignment.Center,
                )
                {
                    Reproducir(color = Color.White, tamano = 28.dp)
                }
            }

            val progreso = arrastre ?: if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
            val tiempoActual = if (arrastre != null && dur > 0) (arrastre!! * dur).toLong() else pos
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(start = 14.dp, end = 14.dp, bottom = 20.dp)
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.10f), RoundedCornerShape(26.dp))
                    .border(0.5.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(26.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            )
            {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .pulsable { alternar() },
                    contentAlignment = Alignment.Center,
                )
                {
                    if (pausado)
                    {
                        Reproducir(color = Color.White, tamano = 16.dp)
                    }
                    else
                    {
                        Pausa(color = Color.White, tamano = 16.dp)
                    }
                }
                Text(duracionVisor(tiempoActual), fontSize = 12.sp, color = Color.White.copy(alpha = 0.92f))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(28.dp)
                        .onSizeChanged { anchoBarra = it.width.toFloat().coerceAtLeast(1f) }
                        .pointerInput(dur) {
                            detectTapGestures { toque ->
                                if (dur > 0)
                                {
                                    val frac = (toque.x / anchoBarra).coerceIn(0f, 1f)
                                    reproductor[0]?.seekTo((frac * dur).toLong())
                                    pos = (frac * dur).toLong()
                                }
                            }
                        }
                        .pointerInput(dur) {
                            detectHorizontalDragGestures(
                                onDragStart = { inicio ->
                                    arrastre = (inicio.x / anchoBarra).coerceIn(0f, 1f)
                                },
                                onDragEnd = {
                                    val frac = arrastre
                                    if (frac != null && dur > 0)
                                    {
                                        reproductor[0]?.seekTo((frac * dur).toLong())
                                        pos = (frac * dur).toLong()
                                    }
                                    arrastre = null
                                },
                                onDragCancel = { arrastre = null },
                            ) { cambio, _ ->
                                arrastre = (cambio.position.x / anchoBarra).coerceIn(0f, 1f)
                            }
                        },
                    contentAlignment = Alignment.CenterStart,
                )
                {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.5.dp)
                            .background(Color.White.copy(alpha = 0.28f), RoundedCornerShape(2.dp)),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progreso)
                            .height(3.5.dp)
                            .background(Color.White, RoundedCornerShape(2.dp)),
                    )
                }
                Text(duracionVisor(dur), fontSize = 12.sp, color = Color.White.copy(alpha = 0.92f))
            }
        }
    }
}

private fun duracionVisor(ms: Long): String
{
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}

@Composable
fun AdjuntoAudio(app: AplicacionVixxer, media: MediaMensaje, mio: Boolean, colores: Paleta)
{
    val contexto = androidx.compose.ui.platform.LocalContext.current
    var reproduciendo by remember(media.path) { mutableStateOf(false) }
    var cargando by remember(media.path) { mutableStateOf(false) }
    var progreso by remember(media.path) { mutableStateOf(0f) }
    var velocidad by remember(media.path) { mutableStateOf(0) }
    val ritmos = remember { listOf(1f, 1.5f, 2f) }
    val reproductor = remember(media.path) { arrayOf<android.media.MediaPlayer?>(null) }
    val colorTexto = if (mio) colores.botonTexto else colores.texto

    androidx.compose.runtime.DisposableEffect(media.path) {
        onDispose {
            reproductor[0]?.release()
            reproductor[0] = null
        }
    }

    fun aplicarVelocidad(mp: android.media.MediaPlayer)
    {
        runCatching { mp.playbackParams = mp.playbackParams.setSpeed(ritmos[velocidad]) }
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
                aplicarVelocidad(actual)
                reproduciendo = true
            }
            return
        }
        cargando = true
    }

    fun cambiarVelocidad()
    {
        velocidad = (velocidad + 1) % ritmos.size
        reproductor[0]?.let {
            if (it.isPlaying)
            {
                aplicarVelocidad(it)
            }
        }
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
                    progreso = 1f
                    it.seekTo(0)
                }
                reproductor[0] = mp
                mp.start()
                aplicarVelocidad(mp)
                reproduciendo = true
            }
        }
    }

    LaunchedEffect(reproduciendo) {
        while (reproduciendo)
        {
            val mp = reproductor[0]
            if (mp != null && mp.duration > 0)
            {
                progreso = (mp.currentPosition.toFloat() / mp.duration).coerceIn(0f, 1f)
            }
            kotlinx.coroutines.delay(90)
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
                .pulsable { alternar() }
                .size(36.dp)
                .background(colorTexto.copy(alpha = 0.15f), androidx.compose.foundation.shape.CircleShape),
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
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            androidx.compose.foundation.Canvas(
                modifier = Modifier
                    .size(width = 130.dp, height = 26.dp)
                    .pointerInput(media.path) {
                        detectTapGestures { toque ->
                            val mp = reproductor[0] ?: return@detectTapGestures
                            if (mp.duration <= 0)
                            {
                                return@detectTapGestures
                            }
                            val fraccion = (toque.x / size.width).coerceIn(0f, 1f)
                            runCatching { mp.seekTo((fraccion * mp.duration).toInt()) }
                            progreso = fraccion
                        }
                    },
            ) {
                val n = barras.size
                val paso = size.width / n
                for (i in 0 until n)
                {
                    val alto = (barras[i].coerceIn(0.08f, 1f)) * size.height
                    val pasado = (i + 1).toFloat() / n <= progreso
                    drawRoundRect(
                        color = colorTexto.copy(alpha = if (pasado) 1f else 0.38f),
                        topLeft = androidx.compose.ui.geometry.Offset(i * paso + paso * 0.2f, (size.height - alto) / 2f),
                        size = androidx.compose.ui.geometry.Size(paso * 0.6f, alto),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f),
                    )
                }
            }
            if (media.dur > 0)
            {
                val restante = if (reproduciendo || progreso > 0f) (media.dur * (1f - progreso)).toInt().coerceAtLeast(0) else media.dur
                Text(duracionLegible(restante), fontSize = 11.sp, color = colorTexto.copy(alpha = 0.8f))
            }
        }
        Box(
            modifier = Modifier
                .pulsable { cambiarVelocidad() }
                .width(44.dp)
                .border(1.dp, colorTexto.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                .padding(vertical = 3.dp),
            contentAlignment = Alignment.Center,
        )
        {
            Text(
                if (ritmos[velocidad] == ritmos[velocidad].toInt().toFloat()) "${ritmos[velocidad].toInt()}x" else "${ritmos[velocidad]}x",
                fontSize = 11.sp,
                fontFamily = FuenteOutfit,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                color = colorTexto,
            )
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
        val destino = File(contexto.cacheDir, "nota-${System.currentTimeMillis()}.m4a")
        val mr = android.media.MediaRecorder()
        return try
        {
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
        }
        catch (_: Exception)
        {
            runCatching { mr.release() }
            destino.delete()
            false
        }
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
        val actual = grabador
        val completa = runCatching {
            actual?.stop()
            actual != null
        }.getOrDefault(false)
        runCatching { actual?.release() }
        grabador = null
        archivo = null
        if (!completa)
        {
            destino?.delete()
            return null
        }
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


@Composable
fun VistaPrevio(previo: PrevioEnvio, modifier: Modifier = Modifier)
{
    val contexto = androidx.compose.ui.platform.LocalContext.current
    var reproduciendo by remember(previo.uri) { mutableStateOf(false) }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (previo.esVideo && reproduciendo)
        {
            val jugador = remember(previo.uri) {
                androidx.media3.exoplayer.ExoPlayer.Builder(contexto).build().apply {
                    setMediaItem(androidx.media3.common.MediaItem.fromUri(previo.uri))
                    prepare()
                    playWhenReady = true
                }
            }
            androidx.compose.runtime.DisposableEffect(previo.uri) {
                onDispose { jugador.release() }
            }
            androidx.compose.ui.viewinterop.AndroidView(
                factory = { c ->
                    androidx.media3.ui.PlayerView(c).apply {
                        player = jugador
                        useController = false
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                        if (jugador.isPlaying) jugador.pause() else jugador.play()
                    },
            )
        }
        else
        {
            AsyncImage(
                model = if (previo.esVideo) previo.miniatura else previo.uri,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            if (previo.esVideo)
            {
                Box(
                    modifier = Modifier
                        .pulsable { reproduciendo = true }
                        .size(64.dp)
                        .background(Color.White.copy(alpha = 0.14f), androidx.compose.foundation.shape.CircleShape)
                        .border(0.5.dp, Color.White.copy(alpha = 0.3f), androidx.compose.foundation.shape.CircleShape),
                    contentAlignment = Alignment.Center,
                )
                {
                    Reproducir(color = Color.White, tamano = 26.dp)
                }
            }
        }
    }
}

private object CachePreviewsEnlace
{
    private const val MAXIMO = 16
    private val valores = object : LinkedHashMap<String, RedMedia.VistaPrevia>(MAXIMO, 0.75f, true)
    {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, RedMedia.VistaPrevia>?): Boolean = size > MAXIMO
    }

    @Synchronized
    fun obtener(url: String): RedMedia.VistaPrevia? = valores[url]

    @Synchronized
    fun guardar(url: String, preview: RedMedia.VistaPrevia)
    {
        valores[url] = preview
    }
}

@Composable
fun TarjetaEnlace(url: String, mio: Boolean, colores: Paleta)
{
    var datos by remember(url) { mutableStateOf(CachePreviewsEnlace.obtener(url)) }
    var estado by remember(url) { mutableStateOf(if (datos != null) "listo" else "espera") }

    LaunchedEffect(url, estado) {
        if (estado == "cargando")
        {
            val preview = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                RedMedia.cargarVistaPrevia(url)
            }
            if (preview != null)
            {
                CachePreviewsEnlace.guardar(url, preview)
                datos = preview
                estado = "listo"
            }
            else
            {
                estado = "fallo"
            }
        }
    }

    val colorTexto = if (mio) colores.botonTexto else colores.texto
    val preview = datos
    Column(
        modifier = Modifier
            .padding(top = 6.dp)
            .pulsable(habilitado = estado != "cargando" && preview == null) { estado = "cargando" }
            .clip(RoundedCornerShape(10.dp))
            .background(colorTexto.copy(alpha = 0.08f)),
    )
    {
        if (preview?.imagen != null)
        {
            AsyncImage(
                model = preview.imagen,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().heightIn(max = 130.dp),
            )
        }
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                preview?.datos?.titulo ?: dev.vixxer.mensajero.nucleo.Enlaces.dominioDe(url),
                fontSize = 13.sp,
                color = colorTexto,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            if (preview?.datos?.desc != null)
            {
                Text(
                    preview.datos.desc!!,
                    fontSize = 11.sp,
                    color = colorTexto.copy(alpha = 0.75f),
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            Text(
                when
                {
                    preview != null -> dev.vixxer.mensajero.nucleo.Enlaces.dominioDe(preview.datos.url)
                    estado == "cargando" -> "Cargando…"
                    estado == "fallo" -> "Vista previa no disponible · Reintentar"
                    else -> "Cargar vista previa"
                },
                fontSize = 10.sp,
                color = colorTexto.copy(alpha = 0.6f),
            )
        }
    }
}

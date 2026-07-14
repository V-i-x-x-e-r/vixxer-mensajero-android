package dev.vixxer.mensajero.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
fun AdjuntoImagen(app: AplicacionVixxer, media: MediaMensaje, colores: Paleta, alAbrir: (File) -> Unit)
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
            .clip(RoundedCornerShape(12.dp))
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
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { alAbrir(listo) },
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
    val transformar = rememberTransformableState { zoom, arrastre, _ ->
        escala = (escala * zoom).coerceIn(1f, 5f)
        despX += arrastre.x
        despY += arrastre.y
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.96f))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { alCerrar() }
            .transformable(transformar),
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

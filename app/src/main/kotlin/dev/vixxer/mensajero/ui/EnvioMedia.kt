package dev.vixxer.mensajero.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dev.vixxer.mensajero.AplicacionVixxer
import dev.vixxer.mensajero.nucleo.Medios
import java.io.File
import java.io.InputStream
import org.json.JSONArray
import org.json.JSONObject

class EnvioMedia(private val app: AplicacionVixxer, private val contexto: Context)
{
    private data class Subida(val path: String, val cifrado: Medios.CifradoFlujo)

    private fun subir(
        abrir: () -> InputStream?,
        limiteBytes: Long,
        pesoEsperado: Long?,
        onProgreso: ((Double) -> Unit)? = null,
    ): Subida?
    {
        val temporal = runCatching { File.createTempFile("media-cifrada-", ".vx2", contexto.cacheDir) }.getOrNull()
            ?: return null
        return try
        {
            val cifrado = runCatching {
                abrir()?.buffered()?.use { entrada ->
                    temporal.outputStream().buffered().use { salida ->
                        Medios.cifrarFlujo(entrada, salida, limiteBytes) { procesados ->
                            val total = pesoEsperado?.takeIf { it > 0 }
                            if (total != null)
                            {
                                onProgreso?.invoke(0.45 * (procesados.toDouble() / total).coerceIn(0.0, 1.0))
                            }
                        }
                    }
                }
            }.getOrNull() ?: return null
            onProgreso?.invoke(0.45)
            val respuesta = runCatching {
                app.api.subirMediaConProgreso(temporal) { avance ->
                    onProgreso?.invoke(0.45 + 0.55 * avance.coerceIn(0.0, 1.0))
                } as JSONObject
            }.getOrNull()
                ?: return null
            val path = respuesta.optString("path").takeIf { it.isNotBlank() } ?: return null
            CacheMedia.guardar(contexto, path, cifrado.peso, abrir)
            Subida(path, cifrado)
        }
        finally
        {
            temporal.delete()
        }
    }

    fun prepararImagen(imagen: ImagenLista, cap: String?, onProgreso: ((Double) -> Unit)? = null): String?
    {
        val (path, cifrado) = subir(
            { imagen.bytes.inputStream() },
            LIMITE_IMAGEN,
            imagen.bytes.size.toLong(),
            onProgreso,
        ) ?: return null
        val obj = JSONObject()
            .put("t", "img")
            .put("path", path)
            .put("mime", "image/jpeg")
            .put("k", cifrado.clave)
            .put("n", cifrado.nonce)
            .put("w", imagen.ancho)
            .put("h", imagen.alto)
            .put("peso", cifrado.peso)
        if (!cap.isNullOrBlank())
        {
            obj.put("cap", cap.trim())
        }
        return obj.toString()
    }

    fun prepararVideo(uri: Uri, cap: String? = null, onProgreso: ((Double) -> Unit)? = null): String?
    {
        val (prev, medidas, dur) = miniaturaVideo(contexto, uri)
        val abrir = { contexto.contentResolver.openInputStream(uri) }
        val (path, cifrado) = subir(abrir, LIMITE_VIDEO, pesoDe(uri), onProgreso) ?: return null
        val obj = JSONObject()
            .put("t", "video")
            .put("path", path)
            .put("mime", contexto.contentResolver.getType(uri) ?: "video/mp4")
            .put("k", cifrado.clave)
            .put("n", cifrado.nonce)
            .put("w", medidas.first)
            .put("h", medidas.second)
            .put("dur", dur)
            .put("peso", cifrado.peso)
        if (prev != null)
        {
            obj.put("prev", prev)
        }
        if (!cap.isNullOrBlank())
        {
            obj.put("cap", cap.trim())
        }
        return obj.toString()
    }

    fun prepararSticker(bytes: ByteArray, ancho: Int, alto: Int, onProgreso: ((Double) -> Unit)? = null): String?
    {
        val (path, cifrado) = subir(
            { bytes.inputStream() },
            LIMITE_STICKER,
            bytes.size.toLong(),
            onProgreso,
        ) ?: return null
        return JSONObject()
            .put("t", "sticker")
            .put("path", path)
            .put("mime", "image/png")
            .put("k", cifrado.clave)
            .put("n", cifrado.nonce)
            .put("w", ancho)
            .put("h", alto)
            .put("peso", cifrado.peso)
            .toString()
    }

    fun prepararDocumento(uri: Uri, onProgreso: ((Double) -> Unit)? = null): String?
    {
        val archivo = leerArchivo(contexto, uri) ?: return null
        if (archivo.peso > LIMITE_DOCUMENTO)
        {
            return null
        }
        val abrir = { contexto.contentResolver.openInputStream(uri) }
        val (path, cifrado) = subir(abrir, LIMITE_DOCUMENTO, archivo.peso.takeIf { it > 0 }, onProgreso) ?: return null
        return JSONObject()
            .put("t", "file")
            .put("path", path)
            .put("mime", archivo.mime)
            .put("k", cifrado.clave)
            .put("n", cifrado.nonce)
            .put("nombre", archivo.nombre)
            .put("peso", cifrado.peso)
            .toString()
    }

    fun prepararAudio(archivo: File, dur: Int, ondas: List<Float>, onProgreso: ((Double) -> Unit)? = null): String?
    {
        if (!archivo.isFile || archivo.length() > LIMITE_AUDIO)
        {
            return null
        }
        val (path, cifrado) = subir(
            { archivo.inputStream() },
            LIMITE_AUDIO,
            archivo.length(),
            onProgreso,
        ) ?: return null
        val wf = JSONArray()
        for (v in ondas)
        {
            wf.put(v.toDouble())
        }
        return JSONObject()
            .put("t", "audio")
            .put("path", path)
            .put("mime", "audio/mp4")
            .put("k", cifrado.clave)
            .put("n", cifrado.nonce)
            .put("dur", maxOf(1, dur))
            .put("peso", cifrado.peso)
            .put("wf", wf)
            .toString()
    }

    fun prepararImagenCercania(imagen: ImagenLista, cap: String?): Pair<String, ByteArray>?
    {
        val mini = miniaturaDe(imagen) ?: return null
        val path = pathCercania("jpg")
        CacheMedia.guardar(contexto, path, mini.bytes.size.toLong()) { mini.bytes.inputStream() }
        val obj = JSONObject()
            .put("t", "img")
            .put("path", path)
            .put("mime", "image/jpeg")
            .put("w", mini.ancho)
            .put("h", mini.alto)
            .put("peso", mini.bytes.size)
        if (!cap.isNullOrBlank())
        {
            obj.put("cap", cap.trim())
        }
        return Pair(obj.toString(), mini.bytes)
    }

    fun prepararImagenCompletaCercania(imagen: ImagenLista, cap: String?): Pair<String, ByteArray>?
    {
        if (imagen.bytes.isEmpty() || imagen.bytes.size > LIMITE_IMAGEN)
        {
            return null
        }
        val path = pathCercania("jpg")
        CacheMedia.guardar(contexto, path, imagen.bytes.size.toLong()) { imagen.bytes.inputStream() }
        val obj = JSONObject()
            .put("t", "img")
            .put("path", path)
            .put("mime", "image/jpeg")
            .put("w", imagen.ancho)
            .put("h", imagen.alto)
            .put("peso", imagen.bytes.size)
        if (!cap.isNullOrBlank())
        {
            obj.put("cap", cap.trim())
        }
        return Pair(obj.toString(), imagen.bytes)
    }

    fun prepararAudioCercania(archivo: File, dur: Int, ondas: List<Float>): Pair<String, ByteArray>?
    {
        val bytes = runCatching { archivo.readBytes() }.getOrNull() ?: return null
        if (bytes.isEmpty() || bytes.size > TOPE_CERCANIA)
        {
            return null
        }
        val path = pathCercania("m4a")
        CacheMedia.guardar(contexto, path, bytes.size.toLong()) { bytes.inputStream() }
        val wf = JSONArray()
        for (v in ondas)
        {
            wf.put(v.toDouble())
        }
        val obj = JSONObject()
            .put("t", "audio")
            .put("path", path)
            .put("mime", "audio/mp4")
            .put("dur", maxOf(1, dur))
            .put("peso", bytes.size)
            .put("wf", wf)
        return Pair(obj.toString(), bytes)
    }

    private fun pathCercania(extension: String): String =
        "ble/${System.currentTimeMillis()}-${(1000..9999).random()}.$extension"

    private fun pesoDe(uri: Uri): Long?
    {
        val descriptor = runCatching {
            contexto.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
        }.getOrNull()
        if (descriptor != null && descriptor >= 0) return descriptor
        return runCatching {
            contexto.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val columna = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (columna < 0 || cursor.isNull(columna)) null else cursor.getLong(columna).takeIf { it >= 0 }
            }
        }.getOrNull()
    }

    private companion object
    {
        const val LIMITE_IMAGEN = 16L * 1024 * 1024
        const val LIMITE_VIDEO = 50L * 1024 * 1024
        const val LIMITE_DOCUMENTO = 25L * 1024 * 1024
        const val LIMITE_AUDIO = 25L * 1024 * 1024
        const val LIMITE_STICKER = 5L * 1024 * 1024
        const val TOPE_CERCANIA = 80 * 1024
    }
}

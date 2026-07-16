package dev.vixxer.mensajero.ui

import android.content.Context
import android.net.Uri
import dev.vixxer.mensajero.AplicacionVixxer
import dev.vixxer.mensajero.nucleo.Medios
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

class EnvioMedia(private val app: AplicacionVixxer, private val contexto: Context)
{
    private fun subir(bytes: ByteArray): Pair<String, Medios.Cifrado>?
    {
        val cifrado = Medios.cifrarArchivo(bytes)
        val respuesta = runCatching { app.api.subirMediaConProgreso(cifrado.datos) as JSONObject }.getOrNull()
            ?: return null
        val path = respuesta.getString("path")
        CacheMedia.guardar(contexto, path, bytes)
        return Pair(path, cifrado)
    }

    fun prepararImagen(imagen: ImagenLista, cap: String?): String?
    {
        val (path, cifrado) = subir(imagen.bytes) ?: return null
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
        return obj.toString()
    }

    fun prepararVideo(uri: Uri, cap: String? = null): String?
    {
        val bytes = runCatching {
            contexto.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull() ?: return null
        if (bytes.size > 50 * 1024 * 1024)
        {
            return null
        }
        val (prev, medidas, dur) = miniaturaVideo(contexto, uri)
        val (path, cifrado) = subir(bytes) ?: return null
        val obj = JSONObject()
            .put("t", "video")
            .put("path", path)
            .put("mime", contexto.contentResolver.getType(uri) ?: "video/mp4")
            .put("k", cifrado.clave)
            .put("n", cifrado.nonce)
            .put("w", medidas.first)
            .put("h", medidas.second)
            .put("dur", dur)
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

    fun prepararSticker(bytes: ByteArray, ancho: Int, alto: Int): String?
    {
        val (path, cifrado) = subir(bytes) ?: return null
        return JSONObject()
            .put("t", "sticker")
            .put("path", path)
            .put("mime", "image/png")
            .put("k", cifrado.clave)
            .put("n", cifrado.nonce)
            .put("w", ancho)
            .put("h", alto)
            .toString()
    }

    fun prepararDocumento(uri: Uri): String?
    {
        val archivo = leerArchivo(contexto, uri) ?: return null
        if (archivo.bytes.size > 25 * 1024 * 1024)
        {
            return null
        }
        val (path, cifrado) = subir(archivo.bytes) ?: return null
        return JSONObject()
            .put("t", "file")
            .put("path", path)
            .put("mime", archivo.mime)
            .put("k", cifrado.clave)
            .put("n", cifrado.nonce)
            .put("nombre", archivo.nombre)
            .put("peso", archivo.peso)
            .toString()
    }

    fun prepararAudio(archivo: File, dur: Int, ondas: List<Float>): String?
    {
        val bytes = runCatching { archivo.readBytes() }.getOrNull() ?: return null
        val (path, cifrado) = subir(bytes) ?: return null
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
            .put("wf", wf)
            .toString()
    }
}

package dev.vixxer.mensajero.nucleo

import java.io.ByteArrayOutputStream
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

object RedMedia
{
    data class VistaPrevia(val datos: Enlaces.Preview, val imagen: ByteArray?)

    private const val MAX_URL = 2048
    private const val MAX_HTML = 128 * 1024
    private const val MAX_IMAGEN = 2 * 1024 * 1024
    private const val MAX_REDIRECCIONES = 3

    private val dnsPublico = object : Dns
    {
        override fun lookup(hostname: String): List<InetAddress>
        {
            val direcciones = Dns.SYSTEM.lookup(hostname)
            if (direcciones.isEmpty() || direcciones.any { !esDireccionPublica(it) })
            {
                throw UnknownHostException("Destino de red no permitido")
            }
            return direcciones
        }
    }

    private val cliente = OkHttpClient.Builder()
        .dns(dnsPublico)
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .build()

    fun esUrlPublica(url: String): Boolean = urlPublica(url) != null

    fun normalizarImagen(baseUrl: String, imagen: String?): String?
    {
        if (imagen.isNullOrBlank()) return null
        val base = urlPublica(baseUrl) ?: return null
        val resuelta = base.resolve(imagen.trim()) ?: return null
        return urlPublica(resuelta.toString())?.toString()
    }

    fun cargarVistaPrevia(url: String): VistaPrevia?
    {
        val pagina = descargar(
            url = url,
            accept = "text/html,application/xhtml+xml",
            limite = MAX_HTML,
            tiposPermitidos = setOf("text/html", "application/xhtml+xml"),
        ) ?: return null
        val charset = pagina.tipo?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
        val html = runCatching { pagina.bytes.toString(charset) }.getOrNull() ?: return null
        val crudo = Enlaces.previewDeHtml(pagina.url.toString(), html) ?: return null
        val imagenUrl = normalizarImagen(pagina.url.toString(), crudo.imagen)
        val bytesImagen = imagenUrl?.let { cargarImagen(it) }
        val titulo = limpiarTexto(crudo.titulo, 200).takeIf { it.isNotEmpty() } ?: return null
        val datos = Enlaces.Preview(
            url = pagina.url.toString(),
            titulo = titulo,
            desc = crudo.desc?.let { limpiarTexto(it, 400).ifEmpty { null } },
            imagen = if (bytesImagen != null) imagenUrl else null,
        )
        return VistaPrevia(datos, bytesImagen)
    }

    internal fun esDireccionPublica(direccion: InetAddress): Boolean
    {
        if (direccion.isAnyLocalAddress || direccion.isLoopbackAddress || direccion.isLinkLocalAddress ||
            direccion.isSiteLocalAddress || direccion.isMulticastAddress)
        {
            return false
        }
        return when (direccion)
        {
            is Inet4Address -> esIpv4Publica(direccion.address)
            is Inet6Address -> esIpv6Publica(direccion.address)
            else -> false
        }
    }

    private data class Descarga(val url: HttpUrl, val bytes: ByteArray, val tipo: okhttp3.MediaType?)

    private fun cargarImagen(url: String): ByteArray?
    {
        val descarga = descargar(
            url = url,
            accept = "image/avif,image/webp,image/*",
            limite = MAX_IMAGEN,
            tiposPermitidos = emptySet(),
        ) ?: return null
        val tipo = descarga.tipo?.let { "${it.type}/${it.subtype}" }?.lowercase() ?: return null
        if (!tipo.startsWith("image/") || tipo == "image/svg+xml") return null
        return descarga.bytes
    }

    private fun descargar(
        url: String,
        accept: String,
        limite: Int,
        tiposPermitidos: Set<String>,
    ): Descarga?
    {
        var actual = urlPublica(url) ?: return null
        repeat(MAX_REDIRECCIONES + 1) { numero ->
            val solicitud = Request.Builder()
                .url(actual)
                .header("Accept", accept)
                .header("User-Agent", "Vixxer-Android/1")
                .get()
                .build()
            val respuesta = runCatching { cliente.newCall(solicitud).execute() }.getOrNull() ?: return null
            respuesta.use { r ->
                if (r.code in 300..399)
                {
                    if (numero >= MAX_REDIRECCIONES) return null
                    val destino = r.header("Location") ?: return null
                    val resuelta = actual.resolve(destino) ?: return null
                    actual = urlPublica(resuelta.toString()) ?: return null
                    return@repeat
                }
                if (!r.isSuccessful) return null
                val cuerpo = r.body ?: return null
                val tipo = cuerpo.contentType()
                val nombreTipo = tipo?.let { "${it.type}/${it.subtype}" }?.lowercase()
                if (tiposPermitidos.isNotEmpty() && nombreTipo !in tiposPermitidos) return null
                val anunciada = cuerpo.contentLength()
                if (anunciada > limite) return null
                val bytes = cuerpo.byteStream().use { leerLimitado(it, limite) } ?: return null
                return Descarga(actual, bytes, tipo)
            }
        }
        return null
    }

    private fun leerLimitado(entrada: java.io.InputStream, limite: Int): ByteArray?
    {
        val salida = ByteArrayOutputStream(minOf(limite, 16 * 1024))
        val bufer = ByteArray(8192)
        var total = 0
        while (true)
        {
            val leidos = entrada.read(bufer)
            if (leidos < 0) break
            total += leidos
            if (total > limite) return null
            salida.write(bufer, 0, leidos)
        }
        return salida.toByteArray()
    }

    private fun urlPublica(texto: String): HttpUrl?
    {
        if (texto.length !in 1..MAX_URL || texto.any { it.isWhitespace() || it.code < 0x20 }) return null
        val url = texto.toHttpUrlOrNull() ?: return null
        if (url.scheme !in setOf("http", "https") || url.username.isNotEmpty() || url.password.isNotEmpty()) return null
        val host = url.host.lowercase()
        if (host.endsWith('.') || host.contains('%') || host in setOf("localhost", "localhost.localdomain") ||
            host.endsWith(".localhost") || host.endsWith(".local") || host.endsWith(".internal") ||
            host.endsWith(".lan") || host.endsWith(".home.arpa"))
        {
            return null
        }
        if (host.contains(':'))
        {
            val direccion = runCatching { InetAddress.getByName(host) }.getOrNull() ?: return null
            if (!esDireccionPublica(direccion)) return null
        }
        else if (host.all { it.isDigit() || it == '.' })
        {
            val bytes = ipv4(host) ?: return null
            if (!esIpv4Publica(bytes)) return null
        }
        else if (!host.contains('.'))
        {
            return null
        }
        return url
    }

    private fun ipv4(host: String): ByteArray?
    {
        val partes = host.split('.')
        if (partes.size != 4) return null
        val numeros = partes.map { parte ->
            if (parte.isEmpty() || parte.length > 1 && parte.startsWith('0')) return null
            parte.toIntOrNull()?.takeIf { it in 0..255 } ?: return null
        }
        return ByteArray(4) { numeros[it].toByte() }
    }

    private fun esIpv4Publica(bytes: ByteArray): Boolean
    {
        if (bytes.size != 4) return false
        val a = bytes[0].toInt() and 255
        val b = bytes[1].toInt() and 255
        val c = bytes[2].toInt() and 255
        return when
        {
            a == 0 || a == 10 || a == 127 -> false
            a == 100 && b in 64..127 -> false
            a == 169 && b == 254 -> false
            a == 172 && b in 16..31 -> false
            a == 192 && b == 0 && c == 0 -> false
            a == 192 && b == 0 && c == 2 -> false
            a == 192 && b == 88 && c == 99 -> false
            a == 192 && b == 168 -> false
            a == 198 && b in 18..19 -> false
            a == 198 && b == 51 && c == 100 -> false
            a == 203 && b == 0 && c == 113 -> false
            a >= 224 -> false
            else -> true
        }
    }

    private fun esIpv6Publica(bytes: ByteArray): Boolean
    {
        if (bytes.size != 16) return false
        val primero = bytes[0].toInt() and 255
        val segundo = bytes[1].toInt() and 255
        val tercero = bytes[2].toInt() and 255
        if (primero and 0xfe == 0xfc) return false
        if (primero == 0x20 && segundo == 0x01 && tercero <= 0x01) return false
        if (primero == 0x20 && segundo == 0x01 && tercero == 0x0d && (bytes[3].toInt() and 255) == 0xb8) return false
        if (primero == 0x20 && segundo == 0x02) return esIpv4Publica(bytes.copyOfRange(2, 6))
        return primero and 0xe0 == 0x20
    }

    private fun limpiarTexto(texto: String, maximo: Int): String
    {
        return texto
            .filter { !it.isISOControl() || it.isWhitespace() }
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(maximo)
    }
}

package dev.vixxer.mensajero.nucleo

import com.goterl.lazysodium.SodiumJava
import java.util.Base64

object Cripto
{
    const val TAMANO_NONCE = 24
    const val TAMANO_CLAVE = 32
    const val TAMANO_MAC = 16
    const val TAMANO_FIRMA = 64

    private val sodio = SodiumJava()

    fun aBase64(bytes: ByteArray): String
    {
        return Base64.getEncoder().encodeToString(bytes)
    }

    fun deBase64(texto: String): ByteArray
    {
        return Base64.getDecoder().decode(texto)
    }

    fun publicaDeSecreta(secreta: ByteArray): ByteArray
    {
        val publica = ByteArray(TAMANO_CLAVE)
        exigir(sodio.crypto_scalarmult_base(publica, secreta))
        return publica
    }

    fun cifrar(mensaje: ByteArray, nonce: ByteArray, publicaDestino: ByteArray, secretaPropia: ByteArray): ByteArray
    {
        val caja = ByteArray(mensaje.size + TAMANO_MAC)
        exigir(sodio.crypto_box_easy(caja, mensaje, mensaje.size.toLong(), nonce, publicaDestino, secretaPropia))
        return caja
    }

    fun descifrar(caja: ByteArray, nonce: ByteArray, publicaRemitente: ByteArray, secretaPropia: ByteArray): ByteArray?
    {
        if (caja.size < TAMANO_MAC)
        {
            return null
        }
        val mensaje = ByteArray(caja.size - TAMANO_MAC)
        val ok = sodio.crypto_box_open_easy(mensaje, caja, caja.size.toLong(), nonce, publicaRemitente, secretaPropia) == 0
        return if (ok) mensaje else null
    }

    fun sellar(bytes: ByteArray, nonce: ByteArray, clave: ByteArray): ByteArray
    {
        val caja = ByteArray(bytes.size + TAMANO_MAC)
        exigir(sodio.crypto_secretbox_easy(caja, bytes, bytes.size.toLong(), nonce, clave))
        return caja
    }

    fun abrir(caja: ByteArray, nonce: ByteArray, clave: ByteArray): ByteArray?
    {
        if (caja.size < TAMANO_MAC)
        {
            return null
        }
        val bytes = ByteArray(caja.size - TAMANO_MAC)
        val ok = sodio.crypto_secretbox_open_easy(bytes, caja, caja.size.toLong(), nonce, clave) == 0
        return if (ok) bytes else null
    }

    fun parFirmaDeSemilla(semilla: ByteArray): Pair<ByteArray, ByteArray>
    {
        val publica = ByteArray(TAMANO_CLAVE)
        val secreta = ByteArray(TAMANO_FIRMA)
        exigir(sodio.crypto_sign_seed_keypair(publica, secreta, semilla))
        return Pair(publica, secreta)
    }

    fun firmar(mensaje: ByteArray, secretaFirma: ByteArray): ByteArray
    {
        val firma = ByteArray(TAMANO_FIRMA)
        exigir(sodio.crypto_sign_detached(firma, null, mensaje, mensaje.size.toLong(), secretaFirma))
        return firma
    }

    fun verificarFirma(firma: ByteArray, mensaje: ByteArray, publicaFirma: ByteArray): Boolean
    {
        return sodio.crypto_sign_verify_detached(firma, mensaje, mensaje.size.toLong(), publicaFirma) == 0
    }

    fun hash(bytes: ByteArray): ByteArray
    {
        val salida = ByteArray(64)
        exigir(sodio.crypto_hash_sha512(salida, bytes, bytes.size.toLong()))
        return salida
    }

    fun numeroSeguridad(publicaA: String, publicaB: String): String
    {
        val ordenadas = listOf(publicaA, publicaB).sorted()
        val juntas = deBase64(ordenadas[0]) + deBase64(ordenadas[1])
        val resumen = hash(juntas)
        val digitos = StringBuilder()
        for (i in 0 until 30)
        {
            digitos.append((resumen[i].toInt() and 255) % 10)
        }
        return digitos.chunked(5).joinToString(" ")
    }

    fun derivarClaveRespaldo(codigo: String, salt: String): ByteArray
    {
        val normalizado = codigo.uppercase().replace(Regex("[^A-Z0-9]"), "")
        return hash((normalizado + salt).toByteArray(Charsets.UTF_8)).copyOfRange(0, TAMANO_CLAVE)
    }

    fun abrirRespaldo(cifradoB64: String, nonceB64: String, salt: String, codigo: String): String?
    {
        val abierto = abrir(deBase64(cifradoB64), deBase64(nonceB64), derivarClaveRespaldo(codigo, salt))
        return abierto?.let { aBase64(it) }
    }

    private fun exigir(resultado: Int)
    {
        if (resultado != 0)
        {
            throw IllegalStateException("libsodium regreso $resultado")
        }
    }
}

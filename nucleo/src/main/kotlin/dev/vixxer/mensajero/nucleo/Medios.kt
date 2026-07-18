package dev.vixxer.mensajero.nucleo

import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom

class MedioDemasiadoGrandeException : Exception("El archivo excede el limite permitido")

object Medios
{
    const val LIMITE_DESCIFRADO = 64L * 1024 * 1024

    data class Cifrado(val datos: String, val clave: String, val nonce: String)
    data class CifradoFlujo(val clave: String, val nonce: String, val peso: Long)

    fun cifrarArchivo(bytes: ByteArray): Cifrado
    {
        val clave = ByteArray(Cripto.TAMANO_CLAVE)
        val nonce = ByteArray(Cripto.TAMANO_NONCE)
        SecureRandom().nextBytes(clave)
        SecureRandom().nextBytes(nonce)
        val datos = FormatoTrozos.cifrarArchivo(Cripto.aBase64(bytes), clave, nonce)
        return Cifrado(datos, Cripto.aBase64(clave), Cripto.aBase64(nonce))
    }

    fun cifrarFlujo(
        entrada: InputStream,
        salida: OutputStream,
        limiteBytes: Long,
        onProgreso: ((Long) -> Unit)? = null,
    ): CifradoFlujo
    {
        require(limiteBytes >= 0)
        val clave = ByteArray(Cripto.TAMANO_CLAVE)
        val nonce = ByteArray(Cripto.TAMANO_NONCE)
        SecureRandom().nextBytes(clave)
        SecureRandom().nextBytes(nonce)
        return cifrarFlujoConLlaves(entrada, salida, limiteBytes, clave, nonce, onProgreso)
    }

    internal fun cifrarFlujoConLlaves(
        entrada: InputStream,
        salida: OutputStream,
        limiteBytes: Long,
        clave: ByteArray,
        nonce: ByteArray,
        onProgreso: ((Long) -> Unit)? = null,
    ): CifradoFlujo
    {
        require(limiteBytes >= 0)
        require(clave.size == Cripto.TAMANO_CLAVE)
        require(nonce.size == Cripto.TAMANO_NONCE)
        salida.write(Cripto.deBase64(FormatoTrozos.MAGIA_B64))

        var indice = 0
        var total = 0L
        var primero = true
        var pendiente = -1
        while (true)
        {
            val plano = ByteArray(FormatoTrozos.TROZO_BYTES)
            var usados = 0
            if (pendiente >= 0)
            {
                plano[usados++] = pendiente.toByte()
                pendiente = -1
            }
            usados += leerHasta(entrada, plano, usados, plano.size - usados)
            if (usados == 0 && !primero)
            {
                break
            }
            primero = false
            total += usados
            if (total > limiteBytes)
            {
                throw MedioDemasiadoGrandeException()
            }

            pendiente = entrada.read()
            if (pendiente >= 0 && total >= limiteBytes)
            {
                throw MedioDemasiadoGrandeException()
            }
            val final = pendiente < 0
            val sellado = Cripto.sellar(
                plano.copyOf(usados),
                FormatoTrozos.nonceDeTrozoStreaming(nonce, indice, final),
                clave,
            )
            salida.write(FormatoTrozos.enmarcarStreaming(sellado, final))
            onProgreso?.invoke(total)
            indice += 1
            if (final)
            {
                break
            }
        }
        return CifradoFlujo(Cripto.aBase64(clave), Cripto.aBase64(nonce), total)
    }

    fun descifrarFlujo(
        entrada: InputStream,
        claveB64: String,
        nonceB64: String,
        salida: OutputStream,
        pesoEsperado: Long? = null,
        limiteBytes: Long = LIMITE_DESCIFRADO,
    ): Boolean
    {
        if (limiteBytes < 0 || pesoEsperado != null && pesoEsperado !in 0..limiteBytes)
        {
            return false
        }
        val clave = runCatching { Cripto.deBase64(claveB64) }.getOrNull() ?: return false
        val nonceBase = runCatching { Cripto.deBase64(nonceB64) }.getOrNull() ?: return false
        if (clave.size != Cripto.TAMANO_CLAVE || nonceBase.size != Cripto.TAMANO_NONCE)
        {
            return false
        }
        val magia = leer(entrada, 6)
        if (magia == null || !magia.contentEquals(Cripto.deBase64(FormatoTrozos.MAGIA_B64)))
        {
            return false
        }
        var indice = 0
        var total = 0L
        var usaMarca: Boolean? = null
        while (true)
        {
            val primeroCabecera = entrada.read()
            if (primeroCabecera < 0)
            {
                return indice > 0 && usaMarca != true && coincidePeso(total, pesoEsperado)
            }
            val cabecera = ByteArray(6)
            cabecera[0] = primeroCabecera.toByte()
            if (!leerEn(entrada, cabecera, 1, 5)) return false
            val marco = FormatoTrozos.medidaMarco(cabecera)
            if (!marco.cabeceraValida || marco.len !in Cripto.TAMANO_MAC..(FormatoTrozos.TROZO_BYTES + Cripto.TAMANO_MAC))
            {
                return false
            }
            if (usaMarca == null)
            {
                usaMarca = marco.marcado
            }
            else if (usaMarca != marco.marcado)
            {
                return false
            }
            val sellado = leer(entrada, marco.len) ?: return false
            val nonce = if (marco.marcado)
            {
                FormatoTrozos.nonceDeTrozoStreaming(nonceBase, indice, marco.final)
            }
            else
            {
                FormatoTrozos.nonceDeTrozo(nonceBase, indice)
            }
            val abierto = Cripto.abrir(sellado, nonce, clave) ?: return false
            if (marco.marcado && !marco.final && abierto.size != FormatoTrozos.TROZO_BYTES)
            {
                return false
            }
            total += abierto.size
            if (total > limiteBytes || pesoEsperado != null && total > pesoEsperado)
            {
                return false
            }
            salida.write(abierto)
            val relleno = marco.salto - 6 - marco.len
            if (relleno > 0)
            {
                val bytesRelleno = leer(entrada, relleno) ?: return false
                if (bytesRelleno.any { it.toInt() != 0 }) return false
            }
            indice += 1
            if (marco.final)
            {
                return entrada.read() < 0 && coincidePeso(total, pesoEsperado)
            }
        }
    }

    private fun coincidePeso(total: Long, esperado: Long?): Boolean = esperado == null || total == esperado

    private fun leerHasta(flujo: InputStream, destino: ByteArray, offset: Int, cuantos: Int): Int
    {
        var pos = 0
        while (pos < cuantos)
        {
            val leidos = flujo.read(destino, offset + pos, cuantos - pos)
            if (leidos < 0) break
            if (leidos == 0)
            {
                val uno = flujo.read()
                if (uno < 0) break
                destino[offset + pos] = uno.toByte()
                pos += 1
            }
            else
            {
                pos += leidos
            }
        }
        return pos
    }

    private fun leer(flujo: InputStream, cuantos: Int): ByteArray?
    {
        val destino = ByteArray(cuantos)
        return if (leerEn(flujo, destino, 0, cuantos)) destino else null
    }

    private fun leerEn(flujo: InputStream, destino: ByteArray, offset: Int, cuantos: Int): Boolean
    {
        return leerHasta(flujo, destino, offset, cuantos) == cuantos
    }
}

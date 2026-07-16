package dev.vixxer.mensajero.nucleo

import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom

object Medios
{
    data class Cifrado(val datos: String, val clave: String, val nonce: String)

    fun cifrarArchivo(bytes: ByteArray): Cifrado
    {
        val clave = ByteArray(Cripto.TAMANO_CLAVE)
        val nonce = ByteArray(Cripto.TAMANO_NONCE)
        SecureRandom().nextBytes(clave)
        SecureRandom().nextBytes(nonce)
        val datos = FormatoTrozos.cifrarArchivo(Cripto.aBase64(bytes), clave, nonce)
        return Cifrado(datos, Cripto.aBase64(clave), Cripto.aBase64(nonce))
    }

    fun descifrarFlujo(entrada: InputStream, claveB64: String, nonceB64: String, salida: OutputStream): Boolean
    {
        val clave = Cripto.deBase64(claveB64)
        val nonceBase = Cripto.deBase64(nonceB64)
        val flujo = entrada
        val magia = leer(flujo, 6)
        if (magia == null || !magia.contentEquals(Cripto.deBase64(FormatoTrozos.MAGIA_B64)))
        {
            return false
        }
        var indice = 0
        while (true)
        {
            val cabecera = leer(flujo, 6)
            if (cabecera == null)
            {
                return indice > 0
            }
            val marco = FormatoTrozos.medidaMarco(cabecera)
            if (marco.len <= 0)
            {
                return false
            }
            val sellado = leer(flujo, marco.len) ?: return false
            val abierto = Cripto.abrir(sellado, FormatoTrozos.nonceDeTrozo(nonceBase, indice), clave) ?: return false
            salida.write(abierto)
            val relleno = marco.salto - 6 - marco.len
            if (relleno > 0 && leer(flujo, relleno) == null)
            {
                return false
            }
            indice += 1
        }
    }

    private fun leer(flujo: InputStream, cuantos: Int): ByteArray?
    {
        val destino = ByteArray(cuantos)
        var pos = 0
        while (pos < cuantos)
        {
            val leidos = flujo.read(destino, pos, cuantos - pos)
            if (leidos < 0)
            {
                return null
            }
            pos += leidos
        }
        return destino
    }
}

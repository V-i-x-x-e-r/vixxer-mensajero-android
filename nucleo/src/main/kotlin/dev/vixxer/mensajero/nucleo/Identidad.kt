package dev.vixxer.mensajero.nucleo

import java.security.SecureRandom
import org.json.JSONObject

class Identidad(
    private val boveda: Almacen,
    private val azar: (Int) -> ByteArray = { cuantos ->
        val bytes = ByteArray(cuantos)
        SecureRandom().nextBytes(bytes)
        bytes
    },
)
{
    data class Nueva(val publicKey: String, val codigo: String, val respaldo: JSONObject)

    private val llavero = LlavesPasadas(boveda)

    fun asegurarClaves(): String
    {
        val existente = boveda.leer(ClavesSeguras.CLAVE_PUBLICA)
        if (existente != null)
        {
            return existente
        }
        val (publica, secreta) = parCaja()
        boveda.escribir(ClavesSeguras.CLAVE_PRIVADA, secreta)
        boveda.escribir(ClavesSeguras.CLAVE_PUBLICA, publica)
        return publica
    }

    fun generarCodigoRecuperacion(): String
    {
        val bytes = azar(20)
        val texto = buildString {
            for (b in bytes)
            {
                append(ALFABETO[(b.toInt() and 255) % ALFABETO.length])
            }
        }
        return texto.chunked(4).joinToString("-")
    }

    fun crearRespaldo(secretaB64: String, codigo: String): JSONObject
    {
        val salt = Cripto.aBase64(azar(16))
        val nonce = azar(Cripto.TAMANO_NONCE)
        val cifrado = Cripto.sellar(Cripto.deBase64(secretaB64), nonce, Cripto.derivarClaveRespaldo(codigo, salt))
        val respaldo = JSONObject()
        respaldo.put("cifrado", Cripto.aBase64(cifrado))
        respaldo.put("nonce", Cripto.aBase64(nonce))
        respaldo.put("salt", salt)
        return respaldo
    }

    fun crearIdentidad(): Nueva
    {
        val (publica, secreta) = parCaja()
        recordarAnterior(secreta)
        boveda.escribir(ClavesSeguras.CLAVE_PRIVADA, secreta)
        boveda.escribir(ClavesSeguras.CLAVE_PUBLICA, publica)
        val codigo = generarCodigoRecuperacion()
        boveda.escribir(ClavesSeguras.CODIGO_RECUP, codigo)
        return Nueva(publica, codigo, crearRespaldo(secreta, codigo))
    }

    fun restaurarDeRespaldo(respaldo: JSONObject?, codigo: String): String?
    {
        if (respaldo == null || !respaldo.has("cifrado"))
        {
            return null
        }
        val secreta = Cripto.abrirRespaldo(
            respaldo.getString("cifrado"),
            respaldo.getString("nonce"),
            respaldo.getString("salt"),
            codigo,
        ) ?: return null
        val publica = Cripto.aBase64(Cripto.publicaDeSecreta(Cripto.deBase64(secreta)))
        recordarAnterior(secreta)
        boveda.escribir(ClavesSeguras.CLAVE_PRIVADA, secreta)
        boveda.escribir(ClavesSeguras.CLAVE_PUBLICA, publica)
        boveda.escribir(ClavesSeguras.CODIGO_RECUP, codigo)
        return publica
    }

    fun leerRespaldoArchivo(contenido: String): JSONObject?
    {
        val obj = runCatching { JSONObject(contenido) }.getOrNull() ?: return null
        if (!obj.has("cifrado") || !obj.has("nonce") || !obj.has("salt"))
        {
            return null
        }
        val limpio = JSONObject()
        limpio.put("cifrado", obj.getString("cifrado"))
        limpio.put("nonce", obj.getString("nonce"))
        limpio.put("salt", obj.getString("salt"))
        return limpio
    }

    private fun parCaja(): Pair<String, String>
    {
        val secreta = azar(Cripto.TAMANO_CLAVE)
        val publica = Cripto.publicaDeSecreta(secreta)
        return Pair(Cripto.aBase64(publica), Cripto.aBase64(secreta))
    }

    private fun recordarAnterior(nuevaSecreta: String)
    {
        val anterior = boveda.leer(ClavesSeguras.CLAVE_PRIVADA)
        if (anterior != null && anterior != nuevaSecreta)
        {
            llavero.recordar(anterior)
        }
    }

    companion object
    {
        const val ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    }
}

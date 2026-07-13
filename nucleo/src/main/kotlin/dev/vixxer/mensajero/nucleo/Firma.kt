package dev.vixxer.mensajero.nucleo

import java.security.SecureRandom

class Firma(
    private val boveda: Almacen,
    private val api: ClienteApi,
    private val semilla: () -> ByteArray = {
        val bytes = ByteArray(Cripto.TAMANO_CLAVE)
        SecureRandom().nextBytes(bytes)
        bytes
    },
)
{
    fun asegurarLlaveFirma(): String
    {
        val existente = boveda.leer(ClavesSeguras.CLAVE_FIRMA_PUBLICA)
        if (existente != null)
        {
            return existente
        }
        val (publica, secreta) = Cripto.parFirmaDeSemilla(semilla())
        val publicaB64 = Cripto.aBase64(publica)
        boveda.escribir(ClavesSeguras.CLAVE_FIRMA_PRIVADA, Cripto.aBase64(secreta))
        boveda.escribir(ClavesSeguras.CLAVE_FIRMA_PUBLICA, publicaB64)
        return publicaB64
    }

    fun publicarLlaveFirma(): String
    {
        val publica = asegurarLlaveFirma()
        api.actualizarLlaveFirma(publica)
        return publica
    }

    fun firmar(mensaje: String): String?
    {
        val secreta = boveda.leer(ClavesSeguras.CLAVE_FIRMA_PRIVADA) ?: return null
        val firma = Cripto.firmar(mensaje.toByteArray(Charsets.UTF_8), Cripto.deBase64(secreta))
        return Cripto.aBase64(firma)
    }

    companion object
    {
        fun mensajeCanonico(remitenteId: String, destinatarioId: String, contenidoCifrado: String, nonce: String, id: String): String
        {
            return "$remitenteId|$destinatarioId|$contenidoCifrado|$nonce|$id"
        }
    }
}

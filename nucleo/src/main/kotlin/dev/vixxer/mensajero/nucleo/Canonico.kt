package dev.vixxer.mensajero.nucleo

object Canonico
{
    fun mensaje(remitenteId: String, destinatarioId: String, contenidoCifrado: String, nonce: String, id: String): String
    {
        return "$remitenteId|$destinatarioId|$contenidoCifrado|$nonce|$id"
    }
}

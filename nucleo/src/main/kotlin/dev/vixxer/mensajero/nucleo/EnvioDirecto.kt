package dev.vixxer.mensajero.nucleo

import org.json.JSONObject

object EnvioDirecto
{
    fun crearPendiente(
        texto: String,
        enviadoEn: String,
        respuestaA: String? = null,
        respuestaTexto: String? = null,
        clienteId: String = IdMensaje.nuevo(),
    ): JSONObject = JSONObject()
        .put("localId", clienteId)
        .put("respuestaA", respuestaA ?: JSONObject.NULL)
        .put("texto", texto)
        .put("respuestaTexto", respuestaTexto ?: JSONObject.NULL)
        .put("enviado_en", enviadoEn)

    fun prepararCuerpo(
        destinoId: String,
        pendiente: JSONObject,
        publicaDestino: String,
        privadaPropia: String,
    ): JSONObject
    {
        val (contenidoCifrado, nonce) = Cripto.cifrarTexto(
            pendiente.getString("texto"),
            publicaDestino,
            privadaPropia,
        )
        return JSONObject()
            .put("destinatarioId", destinoId)
            .put("contenidoCifrado", contenidoCifrado)
            .put("nonce", nonce)
            .put("respuestaA", pendiente.opt("respuestaA") ?: JSONObject.NULL)
            .put("clienteId", pendiente.getString("localId"))
    }
}

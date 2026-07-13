package dev.vixxer.mensajero.nucleo

import io.socket.client.IO
import io.socket.client.Socket
import java.net.URI

object ConexionSocket
{
    private var socket: Socket? = null

    fun conectar(socketUrl: String, token: String): Socket
    {
        val actual = socket
        if (actual != null && actual.connected())
        {
            return actual
        }
        if (socket == null)
        {
            val opciones = IO.Options.builder()
                .setAuth(mapOf("token" to token))
                .setTransports(arrayOf("websocket"))
                .build()
            socket = IO.socket(URI.create(socketUrl), opciones).connect()
        }
        return socket!!
    }

    fun obtener(): Socket? = socket

    fun desconectar()
    {
        socket?.disconnect()
        socket = null
    }
}

package dev.vixxer.mensajero.nucleo

import io.socket.client.IO
import io.socket.client.Socket
import java.net.URI

object ConexionSocket
{
    @Volatile
    private var socket: Socket? = null
    private var tokenActivo: String? = null

    @Synchronized
    fun conectar(socketUrl: String, token: String): Socket
    {
        if (tokenActivo != null && tokenActivo != token)
        {
            desconectar()
        }
        val actual = socket
        if (actual != null)
        {
            if (!actual.connected())
            {
                actual.connect()
            }
            return actual
        }
        val opciones = IO.Options.builder()
            .setAuth(mapOf("token" to token))
            .setTransports(arrayOf("websocket"))
            .build()
        tokenActivo = token
        socket = IO.socket(URI.create(socketUrl), opciones).connect()
        return socket!!
    }

    fun obtener(): Socket? = socket

    @Synchronized
    fun desconectar()
    {
        socket?.disconnect()
        socket = null
        tokenActivo = null
    }
}

package dev.vixxer.mensajero.llamadas

import dev.vixxer.mensajero.nucleo.EventosLlamada
import io.socket.client.Socket
import io.socket.emitter.Emitter
import org.json.JSONObject

object EscuchaLlamadas
{
    fun enganchar(socket: Socket, alEntrante: () -> Unit)
    {
        if (!GestorLlamadas.llamadasDisponibles())
        {
            return
        }
        socket.off(EventosLlamada.OFRECER)
        socket.off(EventosLlamada.CONTESTAR)
        socket.off(EventosLlamada.ICE)
        socket.off(EventosLlamada.COLGAR)
        socket.on(EventosLlamada.OFRECER, Emitter.Listener { args ->
            val data = args.getOrNull(0) as? JSONObject ?: return@Listener
            val libre = GestorLlamadas.estadoLlamada().fase == FaseLlamada.LIBRE
            GestorLlamadas.alRecibirOferta(data)
            if (libre)
            {
                alEntrante()
            }
        })
        socket.on(EventosLlamada.CONTESTAR, Emitter.Listener { args ->
            val data = args.getOrNull(0) as? JSONObject ?: return@Listener
            GestorLlamadas.alRecibirRespuesta(data)
        })
        socket.on(EventosLlamada.ICE, Emitter.Listener { args ->
            val data = args.getOrNull(0) as? JSONObject ?: return@Listener
            GestorLlamadas.alRecibirIce(data)
        })
        socket.on(EventosLlamada.COLGAR, Emitter.Listener { args ->
            val data = args.getOrNull(0) as? JSONObject ?: return@Listener
            GestorLlamadas.alRecibirColgar(data)
        })
    }
}

package dev.vixxer.mensajero

import dev.vixxer.mensajero.nucleo.ClavesSeguras
import dev.vixxer.mensajero.nucleo.ConexionSocket
import dev.vixxer.mensajero.nucleo.Cripto
import dev.vixxer.mensajero.nucleo.EnvioDirecto
import dev.vixxer.mensajero.nucleo.ErrorApi
import dev.vixxer.mensajero.nucleo.EnviosEnVuelo
import dev.vixxer.mensajero.nucleo.Outbox
import io.socket.client.Ack
import io.socket.client.Socket
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject

object DrenadorOutbox
{
    data class Resultado(
        val cuentaId: String,
        val tipo: Outbox.Tipo,
        val destinoId: String,
        val clienteId: String,
        val exitoso: Boolean,
        val idServidor: String? = null,
    )

    private val enVuelo = EnviosEnVuelo()
    private val observadores = CopyOnWriteArraySet<(Resultado) -> Unit>()

    fun observar(observador: (Resultado) -> Unit): () -> Unit
    {
        observadores.add(observador)
        return { observadores.remove(observador) }
    }

    suspend fun drenar(
        app: AplicacionVixxer,
        cuentaId: String,
        tipo: Outbox.Tipo? = null,
        destinoId: String? = null,
        forzar: Boolean = false,
    )
    {
        val pendientes = withContext(Dispatchers.IO) { app.leerOutbox(cuentaId) }
            .filter { tipo == null || it.tipo == tipo }
            .filter { destinoId == null || it.destinoId == destinoId }
        for (pendiente in pendientes)
        {
            enviar(app, cuentaId, pendiente, forzar)
        }
    }

    suspend fun enviar(
        app: AplicacionVixxer,
        cuentaId: String,
        pendiente: Outbox.Pendiente,
        forzar: Boolean = true,
    )
    {
        if (!esCuentaActual(app, cuentaId))
        {
            return
        }
        if (!forzar && !app.outbox.listoParaReintentar(pendiente))
        {
            return
        }
        val claveVuelo = "$cuentaId|${pendiente.tipo.valor}|${pendiente.destinoId}|${pendiente.clienteId}"
        if (!enVuelo.tomar(claveVuelo))
        {
            return
        }
        try
        {
            if (!withContext(Dispatchers.IO) { app.contieneOutbox(cuentaId, pendiente) })
            {
                return
            }
            val idServidor = when (pendiente.tipo)
            {
                Outbox.Tipo.DIRECTO -> enviarDirecto(app, cuentaId, pendiente)
                Outbox.Tipo.GRUPO -> enviarGrupo(app, cuentaId, pendiente)
            }
            val persistido = withContext(Dispatchers.IO) {
                app.persistirResultadoOutbox(cuentaId, pendiente, idServidor != null)
            }
            if (!persistido)
            {
                return
            }
            avisar(resultado(cuentaId, pendiente, idServidor))
        }
        catch (cancelacion: CancellationException)
        {
            throw cancelacion
        }
        catch (_: Exception)
        {
            registrarFallo(app, cuentaId, pendiente)
        }
        finally
        {
            enVuelo.liberar(claveVuelo)
        }
    }

    private suspend fun enviarDirecto(
        app: AplicacionVixxer,
        cuentaId: String,
        pendiente: Outbox.Pendiente,
    ): String?
    {
        val socket = ConexionSocket.obtener()
        if (socket == null || !socket.connected())
        {
            return null
        }
        val cuerpo = prepararCuerpoDirecto(app, pendiente) ?: return null
        if (!esCuentaActual(app, cuentaId) || !socket.connected())
        {
            return null
        }
        return esperarAckDirecto(socket, cuerpo)
    }

    private suspend fun prepararCuerpoDirecto(
        app: AplicacionVixxer,
        pendiente: Outbox.Pendiente,
    ): JSONObject? = withContext(Dispatchers.IO) {
        val privada = app.boveda.leer(ClavesSeguras.CLAVE_PRIVADA) ?: return@withContext null
        val publica = app.llaves.llavePublicaDe(pendiente.destinoId, forzar = true)
        EnvioDirecto.prepararCuerpo(pendiente.destinoId, pendiente.datos, publica, privada)
    }

    private suspend fun esperarAckDirecto(socket: Socket, cuerpo: JSONObject): String? =
        withTimeoutOrNull(8_000L) {
            suspendCancellableCoroutine { continuacion ->
                socket.emit("mensaje:enviar", arrayOf<Any>(cuerpo), Ack { args ->
                    if (!continuacion.isActive)
                    {
                        return@Ack
                    }
                    val respuesta = args.getOrNull(0) as? JSONObject
                    val id = respuesta?.takeIf { it.optBoolean("ok") }?.optString("id")
                    continuacion.resume(id?.takeIf { it.isNotBlank() })
                })
            }
        }

    private suspend fun enviarGrupo(
        app: AplicacionVixxer,
        cuentaId: String,
        pendiente: Outbox.Pendiente,
    ): String? =
        withContext(Dispatchers.IO) {
            for (intento in 0..1)
            {
                try
                {
                    if (!esCuentaActualAhora(app, cuentaId))
                    {
                        return@withContext null
                    }
                    val privada = app.boveda.leer(ClavesSeguras.CLAVE_PRIVADA) ?: return@withContext null
                    val grupo = app.api.infoGrupo(pendiente.destinoId) as? JSONObject ?: return@withContext null
                    if (!esCuentaActualAhora(app, cuentaId))
                    {
                        return@withContext null
                    }
                    val miembros = grupo.optJSONArray("miembros") ?: return@withContext null
                    val copias = cifrarParaMiembros(pendiente.datos.getString("texto"), miembros, privada)
                        ?: return@withContext null
                    if (copias.length() == 0 || copias.length() != miembros.length())
                    {
                        return@withContext null
                    }
                    val respuestaA = pendiente.datos.optString("respuestaA").takeIf { it.isNotBlank() }
                    if (!esCuentaActualAhora(app, cuentaId))
                    {
                        return@withContext null
                    }
                    val respuesta = app.api.enviarGrupo(
                        pendiente.destinoId,
                        pendiente.clienteId,
                        copias,
                        respuestaA,
                    ) as? JSONObject
                    return@withContext respuesta
                        ?.takeIf { it.optBoolean("ok") }
                        ?.optString("id")
                        ?.takeIf { it.isNotBlank() }
                }
                catch (error: ErrorApi)
                {
                    if (error.status != 409 || intento == 1)
                    {
                        return@withContext null
                    }
                }
                catch (_: Exception)
                {
                    return@withContext null
                }
            }
            null
        }

    private fun cifrarParaMiembros(plano: String, miembros: JSONArray, privada: String): JSONArray?
    {
        val copias = JSONArray()
        for (i in 0 until miembros.length())
        {
            val miembro = miembros.optJSONObject(i) ?: return null
            val id = miembro.optString("id")
            val publica = miembro.optString("llave_publica")
            if (id.isBlank() || publica.isBlank())
            {
                return null
            }
            val cifrado = runCatching { Cripto.cifrarTexto(plano, publica, privada) }.getOrNull()
                ?: return null
            copias.put(JSONObject()
                .put("destinatario_id", id)
                .put("contenido_cifrado", cifrado.first)
                .put("nonce", cifrado.second))
        }
        return copias
    }

    private suspend fun esCuentaActual(app: AplicacionVixxer, cuentaId: String): Boolean =
        withContext(Dispatchers.IO) { esCuentaActualAhora(app, cuentaId) }

    private fun esCuentaActualAhora(app: AplicacionVixxer, cuentaId: String): Boolean =
        cuentaId.isNotBlank() && app.boveda.leer(ClavesSeguras.MI_ID) == cuentaId

    private suspend fun registrarFallo(
        app: AplicacionVixxer,
        cuentaId: String,
        pendiente: Outbox.Pendiente,
    )
    {
        val persistido = try
        {
            withContext(Dispatchers.IO) {
                app.persistirResultadoOutbox(cuentaId, pendiente, exitoso = false)
            }
        }
        catch (_: Exception)
        {
            false
        }
        if (persistido)
        {
            avisar(resultado(cuentaId, pendiente, idServidor = null))
        }
    }

    private fun resultado(
        cuentaId: String,
        pendiente: Outbox.Pendiente,
        idServidor: String?,
    ) = Resultado(
        cuentaId = cuentaId,
        tipo = pendiente.tipo,
        destinoId = pendiente.destinoId,
        clienteId = pendiente.clienteId,
        exitoso = idServidor != null,
        idServidor = idServidor,
    )

    private fun avisar(resultado: Resultado)
    {
        for (observador in observadores)
        {
            runCatching { observador(resultado) }
        }
    }
}

package dev.vixxer.mensajero.nucleo

import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

class ErrorApi(val status: Int, mensaje: String) : Exception(mensaje)

data class Sobre(
    val remitenteId: String,
    val destinatarioId: String,
    val contenidoCifrado: String,
    val nonce: String,
    val id: String,
    val firma: String?,
    val respuestaA: String? = null,
)

class ClienteApi(
    private val baseUrl: String,
    private val token: () -> String?,
    private val alExpirarSesion: () -> Unit = {})
{
    private val tipoJson = "application/json".toMediaType()
    private val tipoBinario = "application/octet-stream".toMediaType()
    private val cliente = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    private val clienteMedia = cliente.newBuilder()
        .callTimeout(5, TimeUnit.MINUTES)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.MINUTES)
        .build()

    fun login(usuario: String, contrasena: String): Any? =
        pedir("/api/auth/login", "POST", JSONObject().put("usuario", usuario).put("contrasena", contrasena), auth = false)

    fun registrar(
        usuario: String,
        contrasena: String,
        llavePublica: String,
        llaveFirma: String,
        respaldo: JSONObject? = null,
    ): Any?
    {
        val datos = JSONObject()
            .put("usuario", usuario)
            .put("contrasena", contrasena)
            .put("llave_publica", llavePublica)
            .put("llave_firma", llaveFirma)
        if (respaldo != null)
        {
            datos.put("respaldo", respaldo)
        }
        return pedir("/api/auth/register", "POST", datos, auth = false)
    }

    fun cambiarContrasena(actual: String, nueva: String): Any? =
        pedir("/api/auth/cambiar-contrasena", "POST", JSONObject().put("actual", actual).put("nueva", nueva))

    fun borrarCuenta(contrasena: String): Any? =
        pedir("/api/auth/borrar-cuenta", "POST", JSONObject().put("contrasena", contrasena))

    fun llavePublica(userId: String): Any? = pedir("/api/usuarios/$userId/llave-publica")

    fun actualizarLlavePublica(llavePublica: String): Any? =
        pedir("/api/usuarios/llave-publica", "PUT", JSONObject().put("llave_publica", llavePublica))

    fun actualizarLlaveFirma(llaveFirma: String): Any? =
        pedir("/api/usuarios/llave-firma", "PUT", JSONObject().put("llave_firma", llaveFirma))

    fun publicarIdentidad(llavePublica: String, llaveFirma: String, respaldo: JSONObject? = null): Any?
    {
        val datos = JSONObject()
            .put("llave_publica", llavePublica)
            .put("llave_firma", llaveFirma)
        if (respaldo != null)
        {
            datos.put("respaldo", respaldo)
        }
        return pedir("/api/usuarios/identidad", "PUT", datos)
    }

    fun guardarPushToken(token: String, plataforma: String): Any? =
        pedir("/api/usuarios/push-token", "PUT", JSONObject().put("token", token).put("plataforma", plataforma))

    fun crearGrupo(nombre: String, miembros: List<String>): Any? =
        pedir("/api/grupos", "POST", JSONObject().put("nombre", nombre).put("miembros", JSONArray(miembros)))

    fun misGrupos(): Any? = pedir("/api/grupos")

    fun infoGrupo(grupoId: String): Any? = pedir("/api/grupos/$grupoId")

    fun historialGrupo(grupoId: String, antes: String? = null): Any? =
        pedir("/api/grupos/$grupoId/historial" + colaAntes(antes))

    fun salirGrupo(grupoId: String): Any? = pedir("/api/grupos/$grupoId/salir", "POST")

    fun renombrarGrupo(grupoId: String, nombre: String): Any? =
        pedir("/api/grupos/$grupoId", "PATCH", JSONObject().put("nombre", nombre))

    fun avatarGrupo(grupoId: String, imagen: String, tipo: String): Any? =
        pedir("/api/grupos/$grupoId/avatar", "POST", JSONObject().put("imagen", imagen).put("tipo", tipo))

    fun agregarMiembros(grupoId: String, miembros: List<String>): Any? =
        pedir("/api/grupos/$grupoId/miembros", "POST", JSONObject().put("miembros", JSONArray(miembros)))

    fun expulsarMiembro(grupoId: String, userId: String): Any? =
        pedir("/api/grupos/$grupoId/miembros/$userId", "DELETE")

    fun cambiarRol(grupoId: String, userId: String, rol: String): Any? =
        pedir("/api/grupos/$grupoId/rol", "POST", JSONObject().put("user_id", userId).put("rol", rol))

    fun enviarGrupo(grupoId: String, clienteId: String, cifrados: Any, respuestaA: String? = null): Any? =
        pedir("/api/grupos/$grupoId/mensajes", "POST", JSONObject()
            .put("cliente_id", clienteId)
            .put("cifrados", cifrados)
            .put("respuesta_a", respuestaA ?: JSONObject.NULL))

    fun marcarLeidosGrupo(grupoId: String, ids: List<String>): Any? =
        pedir("/api/grupos/$grupoId/mensajes/leido", "POST", JSONObject().put("ids", JSONArray(ids)))

    fun reaccionarGrupo(grupoId: String, mensajeId: String, emoji: String): Any? =
        pedir("/api/grupos/$grupoId/mensajes/$mensajeId/reaccion", "POST", JSONObject().put("emoji", emoji))

    fun borrarMensajeGrupo(grupoId: String, mensajeId: String): Any? =
        pedir("/api/grupos/$grupoId/mensajes/$mensajeId", "DELETE")

    fun editarMensajeGrupo(grupoId: String, mensajeId: String, cifrados: Any): Any? =
        pedir("/api/grupos/$grupoId/mensajes/$mensajeId", "PUT", JSONObject().put("cifrados", cifrados))

    fun relayMensaje(sobre: Sobre): Any? =
        pedir("/api/mensajes/relay", "POST", JSONObject()
            .put("remitente_id", sobre.remitenteId)
            .put("destinatario_id", sobre.destinatarioId)
            .put("contenido_cifrado", sobre.contenidoCifrado)
            .put("nonce", sobre.nonce)
            .put("cliente_id", sobre.id)
            .put("firma", sobre.firma ?: JSONObject.NULL)
            .put("respuesta_a", sobre.respuestaA ?: JSONObject.NULL))

    fun subirRespaldo(respaldo: JSONObject): Any? = pedir("/api/usuarios/respaldo", "PUT", respaldo)

    fun obtenerRespaldo(): Any? = pedir("/api/usuarios/respaldo")

    fun historial(otroId: String, antes: String? = null): Any? =
        pedir("/api/mensajes/historial/$otroId" + colaAntes(antes))

    fun limpiarConversacion(otroId: String): Any? = pedir("/api/mensajes/conversacion/$otroId", "DELETE")

    fun eliminarAmigo(otroId: String): Any? = pedir("/api/amigos/$otroId", "DELETE")

    fun bloquear(userId: String): Any? =
        pedir("/api/amigos/bloquear", "POST", JSONObject().put("user_id", userId))

    fun bloqueados(): Any? = pedir("/api/amigos/bloqueados")

    fun desbloquear(userId: String): Any? =
        pedir("/api/amigos/desbloquear", "POST", JSONObject().put("user_id", userId))

    fun conversaciones(): Any? = pedir("/api/mensajes/conversaciones")

    fun presencia(userId: String): Any? = pedir("/api/usuarios/$userId/presencia")

    fun preferencias(): Any? = pedir("/api/usuarios/preferencias")

    fun actualizarPreferencias(datos: JSONObject): Any? = pedir("/api/usuarios/preferencias", "PATCH", datos)

    fun subirMediaConProgreso(archivo: File, onProgreso: ((Double) -> Unit)? = null): Any?
    {
        require(archivo.isFile) { "El archivo de media no existe" }
        val longitud = archivo.length()
        val cuerpo = object : RequestBody()
        {
            override fun contentType() = tipoBinario

            override fun contentLength() = longitud

            override fun writeTo(sink: BufferedSink)
            {
                var escrito = 0L
                val bufer = ByteArray(64 * 1024)
                archivo.inputStream().buffered().use { entrada ->
                    while (true)
                    {
                        val leidos = entrada.read(bufer)
                        if (leidos < 0) break
                        sink.write(bufer, 0, leidos)
                        escrito += leidos
                        if (longitud > 0)
                        {
                            onProgreso?.invoke((escrito.toDouble() / longitud).coerceAtMost(1.0))
                        }
                    }
                }
                if (escrito != longitud)
                {
                    throw IOException("El archivo cambio durante la subida")
                }
            }
        }
        val solicitud = Request.Builder()
            .url("$baseUrl/api/media/archivo")
            .header("Authorization", "Bearer ${token()}")
            .method("POST", cuerpo)
        return ejecutar(solicitud.build(), auth = true, clienteHttp = clienteMedia)
    }

    fun urlMedia(path: String): Any? = pedir("/api/media/url?path=${codificar(path)}")

    fun subirAvatar(imagen: String, tipo: String): Any? =
        pedir("/api/usuarios/avatar", "POST", JSONObject().put("imagen", imagen).put("tipo", tipo))

    fun miCodigo(): Any? = pedir("/api/usuarios/mi-codigo")

    fun usuarioPorCodigo(codigo: String): Any? = pedir("/api/usuarios/codigo/${codificar(codigo)}")

    fun amigos(): Any? = pedir("/api/amigos")

    fun solicitudes(): Any? = pedir("/api/amigos/solicitudes")

    fun solicitarAmigo(codigo: String): Any? =
        pedir("/api/amigos/solicitar", "POST", JSONObject().put("codigo", codigo))

    fun aceptarSolicitud(id: String): Any? =
        pedir("/api/amigos/aceptar", "POST", JSONObject().put("id", id))

    fun rechazarSolicitud(id: String): Any? =
        pedir("/api/amigos/rechazar", "POST", JSONObject().put("id", id))

    private fun pedir(ruta: String, metodo: String = "GET", cuerpo: JSONObject? = null, auth: Boolean = true): Any?
    {
        val solicitud = Request.Builder().url("$baseUrl$ruta")
        if (auth)
        {
            solicitud.header("Authorization", "Bearer ${token()}")
        }
        if (cuerpo != null)
        {
            solicitud.method(metodo, cuerpo.toString().toRequestBody(tipoJson))
        }
        else if (metodo != "GET")
        {
            solicitud.method(metodo, ByteArray(0).toRequestBody(null))
        }
        return ejecutar(solicitud.build(), auth)
    }

    private fun ejecutar(solicitud: Request, auth: Boolean, clienteHttp: OkHttpClient = cliente): Any?
    {
        val respuesta = try
        {
            clienteHttp.newCall(solicitud).execute()
        }
        catch (e: IOException)
        {
            throw ErrorApi(0, "No se pudo conectar con el backend")
        }
        respuesta.use { r ->
            if (!r.isSuccessful)
            {
                val detalle = try
                {
                    JSONObject(r.body?.string() ?: "").optString("detail")
                }
                catch (e: Exception)
                {
                    ""
                }
                val tokenActual = token()
                val esSesionActual = tokenActual != null &&
                    solicitud.header("Authorization") == "Bearer $tokenActual"
                if (r.code == 401 && auth && esSesionActual)
                {
                    alExpirarSesion()
                }
                throw ErrorApi(r.code, detalle.ifEmpty { "Error ${r.code}" })
            }
            if (r.code == 204)
            {
                return null
            }
            return JSONTokener(r.body!!.string()).nextValue()
        }
    }

    private fun colaAntes(antes: String?): String =
        if (antes.isNullOrEmpty()) "" else "?antes=${codificar(antes)}"

    private fun codificar(valor: String): String =
        URLEncoder.encode(valor, "UTF-8")
            .replace("+", "%20")
            .replace("%21", "!")
            .replace("%27", "'")
            .replace("%28", "(")
            .replace("%29", ")")
            .replace("%7E", "~")
}

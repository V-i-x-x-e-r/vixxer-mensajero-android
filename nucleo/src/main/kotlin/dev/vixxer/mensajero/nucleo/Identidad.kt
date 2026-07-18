package dev.vixxer.mensajero.nucleo

import java.security.SecureRandom
import java.util.Locale
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
    data class Nueva(
        val publicKey: String,
        val privateKey: String,
        val codigo: String,
        val respaldo: JSONObject,
    )

    data class FirmaNueva(val publicKey: String, val privateKey: String)

    data class RegistroPendiente(
        val usuario: String,
        val identidad: Nueva,
        val firma: FirmaNueva,
    )

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
        val nueva = prepararIdentidad()
        confirmarIdentidad(nueva)
        return nueva
    }

    fun prepararIdentidad(): Nueva
    {
        val (publica, secreta) = parCaja()
        val codigo = generarCodigoRecuperacion()
        return Nueva(publica, secreta, codigo, crearRespaldo(secreta, codigo))
    }

    fun confirmarIdentidad(nueva: Nueva)
    {
        recordarAnterior(nueva.privateKey)
        boveda.escribir(ClavesSeguras.CLAVE_PRIVADA, nueva.privateKey)
        boveda.escribir(ClavesSeguras.CLAVE_PUBLICA, nueva.publicKey)
        boveda.escribir(ClavesSeguras.CODIGO_RECUP, nueva.codigo)
        boveda.escribir(ClavesSeguras.RESPALDO_PENDIENTE, nueva.respaldo.toString())
        boveda.escribir(ClavesSeguras.CODIGO_PENDIENTE, nueva.codigo)
    }

    fun respaldoPendiente(): JSONObject?
    {
        val crudo = boveda.leer(ClavesSeguras.RESPALDO_PENDIENTE) ?: return null
        return checkNotNull(leerRespaldoArchivo(crudo)) { "El respaldo pendiente es invalido" }
    }

    fun tieneRespaldoPendiente(): Boolean =
        boveda.leer(ClavesSeguras.RESPALDO_PENDIENTE) != null

    fun confirmarRespaldoSubido()
    {
        boveda.borrar(ClavesSeguras.RESPALDO_PENDIENTE)
    }

    fun codigoPendiente(): String? = boveda.leer(ClavesSeguras.CODIGO_PENDIENTE)

    fun confirmarCodigoGuardado()
    {
        boveda.borrar(ClavesSeguras.CODIGO_PENDIENTE)
    }

    fun prepararRegistro(usuario: String): RegistroPendiente
    {
        val normalizado = normalizarUsuario(usuario)
        require(normalizado.isNotBlank()) { "El usuario es obligatorio" }
        val existente = registroPendiente()
        if (existente?.usuario == normalizado)
        {
            return existente
        }
        val nuevo = RegistroPendiente(normalizado, prepararIdentidad(), prepararFirma())
        val identidadJson = JSONObject()
            .put("publica", nuevo.identidad.publicKey)
            .put("privada", nuevo.identidad.privateKey)
            .put("codigo", nuevo.identidad.codigo)
            .put("respaldo", nuevo.identidad.respaldo)
        val firmaJson = JSONObject()
            .put("publica", nuevo.firma.publicKey)
            .put("privada", nuevo.firma.privateKey)
        boveda.escribir(
            ClavesSeguras.REGISTRO_PENDIENTE,
            JSONObject()
                .put("usuario", normalizado)
                .put("identidad", identidadJson)
                .put("firma", firmaJson)
                .toString(),
        )
        return nuevo
    }

    fun registroPendiente(): RegistroPendiente?
    {
        val crudo = boveda.leer(ClavesSeguras.REGISTRO_PENDIENTE) ?: return null
        return runCatching { leerRegistro(JSONObject(crudo)) }.getOrNull()
    }

    fun tieneRegistroPendiente(): Boolean =
        boveda.leer(ClavesSeguras.REGISTRO_PENDIENTE) != null

    fun confirmarRegistro(registro: RegistroPendiente)
    {
        confirmarIdentidad(registro.identidad)
        confirmarFirma(registro.firma)
    }

    fun borrarRegistroPendiente()
    {
        boveda.borrar(ClavesSeguras.REGISTRO_PENDIENTE)
    }

    fun restaurarDeRespaldo(respaldo: JSONObject?, codigo: String): String?
    {
        val nueva = prepararRestauracion(respaldo, codigo) ?: return null
        confirmarIdentidad(nueva)
        return nueva.publicKey
    }

    fun prepararRestauracion(respaldo: JSONObject?, codigo: String): Nueva?
    {
        if (respaldo == null || !respaldo.has("cifrado") || !respaldo.has("nonce") || !respaldo.has("salt"))
        {
            return null
        }
        val secreta = runCatching {
            Cripto.abrirRespaldo(
                respaldo.getString("cifrado"),
                respaldo.getString("nonce"),
                respaldo.getString("salt"),
                codigo,
            )
        }.getOrNull() ?: return null
        val publica = Cripto.aBase64(Cripto.publicaDeSecreta(Cripto.deBase64(secreta)))
        return Nueva(publica, secreta, codigo, JSONObject(respaldo.toString()))
    }

    fun prepararFirma(): FirmaNueva
    {
        val publica = boveda.leer(ClavesSeguras.CLAVE_FIRMA_PUBLICA)
        val privada = boveda.leer(ClavesSeguras.CLAVE_FIRMA_PRIVADA)
        if (publica != null && privada != null)
        {
            return FirmaNueva(publica, privada)
        }
        val (nuevaPublica, nuevaPrivada) = Cripto.parFirmaDeSemilla(azar(Cripto.TAMANO_CLAVE))
        return FirmaNueva(Cripto.aBase64(nuevaPublica), Cripto.aBase64(nuevaPrivada))
    }

    fun confirmarFirma(firma: FirmaNueva)
    {
        boveda.escribir(ClavesSeguras.CLAVE_FIRMA_PUBLICA, firma.publicKey)
        boveda.escribir(ClavesSeguras.CLAVE_FIRMA_PRIVADA, firma.privateKey)
    }

    fun descifrarConHistoricas(cifrado: String, nonce: String, publicaRemitente: String): String?
    {
        val actual = boveda.leer(ClavesSeguras.CLAVE_PRIVADA)
        val candidatas = (listOfNotNull(actual) + llavero.cargar()).distinct()
        for (privada in candidatas)
        {
            val abierto = runCatching {
                Cripto.descifrarTexto(cifrado, nonce, publicaRemitente, privada)
            }.getOrNull()
            if (abierto != null)
            {
                return abierto
            }
        }
        return null
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

    private fun leerRegistro(objeto: JSONObject): RegistroPendiente?
    {
        val usuario = objeto.getString("usuario")
        if (usuario != normalizarUsuario(usuario)) return null
        val identidadJson = objeto.getJSONObject("identidad")
        val respaldo = leerRespaldoArchivo(identidadJson.getJSONObject("respaldo").toString()) ?: return null
        val identidad = Nueva(
            publicKey = identidadJson.getString("publica"),
            privateKey = identidadJson.getString("privada"),
            codigo = identidadJson.getString("codigo"),
            respaldo = respaldo,
        )
        val restaurada = prepararRestauracion(respaldo, identidad.codigo) ?: return null
        if (restaurada.publicKey != identidad.publicKey || restaurada.privateKey != identidad.privateKey)
        {
            return null
        }
        val firmaJson = objeto.getJSONObject("firma")
        val firma = FirmaNueva(
            publicKey = firmaJson.getString("publica"),
            privateKey = firmaJson.getString("privada"),
        )
        val prueba = "vixxer-registro".toByteArray()
        val firmada = Cripto.firmar(prueba, Cripto.deBase64(firma.privateKey))
        if (!Cripto.verificarFirma(firmada, prueba, Cripto.deBase64(firma.publicKey))) return null
        return RegistroPendiente(usuario, identidad, firma)
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

        fun normalizarUsuario(usuario: String): String = usuario.trim().lowercase(Locale.ROOT)
    }
}

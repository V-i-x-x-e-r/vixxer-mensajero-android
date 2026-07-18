package dev.vixxer.mensajero.nucleo

import org.json.JSONArray

object ClavesSeguras
{
    const val TOKEN = "vixxer_token"
    const val MI_ID = "vixxer_mi_id"
    const val CLAVE_PRIVADA = "vixxer_clave_privada"
    const val CLAVE_PUBLICA = "vixxer_clave_publica"
    const val CLAVE_FIRMA_PRIVADA = "vixxer_clave_firma_privada"
    const val CLAVE_FIRMA_PUBLICA = "vixxer_clave_firma_publica"
    const val CODIGO_RECUP = "vixxer_codigo_recup"
    const val LLAVES_PASADAS = "vixxer_llaves_pasadas"
    const val RESPALDO_PENDIENTE = "vixxer_respaldo_pendiente"
    const val CODIGO_PENDIENTE = "vixxer_codigo_pendiente"
    const val REGISTRO_PENDIENTE = "vixxer_registro_pendiente"
}

class AlmacenPorCuenta(
    private val almacen: Almacen,
    private val globales: Set<String> = emptySet(),
) : Almacen
{
    @Volatile
    private var cuentaId: String? = null

    fun cambiarCuenta(nuevaCuentaId: String?)
    {
        cuentaId = nuevaCuentaId
    }

    fun migrarLegado(claves: Collection<String>)
    {
        val cuenta = cuentaId ?: return
        for (clave in claves)
        {
            if (clave in globales || clave.startsWith(PREFIJO_CUENTA))
            {
                continue
            }
            val valor = almacen.leer(clave) ?: continue
            val destino = claveDeCuenta(cuenta, clave)
            if (almacen.leer(destino) == null)
            {
                almacen.escribir(destino, valor)
            }
            almacen.borrar(clave)
        }
    }

    fun migrarLegadoSiCoincide(clave: String, valor: String, claves: Collection<String>): Boolean
    {
        if (cuentaId == null)
        {
            return false
        }
        if (almacen.leer(clave) != valor)
        {
            return false
        }
        val valorDeCuenta = leer(clave)
        if (valorDeCuenta != null && valorDeCuenta != valor)
        {
            return false
        }
        migrarLegado(claves)
        return true
    }

    fun clavesDeCuenta(claves: Collection<String>): Set<String>
    {
        val cuenta = cuentaId ?: return emptySet()
        val prefijo = claveDeCuenta(cuenta, "")
        return claves
            .filter { it.startsWith(prefijo) }
            .map { it.removePrefix(prefijo) }
            .toSet()
    }

    override fun leer(clave: String): String?
    {
        val real = claveReal(clave) ?: return null
        return almacen.leer(real)
    }

    override fun escribir(clave: String, valor: String)
    {
        val real = claveReal(clave) ?: return
        almacen.escribir(real, valor)
    }

    override fun borrar(clave: String)
    {
        val real = claveReal(clave) ?: return
        almacen.borrar(real)
    }

    private fun claveReal(clave: String): String?
    {
        if (clave in globales)
        {
            return clave
        }
        val cuenta = cuentaId ?: return null
        return claveDeCuenta(cuenta, clave)
    }

    private fun claveDeCuenta(cuenta: String, clave: String): String = "$PREFIJO_CUENTA$cuenta:$clave"

    companion object
    {
        private const val PREFIJO_CUENTA = "vixxer_cuenta:"
    }
}

class LlavesPasadas(private val almacen: Almacen)
{
    fun cargar(): List<String>
    {
        val crudo = almacen.leer(ClavesSeguras.LLAVES_PASADAS) ?: return emptyList()
        return try
        {
            val arreglo = JSONArray(crudo)
            (0 until arreglo.length()).map { arreglo.optString(it) }
        }
        catch (e: Exception)
        {
            emptyList()
        }
    }

    fun recordar(privada: String?)
    {
        if (privada.isNullOrEmpty())
        {
            return
        }
        val memoria = cargar()
        if (privada in memoria)
        {
            return
        }
        val nueva = (listOf(privada) + memoria).take(5)
        almacen.escribir(ClavesSeguras.LLAVES_PASADAS, JSONArray(nueva).toString())
    }
}

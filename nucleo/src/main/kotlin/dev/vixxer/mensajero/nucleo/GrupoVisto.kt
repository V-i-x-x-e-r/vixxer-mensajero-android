package dev.vixxer.mensajero.nucleo

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import org.json.JSONObject

class GrupoVisto(private val almacen: Almacen)
{
    private val clave = "vixxer_grupos_visto"
    private val formatoIso = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)

    fun leerVistos(): MutableMap<String, String> = mapaDe(almacen.leer(clave))

    fun marcarVisto(grupoId: String, ahora: Instant = Instant.now())
    {
        val mapa = leerVistos()
        mapa[grupoId] = formatoIso.format(ahora)
        almacen.escribir(clave, JSONObject(mapa as Map<*, *>).toString())
    }
}

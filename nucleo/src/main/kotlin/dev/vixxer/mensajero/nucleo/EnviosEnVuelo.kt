package dev.vixxer.mensajero.nucleo

import java.util.concurrent.ConcurrentHashMap

class EnviosEnVuelo
{
    private val claves = ConcurrentHashMap.newKeySet<String>()

    fun tomar(clave: String): Boolean = claves.add(clave)

    fun liberar(clave: String)
    {
        claves.remove(clave)
    }
}

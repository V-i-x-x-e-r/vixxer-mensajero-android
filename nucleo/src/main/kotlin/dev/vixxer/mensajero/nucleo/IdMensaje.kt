package dev.vixxer.mensajero.nucleo

import java.util.UUID

object IdMensaje
{
    fun nuevo(): String = UUID.randomUUID().toString()

    fun esValido(valor: String): Boolean = runCatching {
        UUID.fromString(valor).toString() == valor.lowercase()
    }.getOrDefault(false)
}

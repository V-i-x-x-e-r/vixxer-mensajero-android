package dev.vixxer.mensajero.nucleo

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object Fechas
{
    private val formatoHora = DateTimeFormatter.ofPattern("HH:mm")
    private val formatoDia = DateTimeFormatter.ofPattern("d/M/yyyy")

    fun aInstante(iso: String?): Instant
    {
        if (iso.isNullOrEmpty())
        {
            return Instant.now()
        }
        return try
        {
            Instant.parse(iso)
        }
        catch (e: Exception)
        {
            try
            {
                OffsetDateTime.parse(iso).toInstant()
            }
            catch (e2: Exception)
            {
                LocalDateTime.parse(iso).atZone(ZoneId.systemDefault()).toInstant()
            }
        }
    }

    fun mismoDia(a: String?, b: String?, zona: ZoneId = ZoneId.systemDefault()): Boolean =
        diaDe(a, zona) == diaDe(b, zona)

    fun etiquetaDia(iso: String?, zona: ZoneId = ZoneId.systemDefault(), ahora: Instant = Instant.now()): String
    {
        val dia = diaDe(iso, zona)
        val hoy = ahora.atZone(zona).toLocalDate()
        return when (dia)
        {
            hoy -> "Hoy"
            hoy.minusDays(1) -> "Ayer"
            else -> dia.format(formatoDia)
        }
    }

    fun hora(iso: String?, zona: ZoneId = ZoneId.systemDefault()): String =
        aInstante(iso).atZone(zona).format(formatoHora)

    private fun diaDe(iso: String?, zona: ZoneId): LocalDate = aInstante(iso).atZone(zona).toLocalDate()
}

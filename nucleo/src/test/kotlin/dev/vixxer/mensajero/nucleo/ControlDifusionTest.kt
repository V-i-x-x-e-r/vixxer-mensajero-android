package dev.vixxer.mensajero.nucleo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ControlDifusionTest
{
    private var ahora = 1_000_000L
    private val almacen = AlmacenEnMemoria()
    private val control = ControlDifusion(almacen) { ahora }

    @Test
    fun porOmisionSoloRecibeDeAmigos()
    {
        assertEquals(ControlDifusion.Modo.SOLO_AMIGOS, control.modo())
        assertTrue(control.aceptaDe("u-beto", esAmigo = true))
        assertFalse(control.aceptaDe("u-desconocido", esAmigo = false))
    }

    @Test
    fun enModoNadieNiSiquieraLosAmigosPasan()
    {
        control.fijarModo(ControlDifusion.Modo.NADIE)

        assertFalse(control.aceptaDe("u-beto", esAmigo = true))
    }

    @Test
    fun elModoTodosSeApagaSoloALosDiezMinutos()
    {
        control.fijarModo(ControlDifusion.Modo.TODOS)
        assertTrue(control.aceptaDe("u-desconocido", esAmigo = false))
        assertEquals(600, control.segundosRestantesDeTodos())

        ahora += ControlDifusion.MILIS_TODOS

        assertEquals(ControlDifusion.Modo.SOLO_AMIGOS, control.modo())
        assertFalse(control.aceptaDe("u-desconocido", esAmigo = false))
        assertEquals(0, control.segundosRestantesDeTodos())
    }

    @Test
    fun soloSeAceptaUnaOfertaPendientePorEmisor()
    {
        assertTrue(control.registrarOferta("u-beto", "of-1"))
        assertFalse(control.registrarOferta("u-beto", "of-2"))
        assertTrue(control.registrarOferta("u-carla", "of-3"))
    }

    @Test
    fun reintentarLaMismaOfertaNoCuentaComoOtra()
    {
        assertTrue(control.registrarOferta("u-beto", "of-1"))
        assertTrue(control.registrarOferta("u-beto", "of-1"))
    }

    @Test
    fun alCerrarLaOfertaSePuedeRecibirOtra()
    {
        control.registrarOferta("u-beto", "of-1")
        control.cerrarOferta("u-beto")

        assertTrue(control.registrarOferta("u-beto", "of-2"))
    }

    @Test
    fun laOfertaPendienteCaducaSola()
    {
        control.registrarOferta("u-beto", "of-1")
        ahora += ControlDifusion.MILIS_OFERTA

        assertTrue(control.registrarOferta("u-beto", "of-2"))
    }

    @Test
    fun dosRechazosSilencianAlEmisorUnaHora()
    {
        control.registrarRechazo("u-necio")
        assertFalse(control.silenciado("u-necio"))

        control.registrarRechazo("u-necio")

        assertTrue(control.silenciado("u-necio"))
        assertFalse(control.aceptaDe("u-necio", esAmigo = true))
    }

    @Test
    fun elSilencioSeLevantaSolo()
    {
        control.registrarRechazo("u-necio")
        control.registrarRechazo("u-necio")
        ahora += ControlDifusion.MILIS_SILENCIO

        assertFalse(control.silenciado("u-necio"))
        assertTrue(control.aceptaDe("u-necio", esAmigo = true))
    }

    @Test
    fun elSilencioGanaSobreElModoTodos()
    {
        control.fijarModo(ControlDifusion.Modo.TODOS)
        control.registrarRechazo("u-necio")
        control.registrarRechazo("u-necio")

        assertFalse(control.aceptaDe("u-necio", esAmigo = false))
        assertTrue(control.aceptaDe("u-otro", esAmigo = false))
    }

    @Test
    fun elSilencioSobreviveAReiniciarLaApp()
    {
        control.registrarRechazo("u-necio")
        control.registrarRechazo("u-necio")

        val otraSesion = ControlDifusion(almacen) { ahora }

        assertTrue(otraSesion.silenciado("u-necio"))
    }

    @Test
    fun elEstadoNoCreceSinLimite()
    {
        for (i in 1..ControlDifusion.TOPE_EMISORES + 50)
        {
            control.registrarOferta("u-$i", "of-$i")
        }
        ahora += ControlDifusion.MILIS_OFERTA

        control.registrarOferta("u-final", "of-final")

        assertTrue(almacen.leer("vixxer_difusion_emisores")!!.length < 20_000)
    }
}

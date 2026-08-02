package dev.vixxer.mensajero.ble

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PeersCercaniaTest
{
    @Test
    fun conservaPeerEnElLimite()
    {
        assertTrue(esPeerVigente(70_000L, 100_000L))
    }

    @Test
    fun descartaPeerFueraDelLimite()
    {
        assertFalse(esPeerVigente(69_999L, 100_000L))
    }
}

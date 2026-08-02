package dev.vixxer.mensajero.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.vixxer.mensajero.nucleo.AlmacenEnMemoria
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = android.app.Application::class)
class ControlesTest
{
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun limpiarBusquedaVaciaElCampo()
    {
        var busqueda by mutableStateOf("Vixxer")

        compose.setContent {
            val tema = EstadoTema(AlmacenEnMemoria(), oscuroSistema = false)
            CompositionLocalProvider(LocalTema provides tema) {
                CampoBusqueda(
                    valor = busqueda,
                    alCambiar = { busqueda = it },
                )
            }
        }

        compose.onNodeWithContentDescription("Limpiar búsqueda").performClick()
        compose.runOnIdle { assertEquals("", busqueda) }
    }

    @Test
    fun botonTemaAlternaLaApariencia()
    {
        val tema = EstadoTema(AlmacenEnMemoria(), oscuroSistema = false)

        compose.setContent {
            CompositionLocalProvider(LocalTema provides tema) {
                BotonTema()
            }
        }

        compose.onNodeWithContentDescription("Activar tema oscuro").performClick()
        compose.runOnIdle { assertEquals("oscuro", tema.nombre) }
    }

    @Test
    fun botonCircularEjecutaLaAccion()
    {
        var pulsaciones = 0

        compose.setContent {
            val tema = EstadoTema(AlmacenEnMemoria(), oscuroSistema = false)
            CompositionLocalProvider(LocalTema provides tema) {
                BotonCircularPrimario(
                    descripcion = "Enviar mensaje",
                    alPulsar = { pulsaciones++ },
                )
                {
                    Enviar(color = tema.colores.botonTexto)
                }
            }
        }

        compose.onNodeWithContentDescription("Enviar mensaje").performClick()
        compose.runOnIdle { assertEquals(1, pulsaciones) }
    }

    @Test
    fun cabeceraAbreAjustes()
    {
        var aperturas = 0

        compose.setContent {
            val tema = EstadoTema(AlmacenEnMemoria(), oscuroSistema = false)
            CompositionLocalProvider(LocalTema provides tema) {
                CabeceraMensajero(
                    estado = "conectado",
                    conectado = true,
                    alAbrirAjustes = { aperturas++ },
                )
            }
        }

        compose.onNodeWithContentDescription("Abrir ajustes").performClick()
        compose.runOnIdle { assertEquals(1, aperturas) }
    }

    @Test
    fun barraCambiaDePestana()
    {
        var destino = "chats"

        compose.setContent {
            val tema = EstadoTema(AlmacenEnMemoria(), oscuroSistema = false)
            CompositionLocalProvider(LocalTema provides tema) {
                BarraPestanas(actual = destino, alCambiar = { destino = it })
            }
        }

        compose.onNodeWithText("Grupos").performClick()
        compose.runOnIdle { assertEquals("grupos", destino) }
    }
}

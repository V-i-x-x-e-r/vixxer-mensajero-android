package dev.vixxer.mensajero.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import dev.vixxer.mensajero.ble.PeerCercano
import dev.vixxer.mensajero.nucleo.AlmacenEnMemoria
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [29], application = android.app.Application::class, qualifiers = "w411dp-h891dp-xhdpi")
class CapturaRadarTest
{
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun radarActivo()
    {
        prepararRadar {}

        compose.mainClock.advanceTimeBy(1800)
        compose
            .onNodeWithTag("radar")
            .captureRoboImage("src/test/capturas/radar-activo.png", roborazziOptions = OPCIONES_CAPTURA)
    }

    @Test
    fun marcaCentralDetieneElRadar()
    {
        var toques = 0
        prepararRadar { toques += 1 }

        compose.onNodeWithContentDescription("Detener radar de cercanía").performClick()
        compose.runOnIdle { assertEquals(1, toques) }
    }

    private fun prepararRadar(alTocar: () -> Unit)
    {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val tema = EstadoTema(AlmacenEnMemoria(), oscuroSistema = true)
            CompositionLocalProvider(LocalTema provides tema)
            {
                Box(
                    modifier = Modifier
                        .testTag("radar")
                        .background(tema.colores.fondo)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                )
                {
                    RadarLienzo(
                        corriendo = true,
                        color = Color(0xFF22C55E),
                        peers = peersMuestra(),
                        colores = tema.colores,
                        alTocar = alTocar,
                    )
                }
            }
        }
    }

    private fun peersMuestra(): List<PeerCercano> = listOf(
        PeerCercano(id = "vixxer-nodo-a1", rssi = -52, visto = 1L, amigoId = "amigo-a", nombre = "Ana"),
        PeerCercano(id = "vixxer-nodo-b2", rssi = -71, visto = 1L),
        PeerCercano(id = "vixxer-nodo-c3", rssi = -84, visto = 1L),
    )
}

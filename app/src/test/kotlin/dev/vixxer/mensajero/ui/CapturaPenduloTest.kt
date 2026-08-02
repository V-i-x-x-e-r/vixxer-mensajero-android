package dev.vixxer.mensajero.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import dev.vixxer.mensajero.nucleo.AlmacenEnMemoria
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private class EscalaMovimientoPrueba(var valor: Float = 1f) : MotionDurationScale
{
    override val scaleFactor: Float
        get() = valor
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [29], application = android.app.Application::class, qualifiers = "w411dp-h891dp-xhdpi")
class CapturaPenduloTest
{
    private val escalaMovimiento = EscalaMovimientoPrueba()

    @get:Rule
    val compose = createComposeRule(effectContext = escalaMovimiento)

    @Test
    fun penduloEnClaro()
    {
        capturar(oscuro = false, nombre = "pendulo-claro")
    }

    @Test
    fun penduloEnOscuro()
    {
        capturar(oscuro = true, nombre = "pendulo-oscuro")
    }

    @Test
    fun splashDuranteLaOrbita()
    {
        capturarSplash(780, "splash-orbita")
    }

    @Test
    fun splashAlConverger()
    {
        capturarSplash(1700, "splash-convergido")
    }

    @Test
    fun marcaOrbitalEnElRadar()
    {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Box(
                modifier = Modifier
                    .testTag("marca-radar")
                    .background(Color(0xFF121212))
                    .padding(12.dp),
                contentAlignment = Alignment.Center,
            )
            {
                MarcaOrbital(lado = 112.dp)
            }
        }

        compose.mainClock.advanceTimeBy(1700)

        compose
            .onNodeWithTag("marca-radar")
            .captureRoboImage("src/test/capturas/marca-radar.png", roborazziOptions = OPCIONES_CAPTURA)
    }

    @Test
    fun penduloQuedaQuietoSiLasAnimacionesEstanDesactivadas()
    {
        escalaMovimiento.valor = 0f
        compose.mainClock.autoAdvance = false
        compose.setContent {
            LogoPendulo(alto = 132.dp, colorTexto = androidx.compose.ui.graphics.Color.White)
        }

        compose.mainClock.advanceTimeBy(10_000)

        compose.onNodeWithContentDescription("Vixxer").fetchSemanticsNode()
    }

    private fun capturar(oscuro: Boolean, nombre: String)
    {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val tema = EstadoTema(AlmacenEnMemoria(), oscuroSistema = oscuro)
            CompositionLocalProvider(LocalTema provides tema) {
                Box(
                    modifier = Modifier
                        .background(tema.coloresAuth.fondo)
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                )
                {
                    LogoPendulo(alto = 132.dp, colorTexto = tema.coloresAuth.texto)
                }
            }
        }
        compose.mainClock.advanceTimeBy(500)
        compose.onRoot().captureRoboImage("src/test/capturas/$nombre.png", roborazziOptions = OPCIONES_CAPTURA)
    }

    private fun capturarSplash(instante: Long, nombre: String)
    {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            SplashOrbita(
                listoParaSalir = false,
                alTerminar = {},
            )
        }
        compose.mainClock.advanceTimeBy(instante)
        compose.onRoot().captureRoboImage("src/test/capturas/$nombre.png", roborazziOptions = OPCIONES_CAPTURA)
    }
}

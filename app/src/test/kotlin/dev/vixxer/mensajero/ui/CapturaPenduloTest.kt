package dev.vixxer.mensajero.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
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

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [29], application = android.app.Application::class, qualifiers = "w411dp-h891dp-xhdpi")
class CapturaPenduloTest
{
    @get:Rule
    val compose = createComposeRule()

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
}

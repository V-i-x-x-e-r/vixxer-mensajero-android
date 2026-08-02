package dev.vixxer.mensajero.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import dev.vixxer.mensajero.AplicacionVixxer
import dev.vixxer.mensajero.nucleo.AlmacenEnMemoria
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private class EscalaMovimientoAcceso : MotionDurationScale
{
    override val scaleFactor: Float = 0f
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [29], application = android.app.Application::class, qualifiers = "w411dp-h891dp-xhdpi")
class CapturaAccesoTest
{
    @get:Rule
    val compose = createComposeRule(effectContext = EscalaMovimientoAcceso())

    @Test
    fun accesoEnClaro()
    {
        capturar(oscuro = false, nombre = "acceso-claro")
    }

    @Test
    fun accesoEnOscuro()
    {
        capturar(oscuro = true, nombre = "acceso-oscuro")
    }

    private fun capturar(oscuro: Boolean, nombre: String)
    {
        val tema = EstadoTema(AlmacenEnMemoria(), oscuroSistema = oscuro)
        compose.setContent {
            CompositionLocalProvider(LocalTema provides tema)
            {
                PantallaLogin(AplicacionVixxer(), alNavegar = {})
            }
        }

        compose.onRoot().captureRoboImage("src/test/capturas/$nombre.png", roborazziOptions = OPCIONES_CAPTURA)
    }
}

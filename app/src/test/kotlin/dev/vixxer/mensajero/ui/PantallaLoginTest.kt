package dev.vixxer.mensajero.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import dev.vixxer.mensajero.AplicacionVixxer
import dev.vixxer.mensajero.nucleo.AlmacenEnMemoria
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class, qualifiers = "w411dp-h891dp-xhdpi")
class PantallaLoginTest
{
    @get:Rule
    val compose = createComposeRule(
        effectContext = object : MotionDurationScale
        {
            override val scaleFactor = 0f
        },
    )

    @Test
    fun botonDeTemaRecibeToquesSobreElContenido()
    {
        val tema = EstadoTema(AlmacenEnMemoria(), oscuroSistema = false)

        compose.setContent {
            CompositionLocalProvider(LocalTema provides tema) {
                PantallaLogin(AplicacionVixxer(), alNavegar = {})
            }
        }

        compose
            .onNodeWithContentDescription("Activar tema oscuro")
            .assertHasClickAction()
            .performTouchInput { click() }

        assertEquals("oscuro", tema.nombre)
    }

    @Test
    fun botonDeTemaFuncionaAlCrearUnaCuenta()
    {
        val tema = EstadoTema(AlmacenEnMemoria(), oscuroSistema = false)

        compose.setContent {
            CompositionLocalProvider(LocalTema provides tema) {
                PantallaRegistro(AplicacionVixxer(), alNavegar = {})
            }
        }

        compose
            .onNodeWithContentDescription("Activar tema oscuro")
            .assertHasClickAction()
            .performTouchInput { click() }

        assertEquals("oscuro", tema.nombre)
    }
}

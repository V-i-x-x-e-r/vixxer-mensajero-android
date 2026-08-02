package dev.vixxer.mensajero.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
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
class CapturaCompositorTest
{
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun compositorSinBlur()
    {
        capturar("compositor-sdk29")
    }

    @Test
    @Config(sdk = [33], application = android.app.Application::class, qualifiers = "w411dp-h891dp-xhdpi")
    fun compositorConBlur()
    {
        capturar("compositor-sdk33")
    }

    private fun capturar(nombre: String)
    {
        compose.setContent {
            val tema = EstadoTema(AlmacenEnMemoria(), oscuroSistema = true)
            val haze = recordarHaze()
            CompositionLocalProvider(LocalTema provides tema, LocalHazeState provides haze)
            {
                Box {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .fondoDesenfocable(haze)
                            .background(tema.colores.fondo),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    )
                    {
                        CampoMensaje(
                            valor = "Mensaje listo",
                            alCambiar = {},
                            modifier = Modifier.weight(1f),
                        )
                        BotonCircularVidrio(descripcion = "Grabar nota de voz", alPulsar = {})
                        {
                            Microfono(color = tema.colores.texto)
                        }
                        BotonCircularPrimario(descripcion = "Enviar mensaje", alPulsar = {})
                        {
                            Enviar(color = tema.colores.botonTexto)
                        }
                    }
                }
            }
        }

        compose.onRoot().captureRoboImage("src/test/capturas/$nombre.png", roborazziOptions = OPCIONES_CAPTURA)
    }
}

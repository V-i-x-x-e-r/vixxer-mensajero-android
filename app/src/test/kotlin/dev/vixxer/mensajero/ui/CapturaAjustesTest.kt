package dev.vixxer.mensajero.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
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
class CapturaAjustesTest
{
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun ajustesEnOscuro()
    {
        capturar(temaNombre = "oscuro", nombre = "ajustes-oscuro")
    }

    @Test
    fun ajustesEnClaro()
    {
        capturar(temaNombre = "claro", nombre = "ajustes-claro")
    }

    @Test
    fun ajustesEnColorido()
    {
        capturar(temaNombre = "colorido", nombre = "ajustes-colorido")
    }

    private fun capturar(temaNombre: String, nombre: String)
    {
        compose.setContent {
            val tema = EstadoTema(AlmacenEnMemoria(), oscuroSistema = false)
            tema.elegirTema(temaNombre)
            CompositionLocalProvider(LocalTema provides tema) {
                Ajustes(tema.colores)
            }
        }
        compose.onRoot().captureRoboImage("src/test/capturas/$nombre.png", roborazziOptions = OPCIONES_CAPTURA)
    }
}

@Composable
private fun Ajustes(colores: Paleta)
{
    Column(
        modifier = Modifier
            .fondoVixxer()
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    )
    {
        Seccion("CUENTA", colores)
        Tarjeta {
            FilaNav("Mi código de amigo", colores) {}
            Separador(colores)
            FilaValor("Copia de seguridad", "Diaria, 3:00", colores) {}
            Separador(colores)
            FilaNav("Cambiar contraseña", colores) {}
        }
        Seccion("PRIVACIDAD", colores)
        Tarjeta {
            FilaSwitch("Acuses de lectura", true, colores) {}
            Separador(colores)
            FilaSwitch("Bloquear capturas", false, colores) {}
            Separador(colores)
            FilaValor("Bloqueados", "2", colores) {}
            Separador(colores)
            FilaValor("Biométrico", "No disponible", colores, apagada = true) {}
        }
    }
}

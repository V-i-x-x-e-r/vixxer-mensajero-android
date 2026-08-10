package dev.vixxer.mensajero.ui

import androidx.compose.foundation.layout.Column
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
class CapturaBorrarCuentaTest
{
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun borrarCuentaEnOscuro()
    {
        capturar("oscuro", "", false, "borrar-cuenta-oscuro")
    }

    @Test
    fun borrarCuentaEnClaro()
    {
        capturar("claro", "", false, "borrar-cuenta-claro")
    }

    @Test
    fun borrarCuentaEnColorido()
    {
        capturar("colorido", "", false, "borrar-cuenta-colorido")
    }

    @Test
    fun borrarCuentaConErrorDeContrasena()
    {
        capturar("oscuro", "La contraseña no es correcta", false, "borrar-cuenta-error")
    }

    @Test
    fun borrarCuentaEnCurso()
    {
        capturar("oscuro", "", true, "borrar-cuenta-en-curso")
    }

    private fun capturar(temaNombre: String, estado: String, enCurso: Boolean, nombre: String)
    {
        compose.setContent {
            val tema = EstadoTema(AlmacenEnMemoria(), oscuroSistema = false)
            tema.elegirTema(temaNombre)
            CompositionLocalProvider(LocalTema provides tema) {
                Column(
                    modifier = Modifier
                        .fondoVixxer()
                        .fillMaxWidth()
                        .padding(16.dp),
                )
                {
                    ContenidoBorrarCuenta(
                        colores = tema.colores,
                        contrasena = if (enCurso) "secreta" else "",
                        estado = estado,
                        enCurso = enCurso,
                        alCambiarContrasena = {},
                        alBorrar = {},
                        alCerrar = {},
                    )
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/capturas/$nombre.png", roborazziOptions = OPCIONES_CAPTURA)
    }
}

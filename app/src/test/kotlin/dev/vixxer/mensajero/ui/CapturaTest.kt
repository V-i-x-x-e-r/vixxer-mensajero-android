package dev.vixxer.mensajero.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
class CapturaSinBlurTest
{
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun panelesEnOscuroSinBlur()
    {
        capturar(compose, oscuro = true, nombre = "sdk29-oscuro")
    }

    @Test
    fun panelesEnClaroSinBlur()
    {
        capturar(compose, oscuro = false, nombre = "sdk29-claro")
    }

    @Test
    fun avataresSinFotoCaenAlMismoRespaldo()
    {
        compose.setContent {
            val tema = EstadoTema(AlmacenEnMemoria(), oscuroSistema = true)
            CompositionLocalProvider(LocalTema provides tema) {
                Row(
                    modifier = Modifier
                        .background(tema.colores.fondo)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                )
                {
                    Avatar(nombre = "Familia", tamano = 44.dp)
                    Avatar(nombre = "Equipo Vixxer", tamano = 44.dp)
                    Avatar(nombre = "César", tamano = 44.dp)
                    Avatar(nombre = "Sergio", tamano = 44.dp)
                    Avatar(nombre = "", tamano = 44.dp)
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/capturas/avatares-respaldo.png", roborazziOptions = OPCIONES_CAPTURA)
    }

    @Test
    fun dialogoConEtiquetasDeDistintoLargo()
    {
        compose.setContent {
            val tema = EstadoTema(AlmacenEnMemoria(), oscuroSistema = true)
            CompositionLocalProvider(LocalTema provides tema) {
                Confirmacion(
                    visible = true,
                    titulo = "Empezar de nuevo",
                    mensaje = "Sin tu código de recuperación perderás para siempre el acceso a los mensajes anteriores, tuyos y los de tus chats. Esto no se puede deshacer.",
                    textoConfirmar = "Crear identidad nueva",
                    destructivo = true,
                    alConfirmar = {},
                    alCancelar = {},
                )
            }
        }
        compose.onNode(isDialog()).captureRoboImage("src/test/capturas/dialogo-botones.png", roborazziOptions = OPCIONES_CAPTURA)
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = android.app.Application::class, qualifiers = "w411dp-h891dp-xhdpi")
class CapturaConBlurTest
{
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun panelesEnOscuroConBlur()
    {
        capturar(compose, oscuro = true, nombre = "sdk33-oscuro")
    }
}

private fun capturar(
    compose: androidx.compose.ui.test.junit4.ComposeContentTestRule,
    oscuro: Boolean,
    nombre: String,
)
{
    compose.setContent {
        val tema = EstadoTema(AlmacenEnMemoria(), oscuroSistema = oscuro)
        val haze = recordarHaze()
        CompositionLocalProvider(LocalTema provides tema, LocalHazeState provides haze) {
            Box(
                modifier = Modifier.fillMaxWidth(),
            )
            {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .fondoDesenfocable(haze)
                        .background(tema.colores.fondo),
                )
                Muestrario()
            }
        }
    }
    compose.onRoot().captureRoboImage("src/test/capturas/$nombre.png", roborazziOptions = OPCIONES_CAPTURA)
}

@Composable
private fun Muestrario()
{
    val colores = LocalTema.current.colores
    Column(
        modifier = Modifier
            .background(colores.fondo)
            .fillMaxWidth()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    )
    {
        Text("Tarjeta sobre el fondo", fontSize = 13.sp, fontFamily = FuenteOutfit, color = colores.muted)
        Box(modifier = Modifier.fillMaxWidth().panelVidrio(desenfocar = true).padding(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Texto principal",
                    fontSize = 16.sp,
                    fontFamily = FuenteOutfit,
                    fontWeight = FontWeight.SemiBold,
                    color = colores.texto,
                )
                Text("Texto secundario, para ver la jerarquía", fontSize = 13.sp, color = colores.muted)
            }
        }
        Box(modifier = Modifier.fillMaxWidth().panelVidrio(fuerte = true).padding(16.dp)) {
            Text("Panel fuerte", fontSize = 15.sp, fontFamily = FuenteOutfit, color = colores.texto)
        }
        Boton(titulo = "Botón primario", alPulsar = {})
    }
}

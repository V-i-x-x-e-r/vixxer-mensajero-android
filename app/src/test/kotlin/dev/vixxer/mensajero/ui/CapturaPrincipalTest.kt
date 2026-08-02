package dev.vixxer.mensajero.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
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
class CapturaPrincipalTest
{
    @get:Rule
    val compose = createComposeRule()

    @Test
    @Config(sdk = [26], application = android.app.Application::class, qualifiers = "w411dp-h891dp-xhdpi")
    fun principalAndroid8()
    {
        capturar("principal-sdk26", oscuro = false)
    }

    @Test
    fun principalAndroid10()
    {
        capturar("principal-sdk29", oscuro = false)
    }

    @Test
    fun principalOscuroAndroid10()
    {
        capturar("principal-oscuro-sdk29", oscuro = true)
    }

    @Test
    @Config(sdk = [33], application = android.app.Application::class, qualifiers = "w411dp-h891dp-xhdpi")
    fun principalAndroid13()
    {
        capturar("principal-sdk33", oscuro = false)
    }

    private fun capturar(nombre: String, oscuro: Boolean)
    {
        compose.setContent {
            val tema = EstadoTema(AlmacenEnMemoria(), oscuroSistema = oscuro)
            val haze = recordarHaze()
            CompositionLocalProvider(LocalTema provides tema, LocalHazeState provides haze)
            {
                Box(modifier = Modifier.fillMaxSize())
                {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .fondoDesenfocable(haze)
                            .fondoVixxer()
                            .padding(horizontal = 20.dp, vertical = 210.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    )
                    {
                        FilaPrincipal("Diseño", "Revisión de interfaz")
                        FilaPrincipal("Infraestructura", "Sincronización completa")
                        FilaPrincipal("Pruebas de campo", "Radar preparado")
                    }
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    )
                    {
                        CabeceraMensajero(
                            estado = "conectado",
                            conectado = true,
                            alAbrirAjustes = {},
                        )
                        CampoBusqueda(valor = "", alCambiar = {})
                    }
                    BarraPestanas(
                        actual = "chats",
                        alCambiar = {},
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }

        compose.onRoot().captureRoboImage("src/test/capturas/$nombre.png", roborazziOptions = OPCIONES_CAPTURA)
    }
}

@Composable
private fun FilaPrincipal(titulo: String, detalle: String)
{
    val colores = LocalTema.current.colores

    Column(modifier = Modifier.fillMaxWidth())
    {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        )
        {
            Avatar(nombre = titulo, tamano = 44.dp)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            )
            {
                Text(titulo, fontSize = 16.sp, color = colores.texto)
                Text(detalle, fontSize = 13.sp, color = colores.muted)
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 66.dp)
                .height(1.dp)
                .background(colores.borde),
        )
    }
}

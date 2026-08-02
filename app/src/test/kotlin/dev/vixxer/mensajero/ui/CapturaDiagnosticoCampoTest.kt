package dev.vixxer.mensajero.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import dev.vixxer.mensajero.nucleo.AlmacenEnMemoria
import dev.vixxer.mensajero.nucleo.DiagnosticoMesh
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [29], application = android.app.Application::class, qualifiers = "w411dp-h891dp-xhdpi")
class CapturaDiagnosticoCampoTest
{
    @get:Rule
    val compose = createComposeRule()

    private lateinit var zonaAnterior: TimeZone

    @Before
    fun prepararZona()
    {
        zonaAnterior = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun restaurarZona()
    {
        TimeZone.setDefault(zonaAnterior)
    }

    @Test
    fun diagnosticoEnOscuro()
    {
        capturar(oscuro = true, nombre = "diagnostico-campo-oscuro")
    }

    @Test
    fun diagnosticoEnClaro()
    {
        capturar(oscuro = false, nombre = "diagnostico-campo-claro")
    }

    @Test
    @Config(sdk = [29], application = android.app.Application::class, qualifiers = "w320dp-h640dp-xhdpi")
    fun diagnosticoEnPantallaCompacta()
    {
        capturar(oscuro = true, nombre = "diagnostico-campo-compacto")
    }

    @Test
    fun accionesResponden()
    {
        var compartidos = 0
        var limpiezas = 0
        compose.setContent {
            val tema = EstadoTema(AlmacenEnMemoria(), oscuroSistema = true)
            CompositionLocalProvider(LocalTema provides tema)
            {
                PanelDiagnosticoCampo(
                    estado = estadoMuestra(),
                    alCompartir = { compartidos += 1 },
                    alLimpiar = { limpiezas += 1 },
                    iniciarExpandido = true,
                )
            }
        }

        compose.onNodeWithText("Compartir").performClick()
        compose.onNodeWithText("Limpiar").performClick()

        assertEquals(1, compartidos)
        assertEquals(1, limpiezas)
    }

    @Test
    fun ocultaAccionesSinEventos()
    {
        compose.setContent {
            val tema = EstadoTema(AlmacenEnMemoria(), oscuroSistema = true)
            CompositionLocalProvider(LocalTema provides tema)
            {
                PanelDiagnosticoCampo(
                    estado = EstadoDiagnosticoCampo.vacio(),
                    alCompartir = {},
                    alLimpiar = {},
                    iniciarExpandido = true,
                )
            }
        }

        compose.onAllNodesWithText("Compartir").assertCountEquals(0)
        compose.onAllNodesWithText("Limpiar").assertCountEquals(0)
    }

    @Test
    fun diagnosticoIniciaPlegado()
    {
        compose.setContent {
            val tema = EstadoTema(AlmacenEnMemoria(), oscuroSistema = true)
            CompositionLocalProvider(LocalTema provides tema)
            {
                PanelDiagnosticoCampo(
                    estado = estadoMuestra(),
                    alCompartir = {},
                    alLimpiar = {},
                )
            }
        }

        compose.onNodeWithText("EVENTOS RECIENTES").assertDoesNotExist()
        compose.onNodeWithContentDescription("Mostrar diagnóstico de campo").performClick()
        compose.onNodeWithText("EVENTOS RECIENTES").assertExists()
    }

    private fun capturar(oscuro: Boolean, nombre: String)
    {
        compose.setContent {
            val tema = EstadoTema(AlmacenEnMemoria(), oscuroSistema = oscuro)
            CompositionLocalProvider(LocalTema provides tema)
            {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(tema.colores.fondo)
                        .padding(bottom = 18.dp),
                )
                {
                    PanelDiagnosticoCampo(
                        estado = estadoMuestra(),
                        alCompartir = {},
                        alLimpiar = {},
                        modifier = Modifier.testTag("diagnostico-campo"),
                        iniciarExpandido = true,
                    )
                }
            }
        }
        compose
            .onNodeWithTag("diagnostico-campo")
            .captureRoboImage(
                "src/test/capturas/$nombre.png",
                roborazziOptions = OPCIONES_CAPTURA,
            )
    }

    private fun estadoMuestra(): EstadoDiagnosticoCampo
    {
        val eventos = listOf(
            evento(
                hora = 1_700_000_003_000,
                etapa = DiagnosticoMesh.Etapa.ENVIADO,
                transporte = DiagnosticoMesh.Transporte.BLE,
                enlace = "l2cap",
                duracion = 184,
                saltos = 1,
            ),
            evento(
                hora = 1_700_000_002_000,
                etapa = DiagnosticoMesh.Etapa.REENVIADO,
                transporte = DiagnosticoMesh.Transporte.BLE,
                enlace = "gatt",
                duracion = 612,
                saltos = 2,
                reintentos = 1,
            ),
            evento(
                hora = 1_700_000_001_000,
                etapa = DiagnosticoMesh.Etapa.ENCOLADO,
                transporte = DiagnosticoMesh.Transporte.SIN_RUTA,
                cola = 3,
            ),
            evento(
                hora = 1_700_000_000_000,
                etapa = DiagnosticoMesh.Etapa.ERROR,
                transporte = DiagnosticoMesh.Transporte.WIFI,
                enlace = "wifi_direct",
                duracion = 1_430,
                error = DiagnosticoMesh.CodigoError.WIFI,
            ),
        )
        return EstadoDiagnosticoCampo(
            instantanea = DiagnosticoMesh.Instantanea(
                eventos = eventos,
                ultimaDuracionMs = 184,
                ultimoError = DiagnosticoMesh.CodigoError.WIFI,
                reintentos = 1,
            ),
            colaOutbox = 2,
            colaRelay = 3,
        )
    }

    private fun evento(
        hora: Long,
        etapa: DiagnosticoMesh.Etapa,
        transporte: DiagnosticoMesh.Transporte,
        enlace: String? = null,
        duracion: Long? = null,
        saltos: Int? = null,
        reintentos: Int = 0,
        cola: Int? = null,
        error: DiagnosticoMesh.CodigoError? = null,
    ): DiagnosticoMesh.Evento = DiagnosticoMesh.Evento(
        instanteMs = hora,
        mensaje = "7b9d42e16a0f",
        etapa = etapa,
        transporte = transporte,
        enlace = enlace,
        saltos = saltos,
        duracionMs = duracion,
        reintentos = reintentos,
        cola = cola,
        error = error,
    )
}

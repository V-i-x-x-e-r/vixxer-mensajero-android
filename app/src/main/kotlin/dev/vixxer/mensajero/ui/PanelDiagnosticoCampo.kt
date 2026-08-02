package dev.vixxer.mensajero.ui

import android.content.Context
import android.content.ClipData
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.core.content.FileProvider
import dev.vixxer.mensajero.AplicacionVixxer
import dev.vixxer.mensajero.BuildConfig
import dev.vixxer.mensajero.ble.MensajeriaBle
import dev.vixxer.mensajero.nucleo.DiagnosticoMesh
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val AZUL_DIAGNOSTICO = Color(0xFF38BDF8)
private val VERDE_DIAGNOSTICO = Color(0xFF22C55E)
private val AMBAR_DIAGNOSTICO = Color(0xFFF59E0B)
private val CORAL_DIAGNOSTICO = Color(0xFFFB7185)
private val FORMA_DIAGNOSTICO = RoundedCornerShape(8.dp)
private val FORMATO_HORA = DateTimeFormatter.ofPattern("HH:mm:ss")

internal data class EstadoDiagnosticoCampo(
    val instantanea: DiagnosticoMesh.Instantanea,
    val colaOutbox: Int,
    val colaRelay: Int,
)
{
    companion object
    {
        fun vacio(): EstadoDiagnosticoCampo = EstadoDiagnosticoCampo(
            DiagnosticoMesh.Instantanea(emptyList(), null, null, 0),
            0,
            0,
        )
    }
}

internal fun leerDiagnosticoCampo(app: AplicacionVixxer): EstadoDiagnosticoCampo =
    EstadoDiagnosticoCampo(
        instantanea = app.diagnosticoMesh.instantanea(),
        colaOutbox = app.outbox.leerTodos().size,
        colaRelay = MensajeriaBle.pendientesRelay(app),
    )

internal fun crearArchivoDiagnostico(
    contexto: Context,
    app: AplicacionVixxer,
    estado: EstadoDiagnosticoCampo,
): File
{
    val json = app.diagnosticoMesh.exportar(
        DiagnosticoMesh.ContextoExportacion(
            versionApp = BuildConfig.VERSION_NAME,
            modelo = "${Build.MANUFACTURER} ${Build.MODEL}",
            sdk = Build.VERSION.SDK_INT,
        ),
        colaOutbox = estado.colaOutbox,
        colaRelay = estado.colaRelay,
    )
    val carpeta = File(contexto.cacheDir, "diagnosticos")
    carpeta.mkdirs()
    return File(carpeta, "vixxer-diagnostico-${System.currentTimeMillis()}.json").apply {
        writeText(json)
    }
}

internal fun compartirDiagnostico(contexto: Context, archivo: File)
{
    val uri = FileProvider.getUriForFile(
        contexto,
        BuildConfig.APPLICATION_ID + ".archivos",
        archivo,
    )
    val envio = Intent(Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri("Diagnóstico de Vixxer", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val selector = Intent.createChooser(envio, "Compartir diagnóstico de Vixxer").apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    contexto.startActivity(selector)
}

@Composable
internal fun PanelDiagnosticoCampo(
    estado: EstadoDiagnosticoCampo,
    alCompartir: () -> Unit,
    alLimpiar: () -> Unit,
    modifier: Modifier = Modifier,
)
{
    val colores = LocalTema.current.colores
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 22.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    )
    {
        CabeceraDiagnostico(
            colores,
            estado.instantanea.eventos.isNotEmpty(),
            alCompartir,
            alLimpiar,
        )
        ResumenDiagnostico(estado, colores)
        estado.instantanea.ultimoError?.let {
            UltimoErrorDiagnostico(it, colores)
        }
        Text(
            text = "EVENTOS RECIENTES",
            fontSize = 10.sp,
            fontFamily = FuenteOutfit,
            fontWeight = FontWeight.SemiBold,
            color = colores.muted,
        )
        ListaEventosDiagnostico(estado.instantanea.eventos.take(8), colores)
    }
}

@Composable
private fun CabeceraDiagnostico(
    colores: Paleta,
    hayEventos: Boolean,
    alCompartir: () -> Unit,
    alLimpiar: () -> Unit,
)
{
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    )
    {
        Text(
            text = "DIAGNÓSTICO DE CAMPO",
            fontSize = 11.sp,
            fontFamily = FuenteOutfit,
            fontWeight = FontWeight.SemiBold,
            color = colores.muted,
            modifier = Modifier.weight(1f),
        )
        if (hayEventos)
        {
            AccionDiagnostico("Compartir", colores, alCompartir)
            {
                Documento(colores.texto, 15.dp)
            }
            AccionDiagnostico("Limpiar", colores, alLimpiar)
            {
                Bote(colores.muted, 15.dp)
            }
        }
    }
}

@Composable
private fun AccionDiagnostico(
    texto: String,
    colores: Paleta,
    alPulsar: () -> Unit,
    icono: @Composable () -> Unit,
)
{
    Row(
        modifier = Modifier
            .pulsable(alPulsar = alPulsar)
            .semantics
            {
                role = Role.Button
            }
            .border(1.dp, colores.borde, FORMA_DIAGNOSTICO)
            .padding(horizontal = 9.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    )
    {
        icono()
        Text(
            text = texto,
            fontSize = 11.sp,
            fontFamily = FuenteOutfit,
            fontWeight = FontWeight.SemiBold,
            color = colores.texto,
        )
    }
}

@Composable
private fun ResumenDiagnostico(estado: EstadoDiagnosticoCampo, colores: Paleta)
{
    val datos = listOf(
        estado.colaOutbox.toString() to "OUTBOX",
        estado.colaRelay.toString() to "RELAY",
        textoDuracion(estado.instantanea.ultimaDuracionMs) to "LATENCIA",
        estado.instantanea.reintentos.toString() to "REINTENTOS",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, colores.borde, FORMA_DIAGNOSTICO)
            .background(colores.surface, FORMA_DIAGNOSTICO),
    )
    {
        for ((indice, dato) in datos.withIndex())
        {
            ColumnaResumen(dato.first, dato.second, colores, Modifier.weight(1f))
            if (indice < datos.lastIndex)
            {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .size(width = 1.dp, height = 32.dp)
                        .background(colores.borde),
                )
            }
        }
    }
}

@Composable
private fun ColumnaResumen(
    valor: String,
    etiqueta: String,
    colores: Paleta,
    modifier: Modifier,
)
{
    Column(
        modifier = modifier.padding(horizontal = 3.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    )
    {
        Text(
            text = valor,
            fontSize = 15.sp,
            fontFamily = FuenteOutfit,
            fontWeight = FontWeight.Bold,
            color = colores.texto,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = etiqueta,
            fontSize = 8.sp,
            color = colores.muted,
            maxLines = 1,
        )
    }
}

@Composable
private fun UltimoErrorDiagnostico(error: DiagnosticoMesh.CodigoError, colores: Paleta)
{
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colores.error.copy(alpha = 0.09f), FORMA_DIAGNOSTICO)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    )
    {
        Box(modifier = Modifier.size(7.dp).background(colores.error, CircleShape))
        Text(
            text = "Último error: ${textoError(error)}",
            fontSize = 12.sp,
            color = colores.error,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ListaEventosDiagnostico(eventos: List<DiagnosticoMesh.Evento>, colores: Paleta)
{
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, colores.borde, FORMA_DIAGNOSTICO)
            .background(colores.surface, FORMA_DIAGNOSTICO),
    )
    {
        if (eventos.isEmpty())
        {
            Text(
                text = "Sin actividad registrada",
                fontSize = 12.sp,
                color = colores.muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
            )
            return@Column
        }
        for ((indice, evento) in eventos.withIndex())
        {
            EventoDiagnostico(evento, colores)
            if (indice < eventos.lastIndex)
            {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp)
                        .height(1.dp)
                        .background(colores.borde),
                )
            }
        }
    }
}

@Composable
private fun EventoDiagnostico(evento: DiagnosticoMesh.Evento, colores: Paleta)
{
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    )
    {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        )
        {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(colorTransporte(evento.transporte, colores), CircleShape),
            )
            Text(
                text = textoEtapa(evento.etapa),
                fontSize = 12.sp,
                fontFamily = FuenteOutfit,
                fontWeight = FontWeight.SemiBold,
                color = colores.texto,
            )
            Text(
                text = textoTransporte(evento.transporte),
                fontSize = 10.sp,
                color = colorTransporte(evento.transporte, colores),
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            Text(
                text = textoHora(evento.instanteMs),
                fontSize = 10.sp,
                color = colores.muted,
            )
        }
        Text(
            text = detalleEvento(evento),
            fontSize = 10.sp,
            color = if (evento.error == null) colores.muted else colores.error,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun textoDuracion(duracionMs: Long?): String
{
    if (duracionMs == null)
    {
        return "—"
    }
    if (duracionMs < 1_000)
    {
        return "$duracionMs ms"
    }
    return String.format(Locale.ROOT, "%.1f s", duracionMs / 1_000.0)
}

private fun textoHora(instanteMs: Long): String =
    FORMATO_HORA.withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(instanteMs))

private fun textoTransporte(transporte: DiagnosticoMesh.Transporte): String = when (transporte)
{
    DiagnosticoMesh.Transporte.SERVIDOR -> "SERVIDOR"
    DiagnosticoMesh.Transporte.BLE -> "BLE"
    DiagnosticoMesh.Transporte.WIFI -> "WI-FI"
    DiagnosticoMesh.Transporte.LORA -> "LORA"
    DiagnosticoMesh.Transporte.SIN_RUTA -> "SIN RUTA"
}

private fun textoEtapa(etapa: DiagnosticoMesh.Etapa): String = when (etapa)
{
    DiagnosticoMesh.Etapa.INTENTO -> "Intento"
    DiagnosticoMesh.Etapa.ENVIADO -> "Enviado"
    DiagnosticoMesh.Etapa.RECIBIDO -> "Recibido"
    DiagnosticoMesh.Etapa.REENVIADO -> "Reenviado"
    DiagnosticoMesh.Etapa.ENCOLADO -> "En cola"
    DiagnosticoMesh.Etapa.PUENTE -> "Puente"
    DiagnosticoMesh.Etapa.DESCARTADO -> "Descartado"
    DiagnosticoMesh.Etapa.ERROR -> "Error"
}

private fun textoError(error: DiagnosticoMesh.CodigoError): String = when (error)
{
    DiagnosticoMesh.CodigoError.SIN_RUTA -> "Sin ruta disponible"
    DiagnosticoMesh.CodigoError.SIN_ACUSE -> "El servidor no confirmó el envío"
    DiagnosticoMesh.CodigoError.SIN_VECINO -> "Ningún vecino aceptó el sobre"
    DiagnosticoMesh.CodigoError.PREPARACION -> "No se pudo preparar el envío"
    DiagnosticoMesh.CodigoError.RADIO -> "Falló el enlace Bluetooth"
    DiagnosticoMesh.CodigoError.RELAY -> "El servidor rechazó el relay"
    DiagnosticoMesh.CodigoError.WIFI -> "Falló Wi-Fi Direct"
    DiagnosticoMesh.CodigoError.ESCANEO -> "Falló el escaneo Bluetooth"
}

private fun colorTransporte(
    transporte: DiagnosticoMesh.Transporte,
    colores: Paleta,
): Color = when (transporte)
{
    DiagnosticoMesh.Transporte.SERVIDOR -> VERDE_DIAGNOSTICO
    DiagnosticoMesh.Transporte.BLE -> AZUL_DIAGNOSTICO
    DiagnosticoMesh.Transporte.WIFI -> AMBAR_DIAGNOSTICO
    DiagnosticoMesh.Transporte.LORA -> CORAL_DIAGNOSTICO
    DiagnosticoMesh.Transporte.SIN_RUTA -> colores.muted
}

private fun detalleEvento(evento: DiagnosticoMesh.Evento): String
{
    val partes = mutableListOf<String>()
    evento.mensaje?.let { partes.add("#$it") }
    evento.enlace?.let { partes.add(it.replace('_', ' ')) }
    evento.saltos?.let { partes.add("$it salto${if (it == 1) "" else "s"}") }
    evento.duracionMs?.let { partes.add(textoDuracion(it)) }
    evento.intento?.let { partes.add("intento $it") }
    if (evento.reintentos > 0)
    {
        partes.add("${evento.reintentos} reintento${if (evento.reintentos == 1) "" else "s"}")
    }
    evento.cola?.let { partes.add("cola $it") }
    evento.error?.let { partes.add(textoError(it)) }
    return partes.joinToString(" · ").ifEmpty { "Sin detalle adicional" }
}

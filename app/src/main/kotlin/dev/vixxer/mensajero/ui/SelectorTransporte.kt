package dev.vixxer.mensajero.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.vixxer.mensajero.AplicacionVixxer
import dev.vixxer.mensajero.ble.GestorCercania

@Composable
fun SelectorTransporte(app: AplicacionVixxer, modifier: Modifier = Modifier)
{
    val contexto = LocalContext.current
    val gestor = GestorCercania
    val lanzadorPermisos = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { concedidos ->
        if (concedidos.values.all { it })
        {
            val r = gestor.activar(app, contexto, true)
            if (!r.ok && r.razon != null)
            {
                Toast.makeText(contexto, r.razon, Toast.LENGTH_SHORT).show()
            }
        }
        else
        {
            Toast.makeText(contexto, "Falta el permiso de Dispositivos cercanos", Toast.LENGTH_SHORT).show()
        }
    }
    val transporte = if (gestor.corriendo || gestor.modoGuardado(app)) "bluetooth" else "red"

    SelectorTransporteUi(transporte, modifier) { clave ->
        when (clave)
        {
            "red" -> gestor.activar(app, contexto, false)
            "bluetooth" ->
            {
                if (!gestor.permisosConcedidos(contexto))
                {
                    lanzadorPermisos.launch(gestor.permisos())
                }
                else
                {
                    val r = gestor.activar(app, contexto, true)
                    if (!r.ok && r.razon != null)
                    {
                        Toast.makeText(contexto, r.razon, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            else -> Toast.makeText(
                contexto,
                "Próximamente: requiere un radio LoRa conectado",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
}

@Composable
fun SelectorTransporteUi(
    transporte: String,
    modifier: Modifier = Modifier,
    alElegir: (String) -> Unit,
)
{
    val colores = LocalTema.current.colores

    Row(
        modifier = modifier
            .pildoraVidrio()
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    )
    {
        for ((clave, descripcion) in listOf(
            "red" to "Transporte por internet",
            "bluetooth" to "Transporte por Bluetooth",
            "lora" to "Transporte por LoRa",
        ))
        {
            val activo = transporte == clave
            val disponible = clave != "lora"
            val color = when
            {
                activo -> colores.botonTexto
                disponible -> colores.texto
                else -> colores.muted
            }
            Box(
                modifier = Modifier
                    .pulsable { alElegir(clave) }
                    .semantics
                    {
                        contentDescription = descripcion
                        role = Role.Button
                    }
                    .background(if (activo) colores.botonFondo else Color.Transparent, CircleShape)
                    .size(36.dp),
                contentAlignment = Alignment.Center,
            )
            {
                when (clave)
                {
                    "red" -> Globo(color, 17.dp)
                    "bluetooth" -> RunaBluetooth(color, 17.dp)
                    else -> AntenaLora(color, 17.dp)
                }
            }
        }
    }
}

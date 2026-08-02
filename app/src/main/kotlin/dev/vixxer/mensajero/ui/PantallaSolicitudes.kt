package dev.vixxer.mensajero.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vixxer.mensajero.AplicacionVixxer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

data class Solicitud(val id: String, val usuario: String)

@Composable
fun PantallaSolicitudes(app: AplicacionVixxer, alVolver: () -> Unit)
{
    val colores = LocalTema.current.colores
    val alcance = rememberCoroutineScope()
    var lista by remember { mutableStateOf(listOf<Solicitud>()) }
    var cargando by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }

    suspend fun cargar()
    {
        error = false
        try
        {
            val datos = withContext(Dispatchers.IO) { app.api.solicitudes() as JSONArray }
            lista = (0 until datos.length()).map { i ->
                val s = datos.getJSONObject(i)
                Solicitud(s.getString("id"), s.optString("usuario"))
            }
        }
        catch (e: Exception)
        {
            error = true
        }
        finally
        {
            cargando = false
        }
    }

    LaunchedEffect(Unit) {
        cargar()
    }

    fun responder(id: String, aceptar: Boolean)
    {
        alcance.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    if (aceptar) app.api.aceptarSolicitud(id) else app.api.rechazarSolicitud(id)
                }
            }
            cargar()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .fondoVixxer()
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(top = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    )
    {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "‹",
                fontSize = 26.sp,
                color = colores.texto,
                modifier = Modifier.pulsable { alVolver() },
            )
            Text("Solicitudes", fontSize = 18.sp, fontFamily = FuenteOutfit, fontWeight = FontWeight.SemiBold, color = colores.texto)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
            if (lista.isEmpty())
            {
                item {
                    EstadoLista(
                        cargando = cargando,
                        error = error,
                        vacio = "No tienes solicitudes pendientes.",
                        alReintentar = {
                            cargando = true
                            alcance.launch { cargar() }
                        },
                    )
                }
            }
            items(lista, key = { it.id }) { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .panelVidrio(radio = 12.dp)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                )
                {
                    Text(item.usuario, fontSize = 16.sp, color = colores.texto)
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Rechazar",
                            fontSize = 14.sp,
                            color = colores.muted,
                            modifier = Modifier.pulsable {
                                responder(item.id, false)
                            },
                        )
                        Box(
                            modifier = Modifier
                                .background(colores.botonFondo, RoundedCornerShape(8.dp))
                                .pulsable {
                                    responder(item.id, true)
                                }
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                        )
                        {
                            Text("Aceptar", fontSize = 13.sp, fontFamily = FuenteOutfit, fontWeight = FontWeight.SemiBold, color = colores.botonTexto)
                        }
                    }
                }
            }
        }
    }
}

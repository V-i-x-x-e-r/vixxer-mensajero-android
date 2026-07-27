package dev.vixxer.mensajero.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import dev.vixxer.mensajero.AplicacionVixxer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

object Stickers
{
    private fun carpeta(contexto: Context): File
    {
        val dir = File(contexto.filesDir, "stickers")
        dir.mkdirs()
        return dir
    }

    fun listar(contexto: Context): List<File> =
        carpeta(contexto).listFiles()?.filter { it.isFile && it.extension == "png" }?.sortedByDescending { it.name } ?: emptyList()

    fun crear(contexto: Context, uri: Uri): File?
    {
        return runCatching {
            val original = contexto.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return null
            val mayor = maxOf(original.width, original.height).toFloat()
            val escala = if (mayor > 512f) 512f / mayor else 1f
            val ajustado = if (escala < 1f)
            {
                Bitmap.createScaledBitmap(original, (original.width * escala).toInt(), (original.height * escala).toInt(), true)
            }
            else
            {
                original
            }
            val destino = File(carpeta(contexto), "${System.currentTimeMillis()}.png")
            destino.outputStream().use { ajustado.compress(Bitmap.CompressFormat.PNG, 100, it) }
            destino
        }.getOrNull()
    }

    fun borrar(archivo: File)
    {
        runCatching { archivo.delete() }
    }

    fun leer(archivo: File): Triple<ByteArray, Int, Int>?
    {
        return runCatching {
            val bytes = archivo.readBytes()
            val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, limites)
            Triple(bytes, limites.outWidth, limites.outHeight)
        }.getOrNull()
    }
}

@Composable
fun SelectorSticker(app: AplicacionVixxer, visible: Boolean, alElegir: (File) -> Unit, alCerrar: () -> Unit)
{
    if (!visible)
    {
        return
    }
    val colores = LocalTema.current.colores
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()
    var lista by remember { mutableStateOf(Stickers.listar(contexto)) }
    var porBorrar by remember { mutableStateOf<File?>(null) }

    val crearPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null)
        {
            alcance.launch {
                val nuevo = withContext(Dispatchers.IO) { Stickers.crear(contexto, uri) }
                if (nuevo != null)
                {
                    lista = Stickers.listar(contexto)
                }
            }
        }
    }

    androidx.activity.compose.BackHandler { alCerrar() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.25f))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { alCerrar() },
        contentAlignment = Alignment.BottomCenter,
    )
    {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .vidrioFlotante(radio = 22.dp)
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {}
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        )
        {
            Text("Stickers", fontSize = 15.sp, fontFamily = FuenteOutfit, fontWeight = FontWeight.SemiBold, color = colores.texto)
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            )
            {
                item {
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .border(Vidrio.anchoBorde, colores.borde, RoundedCornerShape(12.dp))
                            .pulsable {
                                app.saltarBloqueo = true
                                crearPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                        contentAlignment = Alignment.Center,
                    )
                    {
                        Text("＋", fontSize = 26.sp, color = colores.muted)
                    }
                }
                items(lista, key = { it.name }) { archivo ->
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .pulsableLargo(
                                alMantener = { porBorrar = archivo },
                                alPulsar = { alElegir(archivo) },
                            ),
                    )
                    {
                        AsyncImage(
                            model = archivo,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
            if (lista.isEmpty())
            {
                Text("Crea uno desde una foto con el ＋.", fontSize = 13.sp, color = colores.muted)
            }
        }
    }

    Confirmacion(
        visible = porBorrar != null,
        titulo = "Borrar sticker",
        mensaje = "Se quitará de tu colección en este dispositivo.",
        textoConfirmar = "Borrar",
        destructivo = true,
        alConfirmar = {
            porBorrar?.let { Stickers.borrar(it) }
            porBorrar = null
            lista = Stickers.listar(contexto)
        },
        alCancelar = { porBorrar = null },
    )
}

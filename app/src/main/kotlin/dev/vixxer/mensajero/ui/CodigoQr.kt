package dev.vixxer.mensajero.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

private fun generarQr(contenido: String, lado: Int = 640): Bitmap
{
    val matriz = QRCodeWriter().encode(contenido, BarcodeFormat.QR_CODE, lado, lado)
    val mapa = Bitmap.createBitmap(lado, lado, Bitmap.Config.RGB_565)
    for (x in 0 until lado)
    {
        for (y in 0 until lado)
        {
            mapa.setPixel(x, y, if (matriz[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }
    }
    return mapa
}

@Composable
fun CodigoQr(codigo: String, colores: Paleta, alCerrar: () -> Unit)
{
    val qr = remember(codigo) { generarQr(codigo) }

    androidx.activity.compose.BackHandler { alCerrar() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { alCerrar() },
        contentAlignment = Alignment.Center,
    )
    {
        Column(
            modifier = Modifier
                .padding(32.dp)
                .background(Color.White, RoundedCornerShape(20.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        )
        {
            Image(
                bitmap = qr.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(240.dp),
            )
            Text(
                codigo,
                fontSize = 16.sp,
                fontFamily = FuenteOutfit,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                color = Color(0xFF111111),
            )
            Text("Tu código de amigo", fontSize = 12.sp, color = Color(0xFF777777))
        }
    }
}

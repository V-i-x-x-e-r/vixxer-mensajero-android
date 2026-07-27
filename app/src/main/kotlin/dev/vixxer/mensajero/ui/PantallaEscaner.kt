package dev.vixxer.mensajero.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

const val PREFIJO_VINCULO = "vixxer-vinculo:"

@androidx.camera.core.ExperimentalGetImage
@Composable
fun PantallaEscaner(app: dev.vixxer.mensajero.AplicacionVixxer, alLeer: (String) -> Unit, alCerrar: () -> Unit)
{
    val colores = LocalTema.current.colores
    val contexto = LocalContext.current
    var concedido by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(contexto, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }

    val pedirPermiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { otorgado ->
        concedido = otorgado
    }

    LaunchedEffect(Unit)
    {
        if (!concedido)
        {
            pedirPermiso.launch(Manifest.permission.CAMERA)
        }
    }

    BackHandler { alCerrar() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    )
    {
        if (concedido)
        {
            VistaCamara(alLeer = alLeer)
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .border(3.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(24.dp)),
            )
        }
        else
        {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            )
            {
                Text(
                    "Necesitamos la cámara para escanear el código QR.",
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
                Text(
                    "Permitir cámara",
                    fontSize = 15.sp,
                    fontFamily = FuenteOutfit,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier
                        .pulsable { pedirPermiso.launch(Manifest.permission.CAMERA) }
                        .border(1.dp, Color.White, RoundedCornerShape(12.dp))
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
        }

        Text(
            "Cerrar",
            fontSize = 15.sp,
            fontFamily = FuenteOutfit,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            modifier = Modifier
                .pulsable { alCerrar() }
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 40.dp)
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(Vidrio.radioPildora))
                .padding(horizontal = 28.dp, vertical = 12.dp),
        )
    }
}

@androidx.camera.core.ExperimentalGetImage
@Composable
private fun VistaCamara(alLeer: (String) -> Unit)
{
    val contexto = LocalContext.current
    val cicloVida = LocalLifecycleOwner.current
    val ejecutor = remember { Executors.newSingleThreadExecutor() }
    val lector = remember { BarcodeScanning.getClient() }
    var yaLeido by remember { mutableStateOf(false) }

    DisposableEffect(Unit)
    {
        onDispose {
            ejecutor.shutdown()
            lector.close()
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val vista = PreviewView(ctx)
            val futuro = ProcessCameraProvider.getInstance(ctx)
            futuro.addListener(
                {
                    val proveedor = futuro.get()
                    val previa = Preview.Builder().build().also { it.setSurfaceProvider(vista.surfaceProvider) }
                    val analisis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    analisis.setAnalyzer(ejecutor) { imagen ->
                        analizar(imagen, lector) { valor ->
                            if (!yaLeido)
                            {
                                yaLeido = true
                                alLeer(valor.trim())
                            }
                        }
                    }
                    proveedor.unbindAll()
                    proveedor.bindToLifecycle(cicloVida, CameraSelector.DEFAULT_BACK_CAMERA, previa, analisis)
                },
                ContextCompat.getMainExecutor(ctx),
            )
            vista
        },
    )
}

@androidx.camera.core.ExperimentalGetImage
private fun analizar(imagen: ImageProxy, lector: com.google.mlkit.vision.barcode.BarcodeScanner, alEncontrar: (String) -> Unit)
{
    val cruda = imagen.image
    if (cruda == null)
    {
        imagen.close()
        return
    }
    val entrada = InputImage.fromMediaImage(cruda, imagen.imageInfo.rotationDegrees)
    lector.process(entrada)
        .addOnSuccessListener { codigos ->
            val valor = codigos.firstOrNull { it.format == Barcode.FORMAT_QR_CODE }?.rawValue
            if (!valor.isNullOrEmpty())
            {
                alEncontrar(valor)
            }
        }
        .addOnCompleteListener { imagen.close() }
}

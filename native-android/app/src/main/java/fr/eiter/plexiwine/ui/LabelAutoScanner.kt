package fr.eiter.plexiwine.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Size
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import fr.eiter.plexiwine.ui.theme.WineColors
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Caméra live façon Vivino : texte d'étiquette stable → capture auto JPEG.
 */
@Composable
fun LabelAutoScanner(
    onCapture: (ByteArray) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCam by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCam = granted }

    LaunchedEffect(Unit) {
        if (!hasCam) permLauncher.launch(Manifest.permission.CAMERA)
    }

    var status by remember { mutableStateOf("Cadre l’étiquette — détection auto…") }
    var borderOk by remember { mutableStateOf(false) }
    var stable by remember { mutableIntStateOf(0) }
    val fired = remember { AtomicBoolean(false) }
    val analyzing = remember { AtomicBoolean(false) }
    val frameN = remember { AtomicInteger(0) }
    var lastSig by remember { mutableStateOf("") }
    val minStable = 4
    val analyzeEvery = 4

    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val recognizer = remember {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    fun takeStill() {
        if (!fired.compareAndSet(false, true)) return
        status = "Étiquette détectée — capture…"
        borderOk = true
        val file = File(context.cacheDir, "label_auto_${System.currentTimeMillis()}.jpg")
        val opts = ImageCapture.OutputFileOptions.Builder(file).build()
        imageCapture.takePicture(
            opts,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    try {
                        onCapture(file.readBytes())
                    } catch (e: Exception) {
                        fired.set(false)
                        status = "Erreur lecture photo"
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    fired.set(false)
                    status = "Erreur capture : ${exception.message}"
                }
            }
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            executor.shutdown()
            recognizer.close()
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (!hasCam) {
            Column(
                Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Autorise la caméra", color = Color.White, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = onCancel) { Text("Fermer", color = WineColors.accent) }
            }
            return@Box
        }

        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
                val providerFuture = ProcessCameraProvider.getInstance(ctx)
                providerFuture.addListener({
                    val provider = providerFuture.get()
                    val preview = Preview.Builder().build().also { p ->
                        p.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val analysis = ImageAnalysis.Builder()
                        .setTargetResolution(Size(1280, 720))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    analysis.setAnalyzer(executor) { proxy ->
                        if (fired.get()) {
                            proxy.close()
                            return@setAnalyzer
                        }
                        val n = frameN.incrementAndGet()
                        if (n % analyzeEvery != 0) {
                            proxy.close()
                            return@setAnalyzer
                        }
                        if (!analyzing.compareAndSet(false, true)) {
                            proxy.close()
                            return@setAnalyzer
                        }
                        try {
                            val media = proxy.image
                            if (media == null) {
                                analyzing.set(false)
                                proxy.close()
                                return@setAnalyzer
                            }
                            val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
                            recognizer.process(image)
                                .addOnSuccessListener { result ->
                                    val lines = result.textBlocks.flatMap { b ->
                                        b.lines.map { it.text.trim() }
                                    }.filter { it.isNotEmpty() }
                                    val text = lines.joinToString("\n")
                                    val chars = text.count { !it.isWhitespace() }
                                    val sig = lines.take(6).joinToString("|").lowercase()
                                    val good = chars >= 12 && lines.size >= 2
                                    // Main thread UI
                                    previewView.post {
                                        if (fired.get()) return@post
                                        if (good) {
                                            if (sig == lastSig && sig.isNotEmpty()) {
                                                stable += 1
                                            } else {
                                                lastSig = sig
                                                stable = 1
                                            }
                                            if (stable >= minStable) {
                                                takeStill()
                                            } else {
                                                status = "Étiquette vue — tiens stable… ($stable/$minStable)"
                                                borderOk = false
                                            }
                                        } else {
                                            stable = 0
                                            lastSig = ""
                                            status = "Cadre l’étiquette dans le cadre"
                                            borderOk = false
                                        }
                                    }
                                }
                                .addOnCompleteListener {
                                    analyzing.set(false)
                                    proxy.close()
                                }
                        } catch (_: Exception) {
                            analyzing.set(false)
                            proxy.close()
                        }
                    }
                    try {
                        provider.unbindAll()
                        provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analysis,
                            imageCapture
                        )
                    } catch (_: Exception) {
                        status = "Caméra indisponible"
                    }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Guide frame
        Box(
            Modifier
                .align(Alignment.Center)
                .padding(bottom = 48.dp)
                .fillMaxWidth(0.78f)
                .aspectRatio(0.72f)
                .border(
                    2.5.dp,
                    if (borderOk) Color(0xFF2ECC71) else Color.White.copy(alpha = 0.85f),
                    RoundedCornerShape(14.dp)
                )
        )

        Text(
            status,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 48.dp, start = 20.dp, end = 20.dp)
                .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        )

        TextButton(
            onClick = onCancel,
            modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)
        ) { Text("Annuler", color = Color.White, fontWeight = FontWeight.SemiBold) }

        TextButton(
            onClick = { takeStill() },
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
        ) {
            Text(
                "Photo manuelle",
                color = Color.Black,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(Color.White.copy(alpha = 0.92f), RoundedCornerShape(22.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }
    }
}

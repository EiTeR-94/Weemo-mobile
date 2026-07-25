package fr.eiter.plexiwine.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.util.Size
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import fr.eiter.plexiwine.ImageUtils
import fr.eiter.plexiwine.ui.theme.WineColors
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Caméra live : texte stable dans le cadre → capture auto JPEG **croppée au cadre**.
 *
 * Stabilité = frames "bon texte" consécutives (pas d’égalité stricte OCR, trop bruyante).
 */
@Composable
fun LabelAutoScanner(
    onCapture: (ByteArray) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val density = LocalDensity.current
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
    // Guide rect en pixels preview (mis à jour par le layout Compose)
    val guideRectPx = remember { AtomicReference(floatArrayOf(0f, 0f, 1f, 1f)) } // L,T,R,B
    val previewSizePx = remember { AtomicReference(intArrayOf(0, 0)) } // W,H

    // Moins strict qu’avant : l’OCR change de sig à chaque frame → exact match = bloqué à 1/7
    val minStable = 4
    val analyzeEvery = 3
    val minChars = 10
    val minLines = 2

    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val recognizer = remember {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    fun cropToGuide(raw: ByteArray): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
        val iw = bounds.outWidth
        val ih = bounds.outHeight
        val (vw, vh) = previewSizePx.get()
        val g = guideRectPx.get()
        if (iw <= 0 || ih <= 0 || vw <= 0 || vh <= 0) return raw
        // Légère marge intérieure (évite le bord du cadre UI)
        val padX = (g[2] - g[0]) * 0.04f
        val padY = (g[3] - g[1]) * 0.04f
        val norm = ImageUtils.fillCenterViewRectToImageNorm(
            viewW = vw,
            viewH = vh,
            imageW = iw,
            imageH = ih,
            rectL = g[0] + padX,
            rectT = g[1] + padY,
            rectR = g[2] - padX,
            rectB = g[3] - padY
        )
        return ImageUtils.cropNormalized(raw, norm[0], norm[1], norm[2], norm[3], quality = 90)
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
                        val raw = file.readBytes()
                        val cropped = cropToGuide(raw)
                        onCapture(cropped)
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

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val viewW = constraints.maxWidth.toFloat()
        val viewH = constraints.maxHeight.toFloat()
        // Cadre guide : 78% largeur, ratio ~étiquette bouteille, centré un peu haut
        val guideW = viewW * 0.78f
        val guideH = guideW / 0.72f
        val guideL = (viewW - guideW) / 2f
        val guideT = (viewH - guideH) / 2f - with(density) { 20.dp.toPx() }
        val guideR = guideL + guideW
        val guideB = guideT + guideH
        guideRectPx.set(floatArrayOf(guideL, guideT, guideR, guideB))
        previewSizePx.set(intArrayOf(viewW.toInt(), viewH.toInt()))

        if (!hasCam) {
            Column(
                Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Autorise la caméra", color = Color.White, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = onCancel) { Text("Fermer", color = WineColors.accent) }
            }
            return@BoxWithConstraints
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
                                    // "Bon" = assez de texte (étiquette) — pas d’égalité stricte de sig
                                    val good = chars >= minChars && lines.size >= minLines
                                    previewView.post {
                                        if (fired.get()) return@post
                                        if (good) {
                                            val next = (stable + 1).coerceAtMost(minStable)
                                            stable = next
                                            if (next >= minStable) {
                                                takeStill()
                                            } else {
                                                status = "Étiquette vue — tiens stable… ($next/$minStable)"
                                                borderOk = next >= 2
                                            }
                                        } else {
                                            // Soft reset : une frame floue ne remet pas à 0
                                            if (stable > 0) stable = (stable - 1).coerceAtLeast(0)
                                            if (stable == 0) {
                                                status = "Cadre l’étiquette dans le cadre"
                                                borderOk = false
                                            } else {
                                                status = "Étiquette vue — tiens stable… ($stable/$minStable)"
                                            }
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

        // Guide frame — même géométrie exacte que guideRectPx (crop)
        Box(
            Modifier
                .offset(
                    x = with(density) { guideL.toDp() },
                    y = with(density) { guideT.toDp() }
                )
                .width(with(density) { guideW.toDp() })
                .height(with(density) { guideH.toDp() })
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

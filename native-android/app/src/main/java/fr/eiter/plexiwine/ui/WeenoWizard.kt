package fr.eiter.plexiwine.ui

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import fr.eiter.plexiwine.*
import fr.eiter.plexiwine.ui.theme.WineColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume


// ───────────────────────── Wizard ─────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WeenoWizard(vm: AppViewModel) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val api = vm.api

    var product by remember { mutableStateOf<WineProduct?>(null) }
    var scanStatus by remember { mutableStateOf("Cadre l’étiquette — touche pour photo") }
    var busy by remember { mutableStateOf(false) }
    var labelPhotoFile by remember { mutableStateOf<File?>(null) }
    var vivinoQuery by remember { mutableStateOf("") }
    var vivinoProducer by remember { mutableStateOf("") }
    var vivinoVintage by remember { mutableStateOf("") }
    var vivinoResults by remember { mutableStateOf(listOf<VivinoHit>()) }
    var vivinoError by remember { mutableStateOf<String?>(null) }
    var showManual by remember { mutableStateOf(false) }
    var manualName by remember { mutableStateOf("") }
    var manualProducer by remember { mutableStateOf("") }
    var manualVintage by remember { mutableStateOf("") }
    var manualRegion by remember { mutableStateOf("") }
    var manualStyle by remember { mutableStateOf("") }
    var customStyle by remember { mutableStateOf("") }
    var styleOptions by remember { mutableStateOf(listOf<StyleOption>()) }
    var photoFile by remember { mutableStateOf<File?>(null) }
    /** Lieu / lien de dégustation (optionnel) — saisi à l'étape Photo, comme iOS. */
    var location by remember { mutableStateOf("") }
    var rating by remember { mutableFloatStateOf(3f) }
    var rebuy by remember { mutableStateOf<String?>(null) }
    var comment by remember { mutableStateOf("") }
    var flavors by remember { mutableStateOf(setOf<String>()) }
    var hops by remember { mutableStateOf(setOf<String>()) }
    var flavorTags by remember { mutableStateOf(listOf<String>()) }
    var hopTags by remember { mutableStateOf(listOf<String>()) }
    var showFlavors by remember { mutableStateOf(true) }
    var showHops by remember { mutableStateOf(true) }
    var customFlavor by remember { mutableStateOf("") }
    var customHop by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var showDuplicate by remember { mutableStateOf(false) }
    var duplicateDetail by remember { mutableStateOf("") }
    var pendingCapture by remember { mutableStateOf<File?>(null) }
    var captureMode by remember { mutableStateOf("photo") } // photo | scan
    var showLabelAutoScanner by remember { mutableStateOf(false) }
    // Plafond de relances auto du scan live sur non-match (parité webapp LIVE_SCAN_MAX_ATTEMPTS) —
    // reste sous le rate-limit serveur partagé WINE_VIVINO_SCAN_RATE_MAX au lieu de boucler à l'infini.
    var scanAttempt by remember { mutableStateOf(0) }
    val maxScanAttempts = 6
    // true seulement quand la réouverture de la caméra vient d'une relance interne (pas un tap utilisateur) —
    // sert à ne pas remettre scanAttempt à 0 dans runLabelAnalysis() lors de cette réouverture.
    var isScanRetryReopen by remember { mutableStateOf(false) }
    // Photo en attente d'un scan réseau qui a échoué hors-ligne — relancée auto au retour du réseau
    // (parité webapp scheduleScanOnlineRetry), voir LaunchedEffect(vm.networkStatus) plus bas.
    var scanNetworkRetryFile by remember { mutableStateOf<File?>(null) }
    // Source de la dernière photo scannée — une photo importée est statique (relancer la
    // caméra live sur non-match n'a aucun sens, contrairement à un recadrage en direct).
    var scanSourceIsGallery by remember { mutableStateOf(false) }
    // Mémoire étiquette (dHash/aHash → vin déjà connu, court-circuite Vivino/IA au rescan).
    var labelMemoryPrint by remember { mutableStateOf<Pair<String, String>?>(null) }
    var labelMemoryHitId by remember { mutableStateOf<Int?>(null) }
    var labelMemoryConfirmMatch by remember { mutableStateOf<LabelMemoryMatch?>(null) }
    var showLabelMemoryConfirm by remember { mutableStateOf(false) }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    // Apply prefill from retaste / wishlist
    LaunchedEffect(vm.wizardProduct) {
        vm.wizardProduct?.let {
            product = it
            scanStatus = "Prérempli ✓"
        }
    }

    LaunchedEffect(Unit) {
        styleOptions = api.styles()
    }

    // Suggestions Vivino live (debounce ~320ms, parité webapp)
    LaunchedEffect(vivinoQuery, vivinoProducer, vivinoVintage) {
        val q = listOf(vivinoQuery, vivinoProducer, vivinoVintage)
            .map { it.trim() }.filter { it.isNotBlank() }.joinToString(" ")
        if (q.length < 2) {
            vivinoResults = emptyList()
            return@LaunchedEffect
        }
        if (product?.wineName?.isNotBlank() == true) return@LaunchedEffect
        kotlinx.coroutines.delay(320)
        try {
            val resp = api.searchVivino(q)
            vivinoResults = resp.results.orEmpty().take(5)
            vivinoError = if (vivinoResults.isEmpty()) resp.error else null
        } catch (e: Exception) {
            // silent live search
        }
    }

    LaunchedEffect(vm.wizardStep, product) {
        if (vm.wizardStep == 3 && product != null) {
            try {
                hopTags = emptyList()
                showFlavors = true
                showHops = false
                flavorTags = api.configFlavors()
            } catch (_: Exception) {
                showHops = false
                flavorTags = emptyList()
            }
        }
    }

    fun resetWizard() {
        product = null
        scanStatus = "Cadre l’étiquette — touche pour photo"
        photoFile = null
        labelPhotoFile = null
        labelMemoryPrint = null
        labelMemoryHitId = null
        showLabelMemoryConfirm = false
        labelMemoryConfirmMatch = null
        scanAttempt = 0
        isScanRetryReopen = false
        scanNetworkRetryFile = null
        location = ""
        rating = 3f
        rebuy = null
        comment = ""
        flavors = emptySet()
        hops = emptySet()
        vivinoQuery = ""
        vivinoProducer = ""
        vivinoVintage = ""
        vivinoResults = emptyList()
        vivinoError = null
        manualName = ""
        manualProducer = ""
        manualVintage = ""
        manualRegion = ""
        manualStyle = ""
        customStyle = ""
        vm.clearWizardPrefill()
        vm.wizardStep = 1
    }

    /**
     * Mémoire serveur partagée étiquette → vin (avant tout appel réseau Vivino/IA).
     * @return true si un match "auto" a été appliqué, ou si un match "confirm" est en attente
     * de validation utilisateur (dialog) — dans les deux cas l'appelant doit s'arrêter là.
     */
    suspend fun tryApplyLabelMemory(f: File): Boolean {
        val bmp = try {
            BitmapFactory.decodeFile(f.absolutePath)
        } catch (_: Exception) {
            null
        } ?: return false
        val print = try {
            ImageUtils.computeLabelPrint(bmp)
        } finally {
            bmp.recycle()
        } ?: return false
        labelMemoryPrint = print
        val match = try {
            api.labelMemoryLookup(print.first, print.second)
        } catch (_: Exception) {
            null
        }
        val wine = match?.wine ?: return false
        if (match.confidence == "confirm") {
            labelMemoryConfirmMatch = match
            showLabelMemoryConfirm = true
            return true
        }
        if (match.confidence == "auto") {
            labelMemoryHitId = match.id
            product = wine.toProduct()
            val bits = listOfNotNull(wine.producer, wine.wineName, wine.vintage?.toString()).joinToString(" · ")
            scanStatus = "Mémoire étiquette (sûr) · $bits"
            vm.showToast("Étiquette reconnue", ToastPayload.Variant.SUCCESS)
            return true
        }
        return false
    }

    // Partagé entre : photo caméra système (mode scan), LabelAutoScanner (capture auto OCR)
    // et le bouton "Lancer le scan". skipMemory=true après un refus explicite d'un match mémoire.
    fun runLabelAnalysis(f: File, skipMemory: Boolean = false, isRetry: Boolean = false) {
        labelPhotoFile = f
        if (!isRetry) {
            scanAttempt = 0
            scanNetworkRetryFile = null
        }
        if (!skipMemory) {
            labelMemoryPrint = null
            labelMemoryHitId = null
        }
        scope.launch {
            busy = true
            scanStatus = "Analyse de l’étiquette…"
            try {
                if (!skipMemory && vm.isEffectivelyOnline() && tryApplyLabelMemory(f)) {
                    return@launch
                }
                val jpeg = ImageUtils.compressJPEG(f.readBytes())
                val scan = api.labelScan(jpeg)
                if (!scan.wineName.isNullOrBlank() || !scan.producer.isNullOrBlank()) {
                    vivinoQuery = listOfNotNull(scan.producer, scan.wineName).filter { it.isNotBlank() }.joinToString(" ")
                }
                if (!scan.producer.isNullOrBlank()) vivinoProducer = scan.producer!!
                if (scan.vintage != null) {
                    vivinoVintage = scan.vintage.toString()
                    manualVintage = scan.vintage.toString()
                }
                if (!scan.wineColor.isNullOrBlank()) manualStyle = scan.wineColor!!
                if (!scan.region.isNullOrBlank()) manualRegion = scan.region!!
                if (scan.candidates.isNotEmpty()) {
                    vivinoResults = scan.candidates.take(5)
                    scanStatus = if (scan.aiAvailable) "Étiquette lue — choisis le bon vin"
                    else "Scan partiel — suggestions Vivino"
                    vm.showToast("${scan.candidates.size} suggestion(s)", ToastPayload.Variant.SUCCESS)
                } else {
                    val raw = (scan.aiError ?: scan.hint ?: "").lowercase()
                    val isPersistentError = raw.contains("429") || raw.contains("quota") || raw.contains("rate") ||
                        raw.contains("clé") || raw.contains("key") || raw.contains("no_provider") ||
                        raw.contains("réseau") || raw.contains("network") || raw.contains("connexion")
                    if (isPersistentError) {
                        // Panne durable (quota/clé/réseau) : inutile de relancer tout de suite,
                        // on laisse la main (saisie/Vivino manuelle).
                        showManual = true
                        scanStatus = when {
                            !scan.hint.isNullOrBlank() -> scan.hint!!
                            !scan.aiError.isNullOrBlank() -> scan.aiError!!
                            else -> "Scan temporairement saturé — réessaie ou saisie manuelle"
                        }
                    } else if (scanSourceIsGallery) {
                        // Photo importée = statique, pas de cadrage à corriger — relancer la
                        // caméra live n'a aucun sens ici, contrairement au flux caméra.
                        showManual = true
                        scanStatus = "Étiquette non reconnue sur cette photo — choisis-en une autre, cherche sur Vivino ou saisis à la main"
                        vm.showToast("Scan sans résultat", ToastPayload.Variant.WARN)
                    } else {
                        // Rien reconnu (étiquette illisible ou pas une étiquette du tout) — comme
                        // Vivino, on ne bloque pas sur un écran d'échec : on relance le scan live,
                        // plafonné comme la webapp (LIVE_SCAN_MAX_ATTEMPTS) pour rester sous le
                        // rate-limit serveur partagé WINE_VIVINO_SCAN_RATE_MAX au lieu de boucler à l'infini.
                        scanAttempt += 1
                        if (scanAttempt < maxScanAttempts) {
                            scanStatus = "${scan.hint ?: "Rien reconnu"} — nouvelle tentative ($scanAttempt/$maxScanAttempts)…"
                            isScanRetryReopen = true
                            delay(400)
                            showLabelAutoScanner = true
                        } else {
                            showManual = true
                            scanStatus = "Étiquette non reconnue après $maxScanAttempts tentatives — cherche sur Vivino ou saisis à la main"
                            vm.showToast("Scan sans résultat", ToastPayload.Variant.WARN)
                        }
                    }
                }
            } catch (e: Exception) {
                if (!vm.isEffectivelyOnline()) {
                    // Hors ligne : la photo est gardée et le scan est relancé auto au retour
                    // réseau (parité webapp scheduleScanOnlineRetry) — pas de retry caméra ici,
                    // le problème est le réseau, pas la reconnaissance.
                    scanNetworkRetryFile = f
                    showManual = true
                    scanStatus = "Hors ligne — relance auto du scan au retour du réseau"
                } else {
                    val m = e.message ?: "Erreur scan"
                    scanStatus = if (m.contains("JsonNull", ignoreCase = true)) {
                        "Erreur lecture réponse scan — mets à jour l’app"
                    } else {
                        m
                    }
                }
            } finally {
                busy = false
            }
        }
    }

    // Relance auto du scan au retour réseau (parité webapp scheduleScanOnlineRetry) —
    // même photo, sans repasser par la caméra.
    LaunchedEffect(vm.networkStatus) {
        val f = scanNetworkRetryFile
        if (vm.networkStatus == NetworkStatus.ONLINE && f != null) {
            scanNetworkRetryFile = null
            vm.showToast("Réseau de retour — relance du scan…", ToastPayload.Variant.SUCCESS)
            runLabelAnalysis(f, isRetry = true)
        }
    }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = pendingCapture
        pendingCapture = null
        if (!ok || f == null) return@rememberLauncherForActivityResult
        if (captureMode == "photo") {
            photoFile = f
            vm.showToast("Photo prête ✓", ToastPayload.Variant.SUCCESS)
            return@rememberLauncherForActivityResult
        }
        // Mode scan = POST /api/label-scan (backend serveur Vivino-vision ou Gemini + candidats Vivino)
        scanSourceIsGallery = false
        runLabelAnalysis(f)
    }

    // Import depuis la photothèque — Photo Picker système (androidx.activity 1.6+), pas de
    // permission READ_MEDIA_IMAGES nécessaire, même pipeline que le scan caméra.
    val pickLabelImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val dir = File(context.cacheDir, "wine").apply { mkdirs() }
        val f = File(dir, "scan_gallery_${System.currentTimeMillis()}.jpg")
        val ok = try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                f.outputStream().use { output -> input.copyTo(output) }
            }
            true
        } catch (e: Exception) {
            false
        }
        if (!ok || f.length() == 0L) {
            vm.showToast("Photo illisible", ToastPayload.Variant.WARN)
            return@rememberLauncherForActivityResult
        }
        scanAttempt = 0
        isScanRetryReopen = false
        scanSourceIsGallery = true
        runLabelAnalysis(f)
    }

    fun launchCamera(mode: String) {
        captureMode = mode
        if (!hasCameraPermission) {
            vm.showToast("Autorise la caméra puis réessaie", ToastPayload.Variant.WARN)
            return
        }
        try {
            val dir = File(context.cacheDir, "wine").apply { mkdirs() }
            val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val f = File(dir, "${mode}_$ts.jpg")
            val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", f)
            pendingCapture = f
            takePicture.launch(uri)
        } catch (e: Exception) {
            vm.showToast("Caméra: ${e.message}", ToastPayload.Variant.ERROR)
        }
    }

    var pendingCamAction by remember { mutableStateOf<String?>(null) }

    val camPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCameraPermission = granted
        val action = pendingCamAction
        pendingCamAction = null
        if (!granted) {
            vm.showToast("Permission caméra refusée", ToastPayload.Variant.ERROR)
            return@rememberLauncherForActivityResult
        }
        if (action == "scan" || action == "photo") {
            launchCamera(action)
        }
    }

    fun ensureCamera(mode: String) {
        captureMode = mode
        if (hasCameraPermission) {
            launchCamera(mode)
        } else {
            pendingCamAction = mode
            camPerm.launch(Manifest.permission.CAMERA)
        }
    }

    suspend fun doSave(force: Boolean) {
        val p = product ?: return
        if (p.wineName.isBlank()) {
            vm.showToast("Nom de vin requis", ToastPayload.Variant.WARN)
            return
        }
        saving = true
        try {
            val msg = vm.saveCheckin(
                product = p,
                rating = rating.toDouble(),
                flavors = flavors.toList(),
                hops = hops.toList(),
                comment = comment,
                photoFile = photoFile,
                force = force,
                location = location,
                rebuy = rebuy
            )
            if (msg.startsWith("duplicate|")) {
                val parts = msg.split("|")
                duplicateDetail = "Déjà notée: ${parts.getOrNull(1)} ★${parts.getOrNull(2)} (${parts.getOrNull(3)})"
                showDuplicate = true
            } else {
                vm.showToast(msg, ToastPayload.Variant.SUCCESS)
                resetWizard()
            }
        } catch (e: Exception) {
            vm.showToast(e.message ?: "Échec", ToastPayload.Variant.ERROR)
        } finally {
            saving = false
        }
    }

    if (showLabelMemoryConfirm && labelMemoryConfirmMatch?.wine != null) {
        val match = labelMemoryConfirmMatch!!
        val w = match.wine!!
        val bits = listOfNotNull(w.producer, w.wineName, w.vintage?.toString()).joinToString(" · ")
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Mémoire étiquette ?") },
            text = { Text("Étiquette proche en mémoire :\n$bits\n\nC'est bien ce vin ?") },
            confirmButton = {
                TextButton(onClick = {
                    showLabelMemoryConfirm = false
                    labelMemoryHitId = match.id
                    product = w.toProduct()
                    scanStatus = "Mémoire étiquette (confirmé) · $bits"
                    vm.showToast("Étiquette confirmée", ToastPayload.Variant.SUCCESS)
                }) { Text("Oui, c’est ça") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showLabelMemoryConfirm = false
                    val print = labelMemoryPrint
                    scope.launch { api.labelMemoryReject(match.id, print?.first, print?.second) }
                    labelPhotoFile?.let { runLabelAnalysis(it, skipMemory = true) }
                }) { Text("Non") }
            }
        )
    }

    if (showDuplicate) {
        AlertDialog(
            onDismissRequest = { showDuplicate = false },
            title = { Text("Déjà dégustée") },
            text = {
                Text(
                    if (duplicateDetail.isBlank()) "Ajouter cette nouvelle note à ton historique ?"
                    else duplicateDetail
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDuplicate = false
                    scope.launch { doSave(force = true) }
                }) { Text("Noter à nouveau") }
            },
            dismissButton = {
                TextButton(onClick = { showDuplicate = false }) { Text("Annuler") }
            }
        )
    }

    val wizardScroll = rememberScrollState()

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(wizardScroll)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        when (vm.wizardStep) {
            1 -> {
                WeenoLead("Scan d’étiquette ou recherche Vivino.")

                WeenoCard {
                    Text("Scan d’étiquette", color = WineColors.text, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(WineColors.photoBg)
                            .border(1.dp, WineColors.border, RoundedCornerShape(16.dp))
                            .clickable {
                                scanAttempt = 0
                                isScanRetryReopen = false
                                showLabelAutoScanner = true
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (labelPhotoFile != null) {
                            AsyncImage(
                                model = labelPhotoFile,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize().padding(8.dp),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("🍾", fontSize = 36.sp)
                                Spacer(Modifier.height(6.dp))
                                Text("Cadre l’étiquette", color = WineColors.text, fontWeight = FontWeight.SemiBold)
                                Text("touche pour prendre une photo", color = WineColors.muted, fontSize = 12.sp)
                            }
                        }
                        if (busy) {
                            CircularProgressIndicator(
                                Modifier.align(Alignment.TopEnd).padding(12.dp).size(22.dp),
                                color = WineColors.accent,
                                strokeWidth = 2.dp,
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Importer depuis la photothèque",
                        color = WineColors.accent,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !busy) {
                                pickLabelImage.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                            .padding(vertical = 4.dp)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(scanStatus, color = WineColors.muted, fontSize = 13.sp, modifier = Modifier.fillMaxWidth())
                    if (labelPhotoFile != null && !busy) {
                        Spacer(Modifier.height(8.dp))
                        WeenoPrimaryButton("Lancer le scan") {
                            val f = labelPhotoFile ?: return@WeenoPrimaryButton
                            runLabelAnalysis(f)
                        }
                    }
                }

                if (showLabelAutoScanner) {
                    Dialog(
                        onDismissRequest = { showLabelAutoScanner = false },
                        properties = DialogProperties(usePlatformDefaultWidth = false)
                    ) {
                        LabelAutoScanner(
                            onCapture = { bytes ->
                                showLabelAutoScanner = false
                                val dir = File(context.cacheDir, "wine").apply { mkdirs() }
                                val f = File(dir, "scan_auto_${System.currentTimeMillis()}.jpg")
                                f.writeBytes(bytes)
                                val retry = isScanRetryReopen
                                isScanRetryReopen = false
                                scanSourceIsGallery = false
                                runLabelAnalysis(f, isRetry = retry)
                            },
                            onCancel = {
                                showLabelAutoScanner = false
                                isScanRetryReopen = false
                            }
                        )
                    }
                }

                WeenoCard {
                    Text("Chercher sur Vivino", color = WineColors.text, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Tape — suggestions en direct (max 5). Scrolle la liste si besoin.",
                        color = WineColors.muted,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    WeenoField("Domaine, cuvée…", vivinoQuery, { vivinoQuery = it }, "ex. Bachelet Saint-Aubin Le Charmois")
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) {
                            WeenoField("Producteur", vivinoProducer, { vivinoProducer = it }, "ex. Domaine Nicolas")
                        }
                        Box(Modifier.width(100.dp)) {
                            WeenoField("Millésime", vivinoVintage, { vivinoVintage = it }, "2019", KeyboardType.Number)
                        }
                    }
                    vivinoError?.let { Text(it, color = WineColors.error, fontSize = 12.sp) }
                    // Liste locale (pas de re-bringIntoView à chaque frappe)
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = if (vivinoResults.isEmpty()) 0.dp else 220.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                    vivinoResults.forEachIndexed { idx, hit ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (idx == 0) WineColors.accent.copy(alpha = 0.08f) else WineColors.bg)
                                .border(0.5.dp, WineColors.border, RoundedCornerShape(8.dp))
                                .clickable {
                                    scope.launch {
                                        // Sélection immédiate (parité webapp) puis enrichissement
                                        product = WineProduct(
                                            wineName = hit.wineName,
                                            producer = hit.producer.orEmpty().ifBlank { "—" },
                                            style = hit.styleFr ?: "autre",
                                            styleFr = hit.styleFr,
                                            vivinoId = hit.bid.takeIf { it > 0 },
                                            source = "vivino",
                                            photoURL = hit.photoURL,
                                            vintage = hit.vintage,
                                            region = hit.region,
                                            country = hit.country
                                        )
                                        vivinoResults = emptyList()
                                        scanStatus = "Fiche sélectionnée — enrichissement…"
                                        busy = true
                                        try {
                                            if (hit.bid > 0) {
                                                val fetched = api.vivinoFetch(
                                                    bid = hit.bid,
                                                    wineName = hit.wineName,
                                                    producer = hit.producer.orEmpty(),
                                                    vintage = hit.vintage
                                                )
                                                if (fetched.ok) {
                                                    val pr = fetched.asProduct("")
                                                    product = pr.copy(
                                                        wineName = pr.wineName.ifBlank { hit.wineName },
                                                        producer = pr.producer.ifBlank { hit.producer.orEmpty() },
                                                        vivinoId = pr.vivinoId ?: hit.bid,
                                                        vintage = hit.vintage ?: pr.let { null },
                                                        region = hit.region,
                                                        country = hit.country,
                                                        photoURL = pr.photoURL ?: hit.photoURL
                                                    )
                                                }
                                            }
                                            scanStatus = "Vin prêt — continue vers la photo"
                                            vm.showToast("Vin sélectionné ✓", ToastPayload.Variant.SUCCESS)
                                        } catch (e: Exception) {
                                            scanStatus = "Base OK — enrichissement indisponible"
                                            vm.showToast("Vin sélectionné ✓", ToastPayload.Variant.SUCCESS)
                                        } finally {
                                            busy = false
                                        }
                                        labelMemoryPrint?.let { (d, a) ->
                                            product?.let { p -> api.labelMemoryRemember(d, a, p) }
                                        }
                                    }
                                }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${idx + 1}",
                                color = if (idx == 0) WineColors.btnPrimaryText else WineColors.muted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(if (idx == 0) WineColors.accent else WineColors.card)
                                    .wrapContentSize(Alignment.Center)
                            )
                            Spacer(Modifier.width(8.dp))
                            if (!hit.photoURL.isNullOrBlank()) {
                                AsyncImage(
                                    model = hit.photoURL,
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Column(Modifier.weight(1f)) {
                                Text(hit.wineName, color = WineColors.text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 2)
                                Text(
                                    listOfNotNull(hit.producer, hit.country, hit.vintage?.toString()).joinToString(" · "),
                                    color = WineColors.muted,
                                    fontSize = 11.sp,
                                    maxLines = 1
                                )
                            }
                            hit.vivinoRating?.let {
                                Text(String.format("%.1f", it), color = WineColors.star, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                    } // Column bringIntoViewRequester (suggestions au-dessus du clavier)
                }

                WeenoCard {
                    Text(
                        if (showManual) "▼ Saisie manuelle (secours)" else "▶ Saisie manuelle (secours)",
                        color = WineColors.muted,
                        modifier = Modifier.clickable { showManual = !showManual }
                    )
                    if (showManual) {
                        Spacer(Modifier.height(8.dp))
                        WeenoField("Nom / cuvée *", manualName, { manualName = it }, "ex. Saint-Aubin 1er Cru…")
                        Spacer(Modifier.height(6.dp))
                        WeenoField("Producteur", manualProducer, { manualProducer = it }, "ex. Domaine Nicolas")
                        Spacer(Modifier.height(6.dp))
                        WeenoField("Année / millésime", manualVintage, { manualVintage = it }, "2019", KeyboardType.Number)
                        Spacer(Modifier.height(6.dp))
                        val manualColorOpts = buildList {
                            add("" to "Choisir…")
                            if (styleOptions.isNotEmpty()) {
                                styleOptions.filter { it.value.isNotBlank() }.forEach {
                                    add(it.value to it.label.ifBlank { it.value })
                                }
                            } else {
                                listOf(
                                    "rouge" to "Rouge",
                                    "blanc" to "Blanc",
                                    "rose" to "Rosé",
                                    "effervescent" to "Effervescent",
                                    "orange" to "Orange",
                                    "fortifie" to "Fortifié",
                                    "autre" to "Autre",
                                ).forEach { add(it) }
                            }
                            add("__other__" to "Autre (saisir manuellement)")
                        }
                        WeenoFormSelectField(
                            label = "Couleur",
                            value = manualStyle,
                            options = manualColorOpts,
                            onChange = { manualStyle = it },
                            placeholder = "Choisir…"
                        )
                        if (manualStyle == "__other__") {
                            Spacer(Modifier.height(6.dp))
                            WeenoField("Couleur", customStyle, { customStyle = it }, "ex. orange, fortifié…")
                        }
                        Spacer(Modifier.height(6.dp))
                        WeenoField("Région", manualRegion, { manualRegion = it }, "ex. Bourgogne…")
                        Spacer(Modifier.height(8.dp))
                        WeenoSecondaryButton("Continuer sans Vivino") {
                            if (manualName.isBlank()) {
                                vm.showToast("Nom / cuvée requis", ToastPayload.Variant.WARN)
                            } else {
                                val color = when {
                                    manualStyle == "__other__" -> customStyle.trim().ifBlank { "autre" }
                                    manualStyle.isBlank() -> "autre"
                                    else -> manualStyle
                                }
                                val summary = listOf(manualVintage, manualRegion)
                                    .map { it.trim() }
                                    .filter { it.isNotEmpty() }
                                    .joinToString(" · ")
                                product = WineProduct(
                                    wineName = manualName.trim(),
                                    producer = manualProducer.trim().ifBlank { "—" },
                                    style = color,
                                    styleFr = color,
                                    summary = summary
                                )
                                scanStatus = "Saisie manuelle ✓"
                                labelMemoryPrint?.let { (d, a) ->
                                    val p = product!!
                                    scope.launch { api.labelMemoryRemember(d, a, p) }
                                }
                                vm.wizardStep = 2
                            }
                        }
                    }
                }

                product?.takeIf { it.wineName.isNotBlank() }?.let { p ->
                    WeenoPreviewCard(p)
                    if (labelMemoryHitId != null) {
                        Text(
                            "Mémoire serveur (tous les users) — signale si ce n'est pas le bon vin.",
                            color = WineColors.muted,
                            fontSize = 11.sp
                        )
                    }
                    WeenoSecondaryButton("Changer de vin") {
                        val hitId = labelMemoryHitId
                        val print = labelMemoryPrint
                        if (hitId != null) {
                            scope.launch { api.labelMemoryReject(hitId, print?.first, print?.second) }
                        }
                        product = null
                        labelMemoryHitId = null
                        labelPhotoFile = null
                        scanStatus = "Cadre l’étiquette — touche pour photo"
                    }
                    WeenoPrimaryButton("Continuer → photo") {
                        labelMemoryHitId?.let { id -> scope.launch { api.labelMemoryHit(id) } }
                        vm.wizardStep = 2
                    }
                }
            }

            2 -> {
                WeenoLead("Photo du verre / bouteille et lieu.")
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(WineColors.card)
                        .border(2.dp, WineColors.border, RoundedCornerShape(16.dp))
                        .clickable { ensureCamera("photo") },
                    contentAlignment = Alignment.Center
                ) {
                    if (photoFile != null) {
                        AsyncImage(
                            model = photoFile,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().padding(8.dp),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Text("📷 Prendre une photo", color = WineColors.muted)
                    }
                }
                if (photoFile != null) {
                    TextButton(onClick = { photoFile = null }) {
                        Text("Retirer la photo", color = WineColors.error)
                    }
                }

                WeenoCard {
                    Text("Où as-tu dégusté ?", color = WineColors.text, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Nom du lieu et/ou lien (Maps, resto…) — optionnel.",
                        color = WineColors.muted,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    WeenoField(
                        label = "Lieu ou lien",
                        value = location,
                        onChange = { if (it.length <= 300) location = it },
                        placeholder = "ex. Chez nous · Producteur X · https://maps…"
                    )
                    Text(
                        "${location.length}/300",
                        color = WineColors.muted,
                        fontSize = 11.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                WeenoSecondaryButton("← Retour") { vm.wizardStep = 1 }
                WeenoPrimaryButton("Continuer → note") { vm.wizardStep = 3 }
            }

            else -> {
                val p = product
                if (p != null && p.wineName.isNotBlank()) {
                    WeenoLead(p.wineName)
                } else {
                    WeenoLead("Pas de vin identifié — retourne à l’étape 1.")
                }

                WeenoCard {
                    VivinoRatingSlider(rating, { rating = it }, onTick = { vm.hapticTick() })
                }

                WeenoCard {
                    RebuyChoiceRow(rebuy) { rebuy = it }
                }

                var noteVintage by remember { mutableStateOf(product?.vintage?.toString().orEmpty()) }
                var noteColor by remember { mutableStateOf(product?.styleFr ?: product?.style?.takeIf { it != "Unknown" }.orEmpty()) }
                var noteRegion by remember { mutableStateOf(product?.region.orEmpty()) }
                var noteCountry by remember { mutableStateOf(product?.country.orEmpty()) }
                var noteAbv by remember { mutableStateOf(product?.abv?.toString().orEmpty()) }

                WeenoCard {
                    Text("Arômes & structure", color = WineColors.text, fontWeight = FontWeight.SemiBold)
                    Text("Texte libre — tape et choisis dans les suggestions.", color = WineColors.muted, fontSize = 11.sp)
                    Spacer(Modifier.height(6.dp))
                    if (flavors.isNotEmpty()) {
                        FlowRowWrap {
                            flavors.sorted().forEach { tag ->
                                Text(
                                    "$tag ×",
                                    color = WineColors.accent,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(WineColors.accent.copy(alpha = 0.2f))
                                        .border(0.5.dp, WineColors.accent.copy(alpha = 0.65f), RoundedCornerShape(999.dp))
                                        .clickable { flavors = flavors - tag }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    FlavorSuggestInput(
                        placeholder = "ex. pierre chaude, salin…",
                        input = customFlavor,
                        onInput = { customFlavor = it },
                        catalog = flavorTags,
                        selected = flavors
                    ) { raw ->
                        var tag = raw.trim().replace(Regex("\\s+"), " ")
                        if (tag.length > 40) tag = tag.take(40)
                        val preset = flavorTags.firstOrNull { it.equals(tag, ignoreCase = true) }
                        if (preset != null) tag = preset
                        when {
                            tag.isBlank() -> {}
                            flavors.any { it.equals(tag, ignoreCase = true) } -> vm.showToast("Déjà ajouté", ToastPayload.Variant.WARN)
                            flavors.size >= 12 -> vm.showToast("Max 12 tags", ToastPayload.Variant.WARN)
                            else -> flavors = flavors + tag
                        }
                    }
                }

                WeenoCard {
                    Text("Détails", color = WineColors.text, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) {
                            WeenoField("Millésime", noteVintage, { noteVintage = it }, "2019", KeyboardType.Number)
                        }
                        Box(Modifier.weight(1f)) {
                            WeenoFormSelectField(
                                label = "Couleur",
                                value = noteColor,
                                options = listOf(
                                    "" to "—",
                                    "rouge" to "Rouge",
                                    "blanc" to "Blanc",
                                    "rose" to "Rosé",
                                    "effervescent" to "Effervescent",
                                    "orange" to "Orange",
                                    "fortifie" to "Fortifié",
                                    "autre" to "Autre",
                                ),
                                onChange = { noteColor = it },
                                placeholder = "Choisir…"
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) {
                            WeenoField("Région", noteRegion, { noteRegion = it }, "Saint-Aubin…")
                        }
                        Box(Modifier.weight(1f)) {
                            WeenoField("Pays", noteCountry, { noteCountry = it }, "France")
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    WeenoField("Degré %", noteAbv, { noteAbv = it }, "13.5", KeyboardType.Decimal)
                }

                WeenoCard {
                    Text("Commentaire (optionnel, 500 car.)", color = WineColors.text, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = comment,
                        onValueChange = { if (it.length <= 500) comment = it },
                        placeholder = { Text("Nez, bouche, accord…", color = WineColors.muted.copy(alpha = 0.6f)) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = WineColors.text,
                            unfocusedTextColor = WineColors.text,
                            focusedBorderColor = WineColors.accent,
                            unfocusedBorderColor = WineColors.border,
                            cursorColor = WineColors.accent,
                            focusedContainerColor = WineColors.fieldBg,
                            unfocusedContainerColor = WineColors.fieldBg
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )
                    Text("${comment.length}/500", color = WineColors.muted, fontSize = 11.sp, modifier = Modifier.align(Alignment.End))
                }

                product?.takeIf { it.wineName.isNotBlank() }?.let { p ->
                    WeenoSecondaryButton("+ Ajouter à la liste « À boire »") {
                        scope.launch {
                            try {
                                api.addWishlist(p.wineName, p.producer, p.style, p.barcode)
                                vm.showToast("Ajouté à À boire ✓", ToastPayload.Variant.SUCCESS)
                            } catch (e: Exception) {
                                vm.showToast(e.message ?: "Échec", ToastPayload.Variant.ERROR)
                            }
                        }
                    }
                }

                WeenoSecondaryButton("← Retour") { vm.wizardStep = 2 }
                WeenoPrimaryButton(
                    title = if (saving) "Enregistrement…" else "Enregistrer",
                    enabled = product != null && product!!.wineName.isNotBlank() && rating >= 0.25f,
                    busy = saving
                ) {
                    scope.launch {
                        product = product?.copy(
                            vintage = noteVintage.toIntOrNull(),
                            style = noteColor.ifBlank { product?.style ?: "autre" },
                            styleFr = noteColor.ifBlank { product?.styleFr },
                            region = noteRegion.ifBlank { null },
                            country = noteCountry.ifBlank { null },
                            abv = noteAbv.replace(',', '.').toDoubleOrNull() ?: product?.abv
                        )
                        doSave(force = false)
                    }
                }

                TextButton(onClick = { resetWizard() }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Reset wizard", color = WineColors.muted)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}


suspend fun tryMlKitBarcode(context: Context, file: File): String? =
    withContext(Dispatchers.IO) {
        try {
            suspendCancellableCoroutine { cont ->
                try {
                    val img = com.google.mlkit.vision.common.InputImage.fromFilePath(context, Uri.fromFile(file))
                    val sc = com.google.mlkit.vision.barcode.BarcodeScanning.getClient()
                    sc.process(img)
                        .addOnSuccessListener { bs ->
                            val code = bs.firstOrNull { b ->
                                val f = b.format
                                (f == com.google.mlkit.vision.barcode.common.Barcode.FORMAT_EAN_13 ||
                                    f == com.google.mlkit.vision.barcode.common.Barcode.FORMAT_EAN_8 ||
                                    f == com.google.mlkit.vision.barcode.common.Barcode.FORMAT_UPC_A ||
                                    f == com.google.mlkit.vision.barcode.common.Barcode.FORMAT_UPC_E) &&
                                    b.rawValue != null
                            }?.rawValue ?: bs.firstOrNull { it.rawValue != null }?.rawValue
                            try { sc.close() } catch (_: Exception) {}
                            cont.resume(code)
                        }
                        .addOnFailureListener { ex ->
                            try { sc.close() } catch (_: Exception) {}
                            cont.resume(null)
                        }
                    cont.invokeOnCancellation { try { sc.close() } catch (_: Exception) {} }
                } catch (e: Exception) {
                    cont.resume(null)
                }
            }
        } catch (_: Exception) {
            null
        }
    }

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



@Composable
fun CheckinEditSheet(vm: AppViewModel, item: CheckinItem) {
    val scope = rememberCoroutineScope()
    var rating by remember { mutableFloatStateOf(item.rating.toFloat()) }
    var rebuy by remember { mutableStateOf(item.rebuy) }
    var comment by remember { mutableStateOf(item.comment.orEmpty()) }
    var location by remember { mutableStateOf(item.location.orEmpty()) }
    var flavors by remember { mutableStateOf(item.flavors.orEmpty().toSet()) }
    var hops by remember { mutableStateOf(item.hops.orEmpty().toSet()) }
    var flavorTags by remember { mutableStateOf(listOf<String>()) }
    var hopTags by remember { mutableStateOf(listOf<String>()) }
    var customFlavor by remember { mutableStateOf("") }
    var customHop by remember { mutableStateOf("") }
    var hidden by remember { mutableStateOf(item.hiddenFromPartner == true) }
    var busy by remember { mutableStateOf(false) }
    var removePhoto by remember { mutableStateOf(false) }
    var newPhoto by remember { mutableStateOf<File?>(null) }
    val context = LocalContext.current
    var pending by remember { mutableStateOf<File?>(null) }

    LaunchedEffect(Unit) {
        try {
            val fh = vm.api.flavors(item.style.orEmpty())
            flavorTags = (fh.suggestedFlavors ?: fh.flavors).orEmpty()
            hopTags = (fh.suggestedHops ?: fh.hops).orEmpty()
        } catch (_: Exception) {
        }
    }

    val takePic = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok && pending != null) {
            newPhoto = pending
            removePhoto = false
        }
        pending = null
    }

    SheetScaffold("Modifier la dégustation", onClose = { vm.closeSheet() }) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "${item.producer ?: "—"} · ${item.style ?: "?"} · ${formatDate(item.createdAt)}",
                color = WineColors.muted,
                fontSize = 13.sp
            )
            WeenoCard {
                VivinoRatingSlider(rating, { rating = it }, onTick = { vm.hapticTick() })
            }
            WeenoCard {
                RebuyChoiceRow(rebuy) { rebuy = it }
            }
            WeenoCard {
                Text("Arômes & structure", color = WineColors.text, fontWeight = FontWeight.SemiBold)
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
            WeenoField("Commentaire", comment, { if (it.length <= 300) comment = it })
            WeenoField(
                label = "Lieu ou lien",
                value = location,
                onChange = { if (it.length <= 300) location = it },
                placeholder = "ex. Chez nous · https://maps…"
            )
            if (vm.isAdmin) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Masqué partenaire", color = WineColors.text, modifier = Modifier.weight(1f))
                    Switch(checked = hidden, onCheckedChange = { hidden = it })
                }
            }
            WeenoSecondaryButton("📷 Nouvelle photo") {
                try {
                    val dir = File(context.cacheDir, "beer").apply { mkdirs() }
                    val f = File(dir, "edit_${System.currentTimeMillis()}.jpg")
                    val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", f)
                    pending = f
                    takePic.launch(uri)
                } catch (e: Exception) {
                    vm.showToast(e.message ?: "Caméra", ToastPayload.Variant.ERROR)
                }
            }
            if (item.resolvedPhoto != null || newPhoto != null) {
                WeenoSecondaryButton("Retirer la photo") {
                    removePhoto = true
                    newPhoto = null
                }
            }
            WeenoPrimaryButton(if (busy) "Enregistrement…" else "Enregistrer", busy = busy) {
                scope.launch {
                    busy = true
                    try {
                        vm.api.updateCheckin(
                            id = item.id,
                            rating = rating.toDouble(),
                            flavors = flavors.toList(),
                            hops = hops.toList(),
                            comment = comment,
                            hiddenFromPartner = if (vm.isAdmin) hidden else null,
                            location = location.take(300),
                            rebuy = rebuy
                        )
                        if (removePhoto) {
                            try { vm.api.removeCheckinPhoto(item.id) } catch (_: Exception) {}
                        }
                        newPhoto?.let { f ->
                            val bytes = ImageUtils.compressJPEG(f.readBytes())
                            vm.api.replaceCheckinPhoto(item.id, bytes)
                        }
                        vm.showToast("Modifié ✓", ToastPayload.Variant.SUCCESS)
                        vm.closeSheet()
                    } catch (e: Exception) {
                        vm.showToast(e.message ?: "Erreur", ToastPayload.Variant.ERROR)
                    } finally {
                        busy = false
                    }
                }
            }
        }
    }
}

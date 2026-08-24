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
fun CheckinDetailSheet(vm: AppViewModel, item: CheckinItem) {
    val scope = rememberCoroutineScope()
    var hidden by remember { mutableStateOf(item.hiddenFromPartner == true) }

    // Parité iOS CheckinDetailView + WeenoDetailHead
    Column(
        Modifier
            .fillMaxSize()
            .background(WineColors.bg)
            .consumeClicks()
    ) {
        // WeenoDetailHead
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            WeenoGhostButton("Fermer", onClick = { vm.closeSheet() })
            Spacer(Modifier.weight(1f))
            if (vm.isAdmin) {
                WeenoGhostButton(
                    if (hidden) "Visible" else "Masquer",
                    onClick = {
                        val next = !hidden
                        hidden = next
                        scope.launch {
                            try {
                                vm.api.updateCheckin(item.id, hiddenFromPartner = next)
                                vm.showToast(
                                    if (next) "Masqué partenaire" else "Visible partenaire",
                                    ToastPayload.Variant.SUCCESS
                                )
                            } catch (e: Exception) {
                                hidden = !next
                                vm.showToast(e.message ?: "Erreur", ToastPayload.Variant.ERROR)
                            }
                        }
                    }
                )
            }
            Button(
                onClick = { vm.startRetaste(item) },
                colors = ButtonDefaults.buttonColors(containerColor = WineColors.accent),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    "Noter à nouveau",
                    color = WineColors.btnPrimaryText,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    maxLines = 1
                )
            }
            WeenoGhostButton(
                "Modifier",
                onClick = {
                    vm.editingCheckin = item
                    vm.openSheet(WeenoSheet.EDIT)
                }
            )
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (!item.resolvedPhoto.isNullOrBlank()) {
                WeenoAuthImage(
                    path = item.resolvedPhoto,
                    api = vm.api,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, WineColors.border, RoundedCornerShape(14.dp))
                )
            } else {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, WineColors.border, RoundedCornerShape(14.dp))
                        .background(WineColors.card),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Pas de photo", color = WineColors.muted)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.wineName,
                    color = WineColors.text,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (vm.isAdmin && (hidden || item.hiddenFromPartner == true)) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "privé",
                        color = WineColors.accent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(WineColors.accent.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
            Text(
                "${item.producer ?: "—"} · ${item.style ?: "?"} · ${formatDate(item.createdAt)}",
                color = WineColors.muted,
                fontSize = 13.sp
            )

            item.location?.trim()?.takeIf { it.isNotEmpty() }?.let { loc ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, WineColors.border, RoundedCornerShape(14.dp))
                        .background(WineColors.card)
                        .padding(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text("📍", fontSize = 14.sp)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Lieu", color = WineColors.muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Text(loc, color = WineColors.text, fontSize = 14.sp)
                    }
                }
            }

            WeenoStarRating(item.rating)

            rebuyLabel(item.rebuy)?.let {
                Text(it, color = WineColors.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            item.alsoTastedBy?.takeIf { it.isNotEmpty() }?.let {
                Text("👥 aussi dégusté par ${it.joinToString(", ")}", color = WineColors.muted, fontSize = 12.sp)
            }

            item.flavors?.takeIf { it.isNotEmpty() }?.let {
                Text(
                    "Goûts : ${it.joinToString(", ")}",
                    color = WineColors.text,
                    fontSize = 13.sp
                )
            }
            item.hops?.takeIf { it.isNotEmpty() }?.let {
                Text(
                    "Houblons : ${it.joinToString(", ")}",
                    color = WineColors.muted,
                    fontSize = 13.sp
                )
            }
            item.comment?.takeIf { it.isNotBlank() }?.let { c ->
                Text(
                    "« $c »",
                    color = WineColors.text,
                    fontSize = 14.sp,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, WineColors.border, RoundedCornerShape(14.dp))
                        .background(WineColors.card)
                        .padding(12.dp)
                )
            }
        }
    }
}

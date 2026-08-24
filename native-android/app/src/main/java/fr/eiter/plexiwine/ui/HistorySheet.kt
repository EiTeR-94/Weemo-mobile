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
fun HistorySheet(vm: AppViewModel) {
    val api = vm.api
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf(listOf<CheckinItem>()) }
    var stats by remember { mutableStateOf<HistoryStats?>(null) }
    var styles by remember { mutableStateOf(listOf<StyleOption>()) }
    var filterStyle by remember { mutableStateOf("") }
    var filterRating by remember { mutableFloatStateOf(0f) }
    var filterPeriod by remember { mutableStateOf("") }
    var offset by remember { mutableIntStateOf(0) }
    var hasMore by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val pageSize = 10
    val cache = vm.listCache

    suspend fun load(append: Boolean) {
        if (loading) return
        loading = true
        error = null
        try {
            val off = if (append) offset else 0
            val page = api.checkins(
                style = filterStyle,
                minRating = filterRating.toDouble(),
                period = filterPeriod,
                limit = pageSize,
                offset = off
            )
            items = if (append) items + page else page
            offset = off + page.size
            hasMore = page.size >= pageSize
            if (!append) {
                stats = api.stats()
                // Ne cache la page « unfiltered » complète que sans filtres
                if (filterStyle.isEmpty() && filterRating <= 0f && filterPeriod.isEmpty()) {
                    cache.saveCheckins(items)
                    stats?.let { cache.saveStats(it) }
                }
            }
        } catch (e: Exception) {
            if (!append) {
                val cached = cache.loadCheckins()
                if (cached.isNotEmpty()) {
                    items = cached
                    stats = cache.loadStats()
                    error = "Hors ligne — cache local (${vm.networkStatus.label.lowercase()})"
                } else {
                    error = e.message ?: "Impossible de charger (pas de cache)"
                }
            } else {
                error = e.message
            }
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        // Styles: live then cache
        styles = try {
            api.styles().also { if (it.isNotEmpty()) cache.saveStyles(it) }
        } catch (_: Exception) {
            cache.loadStyles()
        }
        // Affiche le cache immédiatement si hors ligne
        if (vm.networkStatus != NetworkStatus.ONLINE) {
            val cached = cache.loadCheckins()
            if (cached.isNotEmpty()) {
                items = cached
                stats = cache.loadStats()
                error = "Hors ligne — cache local"
            }
        }
        load(false)
    }
    LaunchedEffect(filterStyle, filterRating, filterPeriod) {
        offset = 0
        load(false)
    }

    SheetScaffold(
        title = "Historique",
        onClose = { vm.closeSheet() },
        trailing = {
            TextButton(onClick = {
                vm.closeSheet()
                vm.openSheet(WeenoSheet.GALLERY)
            }) { Text("📷 Galerie", color = WineColors.accent) }
        }
    ) {
        stats?.takeIf { it.total > 0 }?.let { s ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatCell("${s.total}", "dégust.", Modifier.weight(1f))
                StatCell(formatRating(s.avgRating ?: 0.0), "moyenne", Modifier.weight(1f))
                StatCell(s.topStyles?.firstOrNull()?.style ?: "—", "top style", Modifier.weight(1f))
                StatCell(s.last?.wineName ?: "—", "dernière", Modifier.weight(1f), small = true)
            }
            Spacer(Modifier.height(8.dp))
        }

        // Filtres parité iOS (Style / Note min / Période week|month|year)
        WeenoHistoryFiltersRow(
            filterStyle = filterStyle,
            filterRating = filterRating,
            filterPeriod = filterPeriod,
            styles = styles,
            onStyle = { filterStyle = it },
            onRating = { filterRating = it },
            onPeriod = { filterPeriod = it },
        )

        error?.let { Text(it, color = WineColors.error, fontSize = 12.sp) }

        when {
            loading && items.isEmpty() -> {
                Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = WineColors.accent)
                }
            }
            items.isEmpty() -> {
                val hasFilters = filterStyle.isNotEmpty() || filterRating > 0 || filterPeriod.isNotEmpty()
                WeenoEmptyState(
                    if (hasFilters) "🔍" else "🍷",
                    if (hasFilters) "Aucun résultat" else "Aucune dégustation",
                    if (hasFilters) "Ajuste les filtres." else "Note ta première vin depuis l'accueil."
                )
            }
            else -> {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f, fill = true)) {
                    items(items, key = { it.id }) { item ->
                        HistoryCard(vm, item,
                            onOpen = {
                                vm.selectedCheckin = item
                                vm.openSheet(WeenoSheet.DETAIL)
                            },
                            onEdit = {
                                vm.editingCheckin = item
                                vm.openSheet(WeenoSheet.EDIT)
                            },
                            onDelete = {
                                // confirmation handled inside HistoryCard
                            },
                            onConfirmDelete = {
                                scope.launch {
                                    try {
                                        if (vm.networkStatus != NetworkStatus.ONLINE) {
                                            vm.enqueueDeleteCheckin(item.id)
                                        } else {
                                            try {
                                                api.deleteCheckin(item.id)
                                                vm.listCache.invalidateHistory()
                                                vm.showToast("Supprimé", ToastPayload.Variant.SUCCESS)
                                            } catch (e: Exception) {
                                                if (e is java.io.IOException) {
                                                    vm.enqueueDeleteCheckin(item.id)
                                                } else {
                                                    throw e
                                                }
                                            }
                                        }
                                        load(false)
                                    } catch (e: Exception) {
                                        vm.showToast(e.message ?: "Erreur", ToastPayload.Variant.ERROR)
                                    }
                                }
                            }
                        )
                    }
                    if (hasMore) {
                        item {
                            WeenoSecondaryButton(if (loading) "Chargement…" else "Charger 10 de plus") {
                                scope.launch { load(true) }
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun StatCell(value: String, label: String, modifier: Modifier = Modifier, small: Boolean = false) {
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(WineColors.card)
            .border(1.dp, WineColors.border, RoundedCornerShape(10.dp))
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = WineColors.text, fontWeight = FontWeight.Bold, fontSize = if (small) 11.sp else 14.sp, maxLines = 2)
        Text(label, color = WineColors.muted, fontSize = 11.sp)
    }
}


/** Parité webapp history.js — icône badge "je rachèterais". */
fun rebuyEmoji(rebuy: String?): String? = when (rebuy) {
    "yes" -> "👍"
    "maybe" -> "🤔"
    "no" -> "👎"
    else -> null
}


/** Parité webapp checkin-detail.js — libellé détaillé "je rachèterais". */
fun rebuyLabel(rebuy: String?): String? = when (rebuy) {
    "yes" -> "👍 Je rachèterais"
    "maybe" -> "🤔 Peut-être"
    "no" -> "👎 Je ne rachèterais pas"
    else -> null
}


@Composable
fun HistoryCard(
    vm: AppViewModel,
    item: CheckinItem,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit = {},
    onConfirmDelete: () -> Unit = onDelete
) {
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Supprimer ?") },
            text = { Text("Supprimer « ${item.wineName} » de l'historique ?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onConfirmDelete()
                }) { Text("Supprimer", color = WineColors.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Annuler") }
            }
        )
    }
    WeenoCard {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onOpen),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            WeenoAuthImage(
                path = item.resolvedPhoto,
                api = vm.api,
                modifier = Modifier.size(88.dp).clip(RoundedCornerShape(10.dp))
            )
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.wineName, color = WineColors.text, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    rebuyEmoji(item.rebuy)?.let { emoji ->
                        Text(emoji, fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp))
                    }
                    if (vm.isAdmin && item.hiddenFromPartner == true) {
                        Text("privé", color = WineColors.accent, fontSize = 10.sp)
                    }
                }
                WeenoStarRating(item.rating, showNumber = true)
                Text(
                    "${item.producer ?: "—"} · ${item.style ?: "Inconnu"} · ${formatDate(item.createdAt)}",
                    color = WineColors.muted,
                    fontSize = 12.sp
                )
                item.location?.trim()?.takeIf { it.isNotEmpty() }?.let {
                    Text("📍 $it", color = WineColors.muted, fontSize = 12.sp, maxLines = 2)
                }
                item.flavors?.takeIf { it.isNotEmpty() }?.let {
                    Text(it.joinToString(", "), color = WineColors.muted, fontSize = 12.sp)
                }
                item.hops?.takeIf { it.isNotEmpty() }?.let {
                    Text("Houblons : ${it.joinToString(", ")}", color = WineColors.muted, fontSize = 12.sp)
                }
                item.alsoTastedBy?.takeIf { it.isNotEmpty() }?.let {
                    Text("👥 aussi dégusté par ${it.joinToString(", ")}", color = WineColors.muted, fontSize = 12.sp)
                }
                // Commentaire visible (parité iOS) — manquait sur l’APK
                item.comment?.takeIf { it.isNotBlank() }?.let { c ->
                    Spacer(Modifier.height(6.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                            .clip(RoundedCornerShape(8.dp))
                            .background(WineColors.bg.copy(alpha = 0.55f))
                    ) {
                        Box(
                            Modifier
                                .width(3.dp)
                                .fillMaxHeight()
                                .background(WineColors.accent)
                        )
                        Text(
                            "« $c »",
                            color = WineColors.text,
                            fontSize = 13.sp,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 9.dp, vertical = 7.dp)
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onEdit) { Text("Modifier", color = WineColors.accent) }
            TextButton(onClick = {
                vm.startRetaste(item)
            }) { Text("Re-noter", color = WineColors.text) }
            TextButton(onClick = { confirmDelete = true }) { Text("Suppr.", color = WineColors.error) }
        }
    }
}

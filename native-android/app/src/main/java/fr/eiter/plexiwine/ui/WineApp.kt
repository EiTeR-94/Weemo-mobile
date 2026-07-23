package fr.eiter.plexiwine.ui

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
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
fun WineApp(vm: AppViewModel) {
    val context = LocalContext.current
    Box(
        Modifier
            .fillMaxSize()
            .background(WineColors.bg)
    ) {
        when {
            vm.isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = WineColors.accent)
                }
            }
            !vm.isLoggedIn -> LoginScreen(vm)
            else -> MainScreen(vm)
        }
        // Bannière haut d'écran = iOS (tap ou × pour fermer)
        ToastOverlay(toast = vm.toast, onDismiss = { vm.hideToast() })
        // Weeno intro + célébrations (au-dessus du toast)
        if (vm.isLoggedIn) {
            RpgCelebrationOverlay(vm)
        }
    }
}

/** Lit le presse-papiers et ne garde qu'un lien/token d'invitation Weeno valide (comme iOS). */
private fun readInviteFromClipboard(context: Context): String? {
    return try {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val raw = cm?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
            ?.trim().orEmpty()
        if (raw.isEmpty()) return null
        if (InviteSessionStore.parseInviteToken(raw) != null) return raw
        // Cherche une URL join dans un texte plus large
        val re = Regex("""https?://[^\s]+/wine(?:-alpha)?/join/[A-Za-z0-9_-]{24,}""")
        val m = re.find(raw)?.value
        if (m != null && InviteSessionStore.parseInviteToken(m) != null) m else null
    } catch (_: Exception) {
        null
    }
}

private fun shortInvitePreview(raw: String): String {
    val t = InviteSessionStore.parseInviteToken(raw)
    return if (t != null && t.length >= 16) {
        "Token : ${t.take(10)}…${t.takeLast(6)}"
    } else {
        raw.take(48) + if (raw.length > 48) "…" else ""
    }
}

@Composable
private fun LoginScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val deepLink = vm.pendingInviteLink
    var mode by remember(deepLink) { mutableStateOf(if (deepLink != null) "invite" else "owner") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var inviteLink by remember(deepLink) { mutableStateOf(deepLink.orEmpty()) }
    var inviteEmail by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var clipboardHint by remember { mutableStateOf<String?>(null) }
    var showManual by remember { mutableStateOf(false) }

    fun doJoin(link: String) {
        val email = inviteEmail.trim()
        if (email.isEmpty() || !email.contains("@")) {
            error = "Entre l'email que tu as donné pour l'invitation"
            return
        }
        busy = true
        error = null
        vm.joinInvite(link, email) { result ->
            busy = false
            result.onFailure { e -> error = e.message ?: "Activation impossible" }
        }
    }

    fun applyClipboard(autoActivate: Boolean) {
        val clip = readInviteFromClipboard(context)
        if (clip == null) {
            clipboardHint = null
            if (autoActivate) {
                error = "Aucun lien d'invitation dans le presse‑papiers — copie le lien reçu puis réessaie"
            }
            return
        }
        inviteLink = clip
        clipboardHint = "Lien d'invitation prêt — entre ton email puis active"
        error = null
        // Jamais d'auto-activation : l'email doit être saisi explicitement
    }

    // Deep link → préremplit le lien, l'invité saisit l'email puis active
    LaunchedEffect(deepLink) {
        if (!deepLink.isNullOrBlank()) {
            mode = "invite"
            inviteLink = deepLink
            error = null
            clipboardHint = "Lien reçu — entre ton email pour activer"
        }
    }

    // Au premier affichage : si le presse-papiers a déjà un lien join → onglet Invitation
    LaunchedEffect(Unit) {
        if (!deepLink.isNullOrBlank()) return@LaunchedEffect
        val clip = readInviteFromClipboard(context)
        if (clip != null) {
            mode = "invite"
            inviteLink = clip
            clipboardHint = "Lien d'invitation détecté dans le presse‑papiers"
        }
    }

    // Au retour sur l'app (depuis WhatsApp) : relire le presse-papiers si vide
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && mode == "invite" && !busy && inviteLink.isBlank()) {
                applyClipboard(autoActivate = false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(48.dp))
        Text("🍷", fontSize = 48.sp)
        Text("Weeno", style = MaterialTheme.typography.headlineLarge, color = WineColors.text)
        Text("Journal de dégustation privé", color = WineColors.muted, fontSize = 13.sp)
        Spacer(Modifier.height(20.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            WeenoGhostButton(
                if (mode == "owner") "• Compte" else "Compte",
                { mode = "owner"; error = null },
                Modifier.weight(1f)
            )
            WeenoGhostButton(
                if (mode == "invite") "• Invitation" else "Invitation",
                {
                    mode = "invite"
                    error = null
                    applyClipboard(autoActivate = false)
                },
                Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(20.dp))

        if (mode == "owner") {
            WeenoField("Utilisateur", username, { username = it }, "ton compte")
            Spacer(Modifier.height(10.dp))
            Column(Modifier.fillMaxWidth()) {
                Text("Mot de passe", color = WineColors.muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
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
            }
            Spacer(Modifier.height(16.dp))
            error?.let {
                Text(it, color = WineColors.error, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
            }
            WeenoPrimaryButton(
                title = if (busy) "Connexion…" else "Se connecter",
                enabled = username.isNotBlank() && password.isNotBlank() && !busy,
                busy = busy
            ) {
                busy = true
                error = null
                vm.login(username.trim(), password) { result ->
                    busy = false
                    result.onFailure { e -> error = e.message ?: "Connexion impossible" }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Wi‑Fi maison ou VPN Plexi requis", color = WineColors.muted, fontSize = 11.sp)
        } else {
            // ——— Invitation : lien + email (pas d'indice UI) ———
            Text(
                "Copie le lien reçu, entre l'email que tu as donné, puis active. Aucun indice d'email dans l'app.",
                color = WineColors.muted,
                fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            WeenoSecondaryButton(
                title = "Coller le lien depuis le presse‑papiers",
                enabled = !busy
            ) {
                applyClipboard(autoActivate = false)
            }
            clipboardHint?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = WineColors.ok, fontSize = 12.sp, modifier = Modifier.fillMaxWidth())
            }
            if (inviteLink.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    shortInvitePreview(inviteLink),
                    color = WineColors.muted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(12.dp))
            Text("Ton email", color = WineColors.muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
            OutlinedTextField(
                value = inviteEmail,
                onValueChange = { inviteEmail = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text("celui que tu as donné", color = WineColors.muted, fontSize = 12.sp)
                },
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
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = WineColors.error, fontSize = 13.sp, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(12.dp))
            WeenoPrimaryButton(
                title = if (busy) "Activation…" else "Activer l'invitation",
                enabled = inviteLink.isNotBlank() && inviteEmail.isNotBlank() && !busy,
                busy = busy
            ) {
                doJoin(inviteLink.trim())
            }
            Spacer(Modifier.height(12.dp))
            Text(
                if (showManual) "▾ Saisie manuelle du lien" else "▸ Saisie manuelle du lien (rare)",
                color = WineColors.muted,
                fontSize = 12.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showManual = !showManual }
                    .padding(vertical = 4.dp)
            )
            if (showManual) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = inviteLink,
                    onValueChange = { inviteLink = it },
                    singleLine = false,
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text("https://eiter.freeboxos.fr/wine/join/…", color = WineColors.muted, fontSize = 12.sp)
                    },
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
            }
            Spacer(Modifier.height(12.dp))
            Text("1 téléphone · email requis · 4G/5G OK", color = WineColors.muted, fontSize = 11.sp)
        }
        Spacer(Modifier.height(16.dp))
        Text("Scan · photo · note · historique", color = WineColors.muted, fontSize = 12.sp)
    }
}

@Composable
private fun MainScreen(vm: AppViewModel) {
    BackHandler(enabled = vm.sheet != null) { vm.closeSheet() }

    var showAccountMenu by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var showFeedback by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current

    // Check maj APK + sync léger à chaque retour sur l'app
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                vm.onAppResumed()
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    LaunchedEffect(vm.requestOpenGrimoire) {
        if (vm.requestOpenGrimoire) {
            vm.consumeOpenGrimoireRequest()
            vm.refreshRpg()
            vm.openSheet(WeenoSheet.GRIMOIRE)
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // Header compact — actions dans « Mon compte » (parité PWA)
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Weeno", style = MaterialTheme.typography.headlineSmall, color = WineColors.text)
                        // APK d’abord (version installée), webapp ensuite — parité iOS
                        Text(
                            buildString {
                                append("APK ${vm.appVersion}")
                                if (vm.serverVersion.isNotBlank()) {
                                    append(" · web ${vm.serverVersion}")
                                }
                            },
                            color = WineColors.muted,
                            fontSize = 12.sp
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = { vm.refreshApp() },
                            enabled = !vm.isRefreshing,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = WineColors.text),
                            border = BorderStroke(1.dp, WineColors.border),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                if (vm.isRefreshing) "…" else "MAJ",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                        OutlinedButton(
                            onClick = { showAccountMenu = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = WineColors.text),
                            border = BorderStroke(1.dp, WineColors.border),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Mon compte", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (vm.needsAppUpdate) {
                    AppUpdateBanner(
                        current = vm.appVersion,
                        latest = vm.latestAndroidVersion ?: "?",
                        portalUrl = ServerSettings.portalURL
                    )
                    Spacer(Modifier.height(8.dp))
                }
                // Weeno HUD (raccourci grimoire, comme PWA)
                vm.rpgState?.profile?.takeIf { vm.rpgActive }?.let { profile ->
                    BqHudBar(profile) {
                        vm.refreshRpg()
                        vm.openSheet(WeenoSheet.GRIMOIRE)
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            if (vm.networkStatus != NetworkStatus.ONLINE || vm.pendingCount > 0) {
                Box(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                    NetworkStatusBar(vm.networkStatus, vm.pendingCount, vm.lastEndpointLatencyMs)
                }
                if (vm.networkStatus != NetworkStatus.ONLINE && vm.pendingCount > 0) {
                    Text(
                        "Mode offline — ${vm.pendingCount} en file, sync auto au retour réseau",
                        color = WineColors.muted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                    )
                }
            }

            WeenoStepNav(vm.wizardStep) { vm.wizardStep = it }

            Box(Modifier.weight(1f)) {
                WeenoWizard(vm)
            }
        }

        if (showAccountMenu) {
            AccountMenuOverlay(
                vm = vm,
                onDismiss = { showAccountMenu = false },
                onOpen = { sheet ->
                    showAccountMenu = false
                    when (sheet) {
                        WeenoSheet.GRIMOIRE -> {
                            vm.refreshRpg()
                            vm.openSheet(sheet)
                        }
                        else -> vm.openSheet(sheet)
                    }
                },
                onFeedback = {
                    showAccountMenu = false
                    showFeedback = true
                },
                onLogout = {
                    showAccountMenu = false
                    showLogoutConfirm = true
                }
            )
        }

        if (showFeedback) {
            FeedbackDialog(
                onDismiss = { showFeedback = false },
                onSend = { msg, cat ->
                    vm.sendFeedback(msg, cat) { ok ->
                        if (ok) showFeedback = false
                    }
                }
            )
        }

        // Popup réponses admin feedback (parité iOS/web)
        vm.currentFeedbackReply?.let { item ->
            FeedbackReplyDialog(
                item = item,
                index = vm.feedbackReplyIndex,
                total = vm.pendingFeedbackReplies.size,
                onNext = { vm.advanceFeedbackReply() }
            )
        }

        if (showLogoutConfirm) {
            val invite = vm.isInvite
            AlertDialog(
                onDismissRequest = { showLogoutConfirm = false },
                title = { Text("Se déconnecter ?") },
                text = {
                    Text(
                        if (invite) {
                            "Tu perds l'accès sur cet appareil. Il faudra un nouveau lien d'invitation pour revenir."
                        } else {
                            "Tu devras te reconnecter (Wi‑Fi maison ou VPN) pour accéder à Weeno."
                        }
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        showLogoutConfirm = false
                        vm.logout()
                    }) {
                        Text("Se déconnecter", color = WineColors.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLogoutConfirm = false }) {
                        Text("Annuler")
                    }
                }
            )
        }

        // Sheets as full-screen overlays
        when (vm.sheet) {
            WeenoSheet.HISTORY -> HistorySheet(vm)
            WeenoSheet.GALLERY -> GallerySheet(vm)
            WeenoSheet.WISHLIST -> WishlistSheet(vm)
            WeenoSheet.GIFTS -> GiftsSheet(vm)
            WeenoSheet.PENDING -> PendingSheet(vm)
            WeenoSheet.DETAIL -> vm.selectedCheckin?.let { CheckinDetailSheet(vm, it) }
            WeenoSheet.EDIT -> vm.editingCheckin?.let { CheckinEditSheet(vm, it) }
            WeenoSheet.PATCHNOTES -> PatchnotesSheet(vm)
            WeenoSheet.ADMIN -> AdminSheet(vm)
            WeenoSheet.GRIMOIRE -> GrimoireSheet(vm)
            WeenoSheet.RPG_ADMIN -> RpgAdminSheet(vm)
            null -> {}
        }
    }
}

@Composable
private fun AccountMenuOverlay(
    vm: AppViewModel,
    onDismiss: () -> Unit,
    onOpen: (WeenoSheet) -> Unit,
    onFeedback: () -> Unit,
    onLogout: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    val config = LocalConfiguration.current
    // Plafond écran uniquement si le contenu dépasse — sinon hauteur = contenu (sous Déconnexion)
    val maxPanelH = minOf(config.screenHeightDp * 0.72f, (config.screenHeightDp - 72).toFloat()).dp
    val maxPanelW = minOf(320, config.screenWidthDp - 60).coerceAtLeast(240).dp

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(onClick = onDismiss)
        )
        // wrapContentHeight : pas de vide sous Déconnexion ; heightIn max seulement si trop long
        Column(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 56.dp, end = 12.dp)
                .width(maxPanelW)
                .wrapContentHeight()
                .heightIn(max = maxPanelH)
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, WineColors.border, RoundedCornerShape(16.dp))
                .background(WineColors.card)
                .verticalScroll(rememberScrollState(), enabled = true)
                .padding(horizontal = 10.dp, vertical = 12.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Connecté",
                        color = WineColors.muted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        when {
                            vm.isInvite -> vm.inviteLabel?.let { "invité · $it" } ?: "invité"
                            else -> vm.user ?: "—"
                        },
                        color = WineColors.text,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        buildString {
                            append("APK ${vm.appVersion}")
                            if (vm.serverVersion.isNotBlank()) {
                                append(" · web ${vm.serverVersion}")
                            }
                            vm.latestAndroidVersion?.let { latest ->
                                if (vm.needsAppUpdate) append(" · ⬆️ $latest dispo")
                            }
                        },
                        color = WineColors.muted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Text(
                    "×",
                    color = WineColors.muted,
                    fontSize = 20.sp,
                    modifier = Modifier
                        .clickable(onClick = onDismiss)
                        .padding(4.dp)
                )
            }
            Spacer(Modifier.height(6.dp))

            AccountSection("Journal")
            AccountMenuItem("📜 Historique") { onOpen(WeenoSheet.HISTORY) }
            if (!vm.isInvite) {
                AccountMenuItem("🍷 À boire") { onOpen(WeenoSheet.WISHLIST) }
                AccountMenuItem("🎁 Idées cadeaux") { onOpen(WeenoSheet.GIFTS) }
            }
            if (vm.rpgActive) {
                AccountMenuItem("📖 Grimoire") { onOpen(WeenoSheet.GRIMOIRE) }
            }
            if (vm.pendingCount > 0) {
                AccountMenuItem("⏳ En attente (${vm.pendingCount})") { onOpen(WeenoSheet.PENDING) }
            }

            AccountSection("Parler à l’admin")
            AccountMenuItem("💬 Un retour") { onFeedback() }

            if (vm.isAdmin) {
                AccountSection("Admin")
                AccountMenuItem("⚙️ Administration") { onOpen(WeenoSheet.ADMIN) }
                // Toujours visible admin : même si Weeno est coupé (pour le rallumer)
                AccountMenuItem("⚔ Weeno") { onOpen(WeenoSheet.RPG_ADMIN) }
                AccountMenuItem("📝 Patch notes") { onOpen(WeenoSheet.PATCHNOTES) }
            }

            AccountSection("Application")
            AccountMenuItem(
                if (vm.isRefreshing) "Check MAJ…" else "Check MAJ"
            ) {
                vm.refreshApp()
                onDismiss()
            }
            if (vm.needsAppUpdate) {
                val ctx = LocalContext.current
                AccountMenuItem("⬆️ Installer maj APK ${vm.latestAndroidVersion ?: ""}") {
                    onDismiss()
                    try {
                        ctx.startActivity(
                            android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(ServerSettings.portalURL)
                            )
                        )
                    } catch (_: Exception) {
                    }
                }
            }

            AccountSection("Session")
            AccountMenuItem("Déconnexion", danger = true) { onLogout() }
        }
    }
}

@Composable
private fun AccountSection(title: String) {
    Text(
        title,
        color = WineColors.muted,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 6.dp, top = 10.dp, bottom = 4.dp)
    )
}

@Composable
private fun AccountMenuItem(label: String, danger: Boolean = false, onClick: () -> Unit) {
    Text(
        label,
        color = if (danger) WineColors.error else WineColors.text,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 11.dp)
    )
}

@Composable
private fun AppUpdateBanner(current: String, latest: String, portalUrl: String) {
    val ctx = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(WineColors.accent.copy(alpha = 0.12f))
            .border(1.dp, WineColors.accent.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .clickable {
                try {
                    ctx.startActivity(
                        android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(portalUrl)
                        )
                    )
                } catch (_: Exception) {
                }
            }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "⬆️ Mise à jour APK disponible",
                color = WineColors.accent,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
            Text(
                "v$current → v$latest — tape pour le portail",
                color = WineColors.muted,
                fontSize = 11.sp
            )
        }
        Text("→", color = WineColors.accent, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun FeedbackReplyDialog(
    item: AdminFeedbackItem,
    index: Int,
    total: Int,
    onNext: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { /* forcer Compris */ },
        title = {
            Text(
                if (item.isRejected) "Feedback refusé" else "Feedback mis en place",
                color = WineColors.text,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    item.displayStatus,
                    color = WineColors.accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                item.message?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        "Tu avais écrit : « ${it.take(220)}${if (it.length > 220) "…" else ""} »",
                        color = WineColors.muted,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(6.dp))
                }
                Text(
                    item.adminReply
                        ?: if (item.isRejected) "Ta demande n'a pas été retenue."
                        else "Ta demande a été prise en compte.",
                    color = WineColors.text,
                    fontSize = 14.sp
                )
                if (total > 1) {
                    Spacer(Modifier.height(8.dp))
                    Text("${index + 1} / $total", color = WineColors.muted, fontSize = 11.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onNext) {
                Text(if (index + 1 < total) "Suivant" else "Compris", color = WineColors.accent)
            }
        },
        containerColor = WineColors.card
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FeedbackDialog(
    onDismiss: () -> Unit,
    onSend: (message: String, category: String) -> Unit,
) {
    var message by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("general") }
    var sending by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val scrollState = rememberScrollState()
    val bringIntoView = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    val categories = listOf(
        "general" to "Avis général",
        "bug" to "Bug",
        "idea" to "Idée",
        "ux" to "Interface",
        "rpg" to "RPG",
        "other" to "Autre",
    )

    fun hideKeyboard() {
        focusManager.clearFocus(force = true)
        keyboard?.hide()
    }

    // Dialog + imePadding (pas ModalBottomSheet) : le champ reste au-dessus du clavier
    Dialog(
        onDismissRequest = {
            if (!sending) {
                hideKeyboard()
                onDismiss()
            }
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = true,
            dismissOnBackPress = !sending,
            dismissOnClickOutside = !sending,
        ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(enabled = !sending) {
                    hideKeyboard()
                    onDismiss()
                }
                .imePadding()
                .navigationBarsPadding()
                .statusBarsPadding()
        ) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(WineColors.bg)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { /* absorbe les taps pour ne pas fermer */ }
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp)
                    .padding(top = 12.dp, bottom = 20.dp)
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(WineColors.muted.copy(alpha = 0.45f))
                )
                Spacer(Modifier.height(12.dp))
                Text("💬 Feedback", color = WineColors.text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Dis-nous ce qui va, ce qui coince ou une idée. Seul l’admin le lit.",
                    color = WineColors.muted,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(12.dp))
                Text("C’est plutôt…", color = WineColors.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                categories.chunked(3).forEach { row ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        row.forEach { (key, label) ->
                            val on = category == key
                            Text(
                                label,
                                color = if (on) Color.Black else WineColors.text,
                                fontSize = 12.sp,
                                fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (on) WineColors.accent else WineColors.card)
                                    .border(
                                        1.dp,
                                        if (on) WineColors.accent else WineColors.border,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable {
                                        category = key
                                        hideKeyboard()
                                    }
                                    .padding(vertical = 8.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                    Spacer(Modifier.height(6.dp))
                }
                Spacer(Modifier.height(8.dp))
                Text("Ton message", color = WineColors.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = message,
                    onValueChange = { if (it.length <= 1200) message = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp, max = 160.dp)
                        .bringIntoViewRequester(bringIntoView)
                        .onFocusEvent { state ->
                            if (state.isFocused) {
                                scope.launch {
                                    delay(280)
                                    bringIntoView.bringIntoView()
                                    scrollState.animateScrollTo(scrollState.maxValue)
                                }
                            }
                        },
                    placeholder = { Text("Écris librement…", color = WineColors.muted) },
                    maxLines = 6,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = androidx.compose.ui.text.input.ImeAction.Done
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onDone = { hideKeyboard() }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = WineColors.text,
                        unfocusedTextColor = WineColors.text,
                        focusedBorderColor = WineColors.accent,
                        unfocusedBorderColor = WineColors.border,
                        cursorColor = WineColors.accent,
                        focusedContainerColor = WineColors.card,
                        unfocusedContainerColor = WineColors.card,
                    )
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { hideKeyboard() }) {
                        Text("Masquer le clavier", color = WineColors.accent, fontSize = 12.sp)
                    }
                    Text(
                        "${message.length.coerceAtMost(1200)}/1200",
                        color = WineColors.muted,
                        fontSize = 11.sp
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            hideKeyboard()
                            if (!sending) onDismiss()
                        },
                        enabled = !sending,
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, WineColors.border)
                    ) {
                        Text("Annuler", color = WineColors.muted)
                    }
                    Button(
                        onClick = {
                            if (message.trim().length < 3 || sending) return@Button
                            hideKeyboard()
                            sending = true
                            onSend(message.trim(), category)
                        },
                        enabled = message.trim().length >= 3 && !sending,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = WineColors.accent)
                    ) {
                        Text(
                            if (sending) "…" else "Envoyer",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// ───────────────────────── Wizard ─────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WeenoWizard(vm: AppViewModel) {
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
        labelPhotoFile = f
        scope.launch {
            busy = true
            scanStatus = "Analyse de l’étiquette…"
            try {
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
                } else if (scan.aiAvailable) {
                    scanStatus = scan.hint
                        ?: "Étiquette lue — aucun candidat Vivino, cherche ou saisis"
                    if (vivinoQuery.length >= 2) {
                        // laisse l’utilisateur chercher ; query déjà préremplie
                    }
                } else {
                    showManual = true
                    val raw = (scan.aiError ?: scan.hint ?: "").lowercase()
                    scanStatus = when {
                        !scan.hint.isNullOrBlank() -> scan.hint!!
                        !scan.aiError.isNullOrBlank() -> scan.aiError!!
                        raw.contains("429") || raw.contains("quota") || raw.contains("rate") ->
                            "Scan temporairement saturé — réessaie ou saisie manuelle"
                        else -> "Scan indisponible — saisis ou cherche sur Vivino"
                    }
                }
            } catch (e: Exception) {
                val m = e.message ?: "Erreur scan"
                scanStatus = if (m.contains("JsonNull", ignoreCase = true)) {
                    "Erreur lecture réponse scan — mets à jour l’app"
                } else {
                    m
                }
            } finally {
                busy = false
            }
        }
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
                            .clickable { ensureCamera("scan") },
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
                    Text(scanStatus, color = WineColors.muted, fontSize = 13.sp, modifier = Modifier.fillMaxWidth())
                    if (labelPhotoFile != null && !busy) {
                        Spacer(Modifier.height(8.dp))
                        WeenoPrimaryButton("Lancer le scan") {
                            val f = labelPhotoFile ?: return@WeenoPrimaryButton
                            scope.launch {
                                busy = true
                                scanStatus = "Analyse de l’étiquette…"
                                try {
                                    val jpeg = ImageUtils.compressJPEG(f.readBytes())
                                    val scan = api.labelScan(jpeg)
                                    if (!scan.producer.isNullOrBlank()) vivinoProducer = scan.producer!!
                                    if (!scan.wineName.isNullOrBlank()) {
                                        vivinoQuery = listOfNotNull(scan.producer, scan.wineName)
                                            .filter { it.isNotBlank() }.joinToString(" ")
                                    }
                                    if (scan.candidates.isNotEmpty()) {
                                        vivinoResults = scan.candidates.take(5)
                                        scanStatus = "Étiquette lue — choisis le bon vin"
                                        vm.showToast("${scan.candidates.size} suggestion(s)", ToastPayload.Variant.SUCCESS)
                                    } else if (scan.aiAvailable) {
                                        scanStatus = scan.hint
                                            ?: "Étiquette lue — aucun candidat, cherche sur Vivino"
                                    } else {
                                        showManual = true
                                        scanStatus = scan.hint ?: scan.aiError
                                            ?: "Aucun candidat — cherche sur Vivino"
                                    }
                                } catch (e: Exception) {
                                    val m = e.message ?: "Erreur"
                                    scanStatus = if (m.contains("JsonNull", ignoreCase = true)) {
                                        "Erreur lecture réponse scan — mets à jour l’app"
                                    } else m
                                } finally {
                                    busy = false
                                }
                            }
                        }
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
                                vm.wizardStep = 2
                            }
                        }
                    }
                }

                product?.takeIf { it.wineName.isNotBlank() }?.let { p ->
                    WeenoPreviewCard(p)
                    WeenoSecondaryButton("Changer de vin") {
                        product = null
                        labelPhotoFile = null
                        scanStatus = "Cadre l’étiquette — touche pour photo"
                    }
                    WeenoPrimaryButton("Continuer → photo") { vm.wizardStep = 2 }
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

private suspend fun tryMlKitBarcode(context: Context, file: File): String? =
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

// ───────────────────────── Sheets ─────────────────────────

@Composable
private fun SheetScaffold(title: String, onClose: () -> Unit, trailing: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    // fillMaxSize + consumeClicks : bloque les taps vers le HUD Grimoire en dessous
    Column(
        Modifier
            .fillMaxSize()
            .background(WineColors.bg)
            .consumeClicks()
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = WineColors.text, modifier = Modifier.weight(1f))
            trailing?.invoke()
            TextButton(onClick = onClose) { Text("Fermer ✕", color = WineColors.muted) }
        }
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun HistorySheet(vm: AppViewModel) {
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
private fun StatCell(value: String, label: String, modifier: Modifier = Modifier, small: Boolean = false) {
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
private fun rebuyEmoji(rebuy: String?): String? = when (rebuy) {
    "yes" -> "👍"
    "maybe" -> "🤔"
    "no" -> "👎"
    else -> null
}

/** Parité webapp checkin-detail.js — libellé détaillé "je rachèterais". */
private fun rebuyLabel(rebuy: String?): String? = when (rebuy) {
    "yes" -> "👍 Je rachèterais"
    "maybe" -> "🤔 Peut-être"
    "no" -> "👎 Je ne rachèterais pas"
    else -> null
}

@Composable
private fun HistoryCard(
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

@Composable
private fun GallerySheet(vm: AppViewModel) {
    val api = vm.api
    var items by remember { mutableStateOf(listOf<CheckinItem>()) }
    var styles by remember { mutableStateOf(listOf<StyleOption>()) }
    var filterStyle by remember { mutableStateOf("") }
    var filterRating by remember { mutableFloatStateOf(0f) }
    var filterPeriod by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var offlineHint by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<CheckinItem?>(null) }
    val cache = vm.listCache
    val scope = rememberCoroutineScope()

    suspend fun reload() {
        loading = true
        try {
            styles = try {
                api.styles().also { if (it.isNotEmpty()) cache.saveStyles(it) }
            } catch (_: Exception) {
                cache.loadStyles()
            }
            val live = api.checkins(
                style = filterStyle,
                minRating = filterRating.toDouble(),
                period = filterPeriod,
                limit = 100,
                offset = 0
            )
            if (filterStyle.isEmpty() && filterRating <= 0f && filterPeriod.isEmpty()) {
                cache.saveCheckins(live)
            }
            items = live.filter { !it.resolvedPhoto.isNullOrBlank() }
            offlineHint = null
            vm.prewarmRecentPhotos()
        } catch (_: Exception) {
            val cached = cache.loadCheckins().filter { !it.resolvedPhoto.isNullOrBlank() }
            items = cached
            offlineHint = if (cached.isEmpty()) {
                "Hors ligne — aucune photo en cache"
            } else {
                "Hors ligne — galerie en cache"
            }
        }
        loading = false
    }

    LaunchedEffect(Unit) {
        val cached = cache.loadCheckins().filter { !it.resolvedPhoto.isNullOrBlank() }
        if (cached.isNotEmpty()) items = cached
        reload()
    }
    LaunchedEffect(filterStyle, filterRating, filterPeriod) {
        if (!loading || items.isNotEmpty()) reload()
    }

    SheetScaffold("Galerie photos", onClose = { vm.closeSheet() }) {
        offlineHint?.let {
            Text(it, color = WineColors.accent, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
        }
        WeenoHistoryFiltersRow(
            filterStyle = filterStyle,
            filterRating = filterRating,
            filterPeriod = filterPeriod,
            styles = styles,
            onStyle = { filterStyle = it },
            onRating = { filterRating = it },
            onPeriod = { filterPeriod = it },
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("${items.size} photos", color = WineColors.muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
            if (filterStyle.isNotEmpty() || filterRating > 0 || filterPeriod.isNotEmpty()) {
                TextButton(onClick = {
                    filterStyle = ""; filterRating = 0f; filterPeriod = ""
                }) {
                    Text("Réinit. filtres", color = WineColors.accent, fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        if (loading && items.isEmpty()) {
            CircularProgressIndicator(color = WineColors.accent, modifier = Modifier.align(Alignment.CenterHorizontally))
        } else if (items.isEmpty()) {
            WeenoEmptyState("📷", "Aucune photo", "Les dégustations avec photo apparaîtront ici.")
        } else {
            // Grille 3 colonnes (parité iOS LazyVGrid)
            val cols = 3
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f, fill = true)) {
                items(items.chunked(cols), key = { row -> row.joinToString("-") { it.id.toString() } }) { row ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { item ->
                            Column(
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        selected = item
                                    }
                            ) {
                                WeenoAuthImage(
                                    path = item.resolvedPhoto,
                                    api = api,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                )
                                Text(
                                    item.wineName,
                                    color = WineColors.text,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    modifier = Modifier.padding(top = 3.dp)
                                )
                                Text(
                                    "★ ${formatRating(item.rating)}",
                                    color = WineColors.accent,
                                    fontSize = 10.sp
                                )
                            }
                        }
                        // pad empty cells
                        repeat(cols - row.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }

    selected?.let { item ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(item.wineName, color = WineColors.text, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    WeenoAuthImage(
                        path = item.resolvedPhoto,
                        api = api,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${item.producer ?: "—"} · ★ ${formatRating(item.rating)}",
                        color = WineColors.muted,
                        fontSize = 13.sp
                    )
                    item.comment?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(6.dp))
                        Text("« $it »", color = WineColors.text, fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    selected = null
                    vm.selectedCheckin = item
                    vm.openSheet(WeenoSheet.DETAIL)
                }) { Text("Voir fiche", color = WineColors.accent) }
            },
            dismissButton = {
                TextButton(onClick = { selected = null }) { Text("Fermer", color = WineColors.muted) }
            },
            containerColor = WineColors.card
        )
    }
}

@Composable
private fun WishlistSheet(vm: AppViewModel) {
    val api = vm.api
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf(listOf<WishlistItem>()) }
    var newName by remember { mutableStateOf("") }
    var newProducer by remember { mutableStateOf("") }
    var offlineHint by remember { mutableStateOf<String?>(null) }
    val cache = vm.listCache

    suspend fun reload() {
        try {
            val live = api.wishlist()
            cache.saveWishlist(live)
            items = live
            offlineHint = null
        } catch (_: Exception) {
            val cached = cache.loadWishlist()
            items = cached
            offlineHint = if (cached.isEmpty()) {
                "Hors ligne — liste non en cache"
            } else {
                "Hors ligne — wishlist en cache"
            }
        }
    }

    LaunchedEffect(Unit) {
        val cached = cache.loadWishlist()
        if (cached.isNotEmpty()) items = cached
        reload()
    }

    SheetScaffold("À boire", onClose = { vm.closeSheet() }) {
        Text("Tes souhaits personnels (vins à goûter).", color = WineColors.muted, fontSize = 13.sp)
        offlineHint?.let {
            Text(it, color = WineColors.accent, fontSize = 12.sp)
        }
        Spacer(Modifier.height(8.dp))
        WeenoField("Nom vin", newName, { newName = it })
        Spacer(Modifier.height(6.dp))
        WeenoField("Producteur (optionnel)", newProducer, { newProducer = it })
        Spacer(Modifier.height(8.dp))
        WeenoPrimaryButton("Ajouter", enabled = newName.length >= 2 && vm.networkStatus == NetworkStatus.ONLINE) {
            scope.launch {
                try {
                    api.addWishlist(newName.trim(), newProducer.trim())
                    newName = ""
                    newProducer = ""
                    reload()
                    vm.showToast("Ajouté ✓", ToastPayload.Variant.SUCCESS)
                } catch (e: Exception) {
                    vm.showToast(e.message ?: "Échec", ToastPayload.Variant.ERROR)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (items.isEmpty()) {
            WeenoEmptyState("🍷", "Liste vide", "Ajoute des vins à goûter.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items, key = { it.id }) { w ->
                    WeenoCard {
                        Text(w.wineName, color = WineColors.text, fontWeight = FontWeight.Bold)
                        Text("${w.producer.orEmpty()} · ${w.style.orEmpty()}", color = WineColors.muted, fontSize = 12.sp)
                        Row {
                            TextButton(onClick = { vm.startWishlistTaste(w) }) {
                                Text("Goûter", color = WineColors.accent)
                            }
                            TextButton(onClick = {
                                scope.launch {
                                    try {
                                        api.deleteWishlist(w.id)
                                        reload()
                                    } catch (e: Exception) {
                                        vm.showToast(e.message ?: "Erreur", ToastPayload.Variant.ERROR)
                                    }
                                }
                            }) { Text("Suppr.", color = WineColors.error) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GiftsSheet(vm: AppViewModel) {
    val api = vm.api
    var gifts by remember { mutableStateOf(listOf<GiftIdea>()) }
    var users by remember { mutableStateOf(listOf<CoupleStats.CoupleUser>()) }
    var partner by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var filterStyle by remember { mutableStateOf("") }
    var minRating by remember { mutableFloatStateOf(0f) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }

    val cache = vm.listCache
    LaunchedEffect(Unit) {
        cache.loadCouple()?.let { cached ->
            gifts = cached.giftIdeas.orEmpty()
            users = cached.users.orEmpty()
            partner = users.firstOrNull { it.username != vm.user }?.username.orEmpty()
        }
        try {
            val data = api.coupleStats()
            gifts = data.giftIdeas.orEmpty()
            users = data.users.orEmpty()
            partner = users.firstOrNull { it.username != vm.user }?.username.orEmpty()
            cache.saveCouple(data)
            error = null
        } catch (e: Exception) {
            if (gifts.isEmpty()) {
                error = e.message ?: "Hors ligne — pas de cache cadeaux"
            } else {
                error = "Hors ligne — idées cadeaux en cache"
            }
        }
        loading = false
    }

    val styleOptions = remember(gifts) {
        gifts.mapNotNull { it.style }.filter { it.isNotEmpty() }.distinct().sorted()
    }
    val filtered = gifts.filter { g ->
        if (minRating > 0) {
            if (minRating >= 5f && (g.rating ?: 0.0) < 4.99) return@filter false
            else if ((g.rating ?: 0.0) < minRating) return@filter false
        }
        if (filterStyle.isNotEmpty() && g.resolvedStyle != filterStyle) return@filter false
        if (search.isNotEmpty()) {
            val hay = "${g.wineName} ${g.producer.orEmpty()} ${g.resolvedStyle.orEmpty()}".lowercase()
            if (!hay.contains(search.lowercase())) return@filter false
        }
        true
    }

    SheetScaffold(
        title = if (partner.isEmpty()) "Idées cadeaux" else "Idées cadeaux — $partner",
        onClose = { vm.closeSheet() }
    ) {
        error?.let { Text(it, color = WineColors.error) }
        if (loading) {
            CircularProgressIndicator(color = WineColors.accent, modifier = Modifier.align(Alignment.CenterHorizontally))
            return@SheetScaffold
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            users.forEach { u ->
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(WineColors.card)
                        .border(1.dp, WineColors.border, RoundedCornerShape(10.dp))
                        .padding(9.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(if (u.username == vm.user) "Toi" else u.username, color = WineColors.muted, fontSize = 11.sp)
                    Text("${u.total}", color = WineColors.text, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Text("dégust.", color = WineColors.muted, fontSize = 11.sp)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        WeenoGiftsFiltersRow(
            search = search,
            filterStyle = filterStyle,
            minRating = minRating,
            styleOptions = styleOptions,
            onSearch = { search = it },
            onStyle = { filterStyle = it },
            onRating = { minRating = it },
        )
        Spacer(Modifier.height(8.dp))
        if (filtered.isEmpty()) {
            Text("Aucune idée cadeau avec ces filtres.", color = WineColors.muted, modifier = Modifier.padding(24.dp))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f, fill = true)) {
                items(filtered, key = { it.id }) { g ->
                    WeenoCard {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            WeenoAuthImage(
                                path = ServerSettings.giftPhotoPath(g.photoPath),
                                api = api,
                                modifier = Modifier.size(88.dp).clip(RoundedCornerShape(10.dp))
                            )
                            Column(Modifier.weight(1f)) {
                                Text(g.wineName, color = WineColors.text, fontWeight = FontWeight.Bold)
                                Text(
                                    "${g.producer ?: "—"} · ${g.resolvedStyle ?: "?"}",
                                    color = WineColors.muted,
                                    fontSize = 12.sp
                                )
                                g.rating?.let {
                                    Text("★ ${formatRating(it)}", color = WineColors.accent, fontSize = 12.sp)
                                }
                                Text("Notée par ${g.resolvedLikedBy ?: "?"}", color = WineColors.muted, fontSize = 11.sp)
                                g.comment?.takeIf { it.isNotBlank() }?.let {
                                    Text("« $it »", color = WineColors.text, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PendingSheet(vm: AppViewModel) {
    SheetScaffold("En attente", onClose = { vm.closeSheet() }) {
        Text(
            when (vm.networkStatus) {
                NetworkStatus.ONLINE -> "Réseau OK — tu peux synchroniser."
                NetworkStatus.OFFLINE -> "Pas de réseau — les notes restent sur l'appareil."
                NetworkStatus.SERVER_UNREACHABLE -> "Serveur injoignable — file conservée."
            },
            color = WineColors.muted,
            fontSize = 12.sp
        )
        Spacer(Modifier.height(8.dp))
        WeenoPrimaryButton(
            "Synchroniser maintenant",
            enabled = vm.networkStatus == NetworkStatus.ONLINE && vm.pendingCount > 0
        ) {
            vm.requestSync()
        }
        Spacer(Modifier.height(8.dp))
        Text("Créations en attente (${vm.pendingItems.size})", color = WineColors.text, fontWeight = FontWeight.SemiBold)
        if (vm.pendingItems.isEmpty()) {
            Text("Aucune dégustation en attente.", color = WineColors.muted)
        } else {
            vm.pendingItems.forEach { p ->
                WeenoCard {
                    Text(p.wineName, color = WineColors.text, fontWeight = FontWeight.Bold)
                    Text("${p.producer} · ${p.style} · ★${formatRating(p.rating)}", color = WineColors.muted, fontSize = 12.sp)
                    p.location?.takeIf { it.isNotBlank() }?.let {
                        Text("📍 $it", color = WineColors.muted, fontSize = 12.sp)
                    }
                    TextButton(onClick = { vm.removePending(p.id) }) {
                        Text("Supprimer", color = WineColors.error)
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Suppressions en attente", color = WineColors.text, fontWeight = FontWeight.SemiBold)
        if (vm.pendingDeletes.isEmpty()) {
            Text("Aucune suppression en attente.", color = WineColors.muted)
        } else {
            vm.pendingDeletes.forEach { id ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Suppression #$id", color = WineColors.text, modifier = Modifier.weight(1f))
                    TextButton(onClick = { vm.removePendingDelete(id) }) {
                        Text("Annuler", color = WineColors.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckinDetailSheet(vm: AppViewModel, item: CheckinItem) {
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

@Composable
private fun CheckinEditSheet(vm: AppViewModel, item: CheckinItem) {
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

@Composable
private fun PatchnotesSheet(vm: AppViewModel) {
    var text by remember { mutableStateOf("Chargement…") }
    LaunchedEffect(Unit) {
        text = try {
            val p = vm.api.patchnotes()
            "v${p.version.orEmpty()}\n\n${p.markdown.orEmpty()}"
        } catch (e: Exception) {
            e.message ?: "Indisponible"
        }
    }
    SheetScaffold("Patch notes", onClose = { vm.closeSheet() }) {
        Text(text, color = WineColors.text, fontSize = 13.sp, modifier = Modifier.verticalScroll(rememberScrollState()))
    }
}

/* Admin complet : AdminSheet.kt (parité iOS / webapp) */

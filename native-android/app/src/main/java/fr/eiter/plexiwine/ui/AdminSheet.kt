package fr.eiter.plexiwine.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.eiter.plexiwine.*
import fr.eiter.plexiwine.ui.theme.WineColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Admin comptes / invités / outils — parité webapp + iOS.
 */
@Composable
fun AdminSheet(vm: AppViewModel) {
    var tab by remember { mutableIntStateOf(0) } // 0 comptes, 1 invités, 2 outils
    var users by remember { mutableStateOf<List<AdminUser>>(emptyList()) }
    var invites by remember { mutableStateOf<List<InviteItem>>(emptyList()) }
    var refs by remember { mutableStateOf(ReferentialsResponse()) }
    var feedbackUnread by remember { mutableIntStateOf(0) }
    var rpgPlayers by remember { mutableIntStateOf(0) }
    var rpgProfiles by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    // create user
    var newUser by remember { mutableStateOf("") }
    var newPass by remember { mutableStateOf("") }
    var newAdmin by remember { mutableStateOf(false) }

    // invite
    var invLabel by remember { mutableStateOf("") }
    var invEmail by remember { mutableStateOf("") }
    var invValidity by remember { mutableStateOf("7d") }
    var createdUrl by remember { mutableStateOf<String?>(null) }
    var invBusy by remember { mutableStateOf(false) }

    // referentials
    var refTab by remember { mutableIntStateOf(0) }
    var refFilter by remember { mutableStateOf("") }
    var refNew by remember { mutableStateOf("") }

    LaunchedEffect(reload) {
        loading = true
        error = null
        try {
            users = withContext(Dispatchers.IO) { vm.api.adminUsers() }
            invites = withContext(Dispatchers.IO) {
                try {
                    vm.api.adminInvites()
                } catch (_: Exception) {
                    emptyList()
                }
            }
            refs = withContext(Dispatchers.IO) {
                try {
                    vm.api.adminReferentials()
                } catch (_: Exception) {
                    ReferentialsResponse()
                }
            }
            feedbackUnread = withContext(Dispatchers.IO) {
                vm.api.adminFeedbackStats()?.unread ?: 0
            }
            val rpg = withContext(Dispatchers.IO) {
                try { vm.api.adminRpgPlayers() } catch (_: Exception) { emptyList() }
            }
            rpgPlayers = rpg.size
            rpgProfiles = rpg.count { it.level > 1 || it.xp > 0 || it.hasProfile == true }
        } catch (e: Exception) {
            error = e.message ?: "Erreur chargement admin"
        }
        loading = false
    }

    fun toastOk(msg: String) = vm.showToast(msg, ToastPayload.Variant.SUCCESS)
    fun toastErr(msg: String) = vm.showToast(msg, ToastPayload.Variant.ERROR)

    Column(
        Modifier
            .fillMaxSize()
            .background(WineColors.bg)
            .consumeClicks()
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("⚙️ Administration", color = WineColors.text, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text(
                    "${users.size} comptes · ${invites.count { it.active != false && it.revokedAt == null }} invités · 💬 $feedbackUnread",
                    color = WineColors.muted,
                    fontSize = 12.sp
                )
            }
            Text("↻", color = WineColors.muted, modifier = Modifier.clickable { reload++ }.padding(8.dp))
            Text("Fermer ✕", color = WineColors.muted, modifier = Modifier.clickable { vm.closeSheet() }.padding(8.dp))
        }
        Spacer(Modifier.height(8.dp))

        // Tabs parité web
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Comptes", "Invités", "Outils").forEachIndexed { i, label ->
                val active = tab == i
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .border(
                            1.dp,
                            if (active) WineColors.accent else WineColors.border,
                            RoundedCornerShape(10.dp)
                        )
                        .background(if (active) WineColors.card else WineColors.card.copy(alpha = 0.55f))
                        .clickable { tab = i }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        color = if (active) WineColors.text else WineColors.muted,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        if (loading) {
            Text("Chargement…", color = WineColors.muted)
            return@Column
        }
        error?.let { Text(it, color = WineColors.error, fontSize = 13.sp) }
        message?.let { Text(it, color = WineColors.ok, fontSize = 13.sp) }

        // ── Dashboard (parité iOS) ──
        AdminDashboard(
            users = users,
            invites = invites,
            feedbackUnread = feedbackUnread,
            appVersion = vm.appVersion,
            serverVersion = vm.serverVersion,
            latestAndroid = vm.latestAndroidVersion,
            needsUpdate = vm.needsAppUpdate,
            rpgPlayers = rpgPlayers,
            rpgProfiles = rpgProfiles,
        )
        Spacer(Modifier.height(10.dp))

        val scroll = rememberScrollState()
        Column(Modifier.verticalScroll(scroll).weight(1f, fill = true)) {
            when (tab) {
                0 -> {
                    // ── Comptes ──
                    Text("Nouveau compte", color = WineColors.muted, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = newUser,
                        onValueChange = { newUser = it },
                        label = { Text("Identifiant") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = adminFieldColors()
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = newPass,
                        onValueChange = { newPass = it },
                        label = { Text("Mot de passe") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = adminFieldColors()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = newAdmin, onCheckedChange = { newAdmin = it })
                        Text("Administrateur", color = WineColors.text, fontSize = 13.sp)
                    }
                    WeenoPrimaryButton(
                        "Créer le compte",
                        enabled = newUser.isNotBlank() && newPass.length >= 6
                    ) {
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    vm.api.adminCreateUser(newUser.trim(), newPass, newAdmin)
                                }
                                newUser = ""; newPass = ""; newAdmin = false
                                message = "Compte créé"
                                reload++
                            } catch (e: Exception) {
                                toastErr(e.message ?: "Erreur")
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text("Comptes", color = WineColors.muted, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    users.forEach { u ->
                        AdminUserCard(
                            user = u,
                            isSelf = u.username == vm.user,
                            onSetPassword = { pass ->
                                scope.launch {
                                    try {
                                        withContext(Dispatchers.IO) {
                                            vm.api.adminSetPassword(u.username, pass)
                                        }
                                        toastOk("Mot de passe mis à jour")
                                    } catch (e: Exception) {
                                        toastErr(e.message ?: "Erreur")
                                    }
                                }
                            },
                            onToggleAdmin = {
                                scope.launch {
                                    try {
                                        withContext(Dispatchers.IO) {
                                            vm.api.adminSetAdmin(u.username, !u.isAdmin)
                                        }
                                        reload++
                                    } catch (e: Exception) {
                                        toastErr(e.message ?: "Erreur")
                                    }
                                }
                            },
                            onDelete = {
                                scope.launch {
                                    try {
                                        withContext(Dispatchers.IO) {
                                            vm.api.adminDeleteUser(u.username)
                                        }
                                        reload++
                                    } catch (e: Exception) {
                                        toastErr(e.message ?: "Erreur")
                                    }
                                }
                            }
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
                1 -> {
                    // ── Invités ──
                    Text("Invitations", color = WineColors.muted, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(
                        "Lien + email. Lien 24 h si non utilisé. 1 appareil.",
                        color = WineColors.muted,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = invLabel,
                        onValueChange = { invLabel = it },
                        label = { Text("Nom") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = adminFieldColors()
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = invEmail,
                        onValueChange = { invEmail = it },
                        label = { Text("Email") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = adminFieldColors()
                    )
                    Spacer(Modifier.height(6.dp))
                    // Validité simple
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        listOf(
                            "24h" to "24 h",
                            "48h" to "48 h",
                            "7d" to "7 j",
                            "14d" to "14 j",
                            "30d" to "30 j",
                            "90d" to "90 j",
                            "permanent" to "Permanent",
                        ).forEach { (v, lab) ->
                            val on = invValidity == v
                            Text(
                                lab,
                                color = if (on) Color.Black else WineColors.text,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (on) WineColors.accent else WineColors.card)
                                    .border(1.dp, if (on) WineColors.accent else WineColors.border, RoundedCornerShape(8.dp))
                                    .clickable { invValidity = v }
                                    .padding(horizontal = 10.dp, vertical = 7.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    WeenoPrimaryButton(
                        if (invBusy) "Génération…" else "Créer le lien",
                        enabled = invLabel.length >= 2 && invEmail.contains("@") && !invBusy
                    ) {
                        invBusy = true
                        scope.launch {
                            try {
                                val res = withContext(Dispatchers.IO) {
                                    vm.api.adminCreateInvite(invLabel.trim(), invEmail.trim(), invValidity)
                                }
                                createdUrl = res.url
                                invLabel = ""; invEmail = ""
                                toastOk("Lien créé — copie-le")
                                reload++
                            } catch (e: Exception) {
                                toastErr(e.message ?: "Erreur")
                            }
                            invBusy = false
                        }
                    }
                    createdUrl?.let { url ->
                        Spacer(Modifier.height(8.dp))
                        Text(url, color = WineColors.text, fontSize = 11.sp)
                        WeenoSecondaryButton("Copier le lien") {
                            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("invite", url))
                            createdUrl = null
                            toastOk("Lien copié")
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    invites.forEach { inv ->
                        InviteCard(
                            inv = inv,
                            onCopy = { url ->
                                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("invite", url))
                                toastOk("Lien copié")
                            },
                            onExtend = { v ->
                                scope.launch {
                                    try {
                                        withContext(Dispatchers.IO) { vm.api.adminExtendInvite(inv.id, v) }
                                        toastOk("Prolongé")
                                        reload++
                                    } catch (e: Exception) {
                                        toastErr(e.message ?: "Erreur")
                                    }
                                }
                            },
                            onReissue = {
                                scope.launch {
                                    try {
                                        val url = withContext(Dispatchers.IO) { vm.api.adminReissueInvite(inv.id) }
                                        createdUrl = url
                                        toastOk("Lien réactivation prêt")
                                        reload++
                                    } catch (e: Exception) {
                                        toastErr(e.message ?: "Erreur")
                                    }
                                }
                            },
                            onRevoke = {
                                scope.launch {
                                    try {
                                        withContext(Dispatchers.IO) { vm.api.adminRevokeInvite(inv.id) }
                                        toastOk("Révoquée")
                                        reload++
                                    } catch (e: Exception) {
                                        toastErr(e.message ?: "Erreur")
                                    }
                                }
                            }
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
                else -> {
                    // ── Outils (parité iOS) ──
                    Text("Outils", color = WineColors.muted, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    WeenoPrimaryButton("⚔ Admin Weeno") {
                        vm.openSheet(WeenoSheet.RPG_ADMIN)
                    }
                    Spacer(Modifier.height(8.dp))
                    WeenoSecondaryButton("🧹 Nettoyer photos orphelines") {
                        scope.launch {
                            try {
                                val msg = withContext(Dispatchers.IO) { vm.api.adminCleanupPhotos() }
                                message = msg
                                toastOk(msg)
                            } catch (e: Exception) {
                                toastErr(e.message ?: "Erreur")
                            }
                        }
                    }
                    message?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(it, color = WineColors.ok, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(14.dp))
                    // Bearer Vivino — scan direct téléphone
                    val ctx = LocalContext.current
                    var vivinoConfigured by remember { mutableStateOf(VivinoTokenStore.isConfigured(ctx)) }
                    var bearerDraft by remember { mutableStateOf("") }
                    var userIdDraft by remember { mutableStateOf(VivinoTokenStore.userId(ctx).orEmpty()) }
                    Text("Scan Vivino (depuis le téléphone)", color = WineColors.text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (vivinoConfigured) "● Bearer configuré — scan direct api.vivino.com"
                        else "● Bearer manquant — colle le token session app Vivino",
                        color = if (vivinoConfigured) WineColors.ok else WineColors.error,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Le scan part du téléphone. Le journal reste sur WeenoBis.",
                        color = WineColors.muted,
                        fontSize = 11.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = bearerDraft,
                        onValueChange = { bearerDraft = it },
                        label = { Text("Bearer Vivino") },
                        placeholder = { Text("colle le token…") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = adminFieldColors()
                    )
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = userIdDraft,
                        onValueChange = { userIdDraft = it },
                        label = { Text("User id (optionnel)") },
                        placeholder = { Text("ex. 47968799") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = adminFieldColors()
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WeenoPrimaryButton("Enregistrer") {
                            VivinoTokenStore.setBearer(ctx, bearerDraft)
                            VivinoTokenStore.setUserId(ctx, userIdDraft)
                            vivinoConfigured = VivinoTokenStore.isConfigured(ctx)
                            bearerDraft = ""
                            message = if (vivinoConfigured) "Bearer Vivino enregistré" else "Bearer effacé"
                            toastOk(message ?: "OK")
                        }
                        WeenoSecondaryButton("Effacer") {
                            VivinoTokenStore.setBearer(ctx, null)
                            VivinoTokenStore.setUserId(ctx, null)
                            vivinoConfigured = false
                            bearerDraft = ""
                            userIdDraft = ""
                            message = "Bearer supprimé"
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("Référentiels", color = WineColors.muted, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Cépages", "Arômes", "Régions").forEachIndexed { i, lab ->
                            val on = refTab == i
                            Text(
                                lab,
                                color = if (on) Color.Black else WineColors.text,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (on) WineColors.accent else WineColors.card)
                                    .border(1.dp, WineColors.border, RoundedCornerShape(8.dp))
                                    .clickable { refTab = i }
                                    .padding(horizontal = 10.dp, vertical = 7.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = refFilter,
                        onValueChange = { refFilter = it },
                        label = { Text("Filtrer") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = adminFieldColors()
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = refNew,
                        onValueChange = { refNew = it },
                        label = { Text("Nouveau nom") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = adminFieldColors()
                    )
                    WeenoSecondaryButton("Ajouter", enabled = refNew.trim().length >= 2) {
                        scope.launch {
                            try {
                                val n = refNew.trim()
                                withContext(Dispatchers.IO) {
                                    when (refTab) {
                                        1 -> vm.api.adminAddFlavor(n)
                                        2 -> vm.api.adminAddRegion(n)
                                        else -> { /* couleurs = presets */ }
                                    }
                                }
                                refNew = ""
                                reload++
                            } catch (e: Exception) {
                                toastErr(e.message ?: "Erreur")
                            }
                        }
                    }
                    val list = when (refTab) {
                        1 -> refs.flavors.orEmpty()
                        2 -> refs.regions.orEmpty()
                        else -> (refs.grapes ?: refs.colors).orEmpty()
                    }.filter {
                        refFilter.isBlank() || it.name.contains(refFilter, ignoreCase = true)
                    }
                    Spacer(Modifier.height(8.dp))
                    list.forEach { entry ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(entry.name, color = WineColors.text, fontSize = 13.sp)
                            if (entry.deletable != false && entry.preset != true) {
                                Text(
                                    "Suppr",
                                    color = WineColors.error,
                                    fontSize = 12.sp,
                                    modifier = Modifier.clickable {
                                        scope.launch {
                                            try {
                                                withContext(Dispatchers.IO) {
                                                    val id = entry.id ?: return@withContext
                                                    when (refTab) {
                                                        1 -> vm.api.adminDeleteFlavor(id)
                                                        2 -> vm.api.adminDeleteRegion(id)
                                                        else -> {}
                                                    }
                                                }
                                                reload++
                                            } catch (e: Exception) {
                                                toastErr(e.message ?: "Erreur")
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun adminFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = WineColors.text,
    unfocusedTextColor = WineColors.text,
    focusedBorderColor = WineColors.accent,
    unfocusedBorderColor = WineColors.border,
    focusedLabelColor = WineColors.muted,
    unfocusedLabelColor = WineColors.muted,
    cursorColor = WineColors.accent,
)

@Composable
private fun AdminUserCard(
    user: AdminUser,
    isSelf: Boolean,
    onSetPassword: (String) -> Unit,
    onToggleAdmin: () -> Unit,
    onDelete: () -> Unit,
) {
    var pass by remember { mutableStateOf("") }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, if (user.isAdmin) WineColors.accent.copy(alpha = 0.4f) else WineColors.border, RoundedCornerShape(12.dp))
            .background(WineColors.card)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(user.username, color = WineColors.text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            if (user.isAdmin) {
                Spacer(Modifier.width(6.dp))
                Text(
                    "admin",
                    color = Color.Black,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(WineColors.accent)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            if (isSelf) {
                Spacer(Modifier.width(6.dp))
                Text("toi", color = WineColors.muted, fontSize = 10.sp)
            }
        }
        Text(
            "🍷 ${user.checkins} · 📷 ${user.photos ?: 0}",
            color = WineColors.muted,
            fontSize = 11.sp
        )
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = pass,
            onValueChange = { pass = it },
            label = { Text("Nouveau mot de passe") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = adminFieldColors()
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "MDP",
                color = WineColors.text,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, WineColors.border, RoundedCornerShape(8.dp))
                    .clickable(enabled = pass.length >= 6) {
                        onSetPassword(pass)
                        pass = ""
                    }
                    .padding(horizontal = 10.dp, vertical = 7.dp)
            )
            if (!isSelf) {
                Text(
                    if (user.isAdmin) "Retirer admin" else "Promouvoir",
                    color = WineColors.text,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, WineColors.border, RoundedCornerShape(8.dp))
                        .clickable { onToggleAdmin() }
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                )
                Text(
                    "Suppr.",
                    color = WineColors.error,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, WineColors.error.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .clickable { onDelete() }
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                )
            }
        }
    }
}

@Composable
private fun AdminDashboard(
    users: List<AdminUser>,
    invites: List<InviteItem>,
    feedbackUnread: Int,
    appVersion: String,
    serverVersion: String,
    latestAndroid: String?,
    needsUpdate: Boolean,
    rpgPlayers: Int,
    rpgProfiles: Int,
) {
    val activeInvites = invites.count {
        it.active == true || (it.redeemedAt != null && it.revokedAt == null)
    }
    val totalCheckins = users.sumOf { it.checkins }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, WineColors.border, RoundedCornerShape(14.dp))
            .background(WineColors.card)
            .padding(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("📊 Tableau de bord", color = WineColors.muted, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text("Weeno", color = WineColors.accent, fontWeight = FontWeight.Black, fontSize = 11.sp)
        }
        Spacer(Modifier.height(8.dp))
        Text("Versions", color = WineColors.text, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            VersionPill(
                "Cette APK",
                appVersion,
                if (needsUpdate) WineColors.accent else Color(0xFF4ADE80)
            )
            VersionPill("Webapp", serverVersion.ifBlank { "—" }, Color(0xFF60A5FA))
            latestAndroid?.let { VersionPill("Dernière APK", it, WineColors.muted) }
        }
        if (needsUpdate) {
            Spacer(Modifier.height(6.dp))
            Text(
                "⬆️ APK ancienne — télécharge la dernière sur le portail",
                color = WineColors.accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DashTile("👥", "${users.size}", "Comptes", Modifier.weight(1f))
            DashTile("✉️", "$activeInvites", "Invités", Modifier.weight(1f))
            DashTile("🍷", "$totalCheckins", "Check-ins", Modifier.weight(1f))
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DashTile("⚔", "$rpgProfiles", "RPG profils", Modifier.weight(1f))
            DashTile("💬", "$feedbackUnread", "Feedback", Modifier.weight(1f))
            DashTile("🏅", "$rpgPlayers", "Joueurs RPG", Modifier.weight(1f))
        }
    }
}

@Composable
private fun RowScope.VersionPill(title: String, value: String, accent: Color) {
    Column(
        Modifier
            .weight(1f)
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
            .background(WineColors.bg.copy(alpha = 0.5f))
            .padding(10.dp)
    ) {
        Text(title, color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(value, color = WineColors.text, fontSize = 12.sp, fontWeight = FontWeight.Black, maxLines = 1)
    }
}

@Composable
private fun DashTile(ico: String, v: String, l: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, WineColors.border, RoundedCornerShape(12.dp))
            .background(WineColors.bg.copy(alpha = 0.4f))
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(ico, fontSize = 14.sp)
        Text(v, color = WineColors.text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Text(l, color = WineColors.muted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun InviteCard(
    inv: InviteItem,
    onCopy: (String) -> Unit,
    onExtend: (String) -> Unit,
    onReissue: () -> Unit,
    onRevoke: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, WineColors.border, RoundedCornerShape(12.dp))
            .background(WineColors.card)
            .padding(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(inv.label ?: "—", color = WineColors.text, fontWeight = FontWeight.Bold)
            Text(
                inv.statusText,
                color = WineColors.accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(WineColors.accent.copy(alpha = 0.2f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
        Text(
            "${inv.username ?: "—"} · ${inv.checkins ?: 0} dégustation(s)",
            color = WineColors.muted,
            fontSize = 12.sp
        )
        if (inv.redeemedAt != null && inv.linkActive != true) {
            Text(
                "Lien d'invitation consommé — plus utilisable (session = appareil lié)",
                color = WineColors.muted,
                fontSize = 11.sp
            )
        }
        // Détails (parité iOS inviteDetailLines)
        inv.emailHint?.takeIf { it.isNotBlank() }?.let {
            DetailRow("Email", it)
        }
        if (inv.redeemedAt == null) {
            Text("En attente du 1er clic", color = WineColors.muted, fontSize = 11.sp)
        } else {
            val whenAct = inv.lastUsedAt ?: inv.redeemedAt
            val ip = if (inv.lastUsedAt != null) inv.lastUsedIp else inv.redeemIp
            Text(
                "Dernière activité · ${formatDate(whenAct)}${if (!ip.isNullOrBlank()) " · IP $ip" else ""}",
                color = WineColors.muted,
                fontSize = 11.sp
            )
            inv.redeemClient?.takeIf { it.isKnown }?.let { rc ->
                DetailRow(
                    "Navigateur",
                    "${rc.browser ?: "—"} · ${rc.os ?: "—"} · ${rc.device ?: "—"}"
                )
            }
            inv.deviceShort?.takeIf { it.isNotBlank() }?.let {
                DetailRow("Appareil lié", it)
            }
            if (inv.permanent == true) {
                DetailRow("Validité compte", "permanente")
            } else if (!inv.expiresAt.isNullOrBlank() && inv.reactivationPending != true) {
                DetailRow("Validité compte", "jusqu'au ${formatDate(inv.expiresAt)}")
            }
            if (inv.reactivationPending == true && !inv.linkExpiresAt.isNullOrBlank()) {
                DetailRow("Lien réactivation", "expire ${formatDate(inv.linkExpiresAt)} (10 min)")
            }
        }
        inv.validityLabel?.takeIf { it.isNotBlank() && it != "—" }?.let {
            Text("Type : $it", color = WineColors.muted, fontSize = 11.sp)
        }
        inv.ipLog?.takeIf { it.isNotEmpty() }?.let { log ->
            Text(
                "IP : ${log.mapNotNull { it.ip }.take(5).joinToString()}",
                color = WineColors.muted,
                fontSize = 11.sp
            )
        }
        Spacer(Modifier.height(6.dp))
        // Actions horizontales scrollables (parité iOS)
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            if (!inv.url.isNullOrBlank() && inv.revokedAt == null && inv.linkActive != false) {
                SmallAction("Copier") { onCopy(inv.url!!) }
            }
            if (inv.canExtend == true) {
                SmallAction("+24h") { onExtend("24h") }
                SmallAction("+48h") { onExtend("48h") }
                SmallAction("+7j") { onExtend("7d") }
                SmallAction("+30j") { onExtend("30d") }
                SmallAction("Perm.") { onExtend("permanent") }
            }
            if (inv.canReissue == true || inv.reactivationPending == true) {
                SmallAction("Renvoyer l'accès") { onReissue() }
            }
            if (inv.revokedAt == null) {
                SmallAction("Révoquer", danger = true) { onRevoke() }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.padding(top = 2.dp)) {
        Text("$label ", color = WineColors.text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Text(value, color = WineColors.muted, fontSize = 11.sp)
    }
}

@Composable
private fun SmallAction(label: String, danger: Boolean = false, onClick: () -> Unit) {
    Text(
        label,
        color = if (danger) WineColors.error else WineColors.text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .border(
                1.dp,
                if (danger) WineColors.error.copy(alpha = 0.5f) else WineColors.border,
                RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    )
}

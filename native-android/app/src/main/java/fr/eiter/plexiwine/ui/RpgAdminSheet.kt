package fr.eiter.plexiwine.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import fr.eiter.plexiwine.AdminFeedbackItem
import fr.eiter.plexiwine.AdminFeedbackStats
import fr.eiter.plexiwine.AppViewModel
import fr.eiter.plexiwine.RpgAdminFlags
import fr.eiter.plexiwine.RpgAdminPlayer
import fr.eiter.plexiwine.RpgAdminPlayersResponse
import fr.eiter.plexiwine.RpgBadge
import fr.eiter.plexiwine.RpgCelebration
import fr.eiter.plexiwine.RpgClassInfo
import fr.eiter.plexiwine.RpgLoot
import fr.eiter.plexiwine.RpgProfile
import fr.eiter.plexiwine.RpgQuest
import fr.eiter.plexiwine.RpgState
import fr.eiter.plexiwine.ToastPayload
import fr.eiter.plexiwine.displayIcon
import fr.eiter.plexiwine.rarityLabelFr
import fr.eiter.plexiwine.ui.theme.WineColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


@Composable
fun RpgAdminSheet(vm: AppViewModel) {
    // 0 Joueurs · 1 Contrôle · 2 Feedback
    var tab by remember { mutableIntStateOf(1) }
    var players by remember { mutableStateOf<List<RpgAdminPlayer>>(emptyList()) }
    var rpgFlags by remember { mutableStateOf<RpgAdminFlags?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<RpgAdminPlayer?>(null) }
    var busy by remember { mutableStateOf(false) }
    var levelText by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    var reloadToken by remember { mutableIntStateOf(0) }
    var didPickInitialTab by remember { mutableStateOf(false) }

    // Feedback admin
    var fbItems by remember { mutableStateOf<List<AdminFeedbackItem>>(emptyList()) }
    var fbStats by remember { mutableStateOf<AdminFeedbackStats?>(null) }
    var fbUnreadOnly by remember { mutableStateOf(false) }
    var fbStatus by remember { mutableStateOf("") }
    var fbLoading by remember { mutableStateOf(false) }
    var resolveId by remember { mutableStateOf<Int?>(null) }
    var resolveStatus by remember { mutableStateOf("done") }
    var resolveReply by remember { mutableStateOf("") }
    var showResolve by remember { mutableStateOf(false) }

    fun reload() { reloadToken++ }

    fun patchFlag(key: String, value: Boolean) {
        scope.launch {
            busy = true
            val payload = mutableMapOf<String, Any?>(key to value)
            // Allumer Weeno = moteur + UI (évite ON invisible)
            if (key == "enabled" && value) payload["ui"] = true
            val next = withContext(Dispatchers.IO) {
                vm.api.adminRpgPatchSettings(payload)
            }
            if (next != null) {
                rpgFlags = next
                val msg = when {
                    key == "enabled" && value -> "Weeno allumé"
                    key == "enabled" -> "Weeno coupé"
                    key == "allow_invites" && value -> "Invités inclus"
                    key == "allow_invites" -> "Invités exclus"
                    else -> "Réglage enregistré"
                }
                vm.showToast(msg, ToastPayload.Variant.SUCCESS)
                reload()
            } else {
                vm.showToast("Échec réglages", ToastPayload.Variant.ERROR)
            }
            busy = false
        }
    }

    LaunchedEffect(reloadToken) {
        loading = true
        error = null
        val bundle = withContext(Dispatchers.IO) {
            try { vm.api.adminRpgPlayersBundle() } catch (_: Exception) { RpgAdminPlayersResponse() }
        }
        players = bundle.players
        rpgFlags = bundle.flags
        if (players.isEmpty() && bundle.flags == null) error = "Aucun joueur ou accès refusé."
        if (!didPickInitialTab) {
            didPickInitialTab = true
            tab = if (bundle.flags?.enabled == true) 0 else 1
        }
        loading = false
    }

    LaunchedEffect(tab, fbUnreadOnly, fbStatus, reloadToken) {
        if (tab != 2) return@LaunchedEffect
        fbLoading = true
        try {
            val res = withContext(Dispatchers.IO) {
                vm.api.adminFeedbackList(
                    limit = 80,
                    unreadOnly = fbUnreadOnly,
                    status = fbStatus.ifBlank { null }
                )
            }
            fbItems = res.items.orEmpty()
            fbStats = res.stats
        } catch (e: Exception) {
            error = e.message
        }
        fbLoading = false
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(WineColors.bg)
            .consumeClicks()
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("⚔ Admin Weeno", color = WineColors.text, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                val unread = fbStats?.unread ?: 0
                val f = rpgFlags
                val status = when {
                    f == null -> "${players.size} joueur(s)"
                    f.enabled -> "Weeno ON · ${players.size} joueur(s)"
                    else -> "Weeno OFF · ${players.size} joueur(s)"
                }
                Text(
                    if (unread > 0) "$status · $unread feedback" else status,
                    color = WineColors.muted,
                    fontSize = 12.sp
                )
            }
            Text("↻", color = QuestBlue, modifier = Modifier.clickable { reload() }.padding(8.dp))
            Text("Fermer ✕", color = WineColors.muted, modifier = Modifier.clickable { vm.closeSheet() }.padding(8.dp))
        }
        Spacer(Modifier.height(8.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Joueurs", "Contrôle", "Feedback").forEachIndexed { i, lab ->
                val active = tab == i
                val badge = if (i == 2 && (fbStats?.unread ?: 0) > 0) " ${(fbStats?.unread)}" else ""
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, if (active) Gold else WineColors.border, RoundedCornerShape(10.dp))
                        .background(if (active) WineColors.card else WineColors.card.copy(alpha = 0.55f))
                        .clickable { tab = i }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        lab + badge,
                        color = if (active) WineColors.text else WineColors.muted,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        when {
            tab == 0 && loading -> Text("Chargement…", color = WineColors.muted)
            tab == 0 && error != null && players.isEmpty() -> Text(error!!, color = WineColors.muted)
            tab == 0 -> {
                val scroll = rememberScrollState()
                Column(Modifier.verticalScroll(scroll).weight(1f, fill = true)) {
                    players.forEach { p ->
                        val name = p.username ?: "—"
                        val dayCap = p.dailySoftCap
                        val dayXp = p.dailyXpToday
                        val dayCk = p.dailyCheckinsToday
                        val borderC = if (p.dailySoftCapped) Gold.copy(alpha = 0.55f) else WineColors.border
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, borderC, RoundedCornerShape(12.dp))
                                .background(WineColors.card)
                                .clickable {
                                    selected = p
                                    levelText = p.level.toString()
                                }
                                .padding(12.dp)
                        ) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(Modifier.weight(1f)) {
                                    Text(name, color = WineColors.text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    p.title?.let {
                                        Text(it, color = WineColors.muted, fontSize = 11.sp)
                                    }
                                }
                                Text("Nv ${p.level}", color = Gold, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Text(
                                buildString {
                                    append("${p.xp} XP · ${p.checkins} check-ins · ${p.badgeCount} badges")
                                    if (p.isInvite) append(" · invité")
                                    if (p.beerMaster) append(" · Master")
                                    if (p.allowed) append(" · RPG OK") else append(" · RPG bloqué")
                                    when (p.allowedOverride) {
                                        true -> append(" (forcé ON)")
                                        false -> append(" (forcé OFF)")
                                        null -> {}
                                    }
                                },
                                color = WineColors.muted,
                                fontSize = 12.sp
                            )
                            if (dayCap > 0) {
                                Text(
                                    buildString {
                                        if (p.dailySoftCapped) append("⛔ ") else append("⚡ ")
                                        append("$dayXp/$dayCap XP jour · $dayCk check-in")
                                        if (dayCk != 1) append("s")
                                        append(" RPG")
                                        if (p.dailySoftCapped) append(" · plafond")
                                    },
                                    color = if (p.dailySoftCapped) Gold else QuestBlue,
                                    fontSize = 11.sp,
                                    fontWeight = if (p.dailySoftCapped) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                            // ON/OFF/Auto : dans le détail joueur (tap carte)
                        }
                    }
                }
            }
            tab == 1 -> {
                // Kill-switches clairs
                val f = rpgFlags
                val gameOn = f?.enabled == true
                val invOn = f?.allowInvites == true
                val scroll = rememberScrollState()
                Column(Modifier.verticalScroll(scroll).weight(1f, fill = true)) {
                    Text("Interrupteurs serveur", color = WineColors.text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(
                        "Sans rebuild · admin · Wi‑Fi / VPN maison",
                        color = WineColors.muted,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    // Weeno global
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                1.dp,
                                if (gameOn) Color(0xFF81C784).copy(alpha = 0.5f) else Color(0xFFE57373).copy(alpha = 0.5f),
                                RoundedCornerShape(12.dp)
                            )
                            .background(WineColors.card)
                            .padding(12.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Weeno (tout le monde)",
                                    color = WineColors.text,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    if (gameOn)
                                        "Le jeu est actif : XP, quêtes, grimoire pour les joueurs autorisés."
                                    else
                                        "Le jeu est coupé : plus d’XP ni de grimoire. Le carnet reste.",
                                    color = WineColors.muted,
                                    fontSize = 12.sp
                                )
                            }
                            Switch(
                                checked = gameOn,
                                onCheckedChange = { if (!busy) patchFlag("enabled", it) },
                                enabled = !busy,
                                colors = SwitchDefaults.colors(
                                    checkedTrackColor = Color(0xFF81C784).copy(alpha = 0.7f)
                                )
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, WineColors.border, RoundedCornerShape(12.dp))
                            .background(WineColors.card)
                            .padding(12.dp)
                            .alpha(if (gameOn) 1f else 0.55f)
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Inclure les invités",
                                    color = WineColors.text,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    if (invOn)
                                        "Les comptes invite_* peuvent aussi jouer."
                                    else
                                        "Les invités n’ont que le carnet (pas de jeu).",
                                    color = WineColors.muted,
                                    fontSize = 12.sp
                                )
                            }
                            Switch(
                                checked = invOn,
                                onCheckedChange = { if (!busy) patchFlag("allow_invites", it) },
                                enabled = !busy && gameOn,
                                colors = SwitchDefaults.colors(checkedTrackColor = Gold.copy(alpha = 0.7f))
                            )
                        }
                    }
                    if (!gameOn) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Weeno est OFF — cet onglet sert à le rallumer. Le menu ⚔ reste toujours visible pour l’admin.",
                            color = Gold,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Par joueur : onglet Joueurs → fiche → ON / OFF / Auto.",
                        color = WineColors.muted,
                        fontSize = 11.sp
                    )
                }
            }
            tab == 2 -> {
                // Feedback toolbar
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = fbUnreadOnly, onCheckedChange = { fbUnreadOnly = it })
                    Text("Non lus seulement", color = WineColors.text, fontSize = 13.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("" to "Tous", "open" to "En cours", "done" to "Faits", "rejected" to "Refusés").forEach { (v, lab) ->
                        val on = fbStatus == v
                        Text(
                            lab,
                            color = if (on) Color.Black else WineColors.text,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (on) WineColors.accent else WineColors.card)
                                .border(1.dp, WineColors.border, RoundedCornerShape(8.dp))
                                .clickable { fbStatus = v }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }
                }
                val s = fbStats
                Text(
                    "${s?.unread ?: 0} non lu(s) · ${s?.open ?: 0} en cours · ${s?.done ?: 0} faits · ${s?.rejected ?: 0} refusés",
                    color = WineColors.muted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
                if (fbLoading) {
                    Text("Chargement feedback…", color = WineColors.muted)
                } else if (fbItems.isEmpty()) {
                    Text("Aucun feedback.", color = WineColors.muted)
                } else {
                    val scroll = rememberScrollState()
                    Column(Modifier.verticalScroll(scroll).weight(1f, fill = true)) {
                        fbItems.forEach { f ->
                            FeedbackAdminCard(
                                f = f,
                                busy = busy,
                                onToggleRead = {
                                    scope.launch {
                                        busy = true
                                        try {
                                            withContext(Dispatchers.IO) {
                                                vm.api.adminFeedbackMarkRead(f.id!!, f.adminRead != true)
                                            }
                                            reload()
                                        } catch (e: Exception) {
                                            vm.showToast(e.message ?: "Erreur", ToastPayload.Variant.ERROR)
                                        }
                                        busy = false
                                    }
                                },
                                onDone = {
                                    resolveId = f.id
                                    resolveStatus = "done"
                                    resolveReply = ""
                                    showResolve = true
                                },
                                onReject = {
                                    resolveId = f.id
                                    resolveStatus = "rejected"
                                    resolveReply = ""
                                    showResolve = true
                                },
                                onReopen = {
                                    scope.launch {
                                        busy = true
                                        try {
                                            withContext(Dispatchers.IO) { vm.api.adminFeedbackReopen(f.id!!) }
                                            reload()
                                            vm.showToast("Rouvert", ToastPayload.Variant.SUCCESS)
                                        } catch (e: Exception) {
                                            vm.showToast(e.message ?: "Erreur", ToastPayload.Variant.ERROR)
                                        }
                                        busy = false
                                    }
                                },
                                onDelete = {
                                    scope.launch {
                                        busy = true
                                        try {
                                            withContext(Dispatchers.IO) { vm.api.adminFeedbackDelete(f.id!!) }
                                            reload()
                                            vm.showToast("Supprimé", ToastPayload.Variant.SUCCESS)
                                        } catch (e: Exception) {
                                            vm.showToast(e.message ?: "Erreur", ToastPayload.Variant.ERROR)
                                        }
                                        busy = false
                                    }
                                }
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }

    // Resolve dialog
    if (showResolve && resolveId != null) {
        AlertDialog(
            onDismissRequest = { showResolve = false },
            title = {
                Text(
                    if (resolveStatus == "rejected") "Refuser" else "Mis en place",
                    color = WineColors.text,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        if (resolveStatus == "rejected") "Raison obligatoire (visible par le joueur)"
                        else "Message optionnel pour le joueur",
                        color = WineColors.muted,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = resolveReply,
                        onValueChange = { resolveReply = it },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = WineColors.text,
                            unfocusedTextColor = WineColors.text,
                            focusedBorderColor = WineColors.accent,
                            unfocusedBorderColor = WineColors.border
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val id = resolveId ?: return@TextButton
                        if (resolveStatus == "rejected" && resolveReply.trim().length < 3) {
                            vm.showToast("Raison trop courte", ToastPayload.Variant.ERROR)
                            return@TextButton
                        }
                        busy = true
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    vm.api.adminFeedbackResolve(id, resolveStatus, resolveReply.trim())
                                }
                                showResolve = false
                                reload()
                                vm.showToast(
                                    if (resolveStatus == "rejected") "Refusé — joueur notifié"
                                    else "Fait — joueur notifié",
                                    ToastPayload.Variant.SUCCESS
                                )
                            } catch (e: Exception) {
                                vm.showToast(e.message ?: "Erreur", ToastPayload.Variant.ERROR)
                            }
                            busy = false
                        }
                    }
                ) { Text("Envoyer", color = WineColors.accent) }
            },
            dismissButton = {
                TextButton(onClick = { showResolve = false }) {
                    Text("Annuler", color = WineColors.muted)
                }
            },
            containerColor = WineColors.card
        )
    }

    selected?.let { p ->
        val name = p.username.orEmpty()
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(name, color = WineColors.text, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Nv ${p.level} · ${p.xp} XP · ${p.badgeCount} badges", color = WineColors.muted, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Accès RPG", color = WineColors.text, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf<Pair<String, Boolean?>>(
                            "ON" to true,
                            "OFF" to false,
                            "Auto" to null,
                        ).forEach { (lab, value) ->
                            val active = when (value) {
                                true -> p.allowedOverride == true
                                false -> p.allowedOverride == false
                                null -> p.allowedOverride == null
                            }
                            Text(
                                lab,
                                color = if (active) Color.Black else WineColors.text,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        when {
                                            active && value == true -> Color(0xFF81C784)
                                            active && value == false -> Color(0xFFE57373)
                                            active -> Gold
                                            else -> WineColors.card
                                        }
                                    )
                                    .border(1.dp, WineColors.border, RoundedCornerShape(8.dp))
                                    .clickable(enabled = !busy) {
                                        scope.launch {
                                            busy = true
                                            val ok = withContext(Dispatchers.IO) {
                                                vm.api.adminRpgSetUserAllowed(name, value)
                                            }
                                            if (ok) {
                                                vm.showToast("$name · RPG $lab", ToastPayload.Variant.SUCCESS)
                                                selected = null
                                                reload()
                                            } else {
                                                vm.showToast("Échec accès", ToastPayload.Variant.ERROR)
                                            }
                                            busy = false
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("Niveau (parité iOS)", color = WineColors.text, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    OutlinedTextField(
                        value = levelText,
                        onValueChange = { levelText = it.filter { c -> c.isDigit() }.take(3) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = WineColors.text,
                            unfocusedTextColor = WineColors.text,
                            focusedBorderColor = WineColors.accent,
                            unfocusedBorderColor = WineColors.border
                        )
                    )
                    TextButton(
                        onClick = {
                            val lv = levelText.toIntOrNull()
                            if (lv == null || lv < 1) {
                                vm.showToast("Niveau invalide", ToastPayload.Variant.ERROR)
                                return@TextButton
                            }
                            busy = true
                            scope.launch {
                                val ok = withContext(Dispatchers.IO) {
                                    vm.api.adminRpgPatchPlayer(name, mapOf("level" to lv))
                                }
                                busy = false
                                if (ok) {
                                    vm.showToast("Niveau $lv pour $name", ToastPayload.Variant.SUCCESS, label = "Weeno")
                                    selected = null
                                    reload()
                                } else {
                                    vm.showToast("Échec niveau", ToastPayload.Variant.ERROR)
                                }
                            }
                        },
                        enabled = !busy
                    ) { Text("Appliquer niveau", color = WineColors.accent) }
                    Spacer(Modifier.height(8.dp))
                    Text("Ajuster l’XP", color = WineColors.text, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(-50, -10, 10, 50).forEach { d ->
                            OutlinedButton(
                                onClick = {
                                    busy = true
                                    scope.launch {
                                        val ok = withContext(Dispatchers.IO) {
                                            try { vm.api.adminRpgAdjustXp(name, d) } catch (_: Exception) { false }
                                        }
                                        busy = false
                                        if (ok) {
                                            vm.showToast("XP ${if (d > 0) "+" else ""}$d pour $name", ToastPayload.Variant.SUCCESS, label = "Weeno")
                                            selected = null
                                            reload()
                                        } else {
                                            vm.showToast("Échec XP", ToastPayload.Variant.ERROR)
                                        }
                                    }
                                },
                                enabled = !busy && name.isNotBlank()
                            ) {
                                Text(if (d > 0) "+$d" else "$d", color = WineColors.text, fontSize = 12.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            busy = true
                            scope.launch {
                                val ok = withContext(Dispatchers.IO) {
                                    try { vm.api.adminRpgResetDaily(name) } catch (_: Exception) { false }
                                }
                                busy = false
                                if (ok) {
                                    vm.showToast("Reset journalier $name", ToastPayload.Variant.SUCCESS, label = "Weeno")
                                    selected = null
                                    reload()
                                } else {
                                    vm.showToast("Échec reset", ToastPayload.Variant.ERROR)
                                }
                            }
                        },
                        enabled = !busy && name.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = WineColors.accent)
                    ) {
                        Text("Reset XP du jour", color = Color.Black, fontWeight = FontWeight.Bold)
                    }

                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            busy = true
                            scope.launch {
                                val ok = withContext(Dispatchers.IO) {
                                    try {
                                        vm.api.adminRpgPatchPlayer(name, mapOf("tutorial_seen" to false))
                                    } catch (_: Exception) {
                                        false
                                    }
                                }
                                busy = false
                                if (ok) {
                                    vm.showToast(
                                        "$name reverra le tutoriel à sa prochaine connexion.",
                                        ToastPayload.Variant.SUCCESS,
                                        label = "Weeno Quest"
                                    )
                                    selected = null
                                    reload()
                                } else {
                                    vm.showToast("Échec tuto", ToastPayload.Variant.ERROR)
                                }
                            }
                        },
                        enabled = !busy && name.isNotBlank() && p.tutorialSeen != false,
                        colors = ButtonDefaults.buttonColors(containerColor = WineColors.accent)
                    ) {
                        Text(
                            if (p.tutorialSeen == false) "🎓 Reverra le tuto" else "🎓 Forcer à revoir le tuto",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selected = null }) { Text("Fermer", color = WineColors.muted) }
            },
            containerColor = WineColors.card
        )
    }
}

@Composable
fun FeedbackAdminCard(
    f: AdminFeedbackItem,
    busy: Boolean,
    onToggleRead: () -> Unit,
    onDone: () -> Unit,
    onReject: () -> Unit,
    onReopen: () -> Unit,
    onDelete: () -> Unit,
) {
    val unread = f.adminRead != true
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(
                1.5.dp,
                when {
                    f.isDone -> Color(0xFF4ADE80).copy(alpha = 0.45f)
                    f.isRejected -> WineColors.error.copy(alpha = 0.45f)
                    unread -> WineColors.accent.copy(alpha = 0.45f)
                    else -> WineColors.border
                },
                RoundedCornerShape(12.dp)
            )
            .background(WineColors.card)
            .padding(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(f.username ?: "—", color = WineColors.text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(f.displayStatus, color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Text(f.categoryLabel ?: f.category ?: "", color = WineColors.accent, fontSize = 11.sp)
        Spacer(Modifier.height(4.dp))
        Text(f.message.orEmpty(), color = WineColors.text, fontSize = 13.sp)
        f.adminReply?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(4.dp))
            Text("Réponse : $it", color = WineColors.muted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = f.adminRead == true,
                onCheckedChange = { onToggleRead() },
                enabled = !busy
            )
            Text("Lu", color = WineColors.text, fontSize = 12.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (f.isOpen) {
                Text(
                    "✓ Fait",
                    color = Color.Black,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(WineColors.accent)
                        .clickable(enabled = !busy, onClick = onDone)
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                )
                Text(
                    "✕ Refuser",
                    color = WineColors.text,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, WineColors.border, RoundedCornerShape(8.dp))
                        .clickable(enabled = !busy, onClick = onReject)
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                )
            } else {
                Text(
                    "Rouvrir",
                    color = WineColors.text,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, WineColors.border, RoundedCornerShape(8.dp))
                        .clickable(enabled = !busy, onClick = onReopen)
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                )
            }
            Text(
                "Suppr",
                color = WineColors.error,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, WineColors.error.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                    .clickable(enabled = !busy, onClick = onDelete)
                    .padding(horizontal = 10.dp, vertical = 7.dp)
            )
        }
    }
}

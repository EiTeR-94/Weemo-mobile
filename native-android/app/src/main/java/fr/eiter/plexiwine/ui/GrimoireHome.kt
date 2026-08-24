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
fun ColumnScope.GrimoireHome(state: RpgState, onBadge: (RpgBadge) -> Unit) {
    val p = state.profile ?: return
    val master = p.beerMaster
    val nActive = state.quests?.active?.size ?: 0
    val scroll = rememberScrollState()
    Column(Modifier.verticalScroll(scroll)) {
        // Master card en premier (parité iOS)
        if (master) {
            MasterCard(p)
            Spacer(Modifier.height(10.dp))
        }

        // Fiche d’aventurier unique : avatar + XP + stats (parité iOS homeTab)
        FicheAventurierCard(p = p, state = state, nActive = nActive)

        Spacer(Modifier.height(12.dp))
        SectionCard(
            title = "Quêtes en cours",
            ico = "📜",
            count = nActive.takeIf { it > 0 }
        ) {
            val active = state.quests?.active.orEmpty().take(3)
            if (active.isEmpty()) {
                Text(
                    "Aucune quête active — le tavernier en prépare pour demain.",
                    color = WineColors.muted,
                    fontSize = 12.sp
                )
            } else {
                active.forEach { QuestCard(it) }
            }
        }
        val next = state.nextBadges
        if (next.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            SectionCard(title = "Prochains badges", ico = "🏅", count = next.size) {
                next.forEach {
                    Box(Modifier.clickable { onBadge(it) }) {
                        BadgeProgressRow(it)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        SectionCard(title = "Le tavernier", ico = "🗣️", count = null) {
            Text(
                state.phrase?.takeIf { it.isNotBlank() } ?: "…",
                color = WineColors.muted,
                fontSize = 14.sp,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
            )
        }
        Spacer(Modifier.height(28.dp))
    }
}

/** Encadrement section grimoire (parité iOS sectionCard). */
@Composable
fun SectionCard(
    title: String,
    ico: String,
    count: Int?,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, WineColors.border, RoundedCornerShape(14.dp))
            .background(WineColors.card)
            .padding(12.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "$ico $title",
                color = WineColors.text,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f)
            )
            count?.let { n ->
                Text(
                    "$n",
                    color = WineColors.muted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(WineColors.fieldBg)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        content()
    }
}

/** Fiche d’aventurier — une seule carte (avatar, XP, stats) comme iOS. */
@Composable
fun FicheAventurierCard(p: RpgProfile, state: RpgState, nActive: Int) {
    val master = p.beerMaster
    val className = p.classInfo?.name ?: p.classKey ?: "Aventurier"
    val classIcon = p.classInfo?.icon ?: "🍷"
    // Même cadre coloré par palier que la barre HUD (au lieu du rosé générique
    // du thème, trop peu contrasté sur fond bordeaux).
    val frame = levelFrameFor(p)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(
                1.dp,
                if (master) Gold.copy(alpha = 0.4f) else frame.border,
                RoundedCornerShape(14.dp)
            )
            .background(
                if (master) {
                    Brush.linearGradient(listOf(Color(0xFF47300D), WineColors.card))
                } else {
                    Brush.linearGradient(listOf(WineColors.card, WineColors.card.copy(alpha = 0.98f)))
                }
            )
            .padding(14.dp)
    ) {
        Text(
            "Fiche d’aventurier",
            color = WineColors.muted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.6.sp
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.Top) {
            // Avatar + pastille niveau en bas (parité iOS offset)
            Box(
                Modifier.size(64.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(WineColors.fieldBg)
                        .border(2.5.dp, if (master) Gold else frame.accent, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(p.displayIcon(), fontSize = 28.sp)
                }
                Text(
                    "${p.level}",
                    color = WineColors.text,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = 4.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(WineColors.card.copy(alpha = 0.95f))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    p.title ?: "Aventurier",
                    color = WineColors.text,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
                Spacer(Modifier.height(4.dp))
                if (master) {
                    Text(
                        "Profil unique · Weeno Master",
                        color = Gold,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    Text(
                        "Classe · $classIcon $className",
                        color = WineColors.muted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.height(4.dp))
                if (master) {
                    Text(
                        "Prestige",
                        color = Gold,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(Gold.copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                } else {
                    p.titleBand?.name?.takeIf { it.isNotBlank() }?.let { band ->
                        Text(
                            band,
                            color = WineColors.accent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(WineColors.accent.copy(alpha = 0.12f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        XpHeroBar(p)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTileSoft("🔥", "${p.streakDays}", "Streak", Modifier.weight(1f))
            StatTileSoft(
                if (p.dailySoftCapped) "⛔" else "⚡",
                "${p.dailyXp}/${p.dailySoftCap}",
                if (p.dailySoftCapped) "Soft cap" else "XP du jour",
                Modifier.weight(1f)
            )
            StatTileSoft(
                "🍷",
                "${state.atlas?.totalCheckins ?: 0}",
                "Check-ins",
                Modifier.weight(1f)
            )
            if (master) {
                StatTileSoft("👑", "Unique", "Prestige", Modifier.weight(1f))
            } else {
                StatTileSoft("📜", "$nActive", "Quêtes", Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun MasterCard(p: RpgProfile) {
    // Parité iOS masterCard
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, Gold.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .background(Color(0xFF38240A).copy(alpha = 0.95f))
            .padding(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text("👑", fontSize = 22.sp)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                p.prestige?.ribbon ?: "BEER MASTER",
                color = Gold,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                p.title ?: "Weeno Master",
                color = WineColors.text,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Text(
                p.prestige?.tagline ?: "Couronne de la taverne",
                color = WineColors.muted,
                fontSize = 12.sp
            )
            p.prestige?.blurb?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, color = WineColors.muted, fontSize = 12.sp)
            }
        }
    }
}

/** Barre XP dans la fiche (parité iOS xpHeroBar). */
@Composable
fun XpHeroBar(p: RpgProfile) {
    val into = p.xpIntoLevel
    val span = if (p.xpLevelStart != null && p.xpLevelNext != null) {
        (p.xpLevelNext - p.xpLevelStart).coerceAtLeast(1)
    } else null
    val mid = if (into != null && span != null) "$into / $span XP" else "${p.xp} XP"
    val pct = (p.progressPct.coerceIn(0.0, 100.0) / 100.0).toFloat()
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Nv ${p.level}",
                color = WineColors.text,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(WineColors.fieldBg)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            )
            Spacer(Modifier.weight(1f))
            Text(mid, color = WineColors.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text(
                p.xpToNext?.let { "encore $it" } ?: "max",
                color = WineColors.accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(6.dp))
        // Barre dégradé jaune→orange (parité iOS)
        Box(
            Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(WineColors.fieldBg)
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(pct.coerceIn(0.02f, 1f))
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFFFACC15), Color(0xFFF97316))
                        )
                    )
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "${p.progressPct.toInt()}% vers le prochain niveau",
            color = WineColors.muted,
            fontSize = 11.sp,
            modifier = Modifier.align(Alignment.End)
        )
    }
}

/** Stat tile style iOS (fond fieldBg soft). */
@Composable
fun StatTileSoft(ico: String, value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, WineColors.border.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
            .background(WineColors.fieldBg.copy(alpha = 0.65f))
            .padding(vertical = 8.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(ico, fontSize = 14.sp)
        Text(
            value,
            color = WineColors.text,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(label, color = WineColors.muted, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

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


val Gold = Color(0xFFF5C542)

val QuestBlue = Color(0xFF60A5FA)

val BadgePurple = Color(0xFFC084FC)

val RareBlue = Color(0xFF60A5FA)

val LegendAmber = Color(0xFFF59E0B)

val Copper = Color(0xFFD97706)

val Silver = Color(0xFF94A3B8)

val MythViolet = Color(0xFFA78BFA)

val ExploreGreen = Color(0xFF34D399)

/** Cadre RPG de l’accueil — aligné sur les TITLE_BANDS serveur. */
data class LevelFrame(
    val bandName: String,
    val border: Color,
    val borderWidth: Dp,
    val outerBorder: Color? = null,
    val background: Color,
    val accent: Color,
    val sealRing: Color,
)

fun levelFrameFor(profile: RpgProfile): LevelFrame {
    if (profile.beerMaster) {
        return LevelFrame(
            bandName = profile.prestige?.ribbon ?: "Weeno Master",
            border = Gold.copy(alpha = 0.75f),
            borderWidth = 2.dp,
            outerBorder = Color(0xFFFBBF24).copy(alpha = 0.35f),
            background = Color(0xFF78350F).copy(alpha = 0.42f),
            accent = Gold,
            sealRing = Gold,
        )
    }
    val lvl = profile.level.coerceAtLeast(1)
    val band = profile.titleBand?.name
    return when {
        lvl <= 4 -> LevelFrame(
            // Palier dédié (comme tous les autres) au lieu des couleurs génériques
            // du thème — WineColors.accent/border sont trop proches du fond bordeaux
            // pour ressortir, contrairement à l'ambre de Beer sur son fond bleu-gris.
            bandName = band ?: "Premiers pas",
            border = Silver.copy(alpha = 0.55f),
            borderWidth = 1.5.dp,
            background = Color(0xFF141821),
            accent = Silver,
            sealRing = Silver,
        )
        lvl <= 8 -> LevelFrame(
            bandName = band ?: "Apprentissage",
            border = Copper.copy(alpha = 0.55f),
            borderWidth = 1.5.dp,
            background = Color(0xFF1C1410),
            accent = Copper,
            sealRing = Copper,
        )
        lvl <= 12 -> LevelFrame(
            bandName = band ?: "Exploration",
            border = ExploreGreen.copy(alpha = 0.5f),
            borderWidth = 1.5.dp,
            background = Color(0xFF0F1A16),
            accent = ExploreGreen,
            sealRing = ExploreGreen,
        )
        lvl <= 16 -> LevelFrame(
            bandName = band ?: "Affirmation",
            border = QuestBlue.copy(alpha = 0.55f),
            borderWidth = 1.5.dp,
            background = Color(0xFF0F1620),
            accent = QuestBlue,
            sealRing = QuestBlue,
        )
        lvl <= 20 -> LevelFrame(
            bandName = band ?: "Expertise",
            border = BadgePurple.copy(alpha = 0.55f),
            borderWidth = 1.5.dp,
            background = Color(0xFF16101F),
            accent = BadgePurple,
            sealRing = BadgePurple,
        )
        lvl <= 24 -> LevelFrame(
            bandName = band ?: "Renommée",
            border = Gold.copy(alpha = 0.5f),
            borderWidth = 1.5.dp,
            outerBorder = Gold.copy(alpha = 0.2f),
            background = Color(0xFF1A160E),
            accent = Gold,
            sealRing = Gold,
        )
        lvl <= 28 -> LevelFrame(
            bandName = band ?: "Légende",
            border = Gold.copy(alpha = 0.7f),
            borderWidth = 2.dp,
            outerBorder = LegendAmber.copy(alpha = 0.3f),
            background = Color(0xFF1F180A),
            accent = LegendAmber,
            sealRing = LegendAmber,
        )
        else -> LevelFrame(
            bandName = band ?: "Mythe",
            border = MythViolet.copy(alpha = 0.7f),
            borderWidth = 2.dp,
            outerBorder = Gold.copy(alpha = 0.35f),
            background = Color(0xFF18101F),
            accent = MythViolet,
            sealRing = Gold,
        )
    }
}

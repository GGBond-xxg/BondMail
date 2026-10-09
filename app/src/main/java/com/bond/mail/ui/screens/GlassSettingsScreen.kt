package com.bond.mail.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bond.mail.data.settings.GlassSettings
import com.bond.mail.ui.i18n.tr
import com.bond.mail.ui.theme.*
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import java.util.Locale

@Composable
internal fun GlassSettingsScreen(settings: GlassSettings, onChange: (GlassSettings) -> Unit, onBack: () -> Unit) {
    var draft by remember { mutableStateOf(settings.normalized()) }
    val latestDraft by rememberUpdatedState(draft)
    val latestSave by rememberUpdatedState(onChange)
    // Also persist when back is pressed while a slider still has focus.
    DisposableEffect(Unit) { onDispose { latestSave(latestDraft) } }
    Dialog(onDismissRequest = onBack, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        CompositionLocalProvider(LocalGlassSettings provides draft) {
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).navigationBarsPadding()) {
                BondTopAppBar(tr("glass_adjustments"), navigationIcon = {
                    BondIconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("back")) }
                })
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    GlassOpticsPreview()
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
                        .background(MaterialTheme.colorScheme.surface).padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassParameterSlider(tr("glass_blur"), draft.blurRadius, 0f..24f,
                            { draft = draft.copy(blurRadius = it) }, { onChange(draft) })
                        GlassParameterSlider(tr("glass_height"), draft.refractionHeight, 0f..32f,
                            { draft = draft.copy(refractionHeight = it) }, { onChange(draft) })
                        GlassParameterSlider(tr("glass_amount"), draft.refractionAmount, 0f..64f,
                            { draft = draft.copy(refractionAmount = it) }, { onChange(draft) })
                        GlassParameterSlider(tr("glass_chromatic"), draft.chromaticAberration, 0f..1f,
                            { draft = draft.copy(chromaticAberration = it) }, { onChange(draft) }, percent = true)
                    }
                    if (!LocalGlassEffects.current) Text(tr("glass_effects_reduced"), style = MaterialTheme.typography.bodySmall)
                    BondSecondaryButton(onClick = { draft = GlassSettings(); onChange(draft) }, Modifier.fillMaxWidth()) {
                        Text(tr("glass_reset"))
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassOpticsPreview() {
    val backdrop = rememberLayerBackdrop()
    Box(Modifier.fillMaxWidth().height(174.dp).clip(RoundedCornerShape(28.dp)), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop)
            .background(Brush.linearGradient(listOf(Color(0xFF58D8C8), Color(0xFF288CFF), Color(0xFFAF80E9))))) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceEvenly) {
                repeat(7) { Box(Modifier.width(16.dp).fillMaxHeight().background(Color.White.copy(alpha = 0.28f))) }
            }
        }
        Box(Modifier.size(132.dp).glassSurface(backdrop, RoundedCornerShape(36.dp)), contentAlignment = Alignment.Center) {
            Icon(GlassIcons.Mail, tr("glass_preview"), Modifier.size(44.dp), tint = Color.White)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GlassParameterSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit, onFinished: () -> Unit, percent: Boolean = false) {
    val color = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.outlineVariant
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            Text(if (percent) "${(value * 100).toInt()}%" else String.format(Locale.ROOT, "%.1f dp", value),
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
        }
        Slider(value, onValueChange, Modifier.fillMaxWidth().semantics { contentDescription = label },
            valueRange = range, onValueChangeFinished = onFinished,
            thumb = { Box(Modifier.size(40.dp, 28.dp).glassSurface()) },
            track = { state ->
                Box(Modifier.fillMaxWidth().height(6.dp).drawBehind {
                    val fraction = ((state.value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
                    drawRoundRect(trackColor, cornerRadius = CornerRadius(size.height / 2))
                    drawRoundRect(color, size = Size(size.width * fraction, size.height), cornerRadius = CornerRadius(size.height / 2))
                })
            })
    }
}

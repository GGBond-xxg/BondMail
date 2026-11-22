package com.bond.mail.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bond.mail.ui.i18n.tr
import com.bond.mail.ui.motion.bondMotionEnabled

/** One indicator at the mailbox container's top edge, for both drag and refresh. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MailRefreshIndicator(refreshing: Boolean, pullProgress: Float) {
    val description = tr("refreshing")
    val motionEnabled = bondMotionEnabled()
    var waveStarted by remember(refreshing, motionEnabled) { mutableStateOf(false) }
    LaunchedEffect(refreshing, motionEnabled) {
        if (refreshing && motionEnabled) {
            // Paint the straight indeterminate node before changing its target once. M3 alpha17
            // animates amplitude internally (500ms) but ignores retargets while that job runs;
            // streaming animateFloatAsState values leaves it stuck on the first tiny amplitude.
            withFrameNanos { }
            withFrameNanos { }
            waveStarted = true
        }
    }
    val indicator = Modifier.fillMaxWidth().height(10.dp).semantics {
        if (refreshing) contentDescription = description
    }
    if (refreshing && motionEnabled) {
        LinearWavyProgressIndicator(modifier = indicator, amplitude = if (waveStarted) 1f else 0f)
    } else {
        LinearWavyProgressIndicator(
            progress = { if (refreshing) 0.75f else pullProgress },
            modifier = indicator,
            // Pulling is a stationary straight line. Only a committed refresh starts waves.
            amplitude = { 0f },
            waveSpeed = 0.dp,
        )
    }
}

/** Centered in the document loading area, with no separate container/background. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MailDocumentProgressIndicator(modifier: Modifier = Modifier) {
    val description = tr("loading")
    val indicator = modifier.size(40.dp).semantics { contentDescription = description }
    if (bondMotionEnabled()) {
        CircularWavyProgressIndicator(modifier = indicator)
    } else {
        CircularWavyProgressIndicator(
            progress = { 0.75f }, modifier = indicator, waveSpeed = 0.dp,
        )
    }
}

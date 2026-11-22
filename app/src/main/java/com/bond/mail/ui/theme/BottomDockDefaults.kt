package com.bond.mail.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/** Shared main/reader dock geometry. See docs/BOTTOM_DOCK_SPEC.md. */
internal object BottomDockDefaults {
    val MaxWidth = 324.dp // 300dp visible controls + 12dp outer padding on each side.
    val Height = 52.dp
    val ItemHeight = 48.dp
    val IndicatorHeight = 40.dp // 6dp visual breathing room inside the 52dp capsule.
    val IndicatorHorizontalInset = 6.dp
    val IndicatorItemInset = (ItemHeight - IndicatorHeight) / 2
    val VerticalInset = (Height - ItemHeight) / 2
    val BottomSpacing = 20.dp
    val OuterPadding = PaddingValues(start = 12.dp, top = 10.dp, end = 12.dp, bottom = BottomSpacing)
}

/** Repositions without remeasurement, while notifying coordinate-dependent glass consumers. */
internal fun Modifier.bottomDockOffset(verticalOffset: Dp): Modifier =
    offset { IntOffset(0, verticalOffset.roundToPx()) }

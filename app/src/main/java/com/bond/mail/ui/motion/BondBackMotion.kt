package com.bond.mail.ui.motion

/** A held gesture previews a card; only a committed return moves it off the screen. */
internal data class BackCardTransform(
    val scale: Float,
    val translationX: Float,
    val translationY: Float,
    val cornerFraction: Float,
    val scrimAlpha: Float,
)

internal fun backCardTransform(
    progress: Float,
    completion: Float,
    width: Float,
    height: Float,
    edgeRight: Boolean,
    touchDeltaY: Float = 0f,
): BackCardTransform {
    val p = progress.coerceIn(0f, 1f)
    val preview = 1f - (1f - p) * (1f - p) * (1f - p)
    val finish = completion.coerceIn(0f, 1f)
    val direction = if (edgeRight) -1f else 1f
    return BackCardTransform(
        scale = 1f - 0.10f * preview,
        translationX = direction * width * (0.025f * preview + 1.05f * finish),
        translationY = touchDeltaY.coerceIn(-height * 0.03f, height * 0.03f) * preview * (1f - finish),
        cornerFraction = preview,
        scrimAlpha = 0.18f * preview * (1f - finish),
    )
}

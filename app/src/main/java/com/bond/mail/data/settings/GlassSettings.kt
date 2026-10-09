package com.bond.mail.data.settings

/** Values are stored in dp; each control scales optics to its own geometry. */
data class GlassSettings(
    val blurRadius: Float = 6f,
    val refractionHeight: Float = 12f,
    val refractionAmount: Float = 24f,
    val chromaticAberration: Float = 0f,
) {
    fun normalized() = GlassSettings(
        blurRadius.safe(6f, 24f), refractionHeight.safe(12f, 32f),
        refractionAmount.safe(24f, 64f), chromaticAberration.safe(0f, 1f),
    )
    private fun Float.safe(fallback: Float, max: Float) = if (isFinite()) coerceIn(0f, max) else fallback
}

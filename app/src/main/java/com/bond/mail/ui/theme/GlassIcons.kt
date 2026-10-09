package com.bond.mail.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.bond.mail.data.settings.UiStyle

/** BondMail's own rounded-line glyphs; no Apple assets or symbol fonts are bundled. */
internal object GlassIcons {
    private fun glyph(name: String, body: PathBuilder.() -> Unit) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = body)
    }.build()
    val Mail = glyph("Glass.Mail") {
        moveTo(4f, 5f); lineTo(20f, 5f); quadTo(21f, 5f, 21f, 7f); lineTo(21f, 18f)
        quadTo(21f, 19f, 19f, 19f); lineTo(5f, 19f); quadTo(3f, 19f, 3f, 17f); lineTo(3f, 7f); quadTo(3f, 5f, 4f, 5f)
        moveTo(4f, 7f); lineTo(12f, 13f); lineTo(20f, 7f)
    }
    val People = glyph("Glass.People") {
        moveTo(15.5f, 7f); curveTo(15.5f, 12f, 8.5f, 12f, 8.5f, 7f); curveTo(8.5f, 2f, 15.5f, 2f, 15.5f, 7f)
        moveTo(5f, 21f); lineTo(5f, 18f); curveTo(5f, 12f, 19f, 12f, 19f, 18f); lineTo(19f, 21f)
    }
    val Settings = glyph("Glass.Settings") {
        moveTo(3f, 6f); lineTo(8f, 6f); moveTo(12f, 6f); lineTo(21f, 6f)
        moveTo(8f, 3f); lineTo(12f, 3f); lineTo(12f, 9f); lineTo(8f, 9f); close()
        moveTo(3f, 17f); lineTo(14f, 17f); moveTo(18f, 17f); lineTo(21f, 17f)
        moveTo(14f, 14f); lineTo(18f, 14f); lineTo(18f, 20f); lineTo(14f, 20f); close()
    }
    val Compose = glyph("Glass.Compose") {
        moveTo(11f, 4f); lineTo(5f, 4f); quadTo(3f, 4f, 3f, 6f); lineTo(3f, 19f)
        quadTo(3f, 21f, 5f, 21f); lineTo(18f, 21f); quadTo(20f, 21f, 20f, 19f); lineTo(20f, 13f)
        moveTo(10f, 14f); lineTo(11f, 10f); lineTo(19f, 2f); lineTo(22f, 5f); lineTo(14f, 13f); close()
    }
    val Reply = glyph("Glass.Reply") {
        moveTo(10f, 5f); lineTo(3f, 11f); lineTo(10f, 17f); moveTo(3f, 11f); lineTo(13f, 11f)
        quadTo(21f, 11f, 21f, 20f)
    }
    val Forward = glyph("Glass.Forward") {
        moveTo(14f, 5f); lineTo(21f, 11f); lineTo(14f, 17f); moveTo(21f, 11f); lineTo(11f, 11f)
        quadTo(3f, 11f, 3f, 20f)
    }
    val Share = glyph("Glass.Share") {
        moveTo(8f, 6f); lineTo(12f, 2f); lineTo(16f, 6f); moveTo(12f, 2f); lineTo(12f, 15f)
        moveTo(7f, 10f); lineTo(4f, 10f); lineTo(4f, 21f); lineTo(20f, 21f); lineTo(20f, 10f); lineTo(17f, 10f)
    }
    val Delete = glyph("Glass.Delete") {
        moveTo(4f, 6f); lineTo(20f, 6f); moveTo(9f, 6f); lineTo(9f, 3f); lineTo(15f, 3f); lineTo(15f, 6f)
        moveTo(6f, 6f); lineTo(7f, 21f); lineTo(17f, 21f); lineTo(18f, 6f)
        moveTo(10f, 10f); lineTo(10.5f, 17f); moveTo(14f, 10f); lineTo(13.5f, 17f)
    }
    val Send = glyph("Glass.Send") {
        moveTo(3f, 3f); lineTo(22f, 12f); lineTo(3f, 21f); lineTo(6f, 12f); close()
        moveTo(6f, 12f); lineTo(22f, 12f)
    }
}

@Composable
fun BondIcon(imageVector: ImageVector, contentDescription: String?, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    val glyph = if (LocalUiStyle.current == UiStyle.LIQUID_GLASS) when (imageVector.name.substringAfterLast('.')) {
        "Email", "Mail", "MailOutline" -> GlassIcons.Mail
        "Contacts", "People", "Person" -> GlassIcons.People
        "Settings" -> GlassIcons.Settings
        "Create", "Edit" -> GlassIcons.Compose
        "Reply" -> GlassIcons.Reply
        "Forward" -> GlassIcons.Forward
        "Share" -> GlassIcons.Share
        "Delete", "DeleteOutline" -> GlassIcons.Delete
        "Send" -> GlassIcons.Send
        else -> imageVector
    } else imageVector
    androidx.compose.material3.Icon(glyph, contentDescription, modifier, tint)
}

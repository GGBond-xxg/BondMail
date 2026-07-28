package com.bond.mail.ui.components

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.PathParser
import com.bond.mail.data.mail.BrandMatcher
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.absoluteValue
import kotlin.math.min

@Composable
fun BrandAvatar(
    senderName: String,
    senderAddress: String,
    size: Dp = 48.dp,
    monet: Boolean = true,
) {
    val context = LocalContext.current
    val brand = remember(senderName, senderAddress) {
        BrandMatcher.match(senderName, senderAddress)
    }
    val logo = remember(brand.key, senderAddress) {
        ContactLogoStore.load(context, brand.key, senderAddress)
    }
    val scheme = MaterialTheme.colorScheme
    val tone = remember(brand.key) { brand.key.hashCode().absoluteValue % 3 }
    val background = if (monet) {
        when (tone) {
            0 -> scheme.primaryContainer
            1 -> scheme.secondaryContainer
            else -> scheme.tertiaryContainer
        }
    } else {
        fixedBrandColor(brand.key) ?: when (tone) {
            0 -> scheme.primary
            1 -> scheme.secondary
            else -> scheme.tertiary
        }
    }
    val foreground = if (monet) {
        when (tone) {
            0 -> scheme.onPrimaryContainer
            1 -> scheme.onSecondaryContainer
            else -> scheme.onTertiaryContainer
        }
    } else if (background.luminance() > 0.52f) {
        Color.Black
    } else {
        Color.White
    }

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .border(1.dp, scheme.outlineVariant, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (logo != null) {
            BrandSvg(
                logo = logo,
                tint = foreground,
                modifier = Modifier.size(size * 0.54f),
            )
        } else {
            Text(
                text = brand.label,
                color = foreground,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun BrandSvg(
    logo: ContactLogo,
    tint: Color,
    modifier: Modifier,
) {
    Canvas(modifier) {
        val scale = min(size.width / logo.contentWidth, size.height / logo.contentHeight)
        val offsetX = (size.width - logo.contentWidth * scale) / 2f - logo.contentLeft * scale
        val offsetY = (size.height - logo.contentHeight * scale) / 2f - logo.contentTop * scale
        withTransform({
            translate(offsetX, offsetY)
            scale(scale, scale, pivot = androidx.compose.ui.geometry.Offset.Zero)
        }) {
            logo.paths.forEach { path -> drawPath(path, tint) }
        }
    }
}

private data class ContactLogo(
    val contentLeft: Float,
    val contentTop: Float,
    val contentWidth: Float,
    val contentHeight: Float,
    val paths: List<Path>,
    val pathData: List<String>,
)

internal fun contactLogoSvgMarkup(
    context: Context,
    senderName: String,
    senderAddress: String,
): String? {
    val brand = BrandMatcher.match(senderName, senderAddress)
    val logo = ContactLogoStore.load(context, brand.key, senderAddress) ?: return null
    val paths = logo.pathData.joinToString("") { data ->
        """<path d="${data.replace("&", "&amp;").replace("\"", "&quot;")}"/>"""
    }
    return """<svg viewBox="${logo.contentLeft} ${logo.contentTop} ${logo.contentWidth} ${logo.contentHeight}" aria-hidden="true">$paths</svg>"""
}

/**
 * Local SVG lookup for contact avatars.
 *
 * Add custom files under app/src/main/assets/contact_logos. Lookup order is the complete sender
 * domain, registrable/root domain, then BrandMatcher's key. Simple Icons bundled by the app live
 * in the nested simpleicons directory and use the same names.
 */
private object ContactLogoStore {
    private val cache = ConcurrentHashMap<String, ContactLogo>()
    private val missing = ConcurrentHashMap.newKeySet<String>()
    private val viewBoxRegex = Regex(
        """viewBox\s*=\s*["']\s*[-\d.]+\s+[-\d.]+\s+([\d.]+)\s+([\d.]+)\s*["']""",
        RegexOption.IGNORE_CASE,
    )
    private val pathRegex = Regex(
        """<path\b[^>]*\bd\s*=\s*(["'])(.*?)\1""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val widthRegex = Regex("""\bwidth\s*=\s*["']\s*([\d.]+)""", RegexOption.IGNORE_CASE)
    private val heightRegex = Regex("""\bheight\s*=\s*["']\s*([\d.]+)""", RegexOption.IGNORE_CASE)

    fun load(context: Context, brandKey: String, senderAddress: String): ContactLogo? {
        val domain = senderAddress.substringAfterLast('@', "").lowercase().trim()
        val rootDomain = domain.split('.').takeLast(2).joinToString(".")
        val names = linkedSetOf<String>().apply {
            sanitize(domain).takeIf(String::isNotBlank)?.let(::add)
            sanitize(rootDomain).takeIf(String::isNotBlank)?.let(::add)
            sanitize(assetSlug(brandKey)).takeIf(String::isNotBlank)?.let(::add)
        }
        names.forEach { name ->
            listOf(
                "contact_logos/$name.svg",
                "contact_logos/simpleicons/$name.svg",
            ).forEach { assetPath ->
                cache[assetPath]?.let { return it }
                if (assetPath in missing) return@forEach
                val parsed = runCatching {
                    context.assets.open(assetPath).bufferedReader().use { parse(it.readText()) }
                }.getOrNull()
                if (parsed != null) {
                    cache[assetPath] = parsed
                    return parsed
                }
                missing += assetPath
            }
        }
        return null
    }

    private fun parse(svg: String): ContactLogo? {
        val viewBox = viewBoxRegex.find(svg)
        val width = viewBox?.groupValues?.getOrNull(1)?.toFloatOrNull()
            ?: widthRegex.find(svg)?.groupValues?.getOrNull(1)?.toFloatOrNull()
            ?: 24f
        val height = viewBox?.groupValues?.getOrNull(2)?.toFloatOrNull()
            ?: heightRegex.find(svg)?.groupValues?.getOrNull(1)?.toFloatOrNull()
            ?: 24f
        val parsed = pathRegex.findAll(svg)
            .mapNotNull { match ->
                val data = match.groupValues[2]
                PathParser.createPathFromPathData(data)?.asComposePath()?.let { data to it }
            }
            .toList()
        return parsed.takeIf(List<Pair<String, Path>>::isNotEmpty)?.let { pairs ->
            val bounds = pairs.map { it.second.getBounds() }
            val left = bounds.minOfOrNull { it.left } ?: 0f
            val top = bounds.minOfOrNull { it.top } ?: 0f
            val right = bounds.maxOfOrNull { it.right } ?: width
            val bottom = bounds.maxOfOrNull { it.bottom } ?: height
            ContactLogo(
                contentLeft = left,
                contentTop = top,
                contentWidth = (right - left).takeIf { it > 0f } ?: width,
                contentHeight = (bottom - top).takeIf { it > 0f } ?: height,
                paths = pairs.map(Pair<String, Path>::second),
                pathData = pairs.map(Pair<String, Path>::first),
            )
        }
    }

    private fun sanitize(value: String): String =
        value.lowercase().replace(Regex("[^a-z0-9._-]"), "")

    private fun assetSlug(key: String): String = when (key) {
        "gmail.com" -> "gmail"
        "proton.me" -> "protonmail"
        "tutanota", "tuta.com" -> "tuta"
        "mail.com" -> "maildotcom"
        "mail.ru" -> "maildotru"
        "web.de" -> "webdotde"
        "qq.com" -> "qq"
        "outlook" -> "microsoftoutlook"
        "icloud" -> "icloud"
        "x.com", "twitter" -> "x"
        "163.com", "126.com" -> "neteasecloudmusic"
        "bank of china", "bochk" -> "bocbank"
        "za bank" -> "zabank"
        "gate.io", "gate" -> "gate"
        "neverless" -> "neverless"
        else -> key
    }
}

private fun fixedBrandColor(key: String): Color? = when (key) {
    "binance" -> Color(0xFFF3BA2F)
    "okx" -> Color(0xFF111111)
    "bybit" -> Color(0xFFF7A600)
    "bitget" -> Color(0xFF00D4AA)
    "coinbase" -> Color(0xFF0052FF)
    "kraken" -> Color(0xFF5741D9)
    "github" -> Color(0xFF24292F)
    "gitlab" -> Color(0xFFFC6D26)
    "google", "gmail.com" -> Color(0xFF4285F4)
    "protonmail", "proton.me" -> Color(0xFF6D4AFF)
    "tutanota", "tuta.com" -> Color(0xFF850122)
    "zoho" -> Color(0xFFE42527)
    "gmx" -> Color(0xFF1C449B)
    "web.de" -> Color(0xFFFFCC00)
    "mail.com" -> Color(0xFF004788)
    "mail.ru" -> Color(0xFF005FF9)
    "microsoft", "outlook" -> Color(0xFF0078D4)
    "icloud", "apple" -> Color(0xFF555555)
    "cloudflare" -> Color(0xFFF48120)
    "steam" -> Color(0xFF1B2838)
    "grab" -> Color(0xFF00B14F)
    "qq.com" -> Color(0xFF12B7F5)
    "163.com", "126.com" -> Color(0xFFD81E06)
    "yahoo" -> Color(0xFF6001D2)
    "youtube" -> Color(0xFFFF0000)
    "twitter", "x.com" -> Color(0xFF111111)
    "facebook" -> Color(0xFF1877F2)
    "instagram" -> Color(0xFFE1306C)
    "linkedin" -> Color(0xFF0A66C2)
    "telegram" -> Color(0xFF229ED9)
    "whatsapp" -> Color(0xFF25D366)
    "spotify" -> Color(0xFF1DB954)
    "amazon", "aws" -> Color(0xFFFF9900)
    "samsung" -> Color(0xFF1428A0)
    else -> null
}

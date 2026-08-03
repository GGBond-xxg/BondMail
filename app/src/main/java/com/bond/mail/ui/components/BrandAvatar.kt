package com.bond.mail.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Base64
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.PathParser
import com.bond.mail.data.mail.BrandMatcher
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.absoluteValue
import kotlin.math.min
import kotlin.math.roundToInt

data class BrandAvatarPalette(
    val background: Color,
    val foreground: Color,
)

fun contactAvatarText(senderName: String, senderAddress: String): String =
    BrandMatcher.match(senderName, senderAddress).label

@Composable
fun brandAvatarPalette(
    senderName: String,
    senderAddress: String,
    monet: Boolean,
): BrandAvatarPalette {
    val brand = remember(senderName, senderAddress) {
        BrandMatcher.match(senderName, senderAddress)
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
    } else if (brand.key in DARK_FOREGROUND_BRANDS) {
        Color(0xFF102A1D)
    } else if (background.luminance() > 0.52f) {
        Color.Black
    } else {
        Color.White
    }
    return BrandAvatarPalette(background, foreground)
}

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
    val palette = brandAvatarPalette(senderName, senderAddress, monet)
    val background = palette.background
    val foreground = palette.foreground

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
                brandKey = brand.key,
                tint = foreground,
                // Full-colour embedded artwork already contains its own square background. Fill
                // the parent so CircleShape clips it into a proper round contact avatar.
                modifier = Modifier.size(if (logo.raster != null) size else size * 0.54f),
            )
        } else if (brand.key in MICROSOFT_BRAND_KEYS) {
            MicrosoftMark(
                tint = foreground,
                modifier = Modifier.size(size * 0.48f),
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
fun ContactAvatar(
    name: String,
    email: String,
    customText: String?,
    size: Dp = 48.dp,
    monet: Boolean = true,
) {
    val glyph = customText?.trim().takeUnless { it.isNullOrBlank() }
    if (glyph == null) {
        BrandAvatar(
            senderName = name,
            senderAddress = email,
            size = size,
            monet = monet,
        )
        return
    }

    val palette = brandAvatarPalette(name, email, monet)
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(palette.background)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glyph,
            color = palette.foreground,
            maxLines = 1,
            textAlign = TextAlign.Center,
            fontSize = (size.value * 0.40f).sp,
            lineHeight = (size.value * 0.46f).sp,
            fontWeight = FontWeight.Bold,
            style = TextStyle(
                platformStyle = PlatformTextStyle(includeFontPadding = false),
            ),
        )
    }
}

@Composable
private fun BrandSvg(
    logo: ContactLogo,
    brandKey: String,
    tint: Color,
    modifier: Modifier,
) {
    Canvas(modifier) {
        val scale = min(size.width / logo.contentWidth, size.height / logo.contentHeight)
        val appleOffsetX = if (brandKey == "apple") size.width * 0.015f else 0f
        val appleOffsetY = if (brandKey == "apple") size.height * 0.055f else 0f
        val offsetX = (size.width - logo.contentWidth * scale) / 2f -
            logo.contentLeft * scale + appleOffsetX
        val offsetY = (size.height - logo.contentHeight * scale) / 2f -
            logo.contentTop * scale + appleOffsetY
        withTransform({
            translate(offsetX, offsetY)
            scale(scale, scale, pivot = androidx.compose.ui.geometry.Offset.Zero)
        }) {
            logo.raster?.let { image ->
                drawImage(
                    image = image,
                    dstSize = IntSize(
                        logo.contentWidth.roundToInt().coerceAtLeast(1),
                        logo.contentHeight.roundToInt().coerceAtLeast(1),
                    ),
                )
            } ?: logo.paths.forEach { path -> drawPath(path, tint) }
        }
    }
}

@Composable
private fun MicrosoftMark(
    tint: Color,
    modifier: Modifier,
) {
    Canvas(modifier) {
        val gap = size.minDimension * 0.09f
        val tile = (size.minDimension - gap) / 2f
        drawRect(tint, size = androidx.compose.ui.geometry.Size(tile, tile))
        drawRect(
            tint,
            topLeft = androidx.compose.ui.geometry.Offset(tile + gap, 0f),
            size = androidx.compose.ui.geometry.Size(tile, tile),
        )
        drawRect(
            tint,
            topLeft = androidx.compose.ui.geometry.Offset(0f, tile + gap),
            size = androidx.compose.ui.geometry.Size(tile, tile),
        )
        drawRect(
            tint,
            topLeft = androidx.compose.ui.geometry.Offset(tile + gap, tile + gap),
            size = androidx.compose.ui.geometry.Size(tile, tile),
        )
    }
}

private val MICROSOFT_BRAND_KEYS = setOf(
    "microsoft",
    "outlook",
    "outlook.com",
    "hotmail.com",
    "live.com",
)

private val DARK_FOREGROUND_BRANDS = setOf(
    "wise",
    "bitget",
    "trae",
)

private data class ContactLogo(
    val contentLeft: Float,
    val contentTop: Float,
    val contentWidth: Float,
    val contentHeight: Float,
    val paths: List<Path>,
    val pathData: List<String>,
    val evenOddPaths: List<Boolean>,
    val raster: ImageBitmap? = null,
)

internal fun contactLogoSvgMarkup(
    context: Context,
    senderName: String,
    senderAddress: String,
): String? {
    val brand = BrandMatcher.match(senderName, senderAddress)
    val logo = ContactLogoStore.load(context, brand.key, senderAddress) ?: return null
    if (logo.pathData.isEmpty()) return null
    val paths = logo.pathData.mapIndexed { index, data ->
        val fillRule = if (logo.evenOddPaths.getOrElse(index) { false }) {
            """ fill-rule="evenodd""""
        } else {
            ""
        }
        """<path$fillRule d="${data.replace("&", "&amp;").replace("\"", "&quot;")}"/>"""
    }.joinToString("")
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
    private val embeddedImageRegex = Regex(
        """<image\b[^>]*\b(?:href|xlink:href)\s*=\s*["']data:image/(?:png|jpe?g|webp);base64,([^"']+)["']""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

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
                val evenOdd = FILL_RULE_EVEN_ODD.containsMatchIn(match.value)
                PathParser.createPathFromPathData(data)?.asComposePath()?.let { path ->
                    if (evenOdd) path.fillType = PathFillType.EvenOdd
                    Triple(data, path, evenOdd)
                }
            }
            .toList()
        return parsed.takeIf(List<Triple<String, Path, Boolean>>::isNotEmpty)?.let { triples ->
            val bounds = triples.map { it.second.getBounds() }
            val left = bounds.minOfOrNull { it.left } ?: 0f
            val top = bounds.minOfOrNull { it.top } ?: 0f
            val right = bounds.maxOfOrNull { it.right } ?: width
            val bottom = bounds.maxOfOrNull { it.bottom } ?: height
            ContactLogo(
                contentLeft = left,
                contentTop = top,
                contentWidth = (right - left).takeIf { it > 0f } ?: width,
                contentHeight = (bottom - top).takeIf { it > 0f } ?: height,
                paths = triples.map { it.second },
                pathData = triples.map { it.first },
                evenOddPaths = triples.map { it.third },
                raster = null,
            )
        } ?: embeddedImageRegex.find(svg)?.groupValues?.getOrNull(1)
            ?.let { encoded ->
                runCatching {
                    Base64.decode(encoded.filterNot(Char::isWhitespace), Base64.DEFAULT)
                }.getOrNull()
            }
            ?.let { bytes -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
            ?.asImageBitmap()
            ?.let { image ->
                ContactLogo(
                    contentLeft = 0f,
                    contentTop = 0f,
                    contentWidth = width,
                    contentHeight = height,
                    paths = emptyList(),
                    pathData = emptyList(),
                    evenOddPaths = emptyList(),
                    raster = image,
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
        "outlook", "outlook.com", "hotmail.com", "live.com" -> "microsoftoutlook"
        "icloud" -> "icloud"
        "x.com", "twitter" -> "x"
        "chatgpt" -> "openai"
        "163.com", "126.com" -> "neteasecloudmusic"
        "bank of china", "bochk" -> "bocbank"
        "za bank" -> "zabank"
        "gate.io", "gate" -> "gate"
        "neverless" -> "neverless"
        else -> key
    }

    private val FILL_RULE_EVEN_ODD = Regex(
        """fill-rule\s*=\s*["']evenodd["']""",
        RegexOption.IGNORE_CASE,
    )
}

private fun fixedBrandColor(key: String): Color? = when (key) {
    "binance" -> Color(0xFFF3BA2F)
    "okx" -> Color(0xFF111111)
    "bybit" -> Color(0xFFF7A600)
    "bitget" -> Color(0xFF00D4AA)
    "ibkr" -> Color(0xFFD81222)
    "agoda" -> Color(0xFF5392F9)
    "lottiefiles" -> Color(0xFF00BFA5)
    "wise" -> Color(0xFF9FE870)
    "longbridge" -> Color(0xFF476C88)
    "futu" -> Color(0xFFFF6900)
    "robinhood" -> Color(0xFF00C805)
    "n26" -> Color(0xFF36A18B)
    "ifast" -> Color(0xFF2F3D3E)
    "cathay" -> Color(0xFF005D63)
    "trae" -> Color(0xFF32F08C)
    "osl" -> Color(0xFF121E31)
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
    "microsoft", "outlook", "outlook.com", "hotmail.com", "live.com" -> Color(0xFF0078D4)
    "icloud", "apple" -> Color(0xFF555555)
    "openai", "chatgpt" -> Color(0xFF111111)
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

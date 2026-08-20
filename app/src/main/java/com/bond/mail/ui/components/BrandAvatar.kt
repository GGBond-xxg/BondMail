package com.bond.mail.ui.components

import android.content.Context
import android.graphics.Matrix as AndroidMatrix
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.util.Base64
import android.util.Xml
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
import java.io.StringReader
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.absoluteValue
import kotlin.math.min
import kotlin.math.roundToInt
import org.xmlpull.v1.XmlPullParser

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
    val officialInk = OFFICIAL_LIGHT_AVATAR_INKS[brand.key]
    val isDarkPalette = scheme.background.luminance() < 0.5f
    val background = if (officialInk != null) {
        if (isDarkPalette) scheme.surfaceVariant else Color.White
    } else if (monet) {
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
    val foreground = if (officialInk != null) {
        if (isDarkPalette && brand.key in DARK_AVATAR_LIGHT_INK_BRANDS) {
            scheme.onSurface
        } else {
            officialInk
        }
    } else if (monet) {
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
        if (logo?.raster != null && monet) {
            // Embedded JPEG/PNG logos include their own opaque brand background and cannot be
            // recolored without becoming a solid disk. Use the brand label so these contacts (OSL
            // in particular) participate in the active Material You palette like vector logos.
            Text(
                text = brand.label,
                color = foreground,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
            )
        } else if (logo != null) {
            BrandSvg(
                logo = logo,
                tint = foreground,
                // Full-colour embedded artwork already contains its own square background. Fill
                // the parent so CircleShape clips it into a proper round contact avatar.
                modifier = Modifier.size(
                    if (logo.raster != null) size else size * brandLogoScale(brand.key),
                ),
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
    tint: Color,
    modifier: Modifier,
) {
    Canvas(modifier) {
        val scale = min(size.width / logo.contentWidth, size.height / logo.contentHeight)
        val offsetX = (size.width - logo.contentWidth * scale) / 2f -
            logo.contentLeft * scale
        val offsetY = (size.height - logo.contentHeight * scale) / 2f -
            logo.contentTop * scale
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
    "fliggy",
)

private val OFFICIAL_LIGHT_AVATAR_INKS = mapOf(
    "alipay" to Color(0xFF1677FF),
    "pixiv" to Color(0xFF0096FA),
    "plasmaone" to Color(0xFF141414),
    "safepal" to Color(0xFF4A21EF),
    "qianji" to Color(0xFF111111),
)

private val DARK_AVATAR_LIGHT_INK_BRANDS = setOf(
    "plasmaone",
    "qianji",
)

private fun brandLogoScale(key: String): Float = when (key) {
    "alipay" -> 0.66f
    "pixiv" -> 0.72f
    else -> 0.54f
}

private data class ContactLogo(
    val contentLeft: Float,
    val contentTop: Float,
    val contentWidth: Float,
    val contentHeight: Float,
    val paths: List<Path>,
    val markupElements: List<String>,
    val raster: ImageBitmap? = null,
)

internal fun contactLogoSvgMarkup(
    context: Context,
    senderName: String,
    senderAddress: String,
): String? {
    val brand = BrandMatcher.match(senderName, senderAddress)
    val logo = ContactLogoStore.load(context, brand.key, senderAddress) ?: return null
    if (logo.markupElements.isEmpty()) return null
    return """<svg viewBox="${logo.contentLeft} ${logo.contentTop} ${logo.contentWidth} ${logo.contentHeight}" aria-hidden="true">${logo.markupElements.joinToString("")}</svg>"""
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
        return parseVector(svg)?.let { parsed ->
            val bounds = parsed.map { it.path.getBounds() }
            val left = bounds.minOfOrNull { it.left } ?: 0f
            val top = bounds.minOfOrNull { it.top } ?: 0f
            val right = bounds.maxOfOrNull { it.right } ?: width
            val bottom = bounds.maxOfOrNull { it.bottom } ?: height
            ContactLogo(
                contentLeft = left,
                contentTop = top,
                contentWidth = (right - left).takeIf { it > 0f } ?: width,
                contentHeight = (bottom - top).takeIf { it > 0f } ?: height,
                paths = parsed.map { it.path },
                markupElements = parsed.map { it.markup },
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
                    markupElements = emptyList(),
                    raster = image,
                )
            }
    }

    private data class ParsedShape(
        val path: Path,
        val markup: String,
    )

    private fun parseVector(svg: String): List<ParsedShape>? = runCatching {
        val parser = Xml.newPullParser().apply {
            setInput(StringReader(svg))
        }
        val matrices = mutableMapOf(0 to AndroidMatrix())
        val shapes = mutableListOf<ParsedShape>()
        var ignoredDepth = -1
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val depth = parser.depth
                    val tag = parser.name.substringAfter(':').lowercase()
                    if (ignoredDepth < 0) {
                        val shouldIgnore = tag in IGNORED_SVG_CONTAINERS ||
                            parser.attribute("mask")?.isNotBlank() == true
                        if (shouldIgnore) {
                            ignoredDepth = depth
                        } else {
                            val combined = AndroidMatrix(matrices[depth - 1] ?: AndroidMatrix())
                            parser.attribute("transform")
                                ?.takeIf(String::isNotBlank)
                                ?.let(::parseTransform)
                                ?.let(combined::preConcat)
                            matrices[depth] = combined
                            parseShape(parser, tag, combined)?.let(shapes::add)
                        }
                    }
                }

                XmlPullParser.END_TAG -> {
                    val depth = parser.depth
                    matrices.remove(depth)
                    if (ignoredDepth == depth) ignoredDepth = -1
                }
            }
            event = parser.next()
        }
        shapes.takeIf(List<ParsedShape>::isNotEmpty)
    }.getOrNull()

    private fun parseShape(
        parser: XmlPullParser,
        tag: String,
        transform: AndroidMatrix,
    ): ParsedShape? {
        val source = when (tag) {
            "path" -> parser.attribute("d")
                ?.takeIf(String::isNotBlank)
                ?.let(PathParser::createPathFromPathData)

            "circle" -> {
                val cx = parser.numberAttribute("cx") ?: return null
                val cy = parser.numberAttribute("cy") ?: return null
                val radius = parser.numberAttribute("r") ?: return null
                AndroidPath().apply {
                    addCircle(cx, cy, radius, AndroidPath.Direction.CW)
                }
            }

            "ellipse" -> {
                val cx = parser.numberAttribute("cx") ?: return null
                val cy = parser.numberAttribute("cy") ?: return null
                val rx = parser.numberAttribute("rx") ?: return null
                val ry = parser.numberAttribute("ry") ?: return null
                AndroidPath().apply {
                    addOval(RectF(cx - rx, cy - ry, cx + rx, cy + ry), AndroidPath.Direction.CW)
                }
            }

            "rect" -> {
                val x = parser.numberAttribute("x") ?: 0f
                val y = parser.numberAttribute("y") ?: 0f
                val width = parser.numberAttribute("width") ?: return null
                val height = parser.numberAttribute("height") ?: return null
                val rx = parser.numberAttribute("rx") ?: 0f
                val ry = parser.numberAttribute("ry") ?: rx
                AndroidPath().apply {
                    val bounds = RectF(x, y, x + width, y + height)
                    if (rx > 0f || ry > 0f) addRoundRect(bounds, rx, ry, AndroidPath.Direction.CW)
                    else addRect(bounds, AndroidPath.Direction.CW)
                }
            }

            "polygon" -> parsePolygon(parser.attribute("points"))
            else -> null
        } ?: return null

        val fillRule = parser.styledAttribute("fill-rule")
        if (fillRule.equals("evenodd", ignoreCase = true)) {
            source.fillType = AndroidPath.FillType.EVEN_ODD
        }
        val fill = parser.styledAttribute("fill")
        val stroke = parser.styledAttribute("stroke")
        val strokeOnly = fill.equals("none", ignoreCase = true) &&
            !stroke.isNullOrBlank() && !stroke.equals("none", ignoreCase = true)
        val drawablePath = if (strokeOnly) {
            val outlined = AndroidPath()
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = parser.numberAttribute("stroke-width") ?: 1f
                strokeCap = when (parser.styledAttribute("stroke-linecap")?.lowercase()) {
                    "round" -> Paint.Cap.ROUND
                    "square" -> Paint.Cap.SQUARE
                    else -> Paint.Cap.BUTT
                }
                strokeJoin = when (parser.styledAttribute("stroke-linejoin")?.lowercase()) {
                    "round" -> Paint.Join.ROUND
                    "bevel" -> Paint.Join.BEVEL
                    else -> Paint.Join.MITER
                }
            }.getFillPath(source, outlined)
            outlined
        } else {
            source
        }
        drawablePath.transform(transform)
        val composePath = drawablePath.asComposePath().apply {
            if (fillRule.equals("evenodd", ignoreCase = true)) fillType = PathFillType.EvenOdd
        }
        return ParsedShape(
            path = composePath,
            markup = shapeMarkup(parser, tag, transform, strokeOnly),
        )
    }

    private fun parsePolygon(points: String?): AndroidPath? {
        val values = points?.let(NUMBER_REGEX::findAll)
            ?.mapNotNull { it.value.toFloatOrNull() }
            ?.toList()
            .orEmpty()
        if (values.size < 6 || values.size % 2 != 0) return null
        return AndroidPath().apply {
            moveTo(values[0], values[1])
            var index = 2
            while (index < values.size) {
                lineTo(values[index], values[index + 1])
                index += 2
            }
            close()
        }
    }

    private fun shapeMarkup(
        parser: XmlPullParser,
        tag: String,
        transform: AndroidMatrix,
        strokeOnly: Boolean,
    ): String {
        val geometryAttributes = when (tag) {
            "path" -> listOf("d")
            "circle" -> listOf("cx", "cy", "r")
            "ellipse" -> listOf("cx", "cy", "rx", "ry")
            "rect" -> listOf("x", "y", "width", "height", "rx", "ry")
            "polygon" -> listOf("points")
            else -> emptyList()
        }
        val geometry = geometryAttributes.mapNotNull { name ->
            parser.attribute(name)?.let { value -> " $name=\"${escapeXml(value)}\"" }
        }.joinToString("")
        val fillRule = parser.styledAttribute("fill-rule")
            ?.takeIf { it.equals("evenodd", ignoreCase = true) }
            ?.let { " fill-rule=\"evenodd\"" }
            .orEmpty()
        val paint = if (strokeOnly) {
            val width = parser.numberAttribute("stroke-width") ?: 1f
            val cap = parser.styledAttribute("stroke-linecap")?.let(::escapeXml).orEmpty()
            val join = parser.styledAttribute("stroke-linejoin")?.let(::escapeXml).orEmpty()
            buildString {
                append(" data-bondmail-stroke=\"true\" fill=\"none\" stroke=\"currentColor\"")
                append(" stroke-width=\"").append(width).append('\"')
                if (cap.isNotBlank()) append(" stroke-linecap=\"").append(cap).append('\"')
                if (join.isNotBlank()) append(" stroke-linejoin=\"").append(join).append('\"')
            }
        } else {
            " fill=\"currentColor\""
        }
        return "<$tag$geometry$fillRule$paint transform=\"${transform.toSvgMatrix()}\"/>"
    }

    private fun parseTransform(raw: String): AndroidMatrix {
        val result = AndroidMatrix()
        TRANSFORM_REGEX.findAll(raw).forEach { match ->
            val values = NUMBER_REGEX.findAll(match.groupValues[2])
                .mapNotNull { it.value.toFloatOrNull() }
                .toList()
            val operation = AndroidMatrix()
            when (match.groupValues[1].lowercase()) {
                "matrix" -> if (values.size >= 6) {
                    operation.setValues(
                        floatArrayOf(
                            values[0], values[2], values[4],
                            values[1], values[3], values[5],
                            0f, 0f, 1f,
                        ),
                    )
                }

                "translate" -> if (values.isNotEmpty()) {
                    operation.setTranslate(values[0], values.getOrElse(1) { 0f })
                }

                "scale" -> if (values.isNotEmpty()) {
                    operation.setScale(values[0], values.getOrElse(1) { values[0] })
                }

                "rotate" -> if (values.isNotEmpty()) {
                    if (values.size >= 3) operation.setRotate(values[0], values[1], values[2])
                    else operation.setRotate(values[0])
                }

                "skewx" -> if (values.isNotEmpty()) {
                    operation.setSkew(kotlin.math.tan(Math.toRadians(values[0].toDouble())).toFloat(), 0f)
                }

                "skewy" -> if (values.isNotEmpty()) {
                    operation.setSkew(0f, kotlin.math.tan(Math.toRadians(values[0].toDouble())).toFloat())
                }

                else -> return@forEach
            }
            result.preConcat(operation)
        }
        return result
    }

    private fun AndroidMatrix.toSvgMatrix(): String {
        val values = FloatArray(9)
        getValues(values)
        return "matrix(${values[0]} ${values[3]} ${values[1]} ${values[4]} ${values[2]} ${values[5]})"
    }

    private fun XmlPullParser.attribute(name: String): String? {
        for (index in 0 until attributeCount) {
            if (getAttributeName(index).substringAfter(':').equals(name, ignoreCase = true)) {
                return getAttributeValue(index)
            }
        }
        return null
    }

    private fun XmlPullParser.styledAttribute(name: String): String? =
        attribute(name) ?: attribute("style")
            ?.split(';')
            ?.mapNotNull { declaration ->
                val separator = declaration.indexOf(':')
                if (separator <= 0) null
                else declaration.substring(0, separator).trim() to declaration.substring(separator + 1).trim()
            }
            ?.firstOrNull { it.first.equals(name, ignoreCase = true) }
            ?.second

    private fun XmlPullParser.numberAttribute(name: String): Float? =
        styledAttribute(name)?.let(NUMBER_REGEX::find)?.value?.toFloatOrNull()

    private fun escapeXml(value: String): String = value
        .replace("&", "&amp;")
        .replace("\"", "&quot;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

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
        "163.com" -> "163.com"
        "126.com" -> "126.com"
        "bank of china", "bochk" -> "bocbank"
        "za bank" -> "zabank"
        "gate.io", "gate" -> "gate"
        "neverless" -> "neverless"
        else -> key
    }

    private val IGNORED_SVG_CONTAINERS = setOf("defs", "clippath", "mask", "symbol")
    private val NUMBER_REGEX = Regex("""[-+]?(?:\d*\.?\d+)(?:[eE][-+]?\d+)?""")
    private val TRANSFORM_REGEX = Regex("""([a-zA-Z]+)\s*\(([^)]*)\)""")
}

private fun fixedBrandColor(key: String): Color? = when (key) {
    "binance" -> Color(0xFFF3BA2F)
    "okx" -> Color(0xFF111111)
    "bybit" -> Color(0xFFF7A600)
    "bitget" -> Color(0xFF00D4AA)
    "ibkr" -> Color(0xFFD81222)
    "agoda" -> Color(0xFF5392F9)
    "qunar" -> Color(0xFF00BFEA)
    "tongcheng" -> Color(0xFF5C09C5)
    "variflight" -> Color(0xFF1677FF)
    "airchina" -> Color(0xFF111111)
    "fliggy" -> Color(0xFFFDD700)
    "hostelworld" -> Color(0xFFF47853)
    "airbnb" -> Color(0xFFFF385C)
    "hotels.com" -> Color(0xFFD12D2C)
    "expedia" -> Color(0xFF212844)
    "booking" -> Color(0xFF0C3B7C)
    "trainline" -> Color(0xFF00A88F)
    "rome2rio" -> Color(0xFFDE007B)
    "omio" -> Color(0xFF132968)
    "citymapper" -> Color(0xFF111111)
    "bolt" -> Color(0xFF1C274C)
    "cabify" -> Color(0xFF212240)
    "didi" -> Color(0xFFFC9153)
    "lyft" -> Color(0xFF2CA39C)
    "uber" -> Color(0xFF111111)
    "chinapost" -> Color(0xFF006845)
    "sfexpress" -> Color(0xFFDA2032)
    "alipay" -> Color(0xFF1677FF)
    "moovit" -> Color(0xFFF05523)
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

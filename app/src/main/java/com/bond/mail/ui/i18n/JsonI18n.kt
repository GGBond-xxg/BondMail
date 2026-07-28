package com.bond.mail.ui.i18n

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import org.json.JSONObject
import java.util.Locale

/**
 * JSON-backed UI strings.
 *
 * To add a language:
 * 1. Copy app/src/main/assets/i18n/en.json.
 * 2. Translate every value without changing keys.
 * 3. Add one entry to [SupportedLanguages.options].
 */
data class LanguageOption(
    val code: String,
    val assetFile: String,
    val labelKey: String,
)

object SupportedLanguages {
    const val SYSTEM = "system"

    val options = listOf(
        LanguageOption(SYSTEM, "", "language_system"),
        LanguageOption("zh", "zh.json", "language_zh"),
        LanguageOption("zh-CHT", "zh-CHT.json", "language_zh_cht"),
        LanguageOption("en", "en.json", "language_en"),
    )

    fun resolveAsset(languageCode: String, locale: Locale): String {
        if (languageCode != SYSTEM) {
            return options.firstOrNull { it.code == languageCode }?.assetFile ?: "en.json"
        }
        return when (locale.language.lowercase(Locale.ROOT)) {
            "zh" -> {
                val tag = locale.toLanguageTag().lowercase(Locale.ROOT)
                if (tag.contains("hant") || locale.country.uppercase(Locale.ROOT) in setOf("TW", "HK", "MO")) {
                    "zh-CHT.json"
                } else {
                    "zh.json"
                }
            }
            else -> "en.json"
        }
    }
}

class JsonStrings internal constructor(
    private val primary: Map<String, String>,
    private val fallback: Map<String, String>,
    val locale: Locale,
) {
    fun text(key: String, vararg args: Any): String {
        val template = primary[key] ?: fallback[key] ?: key
        return if (args.isEmpty()) template else runCatching {
            String.format(locale, template, *args)
        }.getOrDefault(template)
    }
}

val LocalJsonStrings = staticCompositionLocalOf {
    JsonStrings(emptyMap(), emptyMap(), Locale.ENGLISH)
}

@Composable
fun JsonStringsProvider(languageCode: String, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val systemLocale = configuration.locales[0] ?: Locale.getDefault()
    val asset = SupportedLanguages.resolveAsset(languageCode, systemLocale)
    val locale = when (asset) {
        "zh.json" -> Locale.SIMPLIFIED_CHINESE
        "zh-CHT.json" -> Locale.TRADITIONAL_CHINESE
        else -> Locale.ENGLISH
    }
    val fallback = rememberAssetStrings(context, "en.json")
    val primary = rememberAssetStrings(context, asset)
    CompositionLocalProvider(
        LocalJsonStrings provides JsonStrings(primary, fallback, locale),
        content = content,
    )
}

@Composable
private fun rememberAssetStrings(context: Context, asset: String): Map<String, String> {
    return androidx.compose.runtime.remember(context, asset) {
        runCatching {
            context.assets.open("i18n/$asset").bufferedReader(Charsets.UTF_8).use { reader ->
                val json = JSONObject(reader.readText())
                buildMap {
                    json.keys().forEach { key -> put(key, json.optString(key, key)) }
                }
            }
        }.getOrDefault(emptyMap())
    }
}

@Composable
fun tr(key: String, vararg args: Any): String = LocalJsonStrings.current.text(key, *args)

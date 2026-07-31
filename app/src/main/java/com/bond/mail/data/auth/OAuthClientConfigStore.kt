package com.bond.mail.data.auth

import android.content.Context
import org.json.JSONObject
import java.io.File

data class OAuthClientConfigurationInfo(
    val configured: Boolean,
    val clientIdHint: String = "",
    val errorKey: String? = null,
)

/**
 * Resolves bundled and user-supplied public OAuth client configuration.
 *
 * OAuth client IDs and redirect metadata are application configuration, not mailbox passwords.
 * Gmail and Outlook ship with the matching public-client registrations. A complete JSON imported
 * by the user remains a separate override, which is useful for independently signed source builds.
 */
class OAuthClientConfigStore(context: Context) {
    private val appContext = context.applicationContext
    private val directory = File(appContext.filesDir, "oauth_clients")

    fun info(providerId: String): OAuthClientConfigurationInfo = runCatching {
        val clientId = when (providerId) {
            "gmail" -> googleClientId()
            "outlook", "m365" -> microsoftClientId()
            else -> return OAuthClientConfigurationInfo(false)
        }
        OAuthClientConfigurationInfo(
            configured = true,
            clientIdHint = clientId.toSafeHint(),
        )
    }.getOrElse { failure ->
        OAuthClientConfigurationInfo(
            configured = false,
            errorKey = if (configurationFile(providerId).isFile) {
                failure.configurationErrorKey(providerId)
            } else {
                null
            },
        )
    }

    fun save(providerId: String, rawJson: String): OAuthClientConfigurationInfo {
        val normalized = rawJson.trim()
        if (normalized.isBlank()) {
            throw OAuthConfigurationException("OAuth client JSON is empty")
        }
        val json = runCatching { JSONObject(normalized) }
            .getOrElse { throw OAuthConfigurationException("OAuth client JSON is invalid", it) }

        when (providerId) {
            "gmail" -> validateGoogle(json)
            "outlook", "m365" -> validateMicrosoft(json)
            else -> throw OAuthConfigurationException("Unsupported OAuth provider: $providerId")
        }

        writeAtomically(configurationFile(providerId), json.toString(2))
        return info(providerId)
    }

    fun googleClientId(): String {
        val root = read("gmail")
        val section = root.optJSONObject("web")
            ?: root.optJSONObject("installed")
            ?: root
        return section.optString("client_id").trim()
            .takeIf { it.endsWith(".apps.googleusercontent.com") }
            ?: throw OAuthConfigurationException("google_client_required")
    }

    /** Web clients request a server auth code; Android/installed clients stay fully on-device. */
    fun googleServerClientId(): String? =
        read("gmail").optJSONObject("web")
            ?.optString("client_id")
            ?.trim()
            ?.takeIf { it.endsWith(".apps.googleusercontent.com") }

    fun microsoftConfigurationFile(): File {
        val customFile = configurationFile("outlook")
        if (customFile.isFile) {
            validateMicrosoft(read("outlook"))
            return customFile
        }

        val bundled = bundledJson(MICROSOFT_CONFIG_ASSET)
        validateMicrosoft(bundled)
        val bundledFile = File(directory, "microsoft.builtin.json")
        val serialized = bundled.toString(2)
        if (!bundledFile.isFile || bundledFile.readText(Charsets.UTF_8) != serialized) {
            writeAtomically(bundledFile, serialized)
        }
        return bundledFile
    }

    private fun microsoftClientId(): String =
        read("outlook").optString("client_id").trim()
            .takeIf(String::isNotBlank)
            ?: throw OAuthConfigurationException("Microsoft JSON must contain client_id")

    private fun validateGoogle(root: JSONObject) {
        val section = root.optJSONObject("web")
            ?: root.optJSONObject("installed")
            ?: root
        val clientId = section.optString("client_id").trim()
        if (!clientId.endsWith(".apps.googleusercontent.com")) {
            throw OAuthConfigurationException("google_client_required")
        }
    }

    private fun validateMicrosoft(root: JSONObject) {
        if (root.optString("client_id").isBlank()) {
            throw OAuthConfigurationException("Microsoft JSON must contain client_id")
        }
        val redirect = root.optString("redirect_uri").trim()
        val expectedPrefix = "msauth://${appContext.packageName}/"
        if (!redirect.startsWith(expectedPrefix, ignoreCase = true)) {
            throw OAuthConfigurationException(
                "Microsoft redirect_uri must start with $expectedPrefix",
            )
        }
        if (root.optString("account_mode").ifBlank { "MULTIPLE" } != "MULTIPLE") {
            throw OAuthConfigurationException("Microsoft account_mode must be MULTIPLE")
        }
    }

    private fun read(providerId: String): JSONObject {
        val file = configurationFile(providerId)
        if (file.isFile) {
            return runCatching { JSONObject(file.readText(Charsets.UTF_8)) }
                .getOrElse {
                    throw OAuthConfigurationException("Stored OAuth client JSON is invalid", it)
                }
        }
        return when (providerId) {
            "gmail" -> bundledJson(GOOGLE_CONFIG_ASSET)
            "outlook", "m365" -> bundledJson(MICROSOFT_CONFIG_ASSET)
            else -> throw OAuthConfigurationException("OAuth client JSON has not been configured")
        }
    }

    private fun bundledJson(assetName: String): JSONObject = runCatching {
        appContext.assets.open(assetName).bufferedReader(Charsets.UTF_8).use { reader ->
            JSONObject(reader.readText())
        }
    }.getOrElse {
        throw OAuthConfigurationException("Bundled OAuth client JSON is invalid", it)
    }

    private fun writeAtomically(destination: File, content: String) {
        directory.mkdirs()
        val temporary = File(directory, "${destination.name}.tmp")
        temporary.writeText(content, Charsets.UTF_8)
        if (destination.exists() && !destination.delete()) {
            temporary.delete()
            throw OAuthConfigurationException("Could not replace OAuth client configuration")
        }
        if (!temporary.renameTo(destination)) {
            temporary.delete()
            throw OAuthConfigurationException("Could not save OAuth client configuration")
        }
    }

    private fun configurationFile(providerId: String): File = when (providerId) {
        "gmail" -> File(directory, "google.json")
        "outlook", "m365" -> File(directory, "microsoft.json")
        else -> File(directory, "$providerId.json")
    }

    private companion object {
        const val GOOGLE_CONFIG_ASSET = "oauth/gmail.json"
        const val MICROSOFT_CONFIG_ASSET = "oauth/outlook.json"
    }
}

private fun String.toSafeHint(): String = when {
    length <= 12 -> this
    else -> "${take(6)}…${takeLast(6)}"
}

private fun Throwable.configurationErrorKey(providerId: String): String = when {
    message == "google_client_required" -> "error_oauth_google_client_invalid"
    providerId == "outlook" || providerId == "m365" -> "error_oauth_microsoft_json_invalid"
    else -> "error_oauth_config_invalid"
}

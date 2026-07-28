package com.bond.mail.data.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.bond.mail.data.db.AccountEntity

/** Provider-aware OAuth entry point shared by account setup and background mail operations. */
class OAuthCredentialBroker(context: Context) {
    private val configurations = OAuthClientConfigStore(context)
    private val microsoft = MicrosoftOAuthManager(context, configurations)
    private val google = GoogleOAuthManager(context, configurations)

    fun configurationInfo(providerId: String): OAuthClientConfigurationInfo =
        configurations.info(providerId)

    fun saveClientConfiguration(
        providerId: String,
        rawJson: String,
    ): OAuthClientConfigurationInfo {
        val info = configurations.save(providerId, rawJson)
        if (providerId == "outlook" || providerId == "m365") {
            microsoft.configurationChanged()
        }
        return info
    }

    suspend fun authorizeMicrosoft(
        activity: Activity,
        providerId: String,
        loginHint: String? = null,
        forceLogin: Boolean = false,
    ): OAuthGrant = microsoft.authorize(activity, providerId, loginHint, forceLogin)

    suspend fun beginGoogleAuthorization(
        activity: Activity,
        accountEmail: String? = null,
    ): GoogleAuthorizationStep = google.beginAuthorization(activity, accountEmail)

    suspend fun finishGoogleAuthorization(activity: Activity, data: Intent): OAuthGrant =
        google.finishAuthorization(activity, data)

    suspend fun accessToken(account: AccountEntity): String = when (account.providerId) {
        "gmail" -> google.accessToken(account.email)
        "outlook", "m365" -> {
            val providerAccountId = account.oauthAccountId
                ?: throw OAuthReauthorizationRequiredException("Microsoft account identity is missing")
            microsoft.accessToken(providerAccountId)
        }
        else -> throw OAuthConfigurationException("Unsupported OAuth provider: ${account.providerId}")
    }
}

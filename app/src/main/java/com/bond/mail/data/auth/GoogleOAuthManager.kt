package com.bond.mail.data.auth

import android.accounts.Account
import android.app.Activity
import android.content.Context
import android.content.Intent
import com.bond.mail.data.mail.MailLog
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Gmail authorization via Google Identity Services. No refresh token is stored on-device. */
class GoogleOAuthManager(
    context: Context,
    private val configurations: OAuthClientConfigStore,
) {
    private val appContext = context.applicationContext

    suspend fun beginAuthorization(
        activity: Activity,
        accountEmail: String? = null,
    ): GoogleAuthorizationStep {
        MailLog.d(
            MailLog.OAUTH,
            "google authorize start mode=${if (accountEmail.isNullOrBlank()) "add" else "renew"} " +
                "account=${accountEmail?.let(MailLog::accountHint) ?: "selector"}",
        )
        // Reading the client validates that the user supplied a Google OAuth configuration.
        // Android clients are selected by Play services through package name + signing SHA-1 and
        // must not be passed as a server client ID.
        configurations.googleClientId()
        val requestBuilder = AuthorizationRequest.builder()
            .setRequestedScopes(SCOPES)
        configurations.googleServerClientId()?.let(requestBuilder::requestOfflineAccess)
        if (accountEmail.isNullOrBlank()) {
            requestBuilder.setPrompt(AuthorizationRequest.Prompt.SELECT_ACCOUNT)
        } else {
            // Reauthorization is pinned to the local mailbox. If Google can no longer satisfy
            // the grant silently, AuthorizationClient returns a PendingIntent for the required
            // consent UI. SELECT_ACCOUNT is intentionally not combined with setAccount(),
            // because Google's API documents that it overrides the requested account.
            requestBuilder.setAccount(Account(accountEmail.trim(), GOOGLE_ACCOUNT_TYPE))
        }
        val request = requestBuilder.build()
        val client = Identity.getAuthorizationClient(activity)
        var result = client.authorize(request).awaitValue()
        if (!accountEmail.isNullOrBlank() && !result.hasResolution()) {
            // “Reauthorize” must not simply hand the exact same cached access token back to
            // JavaMail. Clear only this short-lived token (not the user's OAuth grant) and ask
            // AuthorizationClient for a fresh one. If consent was revoked, the second request
            // naturally returns the provider PendingIntent instead.
            result.accessToken?.takeIf(String::isNotBlank)?.let { cachedToken ->
                client.clearToken(
                    ClearTokenRequest.builder().setToken(cachedToken).build(),
                ).awaitCompletion()
                result = client.authorize(request).awaitValue()
                MailLog.d(MailLog.OAUTH, "google cached token refreshed for reauthorization")
            }
        }
        return if (result.hasResolution()) {
            MailLog.d(MailLog.OAUTH, "google authorize requires resolution")
            val pendingIntent = result.pendingIntent
                ?: throw OAuthConfigurationException("Google authorization did not provide a resolution")
            GoogleAuthorizationStep.RequiresResolution(pendingIntent)
        } else {
            val grant = toGrant(result)
            MailLog.d(MailLog.OAUTH, "google authorize success account=${MailLog.accountHint(grant.email)}")
            GoogleAuthorizationStep.Authorized(grant)
        }
    }

    suspend fun finishAuthorization(activity: Activity, data: Intent): OAuthGrant {
        val result = Identity.getAuthorizationClient(activity).getAuthorizationResultFromIntent(data)
        val grant = toGrant(result)
        MailLog.d(MailLog.OAUTH, "google resolution success account=${MailLog.accountHint(grant.email)}")
        return grant
    }

    suspend fun accessToken(email: String): String {
        MailLog.d(MailLog.OAUTH, "google silent token start account=${MailLog.accountHint(email)}")
        configurations.googleClientId()
        val requestBuilder = AuthorizationRequest.builder()
            .setAccount(Account(email, GOOGLE_ACCOUNT_TYPE))
            .setRequestedScopes(SCOPES)
        configurations.googleServerClientId()?.let(requestBuilder::requestOfflineAccess)
        val request = requestBuilder.build()
        val result = Identity.getAuthorizationClient(appContext).authorize(request).awaitValue()
        if (result.hasResolution()) {
            MailLog.d(MailLog.OAUTH, "google silent token requires reauthorization account=${MailLog.accountHint(email)}")
            throw OAuthReauthorizationRequiredException("Google authorization must be renewed")
        }
        val token = result.accessToken
            ?.takeIf(String::isNotBlank)
            ?: throw OAuthReauthorizationRequiredException("Google did not return an access token")
        MailLog.d(MailLog.OAUTH, "google silent token success account=${MailLog.accountHint(email)}")
        return token
    }

    private suspend fun toGrant(result: AuthorizationResult): OAuthGrant {
        val token = result.accessToken
            ?.takeIf(String::isNotBlank)
            ?: throw OAuthConfigurationException("Google did not return an access token")
        val signInAccount = result.toGoogleSignInAccount()
        val initialEmail = signInAccount?.email.orEmpty().trim()
        val initialName = signInAccount?.displayName.orEmpty().trim()
        val userInfo = if (initialEmail.contains('@')) {
            GoogleUserInfo(initialEmail, initialName)
        } else {
            fetchUserInfo(token)
        }
        return OAuthGrant(
            providerId = "gmail",
            email = userInfo.email,
            displayName = userInfo.name.ifBlank { userInfo.email.substringBefore('@') },
            providerAccountId = userInfo.email.lowercase(),
            accessToken = token,
        )
    }

    private suspend fun fetchUserInfo(accessToken: String): GoogleUserInfo = withContext(Dispatchers.IO) {
        val connection = (URI.create(USER_INFO_ENDPOINT).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Accept", "application/json")
        }
        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val payload = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                throw OAuthConfigurationException("Google user profile request failed ($status)")
            }
            val json = JSONObject(payload)
            val email = json.optString("email").trim()
            if (!email.contains('@')) {
                throw OAuthConfigurationException("Google did not return a mailbox address")
            }
            GoogleUserInfo(email, json.optString("name").trim())
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun <T> Task<T>.awaitValue(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { value ->
            if (continuation.isActive) continuation.resume(value)
        }
        addOnFailureListener { failure ->
            if (continuation.isActive) continuation.resumeWithException(failure)
        }
        addOnCanceledListener {
            if (continuation.isActive) {
                continuation.resumeWithException(OAuthAuthorizationCancelledException())
            }
        }
    }


    private suspend fun Task<*>.awaitCompletion(): Unit = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener {
            if (continuation.isActive) continuation.resume(Unit)
        }
        addOnFailureListener { failure ->
            if (continuation.isActive) continuation.resumeWithException(failure)
        }
        addOnCanceledListener {
            if (continuation.isActive) {
                continuation.resumeWithException(OAuthAuthorizationCancelledException())
            }
        }
    }

    private data class GoogleUserInfo(val email: String, val name: String)

    companion object {
        private const val GOOGLE_ACCOUNT_TYPE = "com.google"
        private const val USER_INFO_ENDPOINT = "https://www.googleapis.com/oauth2/v3/userinfo"
        const val GMAIL_SCOPE = "https://mail.google.com/"

        val SCOPES: List<Scope> = listOf(
            Scope(GMAIL_SCOPE),
            Scope("openid"),
            Scope("email"),
            Scope("profile"),
        )
    }
}

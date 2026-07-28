package com.bond.mail.ui.components

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bond.mail.ui.i18n.tr
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

@Composable
fun BiometricGate(enabled: Boolean, content: @Composable () -> Unit) {
    val activity = LocalContext.current as FragmentActivity
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
    val authenticationAvailable = remember(activity) {
        BiometricManager.from(activity).canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS
    }
    var unlocked by remember { mutableStateOf(!enabled || !authenticationAvailable) }
    var requestUnlock by remember { mutableStateOf(enabled && authenticationAvailable) }
    val appName = tr("app_name")
    val unlockMailbox = tr("unlock_mailbox")

    DisposableEffect(enabled, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (enabled && event == Lifecycle.Event.ON_STOP) unlocked = false
            if (enabled && event == Lifecycle.Event.ON_START && !unlocked) requestUnlock = true
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    fun prompt() {
        val biometric = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                unlocked = true
                requestUnlock = false
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                requestUnlock = false
            }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(appName)
            .setSubtitle(unlockMailbox)
            .setAllowedAuthenticators(authenticators)
            .build()
        biometric.authenticate(info)
    }

    LaunchedEffect(enabled, authenticationAvailable) {
        unlocked = !enabled || !authenticationAvailable
        requestUnlock = enabled && authenticationAvailable
    }

    LaunchedEffect(requestUnlock) {
        if (enabled && authenticationAvailable && !unlocked && requestUnlock) prompt()
    }

    if (unlocked) content() else Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Default.Fingerprint, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(tr("mail_locked"), style = MaterialTheme.typography.titleLarge)
        Button(onClick = { requestUnlock = true }) { Text(tr("unlock")) }
    }
}

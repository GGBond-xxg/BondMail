package com.bond.mail.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bond.mail.BuildConfig
import com.bond.mail.R
import com.bond.mail.data.settings.UiStyle
import com.bond.mail.ui.i18n.tr
import com.bond.mail.ui.motion.rememberBondPressInteraction
import com.bond.mail.ui.motion.rememberBondPressResetter
import com.bond.mail.ui.theme.LocalUiStyle
import com.bond.mail.ui.theme.BondIconButton as IconButton
import com.bond.mail.ui.theme.MiuixActionSetting
import com.bond.mail.ui.theme.MiuixSettingsDivider
import com.bond.mail.ui.theme.bondSurfaces
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar

private const val PROJECT_HOME_URL = "https://github.com/GGBond-xxg/BondMail"

@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onOpenSourceLicenses: () -> Unit,
    onOpenAppLicense: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
) {
    val context = LocalContext.current
    val externalLinkOpenFailed = tr("external_link_open_failed")
    AboutPage(
        title = tr("about"),
        onBack = onBack,
    ) {
        item { AboutHeroCard() }

        item {
            AboutCard {
                Text(
                    text = tr("project_intro"),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = tr("project_intro_body"),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }

        item {
            AboutCard {
                Text(
                    text = tr("more"),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                AboutActionRow(
                    icon = Icons.Default.Code,
                    title = tr("open_source_licenses"),
                    subtitle = tr("open_source_licenses_desc"),
                    onClick = onOpenSourceLicenses,
                )
                AboutDivider()
                AboutActionRow(
                    icon = Icons.Default.Description,
                    title = tr("app_license"),
                    subtitle = tr("app_license_desc"),
                    onClick = onOpenAppLicense,
                )
                AboutDivider()
                AboutActionRow(
                    icon = Icons.Default.PrivacyTip,
                    title = tr("privacy_policy"),
                    subtitle = tr("privacy_policy_desc"),
                    onClick = onOpenPrivacyPolicy,
                )
                AboutDivider()
                AboutActionRow(
                    icon = Icons.Default.OpenInNew,
                    title = tr("project_home"),
                    subtitle = tr("project_home_desc"),
                    external = true,
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PROJECT_HOME_URL)))
                        }.onFailure {
                            Toast.makeText(
                                context,
                                externalLinkOpenFailed,
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun AboutHeroCard() {
    if (LocalUiStyle.current == UiStyle.MIUIX) {
        MiuixCard(
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(horizontal = 24.dp, vertical = 28.dp),
        ) {
            AboutHeroContent()
        }
        return
    }

    Card(
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.tertiaryContainer,
                        ),
                    ),
                )
                .padding(horizontal = 24.dp, vertical = 28.dp),
            contentAlignment = Alignment.Center,
        ) {
            AboutHeroContent()
        }
    }
}

@Composable
private fun AboutHeroContent() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.bondmail_icon_color),
            contentDescription = null,
            modifier = Modifier.size(88.dp).clip(RoundedCornerShape(26.dp)),
        )
        Text(
            text = "BondMail",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 14.dp),
        )
        Text(
            text = tr("about_tagline"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.34f),
            modifier = Modifier.padding(top = 16.dp),
        ) {
            Text(
                text = "V${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
fun OpenSourceLicensesScreen(onBack: () -> Unit) {
    LegalPage(title = tr("open_source_licenses"), onBack = onBack) {
        Text(
            text = tr("open_source_statement"),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))
        val libraries = listOf(
            "AndroidX & Jetpack Compose" to "Apache License 2.0",
            "Kotlin & kotlinx.coroutines" to "Apache License 2.0",
            "MaterialKolor & Material Color Utilities" to "MIT / Apache License 2.0",
            "Microsoft Authentication Library (MSAL)" to "MIT License",
            "jsoup" to "MIT License",
            "Jakarta Mail for Android" to "CDDL 1.1 / GPL 2.0 with Classpath Exception",
            "CircularRevealSwitch (Compose adaptation)" to "MIT License",
            "Simple Icons" to "CC0 1.0",
            "Bootstrap Icons" to "MIT License",
        )
        libraries.forEachIndexed { index, (library, license) ->
            Column(modifier = Modifier.padding(vertical = 9.dp)) {
                Text(
                    text = library,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = license,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            if (index < libraries.lastIndex) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        Text(
            text = tr("open_source_notice"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 14.dp),
        )
    }
}

@Composable
fun AppLicenseScreen(onBack: () -> Unit) {
    LegalPage(title = tr("app_license"), onBack = onBack) {
        Text(
            text = MIT_LICENSE_TEXT,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    LegalPage(title = tr("privacy_policy"), onBack = onBack) {
        Text(
            text = tr("privacy_updated"),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        PrivacySection(tr("privacy_local_title"), tr("privacy_local_body"))
        PrivacySection(tr("privacy_network_title"), tr("privacy_network_body"))
        PrivacySection(tr("privacy_credentials_title"), tr("privacy_credentials_body"))
        PrivacySection(tr("privacy_remote_images_title"), tr("privacy_remote_images_body"))
        PrivacySection(tr("privacy_permissions_title"), tr("privacy_permissions_body"))
        PrivacySection(tr("privacy_delete_title"), tr("privacy_delete_body"))
        PrivacySection(tr("privacy_no_tracking_title"), tr("privacy_no_tracking_body"))
        PrivacySection(tr("privacy_contact_title"), tr("privacy_contact_body"))
    }
}

@Composable
private fun PrivacySection(title: String, body: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 20.dp),
    )
    Text(
        text = body,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun AboutPage(
    title: String,
    onBack: () -> Unit,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.bondSurfaces.page)
            .statusBarsPadding(),
    ) {
        PageTopBar(title, onBack)
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            content = content,
        )
    }
}

@Composable
private fun LegalPage(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    AboutPage(title = title, onBack = onBack) {
        item {
            AboutCard {
                Column(content = content)
            }
        }
    }
}

@Composable
private fun PageTopBar(title: String, onBack: () -> Unit) {
    if (LocalUiStyle.current == UiStyle.MIUIX) {
        MiuixTopAppBar(
            title = title,
            largeTitle = title,
            defaultWindowInsetsPadding = false,
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("back"))
                }
            },
        )
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("back"))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

@Composable
private fun AboutCard(content: @Composable ColumnScope.() -> Unit) {
    if (LocalUiStyle.current == UiStyle.MIUIX) {
        MiuixCard(
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(18.dp),
            content = content,
        )
        return
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
        ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.bondSurfaces.content),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            content = content,
        )
    }
}

@Composable
private fun AboutActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    external: Boolean = false,
    onClick: () -> Unit,
) {
    if (LocalUiStyle.current == UiStyle.MIUIX) {
        MiuixActionSetting(
            icon = icon,
            title = title,
            summary = subtitle,
            onClick = onClick,
        )
        return
    }
    val pressResetter = rememberBondPressResetter()
    key(pressResetter.epoch) {
        val interactionSource = rememberBondPressInteraction()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .clickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    onClick = { pressResetter.resetThen(onClick) },
                )
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(46.dp),
                shape = RoundedCornerShape(15.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.padding(11.dp),
                )
            }
            Column(modifier = Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Icon(
                imageVector = if (external) Icons.Default.OpenInNew
                else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AboutDivider() {
    if (LocalUiStyle.current == UiStyle.MIUIX) {
        MiuixSettingsDivider()
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(60.dp))
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

private val MIT_LICENSE_TEXT = """
MIT License

Copyright (c) 2026 GGBond-xxg

Permission is hereby granted, free of charge, to any person obtaining a copy of
this software and associated documentation files (the "Software"), to deal in
the Software without restriction, including without limitation the rights to
use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
the Software, and to permit persons to whom the Software is furnished to do so,
subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
""".trimIndent()

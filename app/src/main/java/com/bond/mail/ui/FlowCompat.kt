package com.bond.mail.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bond.mail.data.settings.AppSettings
import kotlinx.coroutines.flow.Flow

@Composable
fun Flow<AppSettings>.collectAsStateWithLifecycleCompat(): State<AppSettings> = collectAsStateWithLifecycle(initialValue = AppSettings())

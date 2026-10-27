@file:OptIn(ExperimentalMaterial3Api::class)

package com.gululu.aamediamate.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gululu.aamediamate.R
import com.gululu.aamediamate.SettingsManager
import com.gululu.aamediamate.models.LanguageOption
import com.gululu.aamediamate.models.RomanizeLanguage

@Composable
fun LyricsSettingsScreen(
    onBack: () -> Unit,
    onNavigateToProviders: () -> Unit,
    onNavigateToCleanupRules: () -> Unit,
    onNavigateToArtistNameReplacements: () -> Unit,
) {
    BackHandler {
        onBack()
    }

    val context = LocalContext.current
    var lyricsEnabled by remember { mutableStateOf(SettingsManager.getLyricsEnabled(context)) }
    var simplifyChinese by remember { mutableStateOf(SettingsManager.getSimplifyEnabled(context)) }
    var romanizeEnabled by remember { mutableStateOf(SettingsManager.getRomanizeEnabled(context)) }
    var romanizeLanguages by remember { mutableStateOf(SettingsManager.getRomanizeLanguages(context)) }
    var lyricsTimingOffset by remember { mutableStateOf(SettingsManager.getLyricsTimingOffset(context)) }
    var showLyricsConfirmationDialog by remember { mutableStateOf(false) }

    val selectedLanguage = remember {
        listOf(
            LanguageOption.SYSTEM,
            LanguageOption.ENGLISH,
            LanguageOption.SIMPLIFIED_CHINESE,
            LanguageOption.TRADITIONAL_CHINESE
        ).firstOrNull {
            "${it.language}_${it.country}" == SettingsManager.getLanguagePreference(context)
        } ?: LanguageOption.SYSTEM
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.lyrics_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = stringResource(id = R.string.show_lyrics), modifier = Modifier.weight(1f))
                Switch(
                    checked = lyricsEnabled,
                    onCheckedChange = { checked ->
                        if (checked) {
                            showLyricsConfirmationDialog = true
                        } else {
                            lyricsEnabled = false
                            SettingsManager.setLyricsEnabled(context, false)
                        }
                    }
                )
            }

            if (selectedLanguage == LanguageOption.SIMPLIFIED_CHINESE) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = stringResource(id = R.string.simplify_chinese), modifier = Modifier.weight(1f))
                    Switch(
                        checked = simplifyChinese,
                        enabled = lyricsEnabled,
                        onCheckedChange = {
                            simplifyChinese = it
                            SettingsManager.setSimplifyEnabled(context, it)
                        }
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = stringResource(id = R.string.lyrics_romanize), modifier = Modifier.weight(1f))
                Switch(
                    checked = romanizeEnabled,
                    enabled = lyricsEnabled,
                    onCheckedChange = {
                        romanizeEnabled = it
                        SettingsManager.setRomanizeEnabled(context, it)
                    }
                )
            }

            if (romanizeEnabled) {
                RomanizeLanguage.entries.forEach { language ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = when (language) {
                                RomanizeLanguage.JAPANESE -> stringResource(R.string.language_japanese)
                                RomanizeLanguage.KOREAN -> stringResource(R.string.language_korean)
                            },
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = language in romanizeLanguages,
                            onCheckedChange = { checked ->
                                romanizeLanguages = if (checked) {
                                    romanizeLanguages + language
                                } else {
                                    romanizeLanguages - language
                                }
                                SettingsManager.setRomanizeLanguages(context, romanizeLanguages)
                            }
                        )
                    }
                }
            }

            Text(
                text = stringResource(R.string.lyrics_timing_offset_label, lyricsTimingOffset / 1000f),
                style = MaterialTheme.typography.bodyMedium,
                color = if (lyricsEnabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                }
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    enabled = lyricsEnabled,
                    onClick = {
                        val newOffset = (lyricsTimingOffset - 500).coerceIn(-10000, 10000)
                        lyricsTimingOffset = newOffset
                        SettingsManager.setLyricsTimingOffset(context, newOffset)
                    }
                ) { Text("-0.5s") }
                TextButton(
                    enabled = lyricsEnabled,
                    onClick = {
                        lyricsTimingOffset = 0
                        SettingsManager.setLyricsTimingOffset(context, 0)
                    }
                ) { Text(stringResource(R.string.reset)) }
                OutlinedButton(
                    enabled = lyricsEnabled,
                    onClick = {
                        val newOffset = (lyricsTimingOffset + 500).coerceIn(-10000, 10000)
                        lyricsTimingOffset = newOffset
                        SettingsManager.setLyricsTimingOffset(context, newOffset)
                    }
                ) { Text("+0.5s") }
            }

            OutlinedButton(
                onClick = onNavigateToProviders,
                enabled = lyricsEnabled,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.lyrics_providers_title))
            }

            OutlinedButton(
                onClick = onNavigateToCleanupRules,
                enabled = lyricsEnabled,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.lyrics_cleanup_rules_title))
            }

            OutlinedButton(
                onClick = onNavigateToArtistNameReplacements,
                enabled = lyricsEnabled,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.artist_name_replacements_title))
            }

            if (showLyricsConfirmationDialog) {
                AlertDialog(
                    onDismissRequest = { showLyricsConfirmationDialog = false },
                    title = { Text(stringResource(id = R.string.lyrics_enable_warning_title)) },
                    text = { Text(stringResource(id = R.string.lyrics_enable_warning_text)) },
                    confirmButton = {
                        TextButton(onClick = {
                            lyricsEnabled = true
                            SettingsManager.setLyricsEnabled(context, true)
                            showLyricsConfirmationDialog = false
                        }) {
                            Text(stringResource(id = R.string.lyrics_enable_confirm_button))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showLyricsConfirmationDialog = false }) {
                            Text(stringResource(id = R.string.cancel))
                        }
                    }
                )
            }
        }
    }
}

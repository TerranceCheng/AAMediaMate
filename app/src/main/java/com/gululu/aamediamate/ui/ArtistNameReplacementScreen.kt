@file:OptIn(ExperimentalMaterial3Api::class)

package com.gululu.aamediamate.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
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
import com.gululu.aamediamate.data.ArtistNameReplacement
import java.util.UUID

@Composable
fun ArtistNameReplacementScreen(
    onBack: () -> Unit,
) {
    BackHandler {
        onBack()
    }

    val context = LocalContext.current
    var replacements by remember {
        mutableStateOf(SettingsManager.getArtistNameReplacements(context).toMutableList())
    }
    var editorReplacement by remember { mutableStateOf<ArtistNameReplacement?>(null) }
    var editingIndex by remember { mutableStateOf<Int?>(null) }

    fun persist(updatedReplacements: List<ArtistNameReplacement>) {
        replacements = updatedReplacements.toMutableList()
        SettingsManager.saveArtistNameReplacements(context, updatedReplacements)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.artist_name_replacements_title)) },
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
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.artist_name_replacements_subtitle),
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.artist_name_replacements_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        editorReplacement = ArtistNameReplacement(
                            id = UUID.randomUUID().toString(),
                            englishName = "",
                            chineseName = "",
                            isEnabled = true
                        )
                        editingIndex = null
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.artist_name_replacement_add))
                }
            }

            itemsIndexed(
                items = replacements,
                key = { _, replacement -> replacement.id }
            ) { index, replacement ->
                ArtistNameReplacementCard(
                    replacement = replacement,
                    onToggleEnabled = { enabled ->
                        val updated = replacements.toMutableList()
                        updated[index] = updated[index].copy(isEnabled = enabled)
                        persist(updated)
                    },
                    onEdit = {
                        editorReplacement = replacement.copy()
                        editingIndex = index
                    },
                    onDelete = {
                        val updated = replacements.toMutableList()
                        updated.removeAt(index)
                        persist(updated)
                    }
                )
            }

            if (replacements.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.artist_name_replacements_empty),
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }

    if (editorReplacement != null) {
        ArtistNameReplacementEditorDialog(
            replacement = editorReplacement!!,
            onDismiss = {
                editorReplacement = null
                editingIndex = null
            },
            onSave = { savedReplacement ->
                val updated = replacements.toMutableList()
                val index = editingIndex
                if (index == null) {
                    updated.add(savedReplacement)
                } else {
                    updated[index] = savedReplacement
                }
                persist(updated)
                editorReplacement = null
                editingIndex = null
                Toast.makeText(context, context.getString(R.string.artist_name_replacement_saved), Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
private fun ArtistNameReplacementCard(
    replacement: ArtistNameReplacement,
    onToggleEnabled: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (replacement.isEnabled) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = replacement.englishName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = replacement.chineseName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Switch(
                    checked = replacement.isEnabled,
                    onCheckedChange = onToggleEnabled
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = stringResource(R.string.artist_name_replacement_edit),
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    Text(stringResource(R.string.artist_name_replacement_edit))
                }

                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.artist_name_replacement_delete),
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    Text(stringResource(R.string.artist_name_replacement_delete))
                }
            }
        }
    }
}

@Composable
private fun ArtistNameReplacementEditorDialog(
    replacement: ArtistNameReplacement,
    onDismiss: () -> Unit,
    onSave: (ArtistNameReplacement) -> Unit,
) {
    var englishName by remember { mutableStateOf(replacement.englishName) }
    var chineseName by remember { mutableStateOf(replacement.chineseName) }
    var isEnabled by remember { mutableStateOf(replacement.isEnabled) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.artist_name_replacement_edit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = englishName,
                    onValueChange = { englishName = it },
                    label = { Text(stringResource(R.string.artist_name_replacement_english_name)) },
                    placeholder = { Text(stringResource(R.string.artist_name_replacement_english_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = chineseName,
                    onValueChange = { chineseName = it },
                    label = { Text(stringResource(R.string.artist_name_replacement_chinese_name)) },
                    placeholder = { Text(stringResource(R.string.artist_name_replacement_chinese_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.artist_name_replacement_enabled))
                    Spacer(modifier = Modifier.weight(1f))
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = { isEnabled = it }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (englishName.isNotBlank() && chineseName.isNotBlank()) {
                        onSave(
                            replacement.copy(
                                englishName = englishName,
                                chineseName = chineseName,
                                isEnabled = isEnabled
                            )
                        )
                    }
                }
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

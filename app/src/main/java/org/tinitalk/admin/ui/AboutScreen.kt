package org.tinitalk.admin.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.tinitalk.admin.BuildConfig
import org.tinitalk.admin.R
import org.tinitalk.admin.i18n.AppLanguage
import org.tinitalk.admin.i18n.appString

@Composable
internal fun AboutScreen(onBack: () -> Unit) {
    var languagePicker by rememberSaveable { mutableStateOf(false) }
    BackHandler(onBack = onBack)
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 16.dp)) {
            ScreenHeader(appString(R.string.text_about_102), onBack)
            Column(
                Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    Box(Modifier.size(84.dp).clip(RoundedCornerShape(22.dp)), contentAlignment = Alignment.Center) {
                        Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.requiredSize(144.dp))
                    }
                    Text("TiniTalk Admin", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                }
                Surface(
                    onClick = { languagePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text(appString(R.string.language_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            AppLanguage.supported[AppLanguage.selection] ?: AppLanguage.systemLanguageLabel(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)),
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(appString(R.string.text_application_103), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            listOf(
                                appString(R.string.text_version_104) to BuildConfig.VERSION_NAME,
                                appString(R.string.text_commit_105) to BuildConfig.COMMIT_HASH,
                            ).forEach { (label, value) ->
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (languagePicker) {
        AlertDialog(
            onDismissRequest = { languagePicker = false },
            title = { Text(appString(R.string.language_title)) },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()).selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    (listOf("" to AppLanguage.systemLanguageLabel()) + AppLanguage.sortedLanguages()).forEach { (tag, label) ->
                        val selected = AppLanguage.selection == tag
                        val shape = RoundedCornerShape(16.dp)
                        Surface(
                            shape = shape,
                            color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)) else null,
                            modifier = Modifier.fillMaxWidth().clip(shape).selectable(
                                selected = selected,
                                role = Role.RadioButton,
                                onClick = { languagePicker = false; AppLanguage.select(tag) },
                            ),
                        ) {
                            Row(
                                Modifier.heightIn(min = 56.dp).padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Box(Modifier.width(32.dp).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
                                    if (tag.isEmpty()) Icon(
                                        painterResource(R.drawable.ic_language_device), null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(26.dp),
                                    ) else Text(languageFlag(tag), fontSize = 26.sp)
                                }
                                Text(
                                    label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                )
                                Box(Modifier.size(22.dp)) {
                                    if (selected) Icon(painterResource(R.drawable.ic_language_selected), null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { languagePicker = false }) { Text(appString(R.string.text_cancel_8)) }
            },
        )
    }
}

private fun languageFlag(tag: String): String = when (tag) {
    "en" -> "🇬🇧"
    "ru" -> "🇷🇺"
    "pl" -> "🇵🇱"
    "de" -> "🇩🇪"
    "es" -> "🇪🇸"
    "fr" -> "🇫🇷"
    "pt" -> "🇧🇷"
    "it" -> "🇮🇹"
    "tr" -> "🇹🇷"
    "ja" -> "🇯🇵"
    "ko" -> "🇰🇷"
    "zh-Hans" -> "🇨🇳"
    else -> "🌐"
}

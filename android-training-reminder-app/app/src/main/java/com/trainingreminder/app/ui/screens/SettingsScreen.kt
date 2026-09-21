package com.trainingreminder.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.trainingreminder.app.R
import com.trainingreminder.app.data.ReminderSettings

@Composable
fun SettingsScreen(
    settings: ReminderSettings,
    onLeadMinutesChange: (Int) -> Unit,
    onKeywordFilterChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var leadMinutesText by remember(settings.leadMinutes) {
        mutableStateOf(settings.leadMinutes.toString())
    }
    var keywordText by remember(settings.keywordFilter) {
        mutableStateOf(settings.keywordFilter)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge)

        OutlinedTextField(
            value = leadMinutesText,
            onValueChange = { value ->
                leadMinutesText = value.filter { it.isDigit() }
                leadMinutesText.toIntOrNull()?.let(onLeadMinutesChange)
            },
            label = { Text(stringResource(R.string.settings_lead_time)) },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = KeyboardType.Number,
            ),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = keywordText,
            onValueChange = { keywordText = it },
            label = { Text(stringResource(R.string.settings_keyword_filter)) },
            placeholder = { Text(stringResource(R.string.settings_keyword_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    LaunchedEffect(keywordText) {
        kotlinx.coroutines.delay(500)
        if (keywordText != settings.keywordFilter) onKeywordFilterChange(keywordText)
    }
}

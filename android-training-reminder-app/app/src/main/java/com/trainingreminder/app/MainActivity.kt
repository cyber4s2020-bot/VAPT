package com.trainingreminder.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.trainingreminder.app.ui.MainViewModel
import com.trainingreminder.app.ui.screens.EventListScreen
import com.trainingreminder.app.ui.screens.SettingsScreen
import com.trainingreminder.app.ui.theme.TrainingReminderTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            TrainingReminderTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    var showSettings by rememberSaveable { mutableStateOf(false) }
                    var hasCalendarPermission by remember {
                        mutableStateOf(
                            ContextCompat.checkSelfPermission(
                                this,
                                Manifest.permission.READ_CALENDAR,
                            ) == PackageManager.PERMISSION_GRANTED,
                        )
                    }

                    val permissionLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestMultiplePermissions(),
                    ) { results ->
                        hasCalendarPermission =
                            results[Manifest.permission.READ_CALENDAR] == true
                        if (hasCalendarPermission) viewModel.onPermissionsGranted()
                    }

                    LaunchedEffect(hasCalendarPermission) {
                        if (hasCalendarPermission) viewModel.onPermissionsGranted()
                    }

                    val events by viewModel.upcomingEvents.collectAsState()
                    val scheduled by viewModel.scheduledInstanceIds.collectAsState()
                    val settings by viewModel.settings.collectAsState()

                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = { Text(stringResource(R.string.app_name)) },
                                navigationIcon = {
                                    if (showSettings) {
                                        IconButton(onClick = { showSettings = false }) {
                                            Icon(Icons.Filled.ArrowBack, contentDescription = null)
                                        }
                                    }
                                },
                                actions = {
                                    if (!showSettings) {
                                        IconButton(onClick = { viewModel.refreshNow() }) {
                                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                                        }
                                        IconButton(onClick = { showSettings = true }) {
                                            Icon(Icons.Filled.Settings, contentDescription = "Settings")
                                        }
                                    }
                                },
                            )
                        },
                    ) { padding ->
                        Column(modifier = Modifier.padding(padding)) {
                            when {
                                !hasCalendarPermission -> PermissionRequestScreen(
                                    onRequestClick = {
                                        val permissions = mutableListOf(Manifest.permission.READ_CALENDAR)
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            permissions += Manifest.permission.POST_NOTIFICATIONS
                                        }
                                        permissionLauncher.launch(permissions.toTypedArray())
                                    },
                                )
                                showSettings -> SettingsScreen(
                                    settings = settings,
                                    onLeadMinutesChange = viewModel::setLeadMinutes,
                                    onKeywordFilterChange = viewModel::setKeywordFilter,
                                )
                                else -> EventListScreen(
                                    events = events,
                                    scheduledInstanceIds = scheduled,
                                    leadMinutes = settings.leadMinutes,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionRequestScreen(onRequestClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.permission_calendar_rationale),
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRequestClick) {
            Text(stringResource(R.string.grant_permission))
        }
    }
}

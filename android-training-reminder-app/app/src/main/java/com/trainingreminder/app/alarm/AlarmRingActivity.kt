package com.trainingreminder.app.alarm

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trainingreminder.app.R
import com.trainingreminder.app.ui.theme.TrainingReminderTheme

/**
 * Full-screen alarm UI shown over the lock screen when a training-session
 * reminder fires, the same role the built-in Clock app's alarm screen plays.
 */
class AlarmRingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            )
        }

        val title = intent.getStringExtra(EXTRA_EVENT_TITLE) ?: getString(R.string.app_name)
        val location = intent.getStringExtra(EXTRA_EVENT_LOCATION)
        val leadMinutes = intent.getIntExtra(EXTRA_LEAD_MINUTES, 15)

        setContent {
            TrainingReminderTheme {
                AlarmScreen(
                    title = title,
                    location = location,
                    leadMinutes = leadMinutes,
                    onDismiss = { sendAction(ACTION_DISMISS) },
                    onSnooze = { sendAction(ACTION_SNOOZE) },
                )
            }
        }
    }

    private fun sendAction(action: String) {
        val serviceIntent = Intent(this, AlarmRingtoneService::class.java).setAction(action)
        startService(serviceIntent)
        finish()
    }
}

@Composable
private fun AlarmScreen(
    title: String,
    location: String?,
    leadMinutes: Int,
    onDismiss: () -> Unit,
    onSnooze: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF101423)),
        color = Color(0xFF101423),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Alarm,
                contentDescription = null,
                tint = Color(0xFFFF7A45),
                modifier = Modifier.height(64.dp),
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = "Training session in $leadMinutes min",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 16.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = title,
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
            )
            if (!location.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(text = location, color = Color.White.copy(alpha = 0.6f), fontSize = 16.sp)
            }

            Spacer(Modifier.height(48.dp))

            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF7A45)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.dismiss))
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onSnooze,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.snooze), color = Color.White)
            }
        }
    }
}

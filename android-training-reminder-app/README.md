# Training Reminder

An Android app that reads your **Google Calendar** events on-device and
automatically sets an **alarm** (not just a notification) that rings 15
minutes before each training session/event starts — so you get the same
wake-you-up alert as the stock Clock app, driven by your calendar.

## How it works

Android doesn't require a separate Google API/OAuth integration to read a
user's Google Calendar: once the user has added their Google account to the
device and turned on Calendar sync, the **Calendar Provider**
(`CalendarContract`) on the device is kept in sync with Google Calendar by
Google's own sync adapter. This app reads that provider directly, so:

- No Google Cloud project, API keys, or OAuth consent screen are needed.
- It works for every calendar synced to the device (Google, plus Outlook/
  Samsung/etc. if the user has those too), not only Google's.
- Everything stays on-device; the app performs no network requests at all.

### Pipeline

1. **`CalendarRepository`** queries `CalendarContract.Instances` for events
   in the next 30 days (recurring events are already expanded into
   individual occurrences), optionally filtered by a keyword (e.g.
   "training") set in Settings.
2. **`CalendarSyncWorker`** (a `WorkManager` periodic job, plus a
   `ContentObserver` in `TrainingReminderApp` for near-instant updates while
   the app is running, plus a run on boot) diffs those events against a
   local Room table (`ScheduledReminder`) of alarms already scheduled, and
   for every new/changed one schedules an alarm.
3. **`AlarmScheduler`** uses `AlarmManager.setAlarmClock(...)` — the same
   API the platform Clock app uses — to schedule the reminder for
   `event.startTime - leadMinutes` (15 minutes by default). This means:
   - No special "exact alarm" permission dance is needed.
   - The alarm is exempt from Doze/battery optimization, so it fires on
     time even if the phone has been idle.
   - It survives whatever WorkManager's actual scan cadence is, because the
     alarm is registered with the OS well ahead of time — the periodic sync
     just keeps the schedule up to date, it isn't what fires the alarm.
4. When the alarm fires, **`AlarmReceiver`** starts **`AlarmRingtoneService`**,
   a foreground service which plays the device's alarm tone on a loop,
   vibrates, and posts a full-screen, high-priority notification. That
   notification's full-screen intent launches **`AlarmRingActivity`**, which
   shows the event title/location over the lock screen with **Dismiss** and
   **Snooze (5 min)** actions — a real alarm experience, not just a silent
   notification.
5. **`BootCompletedReceiver`** re-runs the sync on device boot, since
   `AlarmManager` alarms are cleared on reboot.

### Screens

- **Event list** — upcoming matching events, with an alarm-clock badge on
  ones that already have a reminder scheduled, pull-to-refresh via the
  toolbar refresh icon.
- **Settings** — reminder lead time in minutes (default `15`) and an
  optional keyword filter so only events like "Training: ..." get an alarm
  instead of every calendar entry.

## Project layout

```
app/src/main/java/com/trainingreminder/app/
├── MainActivity.kt            # Compose UI shell, permission flow
├── TrainingReminderApp.kt     # Application: notif channels, WorkManager, live sync
├── data/                      # CalendarContract access, Room DB, DataStore prefs
├── alarm/                     # AlarmManager scheduling + the ringing/alarm UI
├── sync/                      # WorkManager periodic calendar → alarm sync
├── notification/              # Notification channel setup
└── ui/                        # ViewModel + Compose screens/theme
```

Kotlin + Jetpack Compose (Material 3), Room, DataStore, WorkManager.
`minSdk 26`, `targetSdk 34`.

## Permissions

| Permission | Why |
|---|---|
| `READ_CALENDAR` | Read events from the on-device Calendar Provider (synced from Google Calendar). |
| `POST_NOTIFICATIONS` | Show the alarm notification (Android 13+ requires this to be requested at runtime). |
| `VIBRATE`, `WAKE_LOCK`, `USE_FULL_SCREEN_INTENT` | Ring/vibrate and show the full-screen alarm UI, including over the lock screen. |
| `RECEIVE_BOOT_COMPLETED` | Re-schedule alarms after a reboot. |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` | Run the alarm-ringing service reliably while it plays the tone. |

The app requests `READ_CALENDAR` (and `POST_NOTIFICATIONS` on Android 13+)
at runtime on first launch; nothing else needs a user prompt because
`setAlarmClock` doesn't require the separate "schedule exact alarms" grant.

## Building

Requires Android Studio (Koala+) or a local Android SDK with `compileSdk 34`.

The Gradle wrapper scripts (`gradlew`/`gradlew.bat`) are included, but the
binary `gradle/wrapper/gradle-wrapper.jar` is intentionally **not** checked
in (kept out of the diff, like many projects do). Opening the project in
Android Studio regenerates it automatically; from the command line, generate
it once with a local Gradle install before the first build:

```
gradle wrapper --gradle-version 8.7
./gradlew assembleDebug
```

On first run Gradle will
download the Android Gradle Plugin and dependencies from Google's/Maven
Central's repositories, so an internet connection reachable to
`dl.google.com` and `repo.maven.apache.org` is required — this repo's own
sandboxed build environment blocks `dl.google.com`, so the project could not
be compiled/tested end-to-end from inside it. The code was written and
reviewed carefully (brace/paren balance checked, imports/APIs cross-checked
against the AndroidX/Compose versions pinned in `app/build.gradle.kts`), but
you should run `./gradlew assembleDebug` locally before relying on it, and
file an issue with the compiler output if anything doesn't line up.

## Trying it out

1. Open the project in Android Studio, or run `./gradlew installDebug` with
   a device/emulator connected that has a Google account signed in with
   Calendar sync on.
2. Launch the app and grant the Calendar (and notification) permission.
3. Create a Google Calendar event starting in the next hour or so (with
   "training" in the title if you've set a keyword filter).
4. It should show up in the event list within a few seconds (or tap the
   refresh icon) with the alarm-set badge.
5. 15 minutes before the event (or your configured lead time), the phone
   will ring/vibrate and show the full-screen alarm screen, even if locked.

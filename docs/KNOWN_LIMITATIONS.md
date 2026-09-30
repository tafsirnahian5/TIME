# Known limitations

What T!ME does not do yet, or does only partly. Each item is either on the roadmap or a deliberate decision.

## Still to come

- **On-device verification of the later phases.** Features from Phase 9 onwards (Bangla and automatic backups included) are covered by unit tests and written instrumented tests, but most have not yet been checked on a phone. The full on-device pass and the instrumented test run are scheduled before the first signed release.
- **A day-start time** (work after midnight counting towards the previous day) is not configurable.

## Partial by design

- **Bangla** covers every screen, notification and widget. The launcher and widget-picker names come from Android, so they follow the phone's own language, and the Settings search also matches English words.
- **Alarm-style timers**: they ring with your chosen tone, rising volume, vibration and optional spoken alerts until answered. Without Android's exact-alarm permission the system may refuse to start the ringing service from the background; the alarm then rings as a plain notification sound, up to about a minute late. On Android 14 and later the system may also refuse full-screen alerts, and Focus › Timers says so.
- **Widgets** use the Violet colours whatever accent you chose, because home-screen widgets can't read the app theme.
- **Tablet layout**: wide screens get a side navigation rail, but screens are the phone layouts stretched. There is no two-pane list/detail view.
- **Drag and drop** works on the Board, not on the calendar's time grid (use a block's menu there).
- **Repeating tasks** copy only their first reserved block to each occurrence.
- **Transfers** can be deleted but not edited. Refunds and loan payments are managed from their own screens.
- **Language & region** changes how dates, times and amounts look. The time zone always follows the phone.
- **Sound and vibration** for timer alarms are chosen in Settings › Timers & focus. The plain-notification fallback uses Android's own channel settings, which that page also opens.

## Security scope

- The app lock protects access through T!ME. It does not encrypt the database file against a rooted device.
- Receipt files are stored in the app's private storage unencrypted. They are encrypted inside a password-protected backup.
- The PIN verifier is bound to this phone's Android Keystore, so a PIN is never carried over by a backup. Set it again after restoring on a new phone.

## Distribution

- The application id is still `com.example.timeapp`. It must change before any store listing. Changing it makes a separate app, so move data across with a backup and restore.
- Release builds are shrunk by R8 but never obfuscated, because stored data depends on the app's own names.

## Decided against

These are not coming: points, levels, scores or XP; quit or negative habits; a daily spending allowance; a subscriptions overview; a Habits tab; a wake-up alarm clock.

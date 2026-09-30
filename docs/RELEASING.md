# Releasing T!ME

## Before a release

1. `./gradlew :app:testDebugUnitTest` must pass.
2. With a phone connected, run `./gradlew connectedDebugAndroidTest`. This covers the migration chain from schema 13, the timer, money reconciliation, loans, undo, schedule blocks and password backups.
3. Check the main flows on a real phone: add a task, book time, record money, run a timer, back up and restore.
4. Update `versionCode` and `versionName` in `app/build.gradle.kts`, and add the release to `docs/CHANGELOG.md`.

## Signing

Release signing reads `keystore.properties` at the project root. The file is git-ignored and must never be committed:

```
storeFile=release.jks
storePassword=…
keyAlias=time
keyPassword=…
```

Without it, `./gradlew :app:assembleRelease` still builds, but unsigned (`app-release-unsigned.apk`).

To create a key once, and keep it somewhere safe outside the repository:

```
keytool -genkeypair -v -keystore release.jks -alias time -keyalg RSA -keysize 4096 -validity 10000
```

Losing the key means an update can't be installed over an existing copy. Users would then have to back up, uninstall and restore.

## Build

```
./gradlew :app:assembleRelease
```

R8 shrinks and optimises the release build but never renames anything (`app/proguard-rules.pro`). Enum names are stored in the database, settings and backups, so renaming them would break existing data.

After installing a release build on a phone, smoke-test these before handing it out: the lock screen, a backup and restore, a reminder firing, and the Focus notification.

# Apply BondMail v0.2.38.0

Upgrade baseline: `BondMail v0.2.37.0`.

This delivery is a complete source package rather than a standalone `git apply` patch. Extract `BondMail_Kotlin_v0.2.38.0_source.zip` into a new directory. Copy back only local OAuth identifiers or signing configuration that you intentionally keep outside source control.

Do not copy old `build/`, `.gradle/`, `.kotlin/`, `local.properties`, APK or AAB files into the new directory. The launcher now uses new resource names, and the prepared HTML cache key changes to `layout-v33`.

Compile on Windows:

```powershell
.\gradlew.bat --stop
.\gradlew.bat clean compileDebugKotlin assembleDebug assemblePerformance --no-daemon
```

Primary source changes:

- exact user-supplied colour and monochrome launcher assets plus new `ic_launcher_bondmail` resource names;
- foreground/manual synchronization consumes new-mail notification UIDs silently;
- background notification channel `new_mail_alerts_v3` is HIGH importance with sound and vibration;
- full-height loading body, 40dp circular progress, conservative short/long content hint and short-mail spring collapse;
- animated drawer account expansion/collapse and rotating chevron;
- `app/src/main/java/com/bond/mail/data/mail/ImapClient.kt` identification updated to `0.2.38.0`;
- `app/build.gradle.kts` updated to `versionCode 59` / `versionName 0.2.38.0`.

No Room migration is required: Room and MIME parser remain at v8. Android launchers can cache the previous icon even though the resource name changed; if necessary, remove the desktop shortcut or uninstall only the test build before reinstalling.

# BondMail v1.2.7

## Material 3 and MIUIX styles

- Added a persistent interface-style selector for switching between Material 3 and MIUIX without
  leaving the current screen.
- Added MIUIX-native grouped settings, selectors, buttons, switches, typography, and press
  feedback while retaining the Material 3 bottom navigation layout.
- Disabled Monet dynamic color while MIUIX is active and kept the Material theme preferences ready
  when switching back.
- Updated the About and account-related screens so the selected interface style remains consistent
  throughout the app.

## Settings and interaction polish

- Removed the fingerprint app-lock feature and its unused dependency.
- Simplified sync-frequency and remote-image rows by removing inconsistent helper text.
- Corrected selector popup spacing, alignment, click handling, and theme-switch responsiveness.
- Added regression tests for interface-style persistence and repeated UI switching.

## Version

- `versionCode = 129`
- `versionName = 1.2.7`

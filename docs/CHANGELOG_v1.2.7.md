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
- Fixed the bottom navigation so Mail can always be reopened after visiting Contacts or Settings.
- Added regression tests for interface-style persistence, repeated UI switching, and bottom-tab
  navigation.

## Contact brand icons

- Added offline sender icons for 23 travel, mobility, delivery, and payment services, including
  Qunar, Tongcheng Travel, VariFlight, Air China, Fliggy, DiDi, China Post, SF Express, and Alipay.
- Added Chinese and international sender-name/domain aliases for the new brands.
- Extended the local SVG renderer to support transforms, circles, ellipses, rectangles, polygons,
  and strokes.
- Added matcher and on-device asset-loading regression tests for every new icon.
- ANT Bank reuses the AiPay mark, and Fliggy coverage includes its production `alitrip.com`
  notification address.

## Version

- `versionCode = 129`
- `versionName = 1.2.7`

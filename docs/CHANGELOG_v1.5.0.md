# BondMail v1.5.0

## Navigation reliability

- Made mail, settings, account setup, and About forward navigation one-shot so rapid repeated taps
  cannot enqueue duplicate destinations.
- Rejected late touches from covered source pages while the custom cover transition keeps them
  composed underneath the active destination.
- Kept the navigation gate closed for the complete transition window and reopened it immediately
  when snapshot capture or navigation fails.
- Added unit coverage for repeated navigation requests, gate release, and inactive source routes.

## Interface polish

- Rounded MIUIX action-row press feedback so About's More entries no longer show a rectangular
  highlight outside their intended shape.
- Switched the external-link icon to its auto-mirrored variant for right-to-left layouts.

## Version

- `versionCode = 143`
- `versionName = 1.5.0`

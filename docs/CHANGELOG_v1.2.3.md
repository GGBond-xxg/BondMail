# BondMail v1.2.3

## Settings polish

- Language, list density, theme mode, sync interval, and remote-image policy now use one
  consistent dropdown interaction.
- Dropdown selectors and menus are right-aligned, use clearer selected-state color and check
  marks, and keep long translated labels readable.
- Appearance settings are ordered as list density, theme mode, and Monet dynamic color.

## Mail list presentation

- Newly synchronized mail now enters one row at a time with a real 90 ms stagger.
- Each arriving row expands, fades, and slides into the list instead of making the full batch
  occupy space in one frame.
- Removed the full-width top chrome shadow and large floating-dock shadow that became rectangular
  bands in light-mode mail transition snapshots.

## Push recovery

- Existing encrypted configurations using the retired `push.usdit.eu.cc` endpoint automatically
  migrate to `push.maili.eu.cc` without asking for the access key again.
- A custom Worker registration is refreshed directly at every process start instead of waiting for
  the bundled Firebase project's token callback.
- Returning to BondMail always performs one quiet reconciliation, providing a deterministic
  fallback when ColorOS delays FCM or periodic WorkManager delivery.
- The push settings page can change only the Worker domain while leaving the access-key field blank
  to reuse the key already encrypted by Android Keystore.

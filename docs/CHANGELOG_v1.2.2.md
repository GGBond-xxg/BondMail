# BondMail v1.2.2

## Independent CF FCM deployments

- CF FCM configuration now opens in its own secondary screen.
- Users enter an HTTPS Worker domain and its `pwd` access key; neither endpoint
  nor authorization is hardcoded into device registration.
- The Worker returns its Firebase client configuration only after access-key
  validation. BondMail creates a token for that deployment's Firebase project,
  so independently deployed Workers do not depend on BondMail's Firebase
  service account.
- Existing v1.2.1 installations migrate their encrypted key to the legacy
  `push.usdit.eu.cc` origin automatically until the user saves another domain.
- The domain and key are encrypted with Android Keystore. Empty configuration
  remains a supported state: mailbox login, manual refresh, foreground sync,
  and local WorkManager scheduling continue without CF FCM.

## Settings layout

- Sync frequency is now a single-line dropdown instead of wrapping chips.
- The main Settings screen shows one concise CF FCM row and status; domain,
  key, validation, and help text live on the dedicated page.
- Chinese, Traditional Chinese, and English strings use shorter descriptions.

## Self-hosting

- The Worker includes authenticated Firebase client configuration, D1
  migrations, an environment-neutral Wrangler example, and full deployment
  instructions.
- The BondMail README links to the standalone
  `GGBond-xxg/BondMail-Cloudflare-Push` repository.

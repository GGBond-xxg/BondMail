# BondMail v1.2.4

## Android e-mail integration

- BondMail now declares Android's full `APP_EMAIL` application category and handles `mailto:`
  links, allowing Android and ColorOS to include it in e-mail app choosers and default-mail
  candidate lists.
- Opening a `mailto:` request imports its To, Cc, Bcc, subject, and body fields into BondMail's
  composer.
- Sharing one or multiple files to an e-mail app now offers BondMail and imports the attachments.
- External compose requests work during both cold starts and an already-running singleTop
  activity, including while the biometric gate is active.

## Version

- `versionCode = 124`
- `versionName = 1.2.4`

# BondMail Push Worker

This Worker schedules authenticated Firebase Cloud Messaging data messages for BondMail.

## Required bindings

- D1 database binding: `DB`
- Secret: `FIREBASE_SERVICE_ACCOUNT_JSON`
- Secret: `pwd`

Never commit either secret. Configure them in Cloudflare Workers settings or with
`wrangler secret put`.

## Push access key

The `pwd` secret is the access key users must enter in BondMail under
Settings > CF FCM push key. BondMail encrypts the key with Android Keystore and
sends it only when registering the current FCM installation.

The Worker stores only a SHA-256 hash in D1. Scheduled pushes are sent only to
registrations whose hash matches the current `pwd` value. Changing `pwd`
immediately disables all older registrations; each authorized user must enter
the new value and tap Verify and enable again.

## Deploy

```sh
npm ci
npm run migrate:remote
npm run deploy
```

The public health check is `GET /health`. Device registration requires the
`X-BondMail-Push-Key` header. Do not expose registration tokens, installation
secrets, service-account JSON, or the push access key in logs.

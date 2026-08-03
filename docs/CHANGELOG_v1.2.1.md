# BondMail v1.2.1

## CF FCM push access control

- Settings now contains a password-style CF FCM push key field and clear
  Missing, Verifying, Verified, and Rejected states.
- The key is encrypted locally with Android Keystore and is never stored in
  DataStore or printed to Logcat.
- Device registration sends the key only to the configured BondMail Cloudflare
  Worker over HTTPS.
- The Worker validates registrations against its `pwd` secret and keeps only a
  SHA-256 hash in D1.
- Scheduled FCM delivery selects only registrations matching the current
  `pwd`. Rotating the Cloudflare secret immediately invalidates older
  registrations until users enter the new key.
- Invalid keys revoke the matching installation record without allowing one
  installation to remove another device.

## Validation

- Wrong-key registration returns HTTP 401.
- Correct-key registration succeeds and creates one authorized D1 device.
- Background FCM delivery starts and completes a `mode=push` mail sync.
- On the tested ColorOS device, swiping the app away still prevents a data FCM
  from waking the removed process. Leaving the app in the normal background
  works; this is a vendor background-policy limitation rather than a Worker
  authentication failure.

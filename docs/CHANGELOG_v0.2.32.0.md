# BondMail v0.2.32.0

- `versionCode`: `51`
- `versionName`: `0.2.32.0`
- Room database: `7`
- HTML prepared-document cache: `layout-v24`
- Upgrade baseline: `v0.2.31.1`

## Selection mode

Long-press selection now disables the home list pull-to-refresh nested-scroll connection. Entering selection mode also settles an in-progress pull offset back to zero, so a selection gesture cannot accidentally trigger a refresh.

The selection toolbar action order is now:

1. Select all / Deselect all
2. Delete
3. Mark read / Mark unread

The final action is contextual: when any selected message is unread it marks the selection read; when the whole selection is already read it marks the selection unread.

## Scroll to top

Home and Contacts reuse the existing circular floating-action component. After the list moves several rows away from the top, a bottom-right up-arrow enters with a short fade, scale and vertical motion. Tapping it uses `animateScrollToItem(0)`. The action also hides automatically when the user manually returns to the top.

The Home action is suppressed during selection and search so it does not compete with those modes.

## Message-detail reveal

The loading transition no longer exposes a rapidly appearing progress animation on ordinary fast opens.

- First presentation keeps the final page background stable while Chromium prepares and attaches the document.
- The committed WebView content fades in over 300 ms.
- A static skeleton is delayed for 450 ms and is only used when loading is genuinely slow; it contains no moving progress bar.
- A document that has already been presented during the process attaches directly after its first committed host frame and does not replay the reveal animation.
- The presented-document set is bounded to 96 keys and is cleared with the WebView pool.

## Attachment-message body loading

The recorded failure occurred after SMTP delivery, while opening a multipart message from Sent or Inbox. Pure text mail worked, which points to the interactive IMAP/MIME body path rather than message submission.

For an ordinary message whose advertised size is at most 8 MiB, interactive opening now performs one full `MESSAGE` fetch with `mail.imaps.peek=true` before parsing. This avoids many lazy MIME-section reads, which are significantly less reliable on some IMAP servers when a message contains attachments.

Additional safeguards:

- interactive body operations use 45-second read/write socket timeouts;
- if the normal parser fails or produces no displayable text/HTML, a complete raw RFC822 message is serialized into a size-limited buffer and reparsed locally;
- the raw fallback is capped at 20 MiB;
- larger messages continue using partial MIME access to avoid unbounded memory use;
- debug logs identify single-PEEK materialization and raw-parser fallback outcomes.

These changes do not download or display attachment bytes inside the HTML body unless the existing MIME parser needs referenced inline resources. Normal file attachments remain attachments; only the message body is made reliable.

## Unchanged behavior

- Room remains v7.
- HTML layout cache remains `layout-v24`.
- SMTP acceptance, Sent reconciliation, Draft synchronization and Message-ID deduplication are unchanged.
- Gmail/Microsoft OAuth and app-password credentials are unchanged.
- Predictive back and the existing provider-specific HTML layout rules are unchanged.

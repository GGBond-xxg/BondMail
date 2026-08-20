# BondMail v1.2.8

## Mail refresh and transitions

- Fixed manual refresh in All Mailboxes immediately after a cold start; an explicit pull now
  pre-empts a delayed silent recovery instead of being ignored.
- Kept the mailbox screen alive underneath the reader so predictive back previews current refresh
  motion and newly arrived messages rather than an old captured frame.
- Restored the cold-start mail-opening cover animation and removed the unnecessary bitmap readback
  before navigation.
- Fixed cancelled predictive-back gestures leaving a frozen reader layer that blocked later
  scrolling.

## Drawer and sender icons

- Simplified account rows with better left spacing, a larger avatar, long-press drag reordering, and
  a compact overflow menu for edit and delete actions.
- Refined the selected-account treatment in dark mode so its row remains distinct from the account
  avatar instead of merging into one solid block of color.
- Corrected the Alipay / ANT Bank avatar treatment and added bundled icons for Pixiv, Plasma One,
  SafePal, and 钱迹 with sender-name and domain matching.

## Interface polish

- Shortened the English new-mail notification prompt and kept both actions on one line on narrow
  screens and large font settings.
- Added matcher and on-device asset-loading coverage for the new sender icons.

## Version

- `versionCode = 130`
- `versionName = 1.2.8`

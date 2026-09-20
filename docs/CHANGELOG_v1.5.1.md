# BondMail v1.5.1

## Sender cover icons

- Added offline SVG cover icons for Instagram, Telegram, Facebook, QuickQ, giffgaff, Vodafone,
  Huobi/HTX, OKX, McDonald's, Charles Schwab, Firstrade, Grok, Holafly, Huatai Securities,
  RedteaGO, and Coolapk.
- Added a generic airplane cover for recognized airlines that do not have a dedicated bundled logo.
- Added a generic SIM-card cover for recognized mobile and eSIM providers, including SoSIM.
- Added missing offline Simple Icons for HSBC, Shopee, Shopify, and Zoom.

## Matching fixes

- Recognize China Unicom notices from `10010@wo.cn` and other official China Unicom domains.
- Added verified sender domains and display-name aliases for the new brand set.
- Domain rules now match only complete domains or their subdomains, preventing lookalike suffixes
  such as `two.cn` from being mistaken for `wo.cn`.
- Added JVM and on-device coverage for brand matching, category fallbacks, lookalike domains, and
  bundled SVG loading.

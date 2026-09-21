# BondMail v1.5.1.1

## Sender cover icons

- Added offline sender covers for Gitee, GitLab, GMX, Google, Alibaba Cloud, Ant Group, AOL, Arc,
  Avalanche, Baidu, Bento, Brave, Burton, Claude, Cloudflare, CMake, CNES, CNET, CNN, Codex,
  Continente, Dianping, DeepAI, DeepSeek, Docker, Dolby, Douban, Drupal, Duolingo, Gemini,
  LinkedIn, Messenger, MEXC, Microsoft Copilot, Patreon, VK, WhatsApp, Xiaomi MiMo, YouTube,
  GameBanana, and Git.
- Added a generic exchange cover for recognized trading platforms without a dedicated bundled mark.
- Removed embedded square and circular backgrounds from affected SVGs so sender covers keep the
  same colored circular treatment as the rest of the app.

## Matching and compatibility

- Added verified sender-domain and display-name aliases for the expanded brand set.
- Kept dedicated exchange mappings ahead of the generic exchange fallback, including MEXC's
  published `mexc.com`, `mexc.link`, and `mexc.sg` sender domains.
- Added polygon, polyline, and line support to the local SVG renderer.
- Normalized compact SVG arc flags before Android path parsing, fixing icons that previously failed
  to render on-device.
- Added JVM and on-device coverage for the new mappings, fallbacks, and bundled SVG assets.

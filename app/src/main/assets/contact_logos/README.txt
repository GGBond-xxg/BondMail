Contact logo SVG overrides
==========================

Place monochrome SVG files in this directory. The avatar resolver checks:

1. Full sender domain, for example: email-service.bybit.com.svg
2. Root domain, for example: bybit.com.svg
3. Brand key, for example: bybit.svg

Files here override the bundled Simple Icons files in the simpleicons subdirectory.
SVGs should include a viewBox and one or more <path d="..."> elements. Fill colors are ignored
because BondMail applies either Monet colors or the configured fixed brand colors at runtime.

Custom travel, mobility, delivery, and payment marks bundled here include Qunar, Tongcheng Travel,
VariFlight, Air China, Fliggy, Hostelworld, Airbnb, Hotels.com, Expedia, Booking.com, Trainline,
Rome2rio, Omio, Citymapper, Bolt, Cabify, DiDi, Lyft, Uber, China Post, SF Express, Alipay, and
Moovit. Spark Mail, ITGSA/Gold Standard Alliance, and Indonesian Immigration marks are also bundled.
The extended offline set also covers major banks, payment networks, hardware and semiconductor
vendors, vehicle makers, retail and entertainment services, including ABC, CCB, ICBC, CMB, Citi,
American Express, Mastercard, Visa, Alibaba, JD, Meituan, Pinduoduo, Huawei, Intel, AMD, Nvidia,
ASML, TSMC, Micron, SK Hynix, Apple ecosystem senders, BMW, Mercedes-Benz, Volkswagen, Toyota,
Tesla, Netflix, Disney, Spotify, Discord, Bilibili, Coolapk, PayPal, Walmart, Costco, and others.
The v1.5.1 set adds Instagram, Telegram, Facebook, QuickQ, giffgaff, Vodafone, Huobi/HTX, OKX,
McDonald's, Charles Schwab, Firstrade, Grok, Holafly, Huatai Securities, and RedteaGO. A generic
airplane mark covers recognized airlines without a dedicated logo, while a SIM-card mark covers
recognized mobile/eSIM providers such as SoSIM. China Unicom includes the official `wo.cn` sender
domain used by 10010 notices. HSBC, Shopee, Shopify, and Zoom fill previously detected asset gaps.
EastWest Bank and Logitech have dedicated marks. The generic bank mark covers otherwise recognized
banks and is also used for known bank senders whose dedicated mark is not bundled.
The v1.5.1.1 expanded offline set also includes Gitee, GitLab, GMX, Google, Alibaba and Alibaba Cloud,
Ant Group, AOL, Arc, Avalanche, Baidu, Bento, Brave, Burton, Claude, Cloudflare, CMake, CNES,
CNET, CNN, Codex, Continente, Dianping, DeepAI, DeepSeek, Docker, Dolby, Douban, Drupal,
Duolingo, Gemini, LinkedIn, Messenger, MEXC, Microsoft Copilot, Patreon, VK, WhatsApp,
Xiaomi MiMo, YouTube, GameBanana, and Git. A generic exchange mark covers recognized trading
platforms without their own bundled logo while dedicated exchange marks remain preferred.
Source filenames from the working-tree ICON folder are mapped to ASCII asset names here for Android
lookup. ANT Bank senders reuse the bundled Alipay/AiPay mark. All official `imigrasi.go.id` mailboxes
and their notification subdomains reuse the Indonesian Immigration mark.

The local renderer supports paths, circles, ellipses, rectangles, polygons, polylines, lines, SVG
transform matrices, and stroked marks. Logos remain fully offline and never trigger favicon or
network requests while scrolling.

Simple Icons source and usage:
https://simpleicons.org/
https://github.com/simple-icons/simple-icons

Bundled Simple Icons were refreshed from the official simple-icons npm package v16.21.0.

Contact logo SVG overrides
==========================

QQ, Gmail, and Yahoo overrides come from ICON/QQ.svg, ICON/Gmail.svg, and ICON/Yahoo.svg.
Yahoo's background circle is removed so the monochrome renderer preserves the white mark
against the avatar's purple background. The original files in ICON are unchanged.

Run the offline synchronization tool documented in tools/icon-sync/README.md to import
local ICON overrides and selected theSVG package resources. domains.json supplies additional
domain-to-asset mappings. Runtime source priority is this curated directory, local/, thesvg/, simpleicons/.
Curated marks have backgrounds removed for tinting. Raw ICON exports must never shadow them;
the synchronization tool removes its own duplicate copies when a curated root asset exists.

Place monochrome SVG files in this directory. Within each source the avatar resolver checks:

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

AIDef generic AI chip: user-supplied ICON/AIDef.svg (SVG Repo export).
The curated aidef.svg preserves the source geometry and puts evenodd on the path so both
Compose and HTML keep the AI lettering transparent while applying the current theme tint.
This generic mark is selected manually; existing dedicated AI brand logos stay unchanged.

Generic Wallet, Social, Gaming, Entertainment, Education, Food, Transport, Health, Jobs,
and Developer marks: user-supplied ICON/*.svg exports from SVG Repo. Source geometry is
preserved; inherited fill, stroke, and fill-rule are made explicit for the local renderer.
Original ICON files are not modified. These categories are selected manually.

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
Source filenames from the working-tree ICON folder are mapped to ASCII asset names here for Android
lookup. ANT Bank senders reuse the bundled Alipay/AiPay mark. All official `imigrasi.go.id` mailboxes
and their notification subdomains reuse the Indonesian Immigration mark.

The local renderer supports paths, circles, ellipses, rectangles, polygons, SVG transform matrices,
and stroked marks. Logos remain fully offline and never trigger favicon or network requests while
scrolling.

Simple Icons source and usage:
https://simpleicons.org/
https://github.com/simple-icons/simple-icons

Bundled Simple Icons were refreshed from the official simple-icons npm package v16.21.0.

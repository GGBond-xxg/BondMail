# v1.6.1.3 品牌图标目录

新增 201 个独立图标，完善 7 个已有品牌（Dropbox、Figma、Notion、Shopee、Stripe、Wise、Zoom）的图标或识别；内置 SVG 共 480 个（含通用分类图标）。本批 208 个品牌覆盖 17 类常见行业，配置 261 个域名；这是一份可持续扩充的常用品牌清单，不代表收录所有品牌。

## 本批品牌

### 交易所（5）

KuCoin、Toobit、BloFin、Tapbit、XT.COM。

### 金融与支付（15）

Wise、Revolut、Monzo、Stripe、Square、Adyen、Affirm、Klarna、Afterpay、Payoneer、Venmo、Paytm、Chime、MoneyGram、TradingView。

### 电商与零售（15）

eBay、Etsy、AliExpress、Shopee、Rakuten、Newegg、IKEA、Target、Tesco、Carrefour、Zalando、Vinted、Wish、Zara、Dior。

### 办公与设计（23）

Notion、Figma、Canva、Slack、Zoom、Dropbox、Asana、Trello、Atlassian、ClickUp、monday.com、Airtable、Miro、Linear、Calendly、DocuSign、Evernote、Obsidian、Todoist、Grammarly、Zapier、Typeform、SurveyMonkey。

### AI 工具（7）

Perplexity、Midjourney、Hugging Face、Cursor、Runway、Replicate、Replit。

### 开发与云服务（20）

Vercel、Netlify、Supabase、DigitalOcean、Vultr、Linode、Hetzner、Render、Railway、JetBrains、Postman、Sentry、Datadog、MongoDB、Elastic、Bitbucket、npm、PyPI、Packagist、RubyGems。

### 社交与内容社区（17）

Bluesky、Mastodon、Signal、LINE、WeChat、Viber、Snapchat、Pinterest、Tumblr、Threads、Medium、Substack、Quora、知乎、小红书、快手、Meetup。

### 影音与票务（14）

Twitch、Vimeo、Hulu、HBO Max、Crunchyroll、Plex、Jellyfin、SoundCloud、Deezer、TIDAL、Audible、Ticketmaster、Eventbrite、StubHub。

### 教育与科研（21）

Coursera、Udemy、edX、Udacity、Khan Academy、Skillshare、Codecademy、DataCamp、Quizlet、Kahoot!、LeetCode、HackerRank、Overleaf、arXiv、ORCID、ResearchGate、IEEE、Elsevier、Zotero、Mendeley、Goodreads。

### 餐饮与外卖（10）

Starbucks、KFC、Domino's、Subway、DoorDash、Deliveroo、foodpanda、Swiggy、Zomato、Yelp。

### 旅游与航空（12）

Tripadvisor、Trip.com、trivago、Klook、Delta Air Lines、United Airlines、Southwest Airlines、Emirates、Qatar Airways、Lufthansa、Ryanair、easyJet。

### 物流（5）

DHL、FedEx、UPS、USPS、DPD。

### 健康与运动（8）

Fitbit、Garmin、Strava、Peloton、AllTrails、Headspace、CVS、Walgreens。

### 企业服务（13）

Salesforce、HubSpot、Zendesk、Intercom、Mailchimp、Brevo、Twilio、Intuit、QuickBooks、Xero、Workday、Gusto、Rippling。

### 招聘与自由职业（8）

Indeed、Glassdoor、Wellfound、Greenhouse、Upwork、Fiverr、Freelancer、Toptal。

### 硬件（5）

Dell、HP、Sony、Epson、Nikon。

### 工业、能源与日用消费品（10）

3M、Siemens、Bosch、Schneider Electric、ABB、Caterpillar、Airbus、Boeing、Shell、Unilever。

## 来源与适配

- Toobit：官网页头引用的官方 SVG，保留蜂形品牌符号，移除圆形底板。
- BloFin：官网 SVG 标志中的 B 符号，移除旁边的字标。
- Tapbit：官网 Media Kit 链接的官方 Google Drive 矢量素材，保留符号并显式写入填充规则。
- XT.COM：官网内嵌的官方应用 SVG，保留几何符号，移除底板。
- 其余素材：固定版本 `@thesvg/icons` 3.3.8；Canva、Eventbrite、Runway、Rippling 等移除影响单色渲染的底板，保留原始轮廓。

准确来源链接、许可和修改说明见 `tools/icon-sync/brands.json` 与 `app/src/main/assets/contact_logos/thesvg/sources.json`。图标随 APK 离线打包，查看邮件时无需请求图标服务。

## 匹配规则

专属域名优先于通用分类和显示名称；支持子域名并使用点号边界，较长的产品域名优先。新增名称别名只匹配完整显示名称，不从主题或邮箱用户名中猜测，避免 XT 命中 Next/Text 等单词。保留尚无专属图标品牌的分类回退。

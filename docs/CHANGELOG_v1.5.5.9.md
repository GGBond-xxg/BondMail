# v1.5.5.9

- 系统补齐银行、运营商、运动、服饰、交易所的通用图标映射，共 28 组品牌规则、29 个域名（其中 maya.net 已有分类，本次补齐名称与优先级）。
- 银行：Maya、GoTyme、Tonik、CIMB、BDO、BPI、RCBC、DBS、OCBC、UOB。
- 运营商／eSIM：Saily、M1、giga、GOMO Singapore、Maya Mobile；保留既有 DITO、Globe、Smart 等规则。
- 运动：Reebok、HOKA、Brooks Running、FILA、Converse、Vans。
- 服饰：ZARA、H&M、Gap、Levi’s、Mango。
- 交易所：Bitpanda、Bitunix；保留既有 KuCoin、Bitfinex、Bitstamp、CoinEx 等规则。
- maya.ph、mayabank.ph 使用银行图标，maya.net 使用 SIM 图标；已知域名优先于名称关键词，已有专属图标仍然优先。
- 新增集中维护的 categories.json，同步生成 Kotlin 匹配规则与头像域名映射，所有分类复用已有 SVG。
- 短名称与普通单词增加边界／全名匹配；不根据正文猜测发件人类别。

版本名：1.5.5.9；版本代码：161。覆盖更新保留账户与设置。

品牌域名的官方核对链接见 tools/icon-sync/categories.json 的 sources 字段。图标仅用于显示，不代表身份认证。

验证：62 项 JVM 测试通过，包含全表域名及子域名、伪相似域名、名称边界、Maya 同名服务及专属图标优先级；262 个 SVG 无重复文件名，111 个域名映射均指向存在的资源。尚未进行本版真机复测。

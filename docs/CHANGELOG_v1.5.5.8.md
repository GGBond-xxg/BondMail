# v1.5.5.8

- 为 DITO（dito.ph 及子域名、DITO 发件人名称）补齐通用 SIM 图标。
- 补齐 Globe、Smart、GOMO、Singtel、StarHub、SIMBA 官方域名的 SIM 图标识别。
- 补齐 DHL、FedEx、UPS 的通用快递图标，以及 Hilton、Marriott 的通用酒店图标。
- 新规则复用现有 SVG，不新增重复图标；专属品牌优先。Smart、Globe、SIMBA 等易混淆名称只按完整品牌名或域名匹配，不扫描主题或正文。
- 域名匹配覆盖子域名并保留点边界，名称匹配保留英文单词边界。

- 修复 DITO 邮件在深色模式下的白底浅字：仅对正文文字块应用浅底深字，链接保留蓝色；二维码、图片和附件不做颜色变换。原始邮件内容不修改。

版本名：1.5.5.8；版本代码：160。可覆盖更新，保留账户与设置。

核对来源：[DITO](https://dito.ph/)、[Globe](https://www.globe.com.ph/)、[Smart](https://smart.com.ph/Pages/esim)、[GOMO](https://www.gomo.ph/)、[Singtel](https://www.singtel.com/)、[StarHub](https://www.starhub.com/personal/support/services-and-plans.html)、[SIMBA](https://simba.sg/)、[DHL](https://www.dhl.com/)、[FedEx](https://www.fedex.com/)、[UPS](https://www.ups.com/)、[Hilton](https://www.hilton.com/en/locations/hilton-hotels/)、[Marriott](https://marriott.com/)。

验证：59 项 JVM 测试通过，覆盖域名边界、品牌名称误匹配及 DITO 深色文字修复；二维码引用、链接和隐藏样式保持不变。图标资源仍为 262 个，无新增重复资源。尚未使用用户原始邮件进行真机复测。

# 邮件翻译

在「设置 → 翻译服务」选择服务并填写自己的密钥。每家密钥分别加密保存，点击保存后
该服务成为默认服务。邮件详情页顶部翻译按钮打开邮件翻译，可选择服务、目标语言，
点击「翻译邮件」发送请求；译文和原文可切换、选择和复制。

| 服务 | 凭据 | 使用接口 |
| --- | --- | --- |
| 阿里云 | AccessKey ID、AccessKey Secret | TranslateGeneral，杭州节点，2018-10-12 RPC |
| 网易有道 | App ID、App Secret | 文本翻译 API，v3 SHA-256 签名 |
| Google | Cloud Translation Basic v2 API Key | 国际版 Basic v2 |
| 微软 | Azure Translator Key，按资源要求填写 Region | 国际版 Text Translation v3 |

国内网络建议选择阿里云或有道；Google 和微软国际版接口的可达性依赖实际网络。
不是 Gmail/Outlook 登录密码，也不支持把 Google v3 服务账号 JSON 当作 API Key。
开通翻译服务和服务商计费由用户自己的账号负责；阿里云建议使用仅授权
`alimt:TranslateGeneral` 的 RAM 用户 AccessKey。密钥不要放入源码或反馈截图。

标题与正文分别以纯文本形式发送，长正文分段处理；不发送发件人等其他头信息，也不读取附件或图片 OCR。
正文内本身包含的签名、引用历史会随正文提交。原始邮件和回复/转发内容不被修改。
不自动重试、不跨服务商回退、不记录正文/凭据/原始 API 错误响应。关闭翻译窗口会取消
后续分段；正在发送的请求可能已经到达服务商。标题和正文译文分别按文本内容、服务商和目标语言缓存，以 Android Keystore AES-GCM 加密保存在不参与备份的目录；上限 20 MiB。再次点击翻译时优先读取缓存，无需重新计费。可在“邮箱工具 → 存储管理”清理。

设置内可用固定短句 Hello. 测试密钥；该操作可能消耗少量额度。服务商与语言并排选择，译文 / 对照 / 原文切换替代无说明的复选框，底部按钮固定在滚动区域之外。标题与正文一起翻译，均成功后展示；译文可放入邮件区域，选择对照时同时显示原标题与原文；原文链接另列保留，顶部翻译按钮可恢复原文。复杂 HTML 排版不会逐元素复刻到译文。

凭据复用应用的 Android Keystore AES-GCM 存储，各服务使用独立键；可以分别删除。
目标语言目前提供简体中文、繁体中文、英语、日语、韩语、法语、德语和西班牙语；
默认跟随应用语言。具体语言组合仍以服务商支持为准。

验证：单元测试检查正文提取、分段完整性、签名编码；Android 契约测试检查四家响应解析。
没有有效的用户密钥时，不能验证真实账号授权、余额与线上翻译结果。

官方接口文档：
- https://help.aliyun.com/zh/machine-translation/developer-reference/java-sdk
- https://ai.youdao.com/DOCSIRMA/html/trans/api/wbfy/index.html
- https://docs.cloud.google.com/translate/docs/reference/rest/v2/translate
- https://learn.microsoft.com/azure/ai-services/translator/text-translation/reference/v3/translate

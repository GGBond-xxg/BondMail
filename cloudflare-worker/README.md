# BondMail Cloudflare Push

BondMail 的可自部署 FCM 调度服务。Cloudflare Cron 按用户选择的频率向 Android
发送高优先级数据消息，应用收到后直接连接邮箱服务器同步邮件；Worker 不接触邮箱账号、
OAuth Token 或邮件正文。

独立仓库：<https://github.com/GGBond-xxg/BondMail-Cloudflare-Push>

## 工作方式

1. Android 端用访问密钥从 Worker 获取该部署专属的 Firebase 客户端配置。
2. 应用为该 Firebase 项目生成独立 FCM Token，并向 Worker 注册设备。
3. Worker 只在 D1 保存安装标识、FCM Token、同步频率和访问密钥的 SHA-256 摘要。
4. Cron 仅向摘要仍与当前 `pwd` Secret 匹配的设备发送同步消息。

CF FCM 是可选功能。应用中不填写域名和密钥时，BondMail 仍可正常登录邮箱、手动刷新，
并使用 Android WorkManager 进行本地定时收信。

## 准备 Firebase

1. 创建 Firebase 项目。
2. 添加 Android 应用，软件包名称填写 `com.bond.mail`。
3. 下载 `google-services.json`，从中取得：
   - `project_info.project_id`
   - `project_info.project_number`
   - `client[0].client_info.mobilesdk_app_id`
   - `client[0].api_key[0].current_key`
4. 在 Firebase 项目设置 > 服务账号中生成 Admin SDK 私钥。私钥只能保存到
   Cloudflare Secret，禁止提交到 Git。

## 部署

安装 Node.js 20 或更高版本，然后执行：

```sh
npm ci
npx wrangler login
npx wrangler d1 create bondmail-push-db
```

复制 `wrangler.example.jsonc` 为 `wrangler.jsonc`，把上一步返回的 D1
`database_id` 填进去。需要自定义域名时，再在 `routes` 中填写自己的域名。

创建三个 Secret：

```sh
npx wrangler secret put FIREBASE_SERVICE_ACCOUNT_JSON
npx wrangler secret put FIREBASE_CLIENT_CONFIG_JSON
npx wrangler secret put pwd
```

`FIREBASE_SERVICE_ACCOUNT_JSON` 填完整 Admin SDK JSON。

`FIREBASE_CLIENT_CONFIG_JSON` 使用一行 JSON，值来自前面下载的
`google-services.json`：

```json
{"projectId":"your-project-id","applicationId":"1:123456789:android:abcdef","apiKey":"your-api-key","senderId":"123456789"}
```

`pwd` 是你发给获准用户的访问密钥。建议使用密码管理器生成至少 32 字符的随机值。

最后迁移 D1 并部署：

```sh
npm run migrate:remote
npm run deploy
```

访问 `https://你的域名/health`，看到 `{"ok":true}` 即表示 Worker 正常。
然后在 BondMail > 设置 > CF FCM 推送中填写服务域名和同一个 `pwd`。

## 更新与撤销

- 修改 Cloudflare 中的 `pwd` 会立即停止所有旧授权；用户必须在应用中输入新值重新验证。
- 删除设备时使用安装专属 Secret，其他客户端不能冒充删除。
- Firebase Admin 私钥和三个 Cloudflare Secret 都不能写入代码、Issue、日志或 Release。
- 应用只接受 HTTPS 服务域名，不接受路径、查询参数或明文 HTTP。

## 接口

- `GET /health`：公开健康检查。
- `GET /v1/client-config`：验证 `X-BondMail-Push-Key` 后返回 Firebase 客户端配置。
- `POST /v1/devices/register`：注册或更新当前设备。
- `POST /v1/devices/unregister`：按安装 Secret 删除当前设备。

## License

MIT

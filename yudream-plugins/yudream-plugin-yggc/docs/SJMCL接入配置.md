# SJMCL 接入配置（Yggdrasil Connect 插件）

> 适用站点：`hall.mc.taru.xj.cn`（API 根 `https://hall.mc.taru.xj.cn/api/plugins/yggc/api/yggdrasil`）
> 策略说明：**不使用 SJMCL 内置的旧 client_id "6"（已弃用）**。采用插件现有的 client_id 策略——在「OAuth 应用管理」自建应用，使用插件生成的 `yggc_*` client_id，后续向 [SJMC-Dev/SJMCL-client-ids](https://github.com/SJMC-Dev/SJMCL-client-ids) 提 PR 登记域名映射。

---

## 一、兼容性结论（已对照 SJMCL 源码逐条验证）

SJMCL 第三方认证走 **设备码流程（RFC 8628）**，与插件服务端完全兼容：

| SJMCL 行为（源码 `authlib_injector/oauth.rs`） | 插件服务端 | 结论 |
|---|---|---|
| `GET <认证服务器>/.well-known/openid-configuration` 获取端点 | `/api/plugins/yggc/api/yggdrasil/.well-known/openid-configuration` | ✅ |
| `POST /oauth/device`，只带 `client_id` + `scope`，无 redirect_uri、无 secret | `startDevice` 不校验 redirect_uri | ✅ 回调地址可留空 |
| 固定 scope：`openid offline_access Yggdrasil.PlayerProfiles.Select Yggdrasil.Server.Join` | 四项均在 `SUPPORTED_SCOPES`，且 Join 已带 Select | ✅ |
| 令牌端点轮询 `grant_type=urn:ietf:params:oauth:grant-type:device_code`，仅 client_id | 设备码兑换**要求公共客户端**（`tokenByDevice` 硬校验 `publicClient`） | ✅ 前提：应用必须是**公共客户端** |
| ID Token 校验 `aud == client_id`（RS256 + JWKS） | `claims.aud = client.id()`，JWKS 单钥匙，SJMCL 取 `keys[0]` | ✅ |
| 读 ID Token 的 `selectedProfile` claim 拿角色 | 申请了 Select scope 即写入 `selectedProfile` | ✅ |
| `selectedProfile` 无 `properties` 时回调传统 API 拉取材质 | 传统 Yggdrasil API 正常返回 profile | ✅ |
| 刷新令牌：`grant_type=refresh_token`，仅 client_id 无 secret | 公共客户端 `requireClientSecret` 直接放行 | ✅ |
| 5 秒轮询间隔 | `interval: 5` 与 SJMCL 缓存一致 | ✅ |

**唯一前提**：SJMCL 按**域名**查内置 client_id 表，新域名 `hall.mc.taru.xj.cn` **不在内置表中**（旧域名映射 `skin.mc.taru.xj.cn → "6"` 已弃用，且不适用于新域名）。未登记新映射前，SJMCL 对未知域名会发送**空 client_id**，服务端直接返回 401 `缺少 client_id`。因此新 client_id 生成后需提交 PR 登记（见第三节）。

---

## 二、管理端配置步骤

### 步骤 1：插件配置（插件配置 → OAuth 2.0 / OIDC）

| 配置项 | 填法 |
|---|---|
| Connect 服务器地址（issuer） | **留空**（自动使用本站 API 地址 `https://hall.mc.taru.xj.cn/api/plugins/yggc/api/yggdrasil`） |
| 禁用 Auth Server | 保持关闭（SJMCL 登录后仍需传统 API 拉材质） |
| 访问令牌有效期 | 保持 7 天（默认） |
| 刷新令牌有效期 | 保持 30 天（默认；SJMCL 支持刷新旋转，可放心用默认值） |
| 设备码有效期 | 保持 10 分钟（默认；SJMCL 页面输码 10 分钟足够） |

### 步骤 2：创建 SJMCL 应用（OAuth 应用管理 → 新建应用）

| 字段 | 填法 | 原因 |
|---|---|---|
| 应用名称 | `SJMCL` | 便于管理员识别 |
| 回调地址 | **留空** | 设备码流程不回调，服务端对 device 端点不校验 redirect_uri |
| 客户端类型 | **公共客户端（开关打开）** | **硬性要求**：设备码兑换 `tokenByDevice` 强制要求 `publicClient=true`，机密客户端会直接失败 |
| 启用状态 | 开启 | — |

创建后**立即复制生成的 client_id**（形如 `yggc_xxxxxxxxxxxxxxxxxxxxxxx`，43 位随机后缀）。这是后续 PR 与全部流程的锚点，务必存档。

### 步骤 3：验证应用配置正确

```bash
# 1) 发现文档正常返回端点
curl -s https://hall.mc.taru.xj.cn/api/plugins/yggc/api/yggdrasil/.well-known/openid-configuration | jq .

# 2) 设备授权发起（用你生成的 client_id，scope 原样粘贴 SJMCL 的固定值）
curl -s -X POST https://hall.mc.taru.xj.cn/api/plugins/yggc/api/yggdrasil/oauth/device \
  -d "client_id=yggc_你的ID" \
  -d "scope=openid offline_access Yggdrasil.PlayerProfiles.Select Yggdrasil.Server.Join"
```

第 2 步应返回：

```json
{
  "device_code": "yggc_dc_...",
  "user_code": "XXXX-XXXX",
  "verification_uri": "https://hall.mc.taru.xj.cn/platform/plugins/yggc/device",
  "verification_uri_complete": "https://hall.mc.taru.xj.cn/platform/plugins/yggc/device?user_code=XXXX-XXXX",
  "expires_in": 600,
  "interval": 5
}
```

若返回 `invalid_scope`，检查 scope 是否原样粘贴（`offline_access` 不能写成 `offline`）；若返回 401，检查 client_id 与应用启用状态。

---

## 三、client_id 登记 PR（待办）

向 [SJMC-Dev/SJMCL-client-ids](https://github.com/SJMC-Dev/SJMCL-client-ids) 提交 PR，**新增**映射：

```
("hall.mc.taru.xj.cn", "yggc_你生成的client_id"),
```

（如仓库中仍存在旧条目 `("skin.mc.taru.xj.cn", "6")`，可在同一 PR 中一并删除。）

注意事项：

1. **client_id 必须与服务端完全一致**——SJMCL 用它做 ID Token 的 `aud` 受众校验，错一个字符就 `ParseError`
2. PR 合并并发布新版 SJMCL 前的过渡期：未知域名会发送空 client_id → 401，属预期行为，不是服务端故障
3. 若后续更换/重建应用，需同步更新 PR 映射，否则已装新版 SJMCL 的用户会全员掉线
4. 建议同时在站点公告告知用户：需使用集成了新映射的 SJMCL 版本

---

## 四、用户端流程（SJMCL 内操作）

1. SJMCL → 添加第三方认证服务器，填 `https://hall.mc.taru.xj.cn/api/plugins/yggc/api/yggdrasil`（或直接点击站点首页卡片/地址页的「添加到 SJMCL」按钮）
2. 选择该服务器登录 → SJMCL 弹出设备码窗口（user_code 已自动复制到剪贴板）
3. 浏览器打开 `verification_uri_complete` 链接 → 登录皮肤站 → 确认授权页显示四个 scope（基础身份 / 离线访问 / 选择角色 / 加入服务器）→ 选择要绑定的游戏角色 → 同意
4. SJMCL 自动轮询拿到令牌，登录完成；后续启动器内自动刷新

---

## 五、故障排查速查

| 现象 | 原因 | 处理 |
|---|---|---|
| 401 `缺少 client_id` | 域名不在 SJMCL 内置表（PR 未合并）或 client_id 为空 | 等 PR / 临时手填 |
| 401 `客户端不存在` | PR 中的 client_id 与服务端不一致 | 核对应用管理页 |
| 401 `客户端已被禁用` | 应用被停用 | 应用管理里重新启用 |
| `invalid_scope` | scope 被改动过 | 必须是那四个固定值 |
| 授权页正常但 SJMCL 报 `ParseError` | 应用建成**机密客户端**，或 ID Token 过期（时钟偏差） | 重建为公共客户端 |
| 换 refresh_token 后旧 token 失效 | 服务端刷新令牌**一次性旋转**设计 | 正常行为，SJMCL 兼容 |

# API 接口文档

最后更新：2026-07-13

默认后端：`http://localhost:8081`。

## 1. 通用约定

除文件字节流和 Actuator 外，响应格式为：

```json
{
  "code": "OK",
  "message": "success",
  "data": {},
  "traceId": "49f3...",
  "timestamp": "2026-07-10T12:00:00+08:00"
}
```

- 管理接口使用 `Authorization: Bearer <adminAccessToken>`。
- 小程序方案和报价接口使用 `Authorization: Bearer <customerAccessToken>`。
- 分页从 1 开始，`size` 范围为 1～100。
- 管理新增成功返回 201，其余成功通常返回 200。
- 参数错误返回 400，未认证 401，不存在 404，业务冲突 409。
- 商品状态：`DRAFT`、`ON_SALE`、`OFF_SALE`。
- 方案报价状态：`NOT_QUOTED`、`VALID`、`STALE`、`INVALID`。
- 订单状态：`PENDING_PAYMENT`、`PAID`、`IN_PRODUCTION`、`SHIPPED`、`COMPLETED`、`CANCELLED`。

## 2. 认证、访客身份和文件

| 方法 | 路径 | 认证 | 说明 |
| --- | --- | --- | --- |
| POST | `/api/admin/auth/login` | 否 | 管理员登录，返回管理员 JWT |
| POST | `/api/app/session/guest` | 否 | 安装 UUID 换取顾客 JWT |
| POST | `/api/admin/files` | 管理员 | `multipart/form-data` 的 `file` 字段 |
| GET | `/api/public/files/{storageKey}` | 否 | 读取公开图片，键可包含年月目录 |

管理员登录请求：

```json
{"username":"admin","password":"Admin@123"}
```

访客会话请求：

```json
{"installationId":"3f6f7816-0f1a-4aa0-870f-f34f9195a2ec"}
```

## 3. 公共商品目录

| 方法 | 路径 | 查询参数 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/public/catalog/categories` | 无 | 已上架分类 |
| GET | `/api/public/catalog/beads` | `categoryId? keyword? page size` | 已上架散珠分页 |
| GET | `/api/public/catalog/beads/{id}` | 无 | 散珠详情 |
| GET | `/api/public/catalog/packages` | `page size` | 已上架套餐分页 |
| GET | `/api/public/catalog/packages/{id}` | 无 | 套餐及有序明细 |

公共 DTO 返回 `coverUrl`，不会返回 `costPrice`、本地文件路径或草稿商品。

## 4. 管理商品目录

所有路径前缀均为 `/api/admin/catalog`。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET/POST | `/categories` | 分类列表/新增 |
| PUT | `/categories/{id}` | 修改分类 |
| PATCH | `/categories/{id}/status` | 切换分类状态 |
| GET/POST | `/beads` | 散珠分页/新增 |
| GET/PUT | `/beads/{id}` | 散珠详情/修改 |
| PATCH | `/beads/{id}/status` | 切换散珠状态 |
| GET/POST | `/packages` | 套餐分页/新增 |
| GET/PUT | `/packages/{id}` | 含有序散珠明细的套餐详情/修改 |
| PATCH | `/packages/{id}/status` | 切换套餐状态 |

新增散珠示例：

```json
{
  "categoryId": 1001,
  "skuCode": "BEAD-OBSIDIAN-001",
  "name": "黑曜石 8mm",
  "material": "天然水晶",
  "color": "黑色",
  "diameterMm": 8,
  "salePrice": 15.8,
  "costPrice": 6.2,
  "stockAlertThreshold": 20,
  "coverAssetId": null,
  "tags": ["守护", "日常"],
  "status": "DRAFT",
  "initialStock": 200
}
```

`initialStock` 只属于新增 DTO，范围为非负整数；修改散珠时不接收该字段。新增商品、库存余额和非零库存的 `INIT` 流水在同一事务完成。

## 5. DIY 配置管理

管理端路径前缀为 `/api/admin/diy`，小程序读取路径为 `/api/app/diy`。

| 方法 | 路径 | 认证 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/app/diy/config` | 顾客 | 小程序读取当前 DIY 规则、工艺费和绳材 |
| GET | `/api/admin/diy/config` | 管理员 | 管理后台读取 DIY 配置 |
| PUT | `/api/admin/diy/rule` | 管理员 | 保存腕围、余量、容差和最大珠数 |
| PUT | `/api/admin/diy/craft-fees` | 管理员 | 保存工艺费区间 |
| POST | `/api/admin/diy/cord-options` | 管理员 | 新增绳材 |
| PUT | `/api/admin/diy/cord-options/{id}` | 管理员 | 修改绳材 |

规则示例：

```json
{
  "minWristMm": 120,
  "maxWristMm": 250,
  "snugAllowanceMm": 5,
  "standardAllowanceMm": 10,
  "looseAllowanceMm": 15,
  "toleranceMm": 4,
  "maxBeadCount": 60
}
```

## 6. 小程序 DIY 报价

| 方法 | 路径 | 认证 | 说明 |
| --- | --- | --- | --- |
| POST | `/api/app/diy/quotes` | 顾客 | 根据服务端 SKU 与配置计算权威报价 |

请求：

```json
{
  "wristCircumferenceMm": 160,
  "fitType": "STANDARD",
  "cordOptionId": 6001,
  "sequence": [2001, 2001, 2002]
}
```

响应核心字段：

```json
{
  "quotable": true,
  "issues": [],
  "skuQuantities": {"2001": 2, "2002": 1},
  "currentLengthMm": 24,
  "targetLengthMm": 170,
  "sizeStatus": "TOO_SHORT",
  "beadAmount": 42.4,
  "craftFee": 10,
  "cordFee": 0,
  "totalAmount": 52.4,
  "quoteFingerprint": "..."
}
```

## 7. 小程序 DIY 方案

所有路径前缀均为 `/api/app/diy/schemes`，均需要顾客 JWT。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/` | 当前访客的方案列表 |
| POST | `/` | 新建方案，成功返回 201 |
| GET | `/{id}` | 读取当前访客拥有的方案 |
| PUT | `/{id}` | 更新方案，使用 `version` 乐观锁 |
| POST | `/{id}/copies` | 复制方案，成功返回 201 |
| DELETE | `/{id}` | 软删除方案 |
| POST | `/{id}/preview` | 上传方案预览图，`multipart/form-data` 的 `file` 字段 |

保存请求：

```json
{
  "name": "我的第一串",
  "wristCircumferenceMm": 160,
  "fitType": "STANDARD",
  "cordOptionId": 6001,
  "sequence": [2001, 2001, 2002],
  "version": 0
}
```

方案响应包含 `sequence`、`catalogSnapshot`、`quoteStatus`、`version`、`previewUrl` 和报价快照。后端只保存 SKU 顺序数组；展示序号、SKU 数量统计和 Canvas 坐标均由前端派生。

## 8. 库存、订单、模拟支付与履约

### 8.1 小程序订单

所有路径均需要顾客 JWT。订单查询始终按 `customer_identity_id` 过滤，访问他人订单返回 404。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/app/orders` | 基于本人 DIY 方案创建待支付订单并预占库存，成功返回 201 |
| GET | `/api/app/orders` | 当前顾客订单列表，按创建时间倒序 |
| GET | `/api/app/orders/{id}` | 当前顾客订单详情与 SKU 聚合明细 |
| POST | `/api/app/orders/{id}/mock-pay` | 本地模拟支付；重复调用幂等 |
| POST | `/api/app/orders/{id}/cancel` | 取消待支付订单并释放预占库存 |

创建订单请求：

```json
{
  "schemeId": 123,
  "contactName": "张三",
  "contactPhone": "13800000000",
  "addressDetail": "上海市浦东新区演示路 1 号"
}
```

创建订单时不接收前端金额。后端重新读取方案、SKU、价格和配置完成权威报价，并在同一事务内写入订单、明细和库存预占。库存不足返回 HTTP 409 与业务码 `INVENTORY_INSUFFICIENT`。

### 8.2 管理后台订单

所有路径均需要管理员 JWT。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/admin/orders` | 分页查询订单；参数为 `status? page size` |
| GET | `/api/admin/orders/{id}` | 订单、BOM 和履约详情 |
| POST | `/api/admin/orders/{id}/start-production` | `PAID → IN_PRODUCTION` |
| POST | `/api/admin/orders/{id}/ship` | `IN_PRODUCTION → SHIPPED`，保存模拟物流 |
| POST | `/api/admin/orders/{id}/complete` | `SHIPPED → COMPLETED` |

发货请求：

```json
{
  "logisticsCompany": "顺丰速运",
  "trackingNo": "SF-DEMO-20260711"
}
```

后台动作接口不允许直接提交任意目标状态。前置状态不匹配返回 HTTP 409 与 `COMMON_CONFLICT`，订单状态和履约任务状态在同一事务内更新。

### 8.3 模拟支付边界

- 当前支付渠道固定为 `MOCK`，不会调用微信支付或产生真实扣款。
- 支付成功创建唯一 `payment_record` 和唯一 `fulfillment_task`。
- 同一订单重复模拟支付直接返回当前已支付订单，不重复扣库存。
- 后续接微信支付时应保持订单、库存和幂等语义不变，通过支付网关适配器接入下单与回调。

## 9. DeepSeek 配置与单套推荐

### 9.1 管理员单例配置

所有接口都需要管理员 JWT。API Key 只允许在保存请求中提交，读取和保存响应都只返回 `apiKeyConfigured`，不会回显明文或密文。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/admin/ai/config` | 读取唯一配置及乐观锁 `version` |
| PUT | `/api/admin/ai/config` | 保存官方地址、模型、启停、超时、Token 上限及可选新 Key |
| POST | `/api/admin/ai/config/test` | 调用 DeepSeek `/models` 校验鉴权和模型存在性 |
| DELETE | `/api/admin/ai/config/api-key?version={version}` | 清除已保存 Key |

保存请求：

```json
{
  "baseUrl": "https://api.deepseek.com",
  "modelName": "deepseek-v4-flash",
  "apiKey": "仅在保存时提交，不回显",
  "enabled": true,
  "timeoutSeconds": 30,
  "maxOutputTokens": 2048,
  "version": 0
}
```

Provider 固定为 `DEEPSEEK`，Base URL 只接受 DeepSeek 官方 HTTPS origin。Key 以 AES-GCM 密文保存，解密主密钥来自后端环境 `AI_CONFIG_ENCRYPTION_KEY`；该主密钥不进入数据库。

### 9.2 顾客推荐

两个接口都必须使用 `/api/app/session/guest` 返回的顾客 JWT；管理员 JWT 不能代替。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/app/recommendations/status` | 返回 `AVAILABLE`、`NOT_CONFIGURED` 或 `DISABLED` |
| POST | `/api/app/recommendations` | 调用模型一次并返回一套经后端复核的推荐 |

请求示例：

```json
{
  "birthDate": "1992-01-20",
  "constellation": "水瓶座",
  "zodiac": "猴",
  "elementPreference": "水",
  "wish": "专注与从容",
  "scene": "通勤",
  "colorPreference": "灰蓝",
  "budget": 500,
  "wristCircumferenceMm": 160,
  "fitType": "STANDARD",
  "freeText": "偏好日常搭配"
}
```

响应只有一套对象，不是候选列表；核心字段包括 `schemeName`、有序 `sequence`、`quantities`、带当前图片与理由的 `items`、`cordOption`、服务端重算的 `beadAmount/craftFee/cordFee/totalAmount`、`quoteFingerprint` 和 `culturalDisclaimer`。模型只能从当前已上架且有库存的 SKU 中选择；后端会再次校验 SKU、库存、预算、腕围、文案、绳材和权威价格，任一失败都不返回部分方案。

生日和完整咨询文本只在本次请求内使用，不持久化、不写日志、不回显。小程序进入 DIY 时只写入 `sequence`、`wristCircumferenceMm`、`fitType`、`cordOptionId`，并在 `source=ai` 首次读取时删除；价格、理由、生日和自由文本不进入 handoff。

无 Key 时状态精确为 `NOT_CONFIGURED`，POST 返回 HTTP 503 + `AI_NOT_CONFIGURED`，小程序文案为“AI 推荐暂未开放”；管理员停用时状态为 `DISABLED`，POST 返回 HTTP 503 + `AI_DISABLED`，文案为“AI 推荐暂停使用”。系统没有规则或本地降级，不会把暂停状态包装成成功。

其他稳定错误包括 `AI_UNAUTHORIZED`、`AI_MODEL_UNAVAILABLE`、`AI_RATE_LIMITED`、`AI_TEMPORARILY_UNAVAILABLE` 和 `AI_INVALID_RECOMMENDATION`；错误响应不含上游完整响应体。

## 10. 健康检查与冒烟

- `GET /actuator/health`
- `GET /api/public/project-health`
- 商品目录真实 HTTP 冒烟：`./scripts/catalog-smoke.sh`
- DIY 真实 HTTP 冒烟：`./scripts/diy-smoke.sh`
- 订单与履约真实 HTTP 冒烟：`./scripts/order-smoke.sh`
- AI 推荐暂停/live 双路径冒烟：`./scripts/recommendation-smoke.sh`（live 必须显式提供 Key）

## 11. 固定视图手工视觉版本

管理端的散珠视觉接口均要求管理员 JWT，路径前缀为
`/api/admin/catalog/beads/{skuId}/visuals`：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/` | 读取版本历史和 `publishedVisualVersionId` |
| POST | `/manual` | 以已上传的托盘、画布素材创建 `MANUAL/READY` 版本，成功返回 201 |
| POST | `/{versionId}/publish` | 发布 `READY` 版本，并将该 SKU 的已发布指针切到该版本 |
| POST | `/{versionId}/rollback` | 从历史版本创建并发布一个新的版本，不改写历史版本 |

`POST /manual` 请求为：

```json
{"trayAssetId": 901, "canvasAssetId": 902}
```

手工素材仍先通过 `POST /api/admin/files` 上传。服务端验证托盘为 160×160、画布为
512×512，且都是带透明通道、大小不超过 500 KB 的 PNG 或 WebP；尺寸、透明通道或大小不合格
返回 HTTP 400 与 `COMMON_BAD_REQUEST`。

视觉历史读取返回：

```json
{
  "publishedVisualVersionId": 31,
  "versions": [{"id": 31, "version": 3, "sourceType": "MANUAL", "status": "PUBLISHED"}]
}
```

`POST /manual`、`POST /{versionId}/publish` 和 `POST /{versionId}/rollback` 的 `data` 均为
`BeadVisualVersionView`：`id`、`version`、`sourceType`、`status`、`trayAssetId`、`trayUrl`、
`canvasAssetId`、`canvasUrl`、`failureCode`、`failureMessage`、`createdBy`、`publishedBy`、
`publishedAt`、`createdAt`。历史中的每个 `versions` 项也是同一形状。

公共 `GET /api/public/catalog/beads/{id}`（以及公共珠子列表）只在 SKU 指针指向
`PUBLISHED` 版本时返回 `diyVisual`，形状如下；未发布时为 `null`：

```json
{
  "diyVisual": {
    "trayUrl": "/api/public/files/...",
    "canvasUrl": "/api/public/files/...",
    "version": 3
  }
}
```

`trayUrl` 与 `canvasUrl` 可独立读取；客户端分别降级到 `coverUrl`，再降级为颜色占位。

# API 接口文档

最后更新：2026-08-08

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
- 订单状态：`PENDING_PAYMENT`、`IN_PRODUCTION`、`PENDING_SHIPMENT`、`SHIPPED`、`COMPLETED`、`CANCELLED`、`AFTER_SALE`、`REFUNDED`。

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
| GET | `/api/public/catalog/categories` | 无 | 已上架的珠子分类 |
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
| GET | `/beads/import-template` | 下载当前分类对应的 `.xlsx` 导入模板 |
| POST | `/beads/import` | `multipart/form-data` 上传 `.xlsx`，字段名为 `file` |
| GET/POST | `/packages` | 套餐分页/新增 |
| GET/PUT | `/packages/{id}` | 含有序散珠明细的套餐详情/修改 |
| PATCH | `/packages/{id}/status` | 切换套餐状态 |

分类写入字段 `productType` 支持 `BEAD`（珠子）、`CORD`（绳类）和 `ACCESSORY`（其他饰品）。为兼容旧调用，省略时后端按 `BEAD` 处理；已有商品的分类不能再修改商品类型。

后台 `GET /beads` 的列表项除经营字段外还返回：

- `productType`：分类对应的商品类型；
- `availableQty/reservedQty/soldQty`：当前可用、预占和累计已售库存；
- `stockWarningStatus`：`NORMAL` 或 `RESTOCK`，计算规则为 `availableQty <= stockAlertThreshold` 时需要补货。

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

绳类或其他饰品沿用同一新增接口，但不传 `diameterMm`；后端会把非珠子直径保存为 `null`。珠子分类仍强制要求有效珠径。非珠子商品可在后台独立维护和查看库存，但不会出现在公共珠子目录、DIY、套餐珠子或 AI 推荐候选中。

批量模板包含 `SKU编码、商品名称、分类名称、材质、颜色、珠径(mm)、销售价(元)、成本价(元)、初始库存、库存预警、标签、状态`。图片不写入 Excel，商品导入后再通过编辑页上传。单次限制 1000 行和 10 MB；合法行独立创建商品、库存余额和初始化流水，失败行不会回滚其他成功行。响应示例：

```json
{
  "totalRows": 3,
  "successCount": 2,
  "failureCount": 1,
  "errors": [
    {"rowNumber": 4, "skuCode": "BEAD-EXAMPLE", "message": "珠子商品必须填写珠径"}
  ]
}
```

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
| POST | `/api/app/orders/{id}/confirm-receipt` | 顾客确认收货，`SHIPPED → COMPLETED` |

创建订单请求：

```json
{
  "schemeId": 123,
  "couponId": 456,
  "contactName": "张三",
  "contactPhone": "13800000000",
  "addressDetail": "上海市浦东新区演示路 1 号"
}
```

`couponId` 可省略。创建订单时不接收前端金额。后端重新读取方案、SKU、价格、配置和顾客优惠券完成权威结算，并在同一事务内写入订单、明细、优惠金额、优惠券核销状态和库存预占。取消待支付订单时会退回优惠券，之后可以再次使用。库存不足返回 HTTP 409 与业务码 `INVENTORY_INSUFFICIENT`。

### 8.2 管理后台订单

所有路径均需要管理员 JWT。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/admin/orders` | 分页查询订单；支持订单号、收件人、手机号、状态、日期、金额、支付方式和排序筛选 |
| GET | `/api/admin/orders/statistics` | 按当前筛选条件统计各订单状态数量 |
| GET | `/api/admin/orders/{id}` | 订单、顾客收货信息、商品方案、BOM、支付、履约和操作记录详情 |
| POST | `/api/admin/orders/{id}/remind-payment` | 记录待付款提醒操作 |
| POST | `/api/admin/orders/{id}/complete-production` | `IN_PRODUCTION → PENDING_SHIPMENT` |
| POST | `/api/admin/orders/{id}/ship` | `PENDING_SHIPMENT → SHIPPED`，保存物流信息 |
| POST | `/api/admin/orders/{id}/complete` | `SHIPPED → COMPLETED` |
| POST | `/api/admin/orders/{id}/cancel` | 取消待付款订单并释放库存、退回优惠券 |
| PATCH | `/api/admin/orders/{id}/address` | 修改订单收件人、手机号、地址和备注 |

发货请求：

```json
{
  "logisticsCompany": "顺丰速运",
  "trackingNo": "SF-DEMO-20260711"
}
```

后台动作接口不允许直接提交任意目标状态。前置状态不匹配返回 HTTP 409 与 `COMMON_CONFLICT`，订单状态和履约任务状态在同一事务内更新。

### 8.3 模拟支付边界

- 当前支付记录渠道兼容本地 `MOCK`/微信演示标识，不会调用真实微信支付或产生真实扣款。
- 支付成功创建唯一 `payment_record` 和唯一 `fulfillment_task`。
- 支付成功后订单与履约任务直接进入 `IN_PRODUCTION`。
- 同一订单重复模拟支付直接返回当前已支付订单，不重复扣库存。
- 后续接微信支付时应保持订单、库存和幂等语义不变，通过支付网关适配器接入下单与回调。

## 9. DeepSeek 配置与多套推荐

### 9.1 管理员单例配置

所有接口都需要管理员 JWT。普通读取和保存响应只返回 `apiKeyConfigured`；只有管理员显式点击眼睛按钮时才调用禁止缓存的密钥查看接口。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/admin/ai/config` | 读取唯一配置及乐观锁 `version` |
| PUT | `/api/admin/ai/config` | 保存官方地址、模型、启停、超时、Token 上限及可选新 Key |
| POST | `/api/admin/ai/config/test` | 调用 DeepSeek `/models` 校验鉴权和模型存在性 |
| GET | `/api/admin/ai/config/api-key` | 显式查看已保存 Key；响应使用 `Cache-Control: no-store` |
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
  "recommendationPrompt": "根据生日画像和期望推荐有区分的日常珠子材质搭配",
  "planCount": 3,
  "version": 0
}
```

Provider 固定为 `DEEPSEEK`，Base URL 只接受 DeepSeek 官方 HTTPS origin，模型固定为 `deepseek-v4-flash`。`recommendationPrompt` 必填且最多 4000 字，`planCount` 为 1～5、默认 3。Key 以 AES-GCM 密文保存，解密主密钥来自后端环境 `AI_CONFIG_ENCRYPTION_KEY`；该主密钥不进入数据库。

### 9.2 顾客推荐

状态和生日画像接口公开且不依赖访客会话；正式生成必须使用 `/api/app/session/guest` 返回的顾客 JWT，管理员 JWT 不能代替。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/public/recommendations/status` | 返回 `AVAILABLE`、`NOT_CONFIGURED` 或 `DISABLED` |
| GET | `/api/public/recommendations/profile?birthDate=YYYY-MM-DD` | 由生日计算星座、生肖和星座元素 |
| POST | `/api/app/recommendations` | 调用模型并返回后台配置套数的库存材质推荐 |

请求示例：

```json
{
  "birthDate": "1992-01-20",
  "wish": "希望日常搭配更从容"
}
```

响应包含 `profile`、`plans` 和 `culturalDisclaimer`。`profile` 返回 `birthDate/constellation/zodiac/constellationElement`；每套 `plan` 包含 `schemeName/summary/items`，每套选择 3～5 种不同珠子，每项只返回服务端当前 `skuId/name/imageUrl` 与模型给出的 `reason`。模型输入只包含后台业务提示词、生日画像、可选期望和当前在售有库存珠子的 `skuId/name/material`，不包含成本、售价、手围、松紧、数量、排列或绳材。后端严格校验套数、结构、SKU、当前库存和文案，任一失败都不返回部分方案。

生日和期望只在本次请求内使用，不持久化、不写日志。小程序选择某套进入 DIY 时只一次性写入该套去重后的 SKU 类型；编辑器读取即删除，并使用 DIY 自己的默认手围、松紧和绳材，后续数量、排列与价格仍由既有 DIY 规则处理。

无 Key 时状态精确为 `NOT_CONFIGURED`，POST 返回 HTTP 503 + `AI_NOT_CONFIGURED`，小程序文案为“AI 推荐暂未开放”；管理员停用时状态为 `DISABLED`，POST 返回 HTTP 503 + `AI_DISABLED`，文案为“AI 推荐暂停使用”。系统没有规则或本地降级，不会把暂停状态包装成成功。

推荐错误按原因区分为 `AI_PROMPT_NOT_CONFIGURED`、`AI_UNAUTHORIZED`、`AI_MODEL_UNAVAILABLE`、`AI_RATE_LIMITED`、`AI_TIMEOUT`、`AI_UPSTREAM_ERROR`、`AI_RESPONSE_FORMAT_INVALID`、`AI_RESULT_COUNT_INVALID`、`AI_RESULT_ITEM_COUNT_INVALID`、`AI_RESULT_DUPLICATE_SKU`、`AI_RESULT_SKU_INVALID`、`AI_RESPONSE_CONTENT_UNSAFE`、`BIRTH_DATE_REQUIRED`、`BIRTH_DATE_INVALID` 和 `CATALOG_EMPTY`。只有格式、套数、单套珠子种类数、重复 SKU、无效 SKU 或文案校验失败时，后端允许同一模型纠正一次；鉴权、限流、超时和上游错误不重试，也没有本地或其他模型降级。错误响应不含上游完整响应体。

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

## 12. 管理端顾客地址与批量发券

以下接口均需要管理员 JWT：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/admin/customers` | 返回顾客资料、默认地址和 `addressCount` |
| GET | `/api/admin/customers/search?phone={keyword}&limit=50` | 按手机号片段服务端模糊搜索顾客，最多返回 100 位 |
| GET | `/api/admin/customer-addresses?customerIdentityId={id}&status={status}` | 查询指定顾客的多个地址，可选 `ACTIVE/DELETED` 状态 |
| PATCH | `/api/admin/customer-addresses/{id}/status` | 停用或恢复地址 |
| POST | `/api/admin/coupons/{templateId}/grants/batch` | 向多选顾客或全部有效顾客发送优惠券 |

多选发送请求：

```json
{
  "recipientMode": "SELECTED",
  "customerIdentityIds": [12, 15, 18],
  "note": "客服关怀"
}
```

全量发送将 `recipientMode` 改为 `ALL`，`customerIdentityIds` 传空数组。服务端按 500 条一批写入，响应返回 `requestedCount`、`grantedCount` 和 `skippedCount`；多选模式会去重并跳过不存在或已停用的顾客。

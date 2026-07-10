# API 接口文档

最后更新：2026-07-10

默认后端：`http://localhost:8080`。

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
  "status": "DRAFT"
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

## 8. 健康检查与冒烟

- `GET /actuator/health`
- `GET /api/public/project-health`
- 商品目录真实 HTTP 冒烟：`./scripts/catalog-smoke.sh`
- DIY 真实 HTTP 冒烟：`./scripts/diy-smoke.sh`

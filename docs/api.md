# API 接口文档

最后更新：2026-07-04

默认后端：`http://localhost:8080`。

## 1. 通用约定

除文件字节流和 Actuator 外，响应格式为：

```json
{
  "code": "OK",
  "message": "success",
  "data": {},
  "traceId": "49f3...",
  "timestamp": "2026-07-04T12:00:00+08:00"
}
```

- 管理接口在请求头使用 `Authorization: Bearer <accessToken>`。
- 分页从 1 开始，`size` 范围为 1～100。
- 管理新增成功返回 201，其余成功通常返回 200。
- 参数错误返回 400，未认证 401，不存在 404，业务冲突 409。
- 商品状态：`DRAFT`、`ON_SALE`、`OFF_SALE`。

## 2. 认证和文件

| 方法 | 路径 | 认证 | 说明 |
| --- | --- | --- | --- |
| POST | `/api/admin/auth/login` | 否 | 管理员登录，返回 JWT 和过期时间 |
| POST | `/api/admin/files` | 管理员 | `multipart/form-data` 的 `file` 字段 |
| GET | `/api/public/files/{storageKey}` | 否 | 读取公开图片，键可包含年月目录 |

登录请求：

```json
{"username":"admin","password":"Admin@123"}
```

上传返回的 `data.id` 用作 `coverAssetId`，`data.publicUrl` 用于预览。

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

状态修改请求：

```json
{"status":"ON_SALE"}
```

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

## 5. 健康检查

- `GET /actuator/health`
- `GET /api/public/project-health`

尚未实现 `/api/app/**` 用户身份、DIY、方案、订单和支付接口；实现后必须在本文件同步更新。

# 数据模型

最后更新：2026-07-04

本文件只记录已经通过 Flyway 落地的表。当前迁移：

- `V1__catalog_schema.sql`：商品与素材结构。
- `V2__catalog_seed.sql`：2 个分类、4 个散珠和 1 个套餐演示数据。

## 1. 关系

```text
category 1 ── n bead_sku
asset_file 1 ── n bead_sku/product_package（可选封面）
product_package 1 ── n package_item n ── 1 bead_sku
```

## 2. 表说明

### `asset_file`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | bigint | 主键 |
| `storage_key` | varchar(255) | 存储内部键，唯一 |
| `original_name` | varchar(255) | 上传原文件名 |
| `content_type` | varchar(100) | 校验后的图片 MIME |
| `size_bytes` | bigint | 文件大小，非负 |
| `public_url` | varchar(500) | 客户端可读取的相对或绝对 URL |
| `status` | varchar(32) | 当前为 `ACTIVE` |
| `created_at/updated_at` | timestamp | 审计时间 |

### `category`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | bigint | 主键 |
| `name` | varchar(80) | 分类名称 |
| `slug` | varchar(80) | 稳定英文标识，唯一 |
| `sort_order` | int | 升序排列 |
| `status` | varchar(32) | `DRAFT`、`ON_SALE`、`OFF_SALE` |
| `created_at/updated_at` | timestamp | 审计时间 |

### `bead_sku`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | bigint | 主键 |
| `category_id` | bigint | 所属分类外键 |
| `sku_code` | varchar(64) | SKU 编码，唯一 |
| `name/material/color` | varchar | 名称、材质、颜色 |
| `diameter_mm` | decimal(5,2) | 珠子直径，必须大于 0 |
| `sale_price/cost_price` | decimal(12,2) | 售价、成本价，非负 |
| `stock_alert_threshold` | int | 库存预警阈值，非负 |
| `cover_asset_id` | bigint | 可空封面素材外键 |
| `tags_json` | json | 标签数组 |
| `status` | varchar(32) | 商品状态 |
| `created_at/updated_at` | timestamp | 审计时间 |

### `product_package`

包含唯一 `package_code`、名称、1000 字以内描述、非负售价、可空封面素材和商品状态。

### `package_item`

包含套餐、散珠、正整数数量和排序值。`(package_id, sort_order)` 唯一；删除套餐时明细级联删除。

## 3. 查询与约束

- 公共散珠索引：`(status, category_id, id)`。
- 公共套餐索引：`(status, id)`。
- 数据库 `check` 约束保证金额、尺寸、数量和文件大小合法。
- 公共查询同时要求散珠和所属分类为 `ON_SALE`。
- 金额映射为 Java `BigDecimal`，前端仅负责显示，不作为最终计价权威。

库存、设计、订单、支付和履约表尚未落地；实施对应阶段时在新 Flyway 迁移中追加。

# 数据模型

最后更新：2026-07-10

本文件只记录已经通过 Flyway 落地的表。当前迁移：

- `V1__catalog_schema.sql`：商品与素材结构。
- `V2__catalog_seed.sql`：2 个分类、4 个散珠和 1 个套餐演示数据。
- `V3__diy_identity_pricing_schema.sql`：访客身份、DIY 规则、工艺费、绳材和方案结构。
- `V4__diy_seed.sql`：默认 DIY 规则、工艺费和绳材演示配置。

## 1. 关系

```text
category 1 ── n bead_sku
asset_file 1 ── n bead_sku/product_package/diy_scheme（可选封面或预览）
product_package 1 ── n package_item n ── 1 bead_sku
customer_identity 1 ── n diy_scheme n ── 1 cord_option
diy_rule_config 1 ── 当前全局 DIY 规则
craft_fee_rule n ── 按珠数区间匹配工艺费
```

## 2. 商品与素材表

### `asset_file`

保存上传文件元数据：`storage_key`、原始名、MIME、大小、公开 URL、状态和审计时间。当前使用本地文件存储，后续对象存储只替换 `FileStorage` 实现。

### `category`

包含分类名称、`slug`、排序、状态和审计时间。公共目录只返回 `ON_SALE` 分类。

### `bead_sku`

| 字段 | 说明 |
| --- | --- |
| `category_id` | 所属分类外键 |
| `sku_code` | SKU 编码，唯一 |
| `name/material/color` | 名称、材质、颜色 |
| `diameter_mm` | 珠子直径，必须大于 0 |
| `sale_price/cost_price` | 售价、成本价，非负 |
| `stock_alert_threshold` | 库存预警阈值，非负；当前尚未实现库存流水 |
| `cover_asset_id` | 可空封面素材外键 |
| `tags_json` | 标签数组 |
| `status` | `DRAFT`、`ON_SALE`、`OFF_SALE` |

### `product_package` 与 `package_item`

套餐保存总价、描述、封面和状态。明细保存 `bead_sku_id`、`quantity` 与 `sort_order`，不保存每颗珠子的独立业务 ID。进入 DIY 时，前端按 `sort_order` 和 `quantity` 展开为 `[skuId, skuId, ...]`。

## 3. 访客身份

### `customer_identity`

| 字段 | 说明 |
| --- | --- |
| `source` | `GUEST` 或预留的 `WECHAT` |
| `external_key_hash` | 安装 UUID 加 pepper 后的哈希，唯一 |
| `status` | `ACTIVE`、`DISABLED` |

当前小程序使用安装 UUID 换取顾客 JWT；未接微信授权。方案访问按 `customer_identity_id` 隔离。

## 4. DIY 配置表

### `diy_rule_config`

保存全局 DIY 规则：腕围最小/最大值、贴手/标准/宽松余量、尺寸容差、最大珠数和 `config_version`。后台修改后，小程序配置和权威报价都读取最新值。

### `craft_fee_rule`

按珠数区间匹配工艺费，包含 `min_bead_count`、`max_bead_count`、`fee`、状态、排序和版本。后台页面阻止区间重叠。

### `cord_option`

保存绳材编码、名称、颜色、附加费、状态、排序和版本。小程序编辑器可选择启用绳材。

## 5. DIY 方案表

### `diy_scheme`

| 字段 | 说明 |
| --- | --- |
| `customer_identity_id` | 方案归属访客 |
| `name` | 方案名称 |
| `wrist_circumference_mm` | 腕围 |
| `fit_type` | `SNUG`、`STANDARD`、`LOOSE` |
| `cord_option_id` | 绳材外键 |
| `sequence_json` | 有序 SKU 数组，唯一权威排珠状态 |
| `bead_count` | 从 `sequence_json` 派生的珠数冗余，便于列表展示 |
| `catalog_snapshot_json` | 保存时的 SKU 名称、直径、封面、状态等快照 |
| `bead_amount/craft_fee/cord_fee/total_amount` | 保存时的报价快照 |
| `quote_fingerprint` | 报价指纹，用于读取时判断是否过期 |
| `quote_status` | `NOT_QUOTED`、`VALID`、`STALE`、`INVALID` |
| `preview_asset_id` | 可空预览图素材 |
| `status` | `ACTIVE`、`DELETED` |
| `version` | 乐观锁版本 |

设计约束：

- 前端临时序号不进入数据库。
- SKU 数量由 `sequence_json` 分组派生，不保存第二份可冲突数量表。
- 旧方案读取时重新按当前 SKU 与配置评估，价格变化标记为 `STALE`。
- 删除为软删除，不物理移除方案记录。

## 6. 查询与约束

- 公共散珠索引：`(status, category_id, id)`。
- 公共套餐索引：`(status, id)`。
- 工艺费匹配索引：`(status, min_bead_count, max_bead_count)`。
- 方案列表索引：`(customer_identity_id, status, updated_at)`。
- 数据库 `check` 约束保证金额、尺寸、数量、状态和文件大小合法。
- 金额映射为 Java `BigDecimal`，前端仅负责显示，不作为最终计价权威。

库存、订单、支付和履约表尚未落地；实施对应阶段时在新 Flyway 迁移中追加。

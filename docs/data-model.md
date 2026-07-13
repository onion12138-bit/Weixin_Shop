# 数据模型

最后更新：2026-07-13

本文件只记录已经通过 Flyway 落地的表。当前迁移：

- `V1__catalog_schema.sql`：商品与素材结构。
- `V2__catalog_seed.sql`：2 个分类、4 个散珠和 1 个套餐演示数据。
- `V3__diy_identity_pricing_schema.sql`：访客身份、DIY 规则、工艺费、绳材和方案结构。
- `V4__diy_seed.sql`：默认 DIY 规则、工艺费和绳材演示配置。
- `V5__commerce_order_inventory_schema.sql`：库存、订单、模拟支付和履约结构及演示库存。
- `V6__ai_model_config.sql`：唯一一行 DeepSeek 配置、默认模型和密钥密文列。

## 1. 关系

```text
category 1 ── n bead_sku
asset_file 1 ── n bead_sku/product_package/diy_scheme（可选封面或预览）
product_package 1 ── n package_item n ── 1 bead_sku
customer_identity 1 ── n diy_scheme n ── 1 cord_option
customer_identity 1 ── n customer_order 1 ── n order_item
diy_scheme 1 ── n customer_order
bead_sku 1 ── 1 inventory_balance 1 ── n inventory_ledger
customer_order 1 ── 1 payment_record
customer_order 1 ── 1 fulfillment_task
diy_rule_config 1 ── 当前全局 DIY 规则
craft_fee_rule n ── 按珠数区间匹配工艺费
ai_model_config 1 ── 全局唯一 DeepSeek 配置（推荐结果不持久化）
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
| `stock_alert_threshold` | 库存预警阈值，非负；余额已落地，后台告警任务待实现 |
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

## 6. 库存、订单、支付和履约表

### `inventory_balance` 与 `inventory_ledger`

`inventory_balance` 每个散珠 SKU 只允许一行，保存：

- `available_qty`：可被新订单预占的数量。
- `reserved_qty`：待支付订单已经占用的数量。
- `sold_qty`：支付成功确认销售的累计数量。
- `version`：每次库存流转递增。

`inventory_ledger` 记录每次流转前后的三类余额，类型包括 `INIT`、`RESERVE`、`PAY_CONFIRM`、`RELEASE`、`ADJUST`。下单、支付和取消均先锁定余额行，再执行带数量下限条件的更新；事务失败时余额、流水和订单一起回滚。

### `customer_order` 与 `order_item`

`customer_order` 保存顾客、来源方案、收货信息、方案快照、分项金额、总额、状态和各阶段时间。状态为：

```text
PENDING_PAYMENT -> PAID -> IN_PRODUCTION -> SHIPPED -> COMPLETED
PENDING_PAYMENT -> CANCELLED
```

`scheme_snapshot_json` 保留原始 SKU 顺序和数量统计；`order_item` 按 SKU 聚合保存编码、名称、数量、单价、总价和稳定排序，作为生产 BOM 与历史价格快照。商品后续调价、改名或下架不会改变已有订单。

### `payment_record`

每个订单最多一条支付记录，`order_id` 与 `payment_no` 均唯一。当前渠道只允许 `MOCK`，状态为 `INIT`、`SUCCESS`、`FAILED`，并保存金额、支付时间和原始响应 JSON。唯一约束与订单行锁共同保证模拟支付幂等。

### `fulfillment_task`

支付成功后每个订单创建唯一履约任务。`bom_json` 保存订单 SKU 数量快照；状态为 `PENDING`、`IN_PRODUCTION`、`SHIPPED`、`COMPLETED`。发货保存模拟物流公司、运单号和时间，完成保存完成时间。

### V5 演示库存

SKU `2001`～`2004` 各初始化 200 个可用库存，并写入 `INIT` 流水。该数据只用于本地开发和演示，生产环境必须使用正式库存初始化流程。

## 7. DeepSeek 单例配置与隐私边界

### `ai_model_config`

Flyway V6 固定创建 `id=1`、`provider=DEEPSEEK` 的单例行。字段包括官方 `base_url`、默认 `model_name=deepseek-v4-flash`、`encrypted_api_key`、启停状态、请求超时、最大输出 Token、乐观锁 `version` 和审计时间。

- API Key 使用 AES-256-GCM，每次保存生成随机 12-byte nonce，数据库只保存 `base64(nonce + ciphertext + tag)`。
- `AI_CONFIG_ENCRYPTION_KEY` 是 Base64 编码的 32 字节主密钥，只来自后端运行环境，不保存到数据库或仓库。
- 后台读取只返回 `apiKeyConfigured`，不返回明文或密文；空 Key 保存会保留已有密钥。
- 推荐请求、生日、自由文本、模型原始响应和推荐结果均不落库。最终方案只有在顾客进入 DIY 并主动保存后才按既有 `diy_scheme` 模型持久化。

### 3 款图片演示散珠导入

`scripts/import-demo-beads.sh` 不是 Flyway 种子，它分页查询后台目录并按稳定 SKU 编码幂等导入；已存在项直接跳过，不重复上传图片或创建库存。2026-07-13 的真实运行证据为首次创建三项、第二次三项全部跳过。

| SKU | 名称 | 售价 | 成本 | `available_qty` | 初始流水 |
| --- | --- | ---: | ---: | ---: | --- |
| `BEAD-LABRADORITE-8MM` | 拉长石 8mm | 9.80 | 2.80 | 200 | `INIT +200` |
| `BEAD-GARNET-8MM` | 石榴石 8mm | 9.50 | 2.50 | 200 | `INIT +200` |
| `BEAD-FLUORITE-8MM` | 萤石 8mm | 8.80 | 1.80 | 200 | `INIT +200` |

创建接口在一个事务中写 `bead_sku`、`inventory_balance` 和非零初始库存的 `inventory_ledger`；任一写入失败全部回滚。编辑 DTO 不含 `initialStock`，避免把商品编辑误当库存调整。

## 8. 查询与约束

- 公共散珠索引：`(status, category_id, id)`。
- 公共套餐索引：`(status, id)`。
- 工艺费匹配索引：`(status, min_bead_count, max_bead_count)`。
- 方案列表索引：`(customer_identity_id, status, updated_at)`。
- 顾客订单索引：`(customer_identity_id, status, updated_at)`；后台订单索引：`(status, updated_at)`。
- 库存预警索引：`available_qty`；库存流水按 SKU/时间和订单建立索引。
- 数据库 `check` 约束保证金额、尺寸、数量、状态和文件大小合法。
- 金额映射为 Java `BigDecimal`，前端仅负责显示，不作为最终计价权威。

- 订单、支付记录和履约任务均有唯一业务约束，防止重复支付和重复建任务。

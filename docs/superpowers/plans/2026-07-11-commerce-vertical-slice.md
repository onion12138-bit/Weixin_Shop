# 库存、订单、模拟支付和履约闭环实施计划

创建时间：2026-07-11

## 1. 范围

本阶段目标是在不接真实支付的前提下，把当前 DIY 方案转成可下单、可模拟支付、可履约处理的完整业务闭环。

已确认限制：

- 不接真实微信支付，只实现模拟支付接口。
- 不接微信授权，继续使用当前顾客 JWT。
- 不接真实物流，只保存模拟物流单号。
- 不实现退款、优惠券、发票、售后。

## 2. 核心设计

### 2.1 库存

新增 `inventory_balance` 和 `inventory_ledger`：

- `inventory_balance` 按 SKU 保存 `available_qty`、`reserved_qty`、`sold_qty` 和乐观锁。
- `inventory_ledger` 记录 `INIT`、`RESERVE`、`PAY_CONFIRM`、`RELEASE`、`ADJUST`。
- 下单时把 `available` 转入 `reserved`。
- 模拟支付成功时把 `reserved` 转入 `sold`。
- 支付前取消时释放预占库存。

### 2.2 订单

新增 `customer_order`、`order_item`：

- 订单归属 `customer_identity_id`。
- 订单保存方案快照、SKU 数量、价格快照、收货信息和状态。
- 创建订单必须基于当前访客拥有的有效 DIY 方案。
- 创建订单时重新权威计价并校验库存，不能信任小程序价格。

订单状态：

```text
PENDING_PAYMENT -> PAID -> IN_PRODUCTION -> SHIPPED -> COMPLETED
PENDING_PAYMENT -> CANCELLED
```

### 2.3 模拟支付

新增 `payment_record`：

- 支付渠道固定为 `MOCK`。
- `POST /api/app/orders/{id}/mock-pay` 把待支付订单置为 `PAID`。
- 模拟支付必须幂等：重复支付已支付订单返回当前订单，不重复扣库存。

### 2.4 履约

新增 `fulfillment_task`：

- 支付成功后生成制作任务。
- 后台可把订单改为制作中、已发货、已完成。
- 发货时保存模拟物流公司和运单号。
- 生产 BOM 从 `order_item` 读取 SKU 数量。

## 3. 任务分解

### Task 1：数据库迁移和种子库存

**文件：**

- Create: `backend/src/main/resources/db/migration/V5__commerce_order_inventory_schema.sql`
- Create: `backend/src/test/java/com/diybracelet/mall/order/infrastructure/CommerceMigrationTest.java`

**步骤：**

1. 写失败测试，验证 `inventory_balance`、`inventory_ledger`、`customer_order`、`order_item`、`payment_record`、`fulfillment_task` 存在。
2. 创建 V5 迁移。
3. 为演示 SKU `2001`～`2004` 初始化库存。
4. 运行 `mvn -Dtest=CommerceMigrationTest test`。

### Task 2：后端订单创建和库存预占

**文件：**

- Create: `backend/src/main/java/com/diybracelet/mall/inventory/**`
- Create: `backend/src/main/java/com/diybracelet/mall/order/**`
- Create: `backend/src/test/java/com/diybracelet/mall/order/api/AppOrderControllerTest.java`

**步骤：**

1. 写失败测试：顾客基于已保存方案创建订单后，订单为 `PENDING_PAYMENT`，SKU 顺序快照保留，库存从 available 转 reserved。
2. 写失败测试：库存不足返回稳定业务错误，不创建订单。
3. 实现库存服务、订单服务、Mapper 和小程序订单接口。
4. 运行目标测试。

### Task 3：模拟支付和取消

**文件：**

- Modify: `backend/src/main/java/com/diybracelet/mall/order/**`
- Modify: `backend/src/test/java/com/diybracelet/mall/order/api/AppOrderControllerTest.java`

**步骤：**

1. 写失败测试：模拟支付成功后订单为 `PAID`，库存 reserved 转 sold，生成 `payment_record` 和 `fulfillment_task`。
2. 写失败测试：重复模拟支付幂等，不重复扣库存。
3. 写失败测试：待支付订单取消释放库存。
4. 实现支付和取消接口。

### Task 4：后台订单履约接口

**文件：**

- Create: `backend/src/test/java/com/diybracelet/mall/order/api/AdminOrderControllerTest.java`
- Modify: `backend/src/main/java/com/diybracelet/mall/order/**`

**步骤：**

1. 写失败测试：管理员可分页查看订单和 BOM。
2. 写失败测试：管理员把 `PAID` 改为 `IN_PRODUCTION`。
3. 写失败测试：管理员发货后状态为 `SHIPPED`，保存物流信息。
4. 写失败测试：管理员完成订单后状态为 `COMPLETED`。
5. 实现后台接口和状态机校验。

### Task 5：管理后台页面

**文件：**

- Create: `admin-web/src/api/orders.ts`
- Create: `admin-web/src/views/OrderView.vue`
- Create: `admin-web/src/views/OrderView.spec.ts`
- Modify: `admin-web/src/App.vue`

**步骤：**

1. 写失败测试：订单页面渲染订单号、状态、金额、BOM 和操作按钮。
2. 实现订单列表、详情、制作、发货、完成操作。
3. 运行 `npm test -- OrderView.spec.ts`、`npm test`、`npm run build`。

### Task 6：小程序订单和支付页面

**文件：**

- Create: `miniprogram/miniprogram/services/orders.ts`
- Create: `miniprogram/miniprogram/pages/order/confirm.*`
- Create: `miniprogram/miniprogram/pages/order/list.*`
- Create: `miniprogram/miniprogram/pages/order/detail.*`
- Create: `miniprogram/src/order-view-model.spec.ts`
- Modify: `miniprogram/miniprogram/pages/diy/schemes.wxml`
- Modify: `miniprogram/miniprogram/pages/diy/schemes.ts`
- Modify: `miniprogram/miniprogram/app.json`

**步骤：**

1. 写失败测试：订单视图模型格式化状态、金额和 BOM。
2. 在方案列表增加“去下单”。
3. 确认页填写收货信息并创建订单。
4. 详情页支持模拟支付和取消。
5. 订单列表展示当前访客订单。
6. 运行小程序测试和类型检查。

### Task 7：真实 HTTP 冒烟、文档和进度

**文件：**

- Create: `scripts/order-smoke.sh`
- Modify: `docs/*.md`
- Modify: `README.md`
- Modify: `CHANGELOG.md`

**步骤：**

1. 新增冒烟脚本：创建访客、保存方案、创建订单、模拟支付、后台登录、制作、发货、完成。
2. 运行 `./scripts/verify.sh`、`./scripts/catalog-smoke.sh`、`./scripts/diy-smoke.sh`、`./scripts/order-smoke.sh`。
3. 更新文档和 `docs/progress.md`。
4. 提交 `feat: add order and mock payment vertical slice`。

## 4. 验收门槛

- 下单重新使用后端权威报价。
- 库存不足不能创建订单。
- 下单预占库存，支付转销售，取消释放库存。
- 模拟支付幂等。
- 订单只允许所属访客访问。
- 管理员 token 不能访问顾客订单接口；顾客 token 不能访问后台订单接口。
- 后台状态流转不能跳步。
- 订单快照保留方案 SKU 顺序和 SKU 数量。
- 全仓验证和三个 HTTP 冒烟脚本通过。

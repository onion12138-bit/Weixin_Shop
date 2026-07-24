# 开发规范

最后更新：2026-07-11

## 1. 环境

进入项目后先执行 `source ./scripts/project-env.sh`，统一使用 JDK 17、项目目录内 Maven 3.9.16 和 Docker Desktop CLI。Node 依赖必须通过锁文件安装。

项目声明使用 pnpm；如果本机暂未安装 pnpm 但 `node_modules` 已存在，可临时用 `npm test`、`npm run typecheck`、`npm run build` 执行同名脚本，不得改动锁文件。

## 2. 后端约定

- 包按业务模块划分，再分 `api/application/domain/infrastructure`。
- Controller 负责 HTTP 契约；应用服务负责事务和业务规则；Mapper 负责 SQL。
- 金额用 `BigDecimal`，时间使用明确时区的 Java Time 类型。
- 新表或字段只新增 Flyway 迁移，禁止修改已经在共享环境执行的迁移。
- 所有可预期失败转换为稳定 `ErrorCode`，禁止把数据库异常直接暴露给客户端。
- 公共 DTO、管理 DTO 和小程序 DTO 分离，避免成本价或内部字段泄漏。
- 管理员 JWT 与顾客 JWT 不得共用过滤器判断结果；接口前缀必须明确。

## 3. DIY 设计约定

- 方案状态只保存有序 SKU 数组，不保存“每颗珠子”的业务 ID。
- 展示序号只在前端临时生成。
- SKU 数量由顺序数组分组派生，不保存第二份可冲突数量状态。
- 权威报价只由后端读取服务端 SKU、DIY 规则、工艺费和绳材配置计算。
- 小程序本地预估价只能用于即时反馈，任何编辑行为都必须清除权威报价标记。
- 尺寸不合适可保存草稿，但不可生成可用权威报价。
- 方案更新必须带 `version`，冲突返回 409。

## 4. 前端约定

- Vue 请求统一通过 `src/api/http.ts`，401 时清理令牌。
- 小程序请求统一通过 `services/request.ts`，页面不拼接后端响应协议。
- 小程序顾客会话由 `services/session.ts` 统一管理，页面不直接读写 token。
- DIY 设计器的状态、计价、校验必须使用可独立测试的纯 TypeScript 模块；Canvas 只负责渲染和命中测试。
- 图片使用后端公开 URL，不依赖本地文件绝对路径。
- 预览上传失败不得回滚方案保存结果，只记录待重试状态。
- 管理后台订单页面只能调用明确的制作、发货、完成动作接口，不能直接提交任意订单状态。
- 小程序下单只提交方案和收货信息，不提交或覆盖服务端订单金额。

## 5. 库存、订单与支付约定

- 创建订单、预占库存、支付确认和取消释放必须处于应用服务事务中。
- 并发修改先锁定订单或 SKU 余额行，更新 SQL 必须同时校验前置数量/状态。
- 一次操作涉及多个 SKU 时，必须先按 SKU 主键升序排序，再依次获取余额行锁。
- 订单创建重新调用 `DiyPricingService`，禁止信任前端价格和旧方案报价快照。
- 订单保存 SKU 名称、编码、数量和单价快照；履约只读取订单 BOM，不读取实时商品价格。
- 支付接口必须幂等；重复调用不得重复扣库存、写支付记录或创建履约任务。
- 后台状态机只允许 `PAID → IN_PRODUCTION → SHIPPED → COMPLETED`；待支付订单只能支付或取消。
- 新接微信支付时保持现有订单/库存语义，通过 `PaymentGateway` 适配外部下单和回调。

## 6. 注释

- Java 类、接口和公共方法使用中文 Javadoc。
- 计价、库存、订单、状态机、支付和文件安全说明原因、边界和失败策略。
- TypeScript 公共函数、组件职责和 Canvas 算法提供说明。
- 不添加只重复代码字面含义的注释。

## 7. 增加可替换实现

以对象存储为例：

1. 实现 `FileStorage`，不修改商品或方案模块。
2. 增加配置属性和条件装配。
3. 保持 `StoredFile` 与公开 URL 契约不变。
4. 为路径、大小、MIME、读取失败和替换配置补测试。
5. 更新架构、部署和测试文档。

支付、微信身份和智能推荐采用相同的端口/适配器方式。

## 8. 提交与验证

每个提交只完成一个可验证目标，提交前运行对应模块测试。较大功能应先更新设计和实施计划，并使用测试驱动开发。

合入前执行：

```bash
./scripts/verify.sh
./scripts/catalog-smoke.sh
./scripts/diy-smoke.sh
./scripts/order-smoke.sh
```

数据库集成测试必须连接真实 Docker MySQL，而不是 H2。

## 9. 手工固定视图素材与冒烟

准备两张透明 PNG 或 WebP：托盘图必须为 160×160，Canvas 合成图必须为 512×512。使用本地绝对
路径，并由服务端在创建手工视觉版本时复核透明通道和尺寸；不要把素材路径、管理员令牌或真实素材
提交进仓库。

`scripts/diy-visual-smoke.sh` 默认不会由 `verify.sh` 调用。它必须显式提供一个在售 SKU 和两份素材：

```bash
DIY_VISUAL_SMOKE_SKU=BEAD-CRYSTAL-001 \
DIY_VISUAL_TRAY_FILE=/absolute/path/tray.png \
DIY_VISUAL_CANVAS_FILE=/absolute/path/canvas.png \
bash scripts/diy-visual-smoke.sh
```

该命令会登录管理员、上传素材、创建并发布一个新视觉版本，然后读取公共散珠验证
`diyVisual.version/trayUrl/canvasUrl`。它不删除数据，也不修改商品价格、库存或上下架字段，但发布会
改变该 SKU 的当前展示图，故只能在明确选定的可接受 SKU 上运行。

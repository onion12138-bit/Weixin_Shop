# 测试说明

最后更新：2026-07-14

## 1. 常用命令

- 后端：`cd backend && mvn test`
- 管理后台：`cd admin-web && pnpm test && pnpm build`
- 小程序：`cd miniprogram && pnpm typecheck && pnpm test`
- 全仓验证：`./scripts/verify.sh`
- 商品目录真实 HTTP 冒烟：`./scripts/catalog-smoke.sh`
- DIY 真实 HTTP 冒烟：`./scripts/diy-smoke.sh`
- 订单与履约真实 HTTP 冒烟：`./scripts/order-smoke.sh`
- AI 推荐暂停/live 双路径 HTTP 冒烟：`./scripts/recommendation-smoke.sh`
- 固定视图手工真实 HTTP 冒烟（显式变更数据）：`bash scripts/diy-visual-smoke.sh`，需提供下述三个环境变量。

当前本机如果没有 pnpm 但 `node_modules` 已存在，可临时用 `npm test`、`npm run build`、`npm run typecheck` 执行同名脚本；提交前不得改动锁文件。

每个功能遵循先写失败测试、再写最小实现、最后重构的顺序。

## 2. 当前覆盖

### 后端

- Flyway 空库迁移、表结构和演示数据。
- Mapper 查询、上下架过滤和分页。
- 公共商品目录与管理 CRUD 的 MySQL 集成测试。
- 管理员登录、JWT 保护、401/403 JSON。
- 访客安装身份、顾客 JWT 和顾客接口保护。
- DIY 配置读取、后台规则保存、工艺费区间校验和绳材维护。
- DIY 权威报价、SKU 数量派生、尺寸校验和不可用 SKU 问题返回。
- DIY 方案新建、读取、更新、乐观锁冲突、跨访客隔离、复制、软删除、价格过期标记和预览上传。
- V5 库存、订单、支付和履约空库迁移及演示库存。
- 下单权威重算、SKU 聚合明细、稳定升序行锁、库存预占、库存不足事务回滚和本人订单查询隔离。
- 模拟支付后预占转已售、支付记录/履约任务生成、重复支付幂等和取消释放库存。
- 后台订单与 BOM 分页查询、制作、发货、完成和禁止状态跳步。
- 本地存储 MIME、大小、路径安全，上传/读取控制器。
- 健康接口、统一错误和参数校验。
- V6 DeepSeek 单例迁移、AES-GCM 往返/篡改/主密钥校验、Key 掩码、官方 Base URL 和乐观锁。
- DeepSeek `/models` 与 `/chat/completions` 请求、超时、无重试、严格 JSON、上游错误映射及敏感字段不泄漏。
- 推荐候选目录、可用状态、单套推荐、SKU/库存/预算/腕围/文案/权威价格复核和文化娱乐免责声明。

数据库测试基于 Testcontainers MySQL 8，需要 Docker Desktop 正常运行。

### 管理后台

- 商品目录页面挂载、API 数据呈现和主要交互。
- DIY 配置页面加载、规则保存、工艺费区间校验和绳材编辑。
- 订单页面金额、收货信息、BOM、动作按钮和制作状态更新。
- `vue-tsc` 严格类型检查和 Vite 生产构建。
- AI 配置 API、页面加载/保存/清 Key/测试连接、安全错误映射和不回显 Key。

### 小程序

- 商品卡片和价格展示纯函数。
- 访客会话创建、安装 UUID 复用和并发刷新合并。
- DIY 编辑状态、SKU 顺序保持、数量调整、撤销/重做。
- 手串环形布局坐标。
- SKU 数量统计和整数分计价。
- DIY 视图模型：套餐数量展开、尺寸/价格文案。
- 订单视图模型：状态、金额、BOM、进度和支付/取消按钮规则。
- 所有运行时页面/服务的 TypeScript 静态检查。
- 微信开发者工具中的 WXML/WXSS 渲染仍需人工烟测。
- AI 咨询字段校验、精确暂停文案、单套结果展示、图片/金额/免责声明，以及跨分页目录的一次性 DIY handoff。

## 3. 已知验证数量

- 后端当前完整测试套件：176 个 JUnit/Testcontainers 测试。
- 管理后台当前完整测试套件：6 个 Vitest 文件、37 个测试，并执行类型检查与生产构建。
- 小程序当前完整测试套件：14 个 Jest 套件、85 个测试，并执行运行时类型检查。
- Shell：`recommendation-smoke.test.sh` 固定边界样本并连续生成 512 个访客 UUID；`verify.sh` 还执行推荐脚本 `bash -n` 和静态端点/安全契约检查。

以上数量以最近一次 `./scripts/verify.sh` 输出为准；新增测试后必须同步更新本节。

## 4. 真实 HTTP 冒烟

后端启动后：

```bash
./scripts/catalog-smoke.sh
./scripts/diy-smoke.sh
./scripts/order-smoke.sh
./scripts/recommendation-smoke.sh
```

`diy-smoke.sh` 会生成随机安装 UUID，依次验证：

1. `POST /api/app/session/guest`
2. `GET /api/app/diy/config`
3. 21 颗演示珠权威报价，总价应为 `314.80`
4. 新建方案
5. 读取方案并校验 SKU 顺序
6. 复制方案
7. 删除副本

`order-smoke.sh` 会继续验证：

1. 基于保存方案创建订单并校验权威总价和 BOM
2. 顾客订单列表和详情可读取本人订单
3. 连续两次模拟支付均返回 `PAID`
4. 管理员可查询已支付订单并按顺序开始制作、发货、完成
5. 发货保存模拟运单号，完成状态对顾客可见
6. 第二笔待支付订单可以取消并释放库存

`recommendation-smoke.sh` 默认不需要 Key，验证：

1. 创建 guest JWT 并读取 `GET /api/app/recommendations/status`；
2. 只接受精确的 `NOT_CONFIGURED` 或 `DISABLED` 暂停状态；
3. POST 必须分别返回 `AI_NOT_CONFIGURED` 或 `AI_DISABLED`；
4. 明确输出“未调用 DeepSeek，也未使用本地降级”；若无 Key 却返回 `AVAILABLE`，脚本失败并要求改走显式 live 路径。

2026-07-13 实测：`catalog`、`diy`、`order` 三个真实 HTTP smoke 均通过；推荐脚本以 `NOT_CONFIGURED → AI_NOT_CONFIGURED` 通过。3 款演示散珠首次创建成功，第二次运行全部按 SKU 跳过。以上没有访问真实 DeepSeek。

只有在安全环境显式设置有效 `DEEPSEEK_API_KEY` 与后端启动时相同的 `AI_CONFIG_ENCRYPTION_KEY`，推荐脚本才会执行外部 live 路径。它会保存官方地址、`deepseek-v4-flash`、启用/超时/Token 配置，调用 `/test`，再请求一套推荐，并分页读取全部公共目录，断言每个 sequence SKU、非负总价、`quoteFingerprint`、免责声明和图片。截至 2026-07-13 尚未提供真实 Key，因此这条 live 验证仍是明确待办，不能记为通过。

## 5. 合入门槛

1. `./scripts/verify.sh` 返回 0。
2. Docker 集成测试没有跳过。
3. 四个 `catalog`、`diy`、`order`、`recommendation` 冒烟脚本返回 0；默认 recommendation 只要求暂停路径，不产生费用。
4. 用户在后台安全输入有效 Key 后，另行完成一次 opt-in live `/test` + 推荐验证并记录结果。
5. 管理后台完成人工烟测：登录、商品维护、DIY 配置、订单履约、AI 配置保存和安全错误显示。
6. 小程序完成人工烟测：访客身份、DIY/订单闭环、推荐暂停文案、可用时的咨询结果与一次性 handoff。
7. 文档中的“已实现”和“已验证”必须分开，不能把模拟网关测试或暂停 smoke 写成真实 DeepSeek 成功。

## 6. 固定视图手工冒烟

默认 `./scripts/verify.sh` 仅运行 `diy-visual-smoke.sh` 的 `bash -n` 与端点/公开字段静态契约，
不会访问后端、更不会创建视觉版本。真实冒烟必须由操作者显式选择运行中的 8081 后端、管理员凭证、
一个可公开读取的 SKU，以及本地透明素材：

```bash
DIY_VISUAL_SMOKE_SKU=BEAD-CRYSTAL-001 \
DIY_VISUAL_TRAY_FILE=/absolute/path/tray.png \
DIY_VISUAL_CANVAS_FILE=/absolute/path/canvas.png \
bash scripts/diy-visual-smoke.sh
```

脚本依次登录管理员、按 SKU 精确定位散珠、上传 160×160 托盘和 512×512 Canvas、创建并发布手工
版本，最后通过公共散珠接口断言 `diyVisual.version`、`trayUrl`、`canvasUrl`。该操作有意保留上传
素材和历史版本，且会改变 SKU 当前视觉版本；没有真实调用者素材与明确授权时不得运行。

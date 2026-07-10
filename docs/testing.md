# 测试说明

最后更新：2026-07-10

## 1. 常用命令

- 后端：`cd backend && mvn test`
- 管理后台：`cd admin-web && pnpm test && pnpm build`
- 小程序：`cd miniprogram && pnpm typecheck && pnpm test`
- 全仓验证：`./scripts/verify.sh`
- 商品目录真实 HTTP 冒烟：`./scripts/catalog-smoke.sh`
- DIY 真实 HTTP 冒烟：`./scripts/diy-smoke.sh`

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
- 本地存储 MIME、大小、路径安全，上传/读取控制器。
- 健康接口、统一错误和参数校验。

数据库测试基于 Testcontainers MySQL 8，需要 Docker Desktop 正常运行。

### 管理后台

- 商品目录页面挂载、API 数据呈现和主要交互。
- DIY 配置页面加载、规则保存、工艺费区间校验和绳材编辑。
- `vue-tsc` 严格类型检查和 Vite 生产构建。

### 小程序

- 商品卡片和价格展示纯函数。
- 访客会话创建、安装 UUID 复用和并发刷新合并。
- DIY 编辑状态、SKU 顺序保持、数量调整、撤销/重做。
- 手串环形布局坐标。
- SKU 数量统计和整数分计价。
- DIY 视图模型：套餐数量展开、尺寸/价格文案。
- 所有运行时页面/服务的 TypeScript 静态检查。
- 微信开发者工具中的 WXML/WXSS 渲染仍需人工烟测。

## 3. 已知验证数量

- 后端当前完整测试套件：39 个 JUnit/Testcontainers 测试。
- 管理后台当前完整测试套件：6 个 Vitest 测试，并执行生产构建。
- 小程序当前完整测试套件：7 个 Jest 套件、11 个测试，并执行运行时类型检查。

以上数量以最近一次 `./scripts/verify.sh` 输出为准；新增测试后必须同步更新本节。

## 4. 真实 HTTP 冒烟

后端启动后：

```bash
./scripts/catalog-smoke.sh
./scripts/diy-smoke.sh
```

`diy-smoke.sh` 会生成随机安装 UUID，依次验证：

1. `POST /api/app/session/guest`
2. `GET /api/app/diy/config`
3. 21 颗演示珠权威报价，总价应为 `314.80`
4. 新建方案
5. 读取方案并校验 SKU 顺序
6. 复制方案
7. 删除副本

## 5. 合入门槛

1. `./scripts/verify.sh` 返回 0。
2. Docker 集成测试没有跳过。
3. `./scripts/catalog-smoke.sh` 和 `./scripts/diy-smoke.sh` 返回 0。
4. 管理后台完成人工烟测：登录、商品维护、DIY 规则保存、工艺费重叠拦截、绳材维护。
5. 小程序完成人工烟测：访客身份、套餐展开、编辑、撤销重做、报价、保存、恢复、复制、删除、断网恢复和预览失败降级。
6. 文档中的“已实现”必须与代码一致。

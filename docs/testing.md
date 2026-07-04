# 测试说明

最后更新：2026-07-04

- 后端：`cd backend && mvn test`
- 管理后台：`cd admin-web && pnpm test && pnpm build`
- 小程序：`cd miniprogram && pnpm typecheck && pnpm test`
- 全仓验证：`./scripts/verify.sh`
- 已启动后端的真实 HTTP 冒烟：`./scripts/catalog-smoke.sh`

每个功能遵循先写失败测试、再写最小实现、最后重构的顺序。

## 当前覆盖

### 后端

- Flyway 空库迁移、表结构和演示数据。
- Mapper 查询、上下架过滤和分页。
- 公共商品目录与管理 CRUD 的 MySQL 集成测试。
- 管理员登录、JWT 保护、401/403 JSON。
- 本地存储 MIME、大小、路径安全，上传/读取控制器。
- 健康接口、统一错误和参数校验。

数据库测试基于 Testcontainers MySQL 8，需要 Docker Desktop 正常运行。

### 管理后台

- 项目状态组件。
- 商品目录页面挂载、API 数据呈现和主要交互。
- `vue-tsc` 严格类型检查和 Vite 生产构建。

### 小程序

- 商品卡片和价格展示纯函数。
- 项目摘要纯函数。
- 所有运行时页面/服务的 TypeScript 静态检查。
- 微信开发者工具中的 WXML/WXSS 渲染仍需人工烟测。

## 合入门槛

1. `./scripts/verify.sh` 返回 0。
2. Docker 集成测试没有跳过。
3. 管理后台完成登录、上传、新增、上下架烟测。
4. 小程序完成目录、筛选、分页、详情和错误重试烟测。
5. 文档中的“已实现”必须与代码一致。

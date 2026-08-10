# DIY 手串定制商城

一个由 Spring Boot 后端、Vue 3 管理后台和原生微信小程序组成的 DIY 手串定制商品系统。

## 当前状态

截至 2026-08-09，项目已经实现本地可用的 DIY 商品、模拟交易和多套 AI 推荐闭环：

- Spring Boot：默认端口 8081、Flyway 迁移、商品目录、图片、双 JWT、DIY 配置/报价/方案、库存与订单、DeepSeek 单例配置、后端校验推荐和管理员调优日志。
- Vue 管理后台：登录、商品/DIY/订单维护，以及 DeepSeek 配置/测试、业务/软偏好提示词和 AI 调用日志。
- 微信小程序：首页、目录、详情、DIY Canvas、方案/订单，以及结构化 AI 咨询、服务端价格展示和一次性 DIY handoff。
- 基础设施：MySQL、Redis、本地文件存储、三端自动化验证、3 款图片演示散珠的幂等导入，以及商品、DIY、订单、推荐四个真实 HTTP 冒烟脚本。

未接入真实微信授权、真实对象存储、微信支付和退款售后。AI 推荐没有规则或本地降级：未配置 Key 时精确暂停，只有管理员显式配置并启用 DeepSeek 后才会访问外部模型。自动化和默认暂停路径已经验证；截至 2026-07-13 尚未使用用户的有效 Key 完成真实 DeepSeek live call，不能把当前状态表述为外部模型已验证。

## 完整产品规划

- 分类、珠子 SKU、套餐和素材管理（已实现）
- Canvas 2D DIY 排珠、规则配置、权威计价、方案保存和预览图（已实现）
- 库存预占、订单、模拟支付、生产 BOM 和后台履约（已实现）
- DeepSeek 多套推荐、可配置软偏好、后台调优日志、服务端复核和一次性进入 DIY（已实现；软偏好真实效果待验收）
- 微信登录、微信支付和对象存储替换（后续第三方集成）

## 项目结构

- `backend/`：Spring Boot 模块化单体
- `admin-web/`：Vue 3 管理后台
- `miniprogram/`：原生微信小程序
- `docs/`：当前有效的项目文档
- `scripts/`：环境配置、检查、统一验证和 HTTP 冒烟脚本
- `storage/`：本地上传文件，运行数据不提交

## 快速开始

完整步骤见 [`docs/setup.md`](docs/setup.md)。首次启动：

```bash
cp .env.example .env
source ./scripts/project-env.sh
./scripts/check-prerequisites.sh
docker compose up -d
```

后端、管理后台和小程序分别启动后，后端默认地址为 `http://localhost:8081`，管理后台为 `http://localhost:5173`，默认演示账号为 `admin / Admin@123`。本地一键启动会在 Git 忽略的 `.local-secrets/` 中生成并复用 AI 加密主密钥；正式部署必须更改账号、密码、JWT 密钥，并在源码仓库外配置和备份 `AI_CONFIG_ENCRYPTION_KEY`。

## 验证

```bash
./scripts/verify.sh
./scripts/catalog-smoke.sh
./scripts/diy-smoke.sh
./scripts/order-smoke.sh
./scripts/recommendation-smoke.sh
```

四个冒烟脚本都需要 8081 后端已启动；订单脚本会写入演示订单、模拟支付和履约数据。推荐脚本默认只验证 `NOT_CONFIGURED`/`DISABLED` 暂停路径，不会访问 DeepSeek；只有同时显式提供 `DEEPSEEK_API_KEY` 与后端主密钥环境时才进入 live 路径。完整命令和安全边界见 [`docs/testing.md`](docs/testing.md)。

## 文档总览

[`docs/overview.md`](docs/overview.md) 是文档入口，[`docs/progress.md`](docs/progress.md) 是当前完成进度的唯一权威记录。需求、架构、数据模型、API、开发、启动、部署和测试文档均保存在 `docs/`，文件内容只维护当前有效版本。

## 设计与计划

- 最新设计：`docs/superpowers/specs/2026-07-04-diy-bracelet-mall-design.md`
- 商品目录纵向链路计划：`docs/superpowers/plans/2026-07-04-catalog-vertical-slice.md`
- DIY 编辑器与计价计划：`docs/superpowers/plans/2026-07-04-diy-editor-pricing.md`
- 库存、订单、模拟支付与履约计划：`docs/superpowers/plans/2026-07-11-commerce-vertical-slice.md`

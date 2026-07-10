# DIY 手串定制商城

一个由 Spring Boot 后端、Vue 3 管理后台和原生微信小程序组成的 DIY 手串定制商品系统。

## 当前状态

截至 2026-07-10，项目已经实现第一条 DIY 可用产品链路：

- Spring Boot：Flyway 数据库迁移、商品目录、图片上传、管理员 JWT、访客身份、DIY 配置、权威报价和方案生命周期。
- Vue 管理后台：管理员登录、分类/散珠/套餐维护、有序明细、封面上传、上下架和 DIY 规则配置。
- 微信小程序：首页、商品目录、搜索筛选、详情、DIY Canvas 工作台、权威报价、方案保存和“我的方案”列表。
- 基础设施：MySQL、Redis、本地文件存储、三端自动化验证、商品目录 HTTP 冒烟和 DIY HTTP 冒烟脚本。

未接入真实微信授权、真实对象存储、推荐、库存、订单、模拟支付和履约。当前版本可用于产品展示和本地演示，但不能完成真实交易收款。项目完整范围与实际进度见 [`docs/overview.md`](docs/overview.md) 和 [`docs/progress.md`](docs/progress.md)。

## 完整产品规划

- 分类、珠子 SKU、套餐和素材管理（已实现）
- Canvas 2D DIY 排珠、规则配置、权威计价、方案保存和预览图（已实现）
- 规则推荐、库存预占、订单、模拟支付和生产 BOM（后续阶段）
- 管理后台履约、用户和运营配置（后续阶段）
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

后端、管理后台和小程序分别启动后，管理后台默认地址为 `http://localhost:5173`，默认演示账号为 `admin / Admin@123`。正式部署必须更改账号、密码与 JWT 密钥。

## 验证

```bash
./scripts/verify.sh
./scripts/catalog-smoke.sh
./scripts/diy-smoke.sh
```

`catalog-smoke.sh` 和 `diy-smoke.sh` 需要后端已启动。

## 文档总览

[`docs/overview.md`](docs/overview.md) 是文档入口，[`docs/progress.md`](docs/progress.md) 是当前完成进度的唯一权威记录。需求、架构、数据模型、API、开发、启动、部署和测试文档均保存在 `docs/`，文件内容只维护当前有效版本。

## 设计与计划

- 最新设计：`docs/superpowers/specs/2026-07-04-diy-bracelet-mall-design.md`
- 商品目录纵向链路计划：`docs/superpowers/plans/2026-07-04-catalog-vertical-slice.md`
- DIY 编辑器与计价计划：`docs/superpowers/plans/2026-07-04-diy-editor-pricing.md`

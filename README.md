# DIY 手串定制商城

一个由 Spring Boot 后端、Vue 3 管理后台和原生微信小程序组成的 DIY 手串定制商品系统。

## 当前状态

截至 2026-07-11，项目已经实现本地可用的 DIY 商品与模拟交易闭环：

- Spring Boot：Flyway V1～V5、商品目录、图片、双 JWT、DIY 配置/报价/方案、库存余额与流水、订单快照、幂等模拟支付和履约状态机。
- Vue 管理后台：登录、分类/散珠/套餐/封面维护、DIY 规则配置、订单与 BOM 查询、制作、发货和完成。
- 微信小程序：首页、目录、详情、DIY Canvas 工作台、方案管理、订单确认、我的订单、详情、模拟支付和取消。
- 基础设施：MySQL、Redis、本地文件存储、三端自动化验证及商品、DIY、订单三个真实 HTTP 冒烟脚本。

未接入真实微信授权、真实对象存储、微信支付、退款售后和规则推荐。当前版本可以完成除真实扣款外的本地产品操作与演示；“模拟支付”不会产生真实交易。项目完整范围与实际进度见 [`docs/overview.md`](docs/overview.md) 和 [`docs/progress.md`](docs/progress.md)。

## 完整产品规划

- 分类、珠子 SKU、套餐和素材管理（已实现）
- Canvas 2D DIY 排珠、规则配置、权威计价、方案保存和预览图（已实现）
- 库存预占、订单、模拟支付、生产 BOM 和后台履约（已实现）
- 规则推荐、用户和运营配置（后续阶段）
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
./scripts/order-smoke.sh
```

三个冒烟脚本都需要后端已启动；订单脚本会写入演示订单、模拟支付和履约数据。

## 文档总览

[`docs/overview.md`](docs/overview.md) 是文档入口，[`docs/progress.md`](docs/progress.md) 是当前完成进度的唯一权威记录。需求、架构、数据模型、API、开发、启动、部署和测试文档均保存在 `docs/`，文件内容只维护当前有效版本。

## 设计与计划

- 最新设计：`docs/superpowers/specs/2026-07-04-diy-bracelet-mall-design.md`
- 商品目录纵向链路计划：`docs/superpowers/plans/2026-07-04-catalog-vertical-slice.md`
- DIY 编辑器与计价计划：`docs/superpowers/plans/2026-07-04-diy-editor-pricing.md`
- 库存、订单、模拟支付与履约计划：`docs/superpowers/plans/2026-07-11-commerce-vertical-slice.md`

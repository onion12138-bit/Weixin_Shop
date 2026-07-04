# DIY 手串定制商城

一个由 Spring Boot 后端、Vue 3 管理后台和原生微信小程序组成的 DIY 手串定制商品系统。

## 当前状态

截至 2026-07-04，工程骨架和第一条“后台维护商品 → 小程序浏览商品”纵向链路已经实现：

- Spring Boot：Flyway 数据库迁移、商品目录、图片上传、JWT 管理员认证和统一错误响应。
- Vue 管理后台：管理员登录、分类/散珠/套餐维护、有序明细、封面上传和上下架。
- 微信小程序：首页、散珠/套餐目录、搜索筛选、分页和商品详情。
- 基础设施：MySQL、Redis、本地文件存储和三端自动化验证。

DIY 排珠、方案保存、库存、订单、模拟支付和履约仍属于后续阶段。项目的完整范围与实际进度见
[`docs/overview.md`](docs/overview.md)，不得把当前版本描述为已完成全部业务。

## 完整产品规划

- 分类、珠子 SKU、套餐和素材管理（商品目录阶段已实现）
- Canvas 2D DIY 排珠与实时计价
- 规则推荐、方案保存和预览图
- 订单、库存预占、模拟支付和生产 BOM
- 管理后台履约、用户和运营配置

## 项目结构

- `backend/`：Spring Boot 模块化单体
- `admin-web/`：Vue 3 管理后台
- `miniprogram/`：原生微信小程序
- `docs/`：当前有效的项目文档
- `scripts/`：环境配置、检查与统一验证脚本
- `storage/`：本地上传文件，运行数据不提交

## 快速开始

完整步骤见 [`docs/setup.md`](docs/setup.md)。首次启动：

```bash
cp .env.example .env
source ./scripts/project-env.sh
./scripts/check-prerequisites.sh
docker compose up -d
```

后端、管理后台和小程序分别启动后，管理后台默认地址为
`http://localhost:5173`，默认演示账号为 `admin / Admin@123`。正式部署必须更改账号、密码与 JWT 密钥。

## 文档总览

[`docs/overview.md`](docs/overview.md) 是文档入口。需求、架构、数据模型、API、开发、启动、部署和测试文档均保存在
`docs/`，文件内容只维护当前有效版本。

## 设计与计划

- 最新设计：`docs/superpowers/specs/2026-07-04-diy-bracelet-mall-design.md`
- 商品目录纵向链路计划：`docs/superpowers/plans/2026-07-04-catalog-vertical-slice.md`

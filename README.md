# DIY 手串定制商城

一个由 Spring Boot 后端、Vue 3 管理后台和原生微信小程序组成的 DIY 手串定制商品系统。

## 当前状态

工程基础阶段：建立三端可启动骨架、运行环境、测试入口和权威文档。

## 核心能力

- 珠子 SKU、套餐、库存和素材管理
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

完整步骤见 `docs/setup.md`。首先执行：

```bash
source ./scripts/project-env.sh
./scripts/check-prerequisites.sh
```

## 权威文档

- `docs/requirements.md`
- `docs/architecture.md`
- `docs/data-model.md`
- `docs/api.md`
- `docs/development.md`
- `docs/setup.md`
- `docs/deployment.md`
- `docs/testing.md`

## 设计与计划

- 最新设计：`docs/superpowers/specs/2026-07-04-diy-bracelet-mall-design.md`
- 当前计划：`docs/superpowers/plans/2026-07-04-project-foundation.md`

# 系统架构说明

最后更新：2026-07-04

## 1. 总体结构

系统采用“模块化单体后端 + 两个独立前端”：

```text
Vue 3 管理后台 ── /api/admin/**  ┐
                                  ├─ Spring Boot ─ MySQL 8
微信小程序      ── /api/public/** ┘       │
                                           ├─ 本地 FileStorage
                                           └─ Redis（为后续库存/会话协调预留）
```

模块化单体降低首版部署和事务复杂度，同时通过包边界保持后续拆分能力。

## 2. 后端模块

- `auth`：管理员凭证校验、JWT 签发、请求过滤和安全策略。
- `catalog`：分类、散珠、套餐领域模型，管理命令和公共查询。
- `file`：文件元数据、存储抽象、本地实现、上传和公开读取。
- `shared`：统一响应、业务异常、错误码和健康接口。
- 后续模块：`design`、`inventory`、`order`、`payment`、`fulfillment`。

模块内部按 `api`、`application`、`domain`、`infrastructure` 分层。Controller
不直接编写业务 SQL；事务边界位于应用服务；Mapper 只处理持久化。

## 3. 数据与接口边界

- 管理端返回完整维护数据，可包含成本价，但必须携带管理员 JWT。
- 公共端只返回已上架商品和公开字段，封面返回公开 URL，不返回本地路径。
- 数据库外键保证分类、封面和套餐明细引用有效；应用服务提供可读的业务错误。
- Flyway `V1` 建表、`V2` 写入稳定演示数据，后续只新增迁移文件，不修改已发布迁移。

## 4. 文件存储替换设计

业务层只依赖 `FileStorage`：

- 当前实现 `LocalFileStorage`，使用 UUID 文件名和 `yyyy/MM` 分目录。
- `asset_file` 保存存储键、原始名、MIME、大小和公开 URL。
- 后续新增 `OssFileStorage` 或 `CosFileStorage` 并通过配置选择实现。
- 商品表只保存 `cover_asset_id`，因此迁移对象存储无需修改商品模型。

## 5. 认证与安全

- `/api/admin/auth/login` 公开，用配置中的演示管理员账号登录。
- `/api/admin/**` 其余接口要求 `Authorization: Bearer <JWT>`。
- `/api/public/**`、健康接口公开。
- 401/403 使用统一 JSON，不返回 Spring Security 默认 HTML。
- JWT 默认 8 小时有效；生产环境必须更换 32 字节以上随机密钥。
- 文件上传校验 MIME、扩展名、大小和规范化路径，读取时阻止目录穿越。

## 6. 第三方能力替换点

- 支付：核心订单依赖 `PaymentGateway`，当前计划实现模拟网关，后续接微信支付。
- 用户身份：首版本地演示身份，后续新增微信登录适配器。
- 文件：`FileStorage` 当前本地实现，后续对象存储实现。
- 推荐：规则推荐作为默认实现，智能推荐通过独立接口接入。

完整设计决策见 `docs/superpowers/specs/2026-07-04-diy-bracelet-mall-design.md`。

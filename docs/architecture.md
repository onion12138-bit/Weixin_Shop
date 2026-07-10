# 系统架构说明

最后更新：2026-07-10

## 1. 总体结构

系统采用“模块化单体后端 + 两个独立前端”：

```text
Vue 3 管理后台 ── /api/admin/**  ┐
                                  ├─ Spring Boot ─ MySQL 8
微信小程序      ── /api/public/** ┤       │
微信小程序      ── /api/app/**    ┘       ├─ 本地 FileStorage
                                           └─ Redis（为后续库存/会话协调预留）
```

模块化单体降低首版部署和事务复杂度，同时通过包边界保持后续拆分能力。

## 2. 后端模块

- `auth`：管理员凭证校验、JWT 签发、请求过滤和安全策略。
- `identity`：小程序访客身份、安装 UUID 哈希、顾客 JWT。
- `catalog`：分类、散珠、套餐领域模型，管理命令和公共查询。
- `diy`：DIY 规则配置、工艺费、绳材、权威报价、方案生命周期和预览绑定。
- `file`：文件元数据、存储抽象、本地实现、上传和公开读取。
- `shared`：统一响应、业务异常、错误码和健康接口。
- 后续模块：`recommendation`、`inventory`、`order`、`payment`、`fulfillment`。

模块内部按 `api`、`application`、`domain`、`infrastructure` 分层。Controller 不直接编写业务 SQL；事务边界位于应用服务；Mapper 只处理持久化。

## 3. 前端结构

### 管理后台

- 登录页获取管理员 JWT。
- 商品工作区维护分类、散珠、套餐、封面和上下架。
- DIY 规则工作区维护腕围规则、工艺费区间和绳材选项。
- 所有请求通过统一 HTTP 客户端；401 时清理令牌。

### 微信小程序

- `services/request.ts` 统一处理后端响应、公共 URL 和顾客会话。
- 首次访问需要顾客接口时，使用本地安装 UUID 换取顾客 JWT。
- 商品详情可按 `skuId` 或 `packageId` 进入 DIY 编辑器。
- DIY 编辑器只以有序 SKU 数组作为设计状态，撤销/重做、临时序号、数量统计、价格摘要和 Canvas 坐标均由该数组派生。
- 方案列表按当前访客 token 加载，只显示本人方案。

## 4. 数据与接口边界

- 管理端返回维护数据，可包含成本价，必须携带管理员 JWT。
- 公共端只返回已上架商品和公开字段，封面返回公开 URL，不返回本地路径。
- 小程序 `/api/app/**` 使用顾客 JWT；管理员 JWT 不能访问顾客方案接口。
- DIY 权威报价只读取服务端 SKU、绳材、工艺费和规则配置；小程序本地预估不参与最终报价。
- 方案只保存 SKU 顺序数组和报价快照，不保存每颗珠子的业务记录。
- Flyway `V1`～`V4` 已落地，后续只新增迁移文件，不修改已发布迁移。

## 5. 文件存储替换设计

业务层只依赖 `FileStorage`：

- 当前实现 `LocalFileStorage`，使用 UUID 文件名和 `yyyy/MM` 分目录。
- `asset_file` 保存存储键、原始名、MIME、大小和公开 URL。
- 商品封面和 DIY 预览图都通过 `asset_file` 引用。
- 后续新增 `OssFileStorage` 或 `CosFileStorage` 并通过配置选择实现。
- 商品和方案表只保存素材外键，因此迁移对象存储无需修改业务模型。

## 6. 认证与安全

- `/api/admin/auth/login` 公开，用配置中的演示管理员账号登录。
- `/api/admin/**` 其余接口要求管理员 JWT。
- `/api/app/session/guest` 公开，用安装 UUID 换取顾客 JWT。
- `/api/app/**` 除访客会话外要求顾客 JWT。
- `/api/public/**`、健康接口公开。
- 401/403 使用统一 JSON，不返回 Spring Security 默认 HTML。
- 文件上传校验 MIME、扩展名、大小和规范化路径，读取时阻止目录穿越。
- 方案查询按 `customer_identity_id` 过滤，其他访客访问返回 404。

## 7. 第三方能力替换点

- 用户身份：当前为 `GUEST` 安装身份；后续增加微信登录适配器并写入 `WECHAT` 身份。
- 支付：后续订单核心依赖 `PaymentGateway`，首版计划模拟支付，之后替换为微信支付。
- 文件：`FileStorage` 当前本地实现，后续切换对象存储。
- 推荐：规则推荐作为默认实现，智能推荐通过独立接口接入。

完整设计决策见 `docs/superpowers/specs/2026-07-04-diy-bracelet-mall-design.md` 和实施计划 `docs/superpowers/plans/2026-07-04-diy-editor-pricing.md`。

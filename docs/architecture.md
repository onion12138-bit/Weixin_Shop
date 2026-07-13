# 系统架构说明

最后更新：2026-07-13

## 1. 总体结构

系统采用“模块化单体后端 + 两个独立前端”：

```text
Vue 3 管理后台 ── /api/admin/**  ┐
                                  ├─ Spring Boot ─ MySQL 8
微信小程序      ── /api/public/** ┤       │
微信小程序      ── /api/app/**    ┘       ├─ 本地 FileStorage
                                           ├─ Redis（为后续缓存/任务协调预留）
                                           └─ DeepSeek 官方 HTTPS（仅显式启用时）
```

模块化单体降低首版部署和事务复杂度，同时通过包边界保持后续拆分能力。

## 2. 后端模块

- `auth`：管理员凭证校验、JWT 签发、请求过滤和安全策略。
- `identity`：小程序访客身份、安装 UUID 哈希、顾客 JWT。
- `catalog`：分类、散珠、套餐领域模型，管理命令和公共查询。
- `diy`：DIY 规则配置、工艺费、绳材、权威报价、方案生命周期和预览绑定。
- `inventory`：SKU 库存余额、行锁、预占、支付确认、取消释放和库存流水。
- `order`：顾客订单、价格/BOM 快照、模拟支付、履约任务和后台状态机。
- `file`：文件元数据、存储抽象、本地实现、上传和公开读取。
- `shared`：统一响应、业务异常、错误码和健康接口。
- `recommendation`：DeepSeek 单例配置、AES-GCM 密钥、候选 SKU、模型网关、严格 JSON 解析、服务端推荐复核和顾客接口。
- 真实支付接入时在订单模块边界增加支付网关适配器。

模块内部按 `api`、`application`、`domain`、`infrastructure` 分层。Controller 不直接编写业务 SQL；事务边界位于应用服务；Mapper 只处理持久化。

## 3. 前端结构

### 管理后台

- 登录页获取管理员 JWT。
- 商品工作区维护分类、散珠、套餐、封面和上下架。
- DIY 规则工作区维护腕围规则、工艺费区间和绳材选项。
- 订单工作区查看顾客订单和 BOM，执行开始制作、发货和完成。
- AI 模型工作区维护唯一 DeepSeek 配置。Key 使用密码输入框且不回显，未保存变更不能直接测试连接。
- 所有请求通过统一 HTTP 客户端；401 时清理令牌。

### 微信小程序

- `services/request.ts` 统一处理后端响应、公共 URL 和顾客会话。
- 首次访问需要顾客接口时，使用本地安装 UUID 换取顾客 JWT。
- 商品详情可按 `skuId` 或 `packageId` 进入 DIY 编辑器。
- DIY 编辑器只以有序 SKU 数组作为设计状态，撤销/重做、临时序号、数量统计、价格摘要和 Canvas 坐标均由该数组派生。
- 方案列表按当前访客 token 加载，只显示本人方案。
- 方案可进入订单确认页；订单列表和详情只读取本人订单，详情支持模拟支付与取消。
- 首页进入 AI 咨询页；暂停状态显示精确文案，可用时只生成一套推荐并展示服务端价格、图片、理由与文化娱乐免责声明。
- “一键进入 DIY”只通过 `pending_ai_recommendation` 传 `sequence/wristCircumferenceMm/fitType/cordOptionId`；编辑器仅在 `source=ai` 时一次性读取并删除，随后仍需重新权威报价。

## 4. 数据与接口边界

- 管理端返回维护数据，可包含成本价，必须携带管理员 JWT。
- 公共端只返回已上架商品和公开字段，封面返回公开 URL，不返回本地路径。
- 小程序 `/api/app/**` 使用顾客 JWT；管理员 JWT 不能访问顾客方案接口。
- DIY 权威报价只读取服务端 SKU、绳材、工艺费和规则配置；小程序本地预估不参与最终报价。
- 方案只保存 SKU 顺序数组和报价快照，不保存每颗珠子的业务记录。
- 订单创建重新执行权威报价；小程序不提交金额。订单保存方案顺序快照与按 SKU 聚合的 BOM。
- 库存、订单、支付和履约更新位于同一 MySQL 事务，关键余额和订单行使用 `for update`，状态更新同时附加前置状态条件。
- 新增散珠的商品、初始库存余额和 `INIT` 流水位于同一事务；演示导入按 SKU 分页查重，重复运行不重复创建。
- 推荐模型只接收服务端筛选的当前 SKU 字段；模型输出必须经过严格字段、SKU、库存、腕围、预算、绳材、文案和权威计价复核。金额、图片与指纹全部来自服务器，不信任模型价格。
- Flyway `V1`～`V6` 已落地，后续只新增迁移文件，不修改已发布迁移。

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
- 订单查询同样按 `customer_identity_id` 过滤；管理员只能访问 `/api/admin/**`，顾客只能访问 `/api/app/**`。
- AI 配置固定官方 `https://api.deepseek.com`，阻止自定义主机、端口、路径、userinfo、query 和 fragment，避免配置型 SSRF。
- Provider Key 仅以 AES-256-GCM 密文存库；32 字节 Base64 主密钥只来自后端环境。配置响应、日志和异常都不含 Key、Authorization 或上游完整响应。
- 推荐生日和完整咨询文本不持久化、不写日志、不回显；Provider 调用不自动重试，避免重复费用。

## 7. AI 推荐调用与暂停边界

```text
顾客 JWT -> status
  ├─ 无 Key：NOT_CONFIGURED -> POST 503 AI_NOT_CONFIGURED（“AI 推荐暂未开放”）
  ├─ 已停用：DISABLED -> POST 503 AI_DISABLED（“AI 推荐暂停使用”）
  └─ AVAILABLE：候选目录 -> DeepSeek 一次调用 -> 严格解析 -> 库存/预算/计价复核 -> 单套响应
```

系统没有本地规则或静态推荐降级；暂停和外部故障保持失败语义。后台测试连接调用 `/models`，实际推荐调用 `/chat/completions`，都不跟随重定向、不自动重试，并使用保存的超时。默认 `./scripts/verify.sh` 和无 Key 冒烟都不会访问外部模型；live smoke 只能由显式 `DEEPSEEK_API_KEY` opt-in。

## 8. 订单与库存事务边界

```text
创建订单：重新报价 -> 写订单/明细 -> available 转 reserved -> 写 RESERVE 流水
模拟支付：锁订单 -> reserved 转 sold -> 写 PAY_CONFIRM 流水 -> 支付记录 -> 履约任务 -> PAID
取消订单：锁订单 -> reserved 转 available -> 写 RELEASE 流水 -> CANCELLED
后台履约：锁订单和任务 -> 校验前置状态 -> 同事务更新两个状态
```

- 任一步失败会回滚整个事务，不产生“有订单无库存”或“扣库存无支付记录”的中间状态。
- 多 SKU 预占、确认和释放统一按 SKU 主键升序加锁，避免不同请求因加锁顺序相反而放大数据库死锁风险。
- `payment_record.order_id`、`fulfillment_task.order_id` 均唯一；重复模拟支付在锁内识别 `PAID` 并直接返回。
- 当前没有待支付超时任务，库存会一直预占到顾客主动取消；这是后续必补能力。

## 9. 第三方能力替换点

- 用户身份：当前为 `GUEST` 安装身份；后续增加微信登录适配器并写入 `WECHAT` 身份。
- 支付：当前 `MOCK` 记录已经与订单/库存解耦；接入微信支付时新增 `PaymentGateway`，保持支付回调幂等和现有库存确认语义。
- 文件：`FileStorage` 当前本地实现，后续切换对象存储。
- 推荐：当前只有 DeepSeek 适配器且无降级；若未来增加其他 Provider，仍须保持单次调用、服务端复核、隐私和失败语义。

完整设计决策见 `docs/superpowers/specs/2026-07-04-diy-bracelet-mall-design.md` 和实施计划 `docs/superpowers/plans/2026-07-04-diy-editor-pricing.md`。

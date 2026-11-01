# 部署说明

最后更新：2026-10-23

生产环境必须使用独立密钥和数据库账号，不得使用 `.env.example` 中的值。

## 1. 当前支持范围

当前版本用于本地开发、毕业设计演示和产品原型验证：

- MySQL、Redis 使用 Docker Compose。
- 后端使用 `mvn spring-boot:run` 或打包后的 JAR。
- 管理后台使用 Vite 开发服务器或 `dist/` 静态文件。
- 小程序在微信开发者工具中运行，后端地址由 `services/request.ts` 配置。
- 图片和方案预览保存在 `FILE_STORAGE_LOCAL_ROOT` 指向的本地目录。
- 顾客身份使用安装 UUID + 顾客 JWT。
- MySQL 保存库存、订单、模拟支付和履约数据；管理后台与小程序均已接入订单流程。

## 2. 当前不可对外承诺的能力

- 未接微信授权，不能识别真实微信用户。
- 未接真实对象存储，本地文件目录不适合多实例部署。
- 未实现规则推荐、退款、售后和真实物流查询。
- 当前支付固定为本地 `MOCK`，未接微信支付，不允许对外宣称可完成真实交易或真实扣款。
- 小程序访客方案只能在同一安装身份下访问。
- 访客安装 UUID 实际具有恢复该安装账户的能力，应当像登录凭据一样保密；它不等同于微信实名身份。
- 默认配置仍保留本地演示管理员和 JWT 密钥。启用 `prod` 或 `production` profile 会在数据源/Flyway 初始化前直接拒绝启动，直到真实身份、支付和密钥管理完成；默认本地演示 profile 不能用于公网部署。

## 3. 本地启动

```bash
cp .env.example .env
source ./scripts/project-env.sh
docker compose up -d mysql redis
cd backend && mvn spring-boot:run
```

另开终端：

```bash
cd admin-web && pnpm dev --host 127.0.0.1
```

小程序使用微信开发者工具打开 `miniprogram/`。

后端默认 `BACKEND_ADDRESS=127.0.0.1`，Docker 中 MySQL/Redis 也只发布到宿主机的
`127.0.0.1`。手机联调时仅临时开放后端，具体步骤见 [本地启动](setup.md)；
不要把数据库端口一起开放。已有容器需要下次执行 `docker compose up -d` 后才会应用新端口绑定。

## 4. 构建

本轮 V18/V19 必须随新版后端执行，禁止修改已经执行过的旧迁移。先备份数据库；历史待付款订单会获得
迁移后 30 分钟宽限，旧券按迁移时模板回填规则及来源。新前端必须随接口契约一起发布：下单需要
`checkoutToken`、`quoteFingerprint`，发券需要 `batchId`，旧客户端缺字段会被拒绝。

到期任务默认开启，每 30 秒扫描最多 100 笔，逐单事务处理。订单行锁与状态前置条件保证重复扫描和支付竞争
不会重复释放库存或优惠券；多实例运行时也依赖同一数据库锁，未引入独立消息队列。配置项见
[本地启动](setup.md)。修改期限不会重写已有订单的到期时间。付款提醒仅记操作日志，未发送外部消息。

```bash
cd backend && mvn clean package
cd ../admin-web && pnpm build
cd ../miniprogram && pnpm typecheck && pnpm test
```

如果本机没有 pnpm 但依赖已安装，可临时使用对应 npm 脚本执行验证；正式团队环境仍应安装 pnpm 并使用锁文件。

## 5. 冒烟验证

后端启动后执行：

```bash
./scripts/catalog-smoke.sh
./scripts/diy-smoke.sh
./scripts/order-smoke.sh
```

`catalog-smoke.sh` 验证健康、公共目录、管理员登录和未认证保护。`diy-smoke.sh` 验证访客身份、配置、报价和方案生命周期。`order-smoke.sh` 验证下单、本人查询、幂等模拟支付、取消、制作、发货和完成。

## 6. 生产化前置清单

当前 `SPRING_PROFILES_ACTIVE=prod` 和 `SPRING_PROFILES_ACTIVE=production` 均被代码禁止启动，独立强密钥也不会绕过这一限制。完成以下生产化工作并通过专项验收后，才应有意修改 `ProductionDeploymentGuard`；不要通过改用默认 profile 绕过保护。

- 设置强管理员密码，分别配置至少 32 字节随机的 `JWT_SECRET`、`CUSTOMER_JWT_SECRET`、`CUSTOMER_IDENTITY_PEPPER`，不得复用这些值；接入正式管理员账户体系。
- 使用独立 MySQL 用户、最小权限、防火墙、TLS、自动备份和恢复演练。
- 使用 Nginx/网关统一 HTTPS、限流、请求体大小和访问日志。
- 对管理员登录、访客会话创建、AI 推荐、图片上传与订单创建分别限制频率和配额；现有访客鉴权不能阻止批量创建身份或消耗模型、磁盘、库存资源。AI 已有单进程并发上限和同顾客并发互斥，仍需跨实例调用次数、费用与存储配额。
- 把 `FileStorage` 切换到 OSS/COS，并制定历史文件迁移、生命周期和防盗链策略。
- 本地存储目录只能由服务账户写入，不能与其他用户可写目录共用；代码会拒绝存储目录下的符号链接，但不把拥有本地目录写权限的恶意进程作为隔离边界。上传接口目前根据允许的 MIME 类型处理，不构成图片内容消毒。
- 配置正式小程序域名、微信登录、支付证书、回调验签和幂等处理。
- 把 `MOCK` 支付替换为微信支付网关，增加统一下单、回调验签、关单、退款和对账。
- 增加待支付订单超时取消、库存自动释放、库存调整审计和低库存告警。
- 增加真实物流接口、售后、退款和发票能力；现有优惠券仅覆盖后台发放、下单核销和未支付取消退回。
- 增加日志聚合、指标、告警、审计和敏感信息脱敏。
- 在现有订单/库存测试基础上补齐支付回调并发、退款、超时任务、压测和灾难恢复测试。

任何部署环境都必须在启动时执行 Flyway；生产迁移应先备份并在预发布环境验证。

## 7. 后端依赖维护边界

本次审查把后端升级到 Spring Boot 3.5.16、Tomcat 10.1.59 和 Apache POI 5.5.1，保留 Java 17 与现有 Spring 3.x 应用结构。Spring Boot 3.5.16 是该分支最后一个开源支持版本，不能把这次升级视为长期安全支持方案。[Spring 官方发布说明](https://spring.io/blog/2026/06/25/spring-boot-3-5-16-available-now/)

部署前仍需针对完整依赖树做持续漏洞扫描，并根据实际使用的功能评估可达性；例如 Spring Framework 6.2 后续部分修复只提供商业版本。长期方案是规划迁移到仍受支持的 Spring Boot 4.x，或采用供应商维护版本。[Spring 安全公告](https://spring.io/security/)、[Tomcat 10 安全公告](https://tomcat.apache.org/security-10)、[POI 更新记录](https://poi.apache.org/changes.html)

审查范围、回归证据与剩余边界见 [后端安全与逻辑审查记录](backend-security-review-2026-09-12.md)。

2026-10-23 复查进一步更新 Tomcat 至 10.1.61、Jackson BOM 至 2.21.7、Log4j2 至 2.25.5。
Spring MVC 6.2.19 仍匹配 SSE 片段渲染与 XsltView 两条公告；本项目未使用这些功能，
该判断来自当前源码核查，不等同于组件已打补丁。6.2 修复版仅商业可用，需规划受支持框架迁移。
本轮前后端问题、依赖证据和验证结果见 [2026-10-23 审查报告](audit-2026-10-23.md)。

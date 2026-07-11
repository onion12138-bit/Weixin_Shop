# 部署说明

最后更新：2026-07-11

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

## 4. 构建

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

- 设置强管理员密码和至少 32 字节随机 JWT 密钥，接入正式管理员账户体系。
- 使用独立 MySQL 用户、最小权限、防火墙、TLS、自动备份和恢复演练。
- 使用 Nginx/网关统一 HTTPS、限流、请求体大小和访问日志。
- 把 `FileStorage` 切换到 OSS/COS，并制定历史文件迁移、生命周期和防盗链策略。
- 配置正式小程序域名、微信登录、支付证书、回调验签和幂等处理。
- 把 `MOCK` 支付替换为微信支付网关，增加统一下单、回调验签、关单、退款和对账。
- 增加待支付订单超时取消、库存自动释放、库存调整审计和低库存告警。
- 增加真实物流接口、售后、退款、优惠券和发票能力。
- 增加日志聚合、指标、告警、审计和敏感信息脱敏。
- 在现有订单/库存测试基础上补齐支付回调并发、退款、超时任务、压测和灾难恢复测试。

任何部署环境都必须在启动时执行 Flyway；生产迁移应先备份并在预发布环境验证。

# 部署说明

最后更新：2026-07-10

生产环境必须使用独立密钥和数据库账号，不得使用 `.env.example` 中的值。

## 1. 当前支持范围

当前版本用于本地开发、毕业设计演示和产品原型验证：

- MySQL、Redis 使用 Docker Compose。
- 后端使用 `mvn spring-boot:run` 或打包后的 JAR。
- 管理后台使用 Vite 开发服务器或 `dist/` 静态文件。
- 小程序在微信开发者工具中运行，后端地址由 `services/request.ts` 配置。
- 图片和方案预览保存在 `FILE_STORAGE_LOCAL_ROOT` 指向的本地目录。
- 顾客身份使用安装 UUID + 顾客 JWT。

## 2. 当前不可对外承诺的能力

- 未接微信授权，不能识别真实微信用户。
- 未接真实对象存储，本地文件目录不适合多实例部署。
- 未实现推荐、库存、订单、模拟支付和履约。
- 未接微信支付，不允许对外宣称可完成真实交易。
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
```

`catalog-smoke.sh` 验证健康检查、公共目录、管理员登录和未认证保护。`diy-smoke.sh` 验证访客身份、DIY 配置、权威报价、方案保存、读取、复制和删除副本。

## 6. 生产化前置清单

- 设置强管理员密码和至少 32 字节随机 JWT 密钥，接入正式管理员账户体系。
- 使用独立 MySQL 用户、最小权限、防火墙、TLS、自动备份和恢复演练。
- 使用 Nginx/网关统一 HTTPS、限流、请求体大小和访问日志。
- 把 `FileStorage` 切换到 OSS/COS，并制定历史文件迁移、生命周期和防盗链策略。
- 配置正式小程序域名、微信登录、支付证书、回调验签和幂等处理。
- 实现库存、订单、模拟支付/真实支付、退款和履约状态机。
- 增加日志聚合、指标、告警、审计和敏感信息脱敏。
- 完成订单、库存、支付、退款和灾难恢复测试。

任何部署环境都必须在启动时执行 Flyway；生产迁移应先备份并在预发布环境验证。

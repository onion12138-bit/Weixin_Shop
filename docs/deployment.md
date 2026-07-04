# 部署说明

最后更新：2026-07-04

生产环境必须使用独立密钥和数据库账号，不得使用 `.env.example` 中的值。

## 当前支持范围

当前版本用于本地开发和产品演示：

- MySQL、Redis 使用 Docker Compose。
- 后端使用 `mvn spring-boot:run` 或打包后的 JAR。
- 管理后台使用 Vite 开发服务器或 `dist/` 静态文件。
- 图片保存在 `FILE_STORAGE_LOCAL_ROOT` 指向的本地目录。
- 支付和微信身份尚未接入，不允许对外宣称可完成真实交易。

## 生产化前置清单

- 设置强管理员密码和至少 32 字节随机 JWT 密钥，接入正式管理员账户体系。
- 使用独立 MySQL 用户、最小权限、防火墙、TLS、自动备份和恢复演练。
- 使用 Nginx/网关统一 HTTPS、限流、请求体大小和访问日志。
- 把 `FileStorage` 切换到 OSS/COS，并制定历史文件迁移、生命周期和防盗链策略。
- 配置正式小程序域名、微信登录、支付证书、回调验签和幂等处理。
- 增加日志聚合、指标、告警、审计和敏感信息脱敏。
- 完成订单、库存、模拟支付/真实支付、退款和灾难恢复测试。

## 构建

```bash
cd backend && mvn clean package
cd ../admin-web && pnpm build
cd ../miniprogram && pnpm typecheck && pnpm test
```

任何部署环境都必须在启动时执行 Flyway；生产迁移应先备份并在预发布环境验证。

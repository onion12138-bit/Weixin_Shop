# 本地启动

## 前置依赖

- Temurin JDK 17.0.19+
- Apache Maven 3.9.16，解压在项目根目录
- Node.js 20+
- pnpm 9+
- Docker Desktop 4.80.0+

## 环境配置

每个新终端先执行：

```bash
source ./scripts/project-env.sh
./scripts/check-prerequisites.sh
```

## 启动顺序

1. 复制 `.env.example` 为 `.env` 并修改本地密码。
2. 执行 `docker compose up -d`。
3. 在 `backend/` 执行 `mvn spring-boot:run`。
4. 在 `admin-web/` 执行 `pnpm install && pnpm dev`。
5. 使用微信开发者工具导入 `miniprogram/`。

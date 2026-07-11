# 本地启动

最后更新：2026-07-04

## 前置依赖

- Temurin JDK 17.0.19+
- Apache Maven 3.9.16，解压在项目根目录
- Node.js 20+
- pnpm 11.7.0
- Docker Desktop 4.80.0+
- 微信开发者工具稳定版

## 环境配置

在项目根目录首次执行：

```bash
cp .env.example .env
source ./scripts/project-env.sh
./scripts/check-prerequisites.sh
```

`.env` 至少应修改 MySQL 密码、管理员密码和 JWT 密钥。`project-env.sh` 会自动把
`.env` 导出到当前 Shell，使 Docker、后端和前端读取同一份配置；本地开发也可以
直接使用 `application.yml` 中的安全默认值。

## 安装前端依赖

```bash
cd admin-web && pnpm install --frozen-lockfile
cd ../miniprogram && pnpm install --frozen-lockfile
cd ..
```

## 启动顺序

1. 基础设施：`docker compose up -d`。
2. 后端：`cd backend && mvn spring-boot:run`。
3. 管理后台：新终端执行 `cd admin-web && pnpm dev`。
4. 小程序：微信开发者工具导入仓库中的 `miniprogram/` 目录。

管理后台打开 `http://localhost:5173`，默认账号为 `admin / Admin@123`。
默认演示数据由 Flyway 自动写入。

## 双击一键启动

macOS 用户可以直接在 Finder 中双击项目根目录的 `启动管理后台.command`。该文件会依次启动 MySQL、Redis、Spring Boot 后端和 Vue 管理后台，等待服务就绪后自动打开：

`http://localhost:5173`

默认账号为 `admin / Admin@123`。运行日志和进程信息保存在项目根目录的 `.local-run/`，该目录不会进入 Git。

启动成功后请保持自动打开的 Terminal 窗口开启；该窗口负责维持并监测本地后端和管理后台。需要结束本地开发会话时，关闭这个 Terminal 窗口即可。

如果默认后端端口 8080 已被 Jenkins 或其他程序占用，启动脚本不会结束现有进程，也不会修改 `.env`；它会为本次管理后台会话选择 8081–8099 中的空闲端口，并把 Vue API 地址同步到实际端口。5173 仍固定为管理后台地址，若该端口被其他非本项目服务占用，脚本会停止并给出提示。

小程序开发工具中已关闭合法域名校验，默认调用 `http://localhost:8080/api`。
真机调试不能使用手机自身的 `localhost`，应把 `miniprogram/app.ts` 中地址改为电脑局域网 IP；
正式发布则必须使用已备案 HTTPS 域名。

## 验证基础设施

```bash
docker compose ps
curl http://localhost:8080/actuator/health
curl http://localhost:8080/api/public/project-health
```

MySQL 和 Redis 应显示为 `healthy`。启动后端后，两个 HTTP 接口均应返回
HTTP 200。

后端启动后可执行真实商品链路冒烟：

```bash
./scripts/catalog-smoke.sh
```

## 常见问题

- Maven 找不到：确认 `apache-maven-3.9.16` 位于主项目根目录，并重新 source 环境脚本。
- MySQL 端口占用：在 `.env` 修改 `MYSQL_PORT`，后端使用相同环境变量。
- Docker 集成测试提示 API 版本过低：项目已锁定 Testcontainers 1.21.4；确认 Maven
  未被旧依赖覆盖。
- 图片无法显示：确认后端 8080 端口可访问，且 `FILE_STORAGE_LOCAL_ROOT` 对进程可写。

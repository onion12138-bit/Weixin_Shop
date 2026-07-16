# 本地启动

最后更新：2026-07-17

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

`.env` 至少应修改 MySQL 密码、管理员密码和两类 JWT 密钥。`project-env.sh` 会自动把
`.env` 导出到当前 Shell，使 Docker、后端和前端读取同一份配置。本地未显式配置
`AI_CONFIG_ENCRYPTION_KEY` 时，脚本会生成 Base64 编码的 32 字节 AES 主密钥，保存到
Git 忽略的 `.local-secrets/ai-config-encryption-key`，并在后续启动中稳定复用。不要在保存
DeepSeek API Key 后删除该文件，否则已有密文将无法解密。正式部署仍应在仓库外显式配置
并备份 `AI_CONFIG_ENCRYPTION_KEY`；加密主密钥和 Provider Key 都不得提交到仓库。

## 安装前端依赖

```bash
cd admin-web && pnpm install --frozen-lockfile
cd ../miniprogram && pnpm install --frozen-lockfile
cd ..
```

## 启动顺序

1. 基础设施：`docker compose up -d`。
2. 后端：`cd backend && mvn spring-boot:run`，默认监听 `http://localhost:8081`。
3. 管理后台：新终端执行 `cd admin-web && pnpm dev`。
4. 小程序：微信开发者工具导入仓库中的 `miniprogram/` 目录。

管理后台打开 `http://localhost:5173`，默认账号为 `admin / Admin@123`。
默认演示数据由 Flyway 自动写入。

## 双击一键启动

macOS 用户可以直接在 Finder 中双击项目根目录的 `启动管理后台.command`。该文件会依次启动 MySQL、Redis、Spring Boot 后端和 Vue 管理后台，等待服务就绪后自动打开：

`http://localhost:5173`

默认账号为 `admin / Admin@123`。运行日志和进程信息保存在项目根目录的 `.local-run/`，本地 AI 加密主密钥保存在 `.local-secrets/`；两个目录都不会进入 Git。

启动成功后请保持自动打开的 Terminal 窗口开启；该窗口负责维持并监测本地后端和管理后台。需要结束本地开发会话时，关闭这个 Terminal 窗口即可。

后端端口固定为 8081，不扫描或改用其他端口。若 8081 上已经是健康且包含当前 AI 加密能力的本项目后端，启动脚本会直接复用；若识别到同一项目的旧后端缺少该能力，则只重启该项目进程。其他程序占用端口时会明确失败，不会结束占用进程；长期运行在 8080 的 Jenkins 也不会被触碰。5173 同样固定为管理后台地址，被其他非本项目服务占用时脚本会停止并给出提示。

小程序开发工具中已关闭合法域名校验，默认调用 `http://localhost:8081/api`。
真机调试不能使用手机自身的 `localhost`，应把 `miniprogram/app.ts` 中地址改为电脑局域网 IP；
正式发布则必须使用已备案 HTTPS 域名。

## 验证基础设施

```bash
docker compose ps
curl http://localhost:8081/actuator/health
curl http://localhost:8081/api/public/project-health
```

MySQL 和 Redis 应显示为 `healthy`。启动后端后，两个 HTTP 接口均应返回
HTTP 200。

后端启动后可执行四条真实 HTTP 冒烟：

```bash
./scripts/catalog-smoke.sh
./scripts/diy-smoke.sh
./scripts/order-smoke.sh
./scripts/recommendation-smoke.sh
```

`recommendation-smoke.sh` 无 `DEEPSEEK_API_KEY` 时只验证暂停状态和对应错误码，绝不调用外部模型或本地降级。live 路径必须显式 opt-in：

```bash
AI_CONFIG_ENCRYPTION_KEY='<与后端启动时相同的 Base64 32 字节主密钥>' \
DEEPSEEK_API_KEY='<有效 Key>' \
BACKEND_BASE_URL=http://localhost:8081 \
./scripts/recommendation-smoke.sh
```

脚本会通过后台 API 保存官方 `https://api.deepseek.com`、`deepseek-v4-flash`、启用状态和超时配置，再调用连接测试与一套推荐。不得在共享终端历史或日志中粘贴真实密钥；更推荐先在安全 Shell 环境中导出变量。当前里程碑尚未执行这条有效 Key live 验证。

## 3 款图片演示散珠

后端启动后可执行 `./scripts/import-demo-beads.sh`。脚本会分页读取后台目录并按 SKU 幂等跳过已存在项；每个新 SKU 的商品、`inventory_balance.available_qty=200` 和 `INIT` 库存流水在同一创建事务完成。

| SKU | 名称 | 售价 | 成本 | 初始库存 |
| --- | --- | ---: | ---: | ---: |
| `BEAD-LABRADORITE-8MM` | 拉长石 8mm | 9.80 | 2.80 | 200 |
| `BEAD-GARNET-8MM` | 石榴石 8mm | 9.50 | 2.50 | 200 |
| `BEAD-FLUORITE-8MM` | 萤石 8mm | 8.80 | 1.80 | 200 |

## 常见问题

- Maven 找不到：确认 `apache-maven-3.9.16` 位于主项目根目录，并重新 source 环境脚本。
- MySQL 端口占用：在 `.env` 修改 `MYSQL_PORT`，后端使用相同环境变量。
- Docker 集成测试提示 API 版本过低：项目已锁定 Testcontainers 1.21.4；确认 Maven
  未被旧依赖覆盖。
- 图片无法显示：确认后端 8081 端口可访问，且 `FILE_STORAGE_LOCAL_ROOT` 对进程可写。

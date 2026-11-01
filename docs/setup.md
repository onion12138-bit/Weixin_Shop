# 本地启动

最后更新：2026-10-23

## 前置依赖

- Temurin JDK 17.0.19+
- Apache Maven 3.9.16，解压在项目根目录
- Node.js 24 LTS（推荐，项目根目录 `.nvmrc` 指定 24）
- pnpm 11.7.0
- Docker Desktop 4.80.0+
- 微信开发者工具稳定版

管理端 Vitest 4.1.11 支持 Node.js 20、22 和 24+，不支持 Node.js 21/23；
项目锁定的 pnpm 11.7.0 另要求 Node.js >=22.13，因此本项目管理端完整工具链的兼容范围为
`^22.13.0 || >=24.0.0`。已有 Node.js 20 或 23 的环境应先切换到 24 LTS。
使用 nvm 时，在仓库根目录运行 `nvm install && nvm use`，再执行后续安装、测试和启动命令。
依赖升级与审计证据见 [2026-09-12 依赖安全验证](qa/2026-09-12/dependencies.md)。

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
DeepSeek API Key 后删除该文件，否则已有密文将无法解密。后台普通配置响应不会返回 Key；管理员只有主动点击眼睛按钮时才通过受保护且禁止缓存的接口临时查看。正式部署仍应在仓库外显式配置
并备份 `AI_CONFIG_ENCRYPTION_KEY`；加密主密钥和 Provider Key 都不得提交到仓库。

## 安装前端依赖

待支付订单默认在创建后 30 分钟到期。可在 `.env` 设置 `ORDER_PAYMENT_TIMEOUT=PT30M`、
`ORDER_EXPIRY_SCAN_DELAY=PT30S` 和 `ORDER_EXPIRY_ENABLED=true`，分别控制新订单期限、扫描间隔和任务开关。
期限及间隔使用 ISO-8601 Duration，必须为正值；修改期限只影响之后创建的订单。任务每轮最多处理 100 单，
失败订单留待后续扫描；到期支付请求也会直接关闭订单。后端停止时后台扫描不会执行，重启后继续处理。

升级到本轮版本会自动执行 V18、V19。V18 对历史待支付订单从迁移时起统一给予 30 分钟宽限；
V19 将旧券按迁移当时模板回填，标记 `MIGRATED_CURRENT_TEMPLATE`，不会伪造历史原始权益。
新的结算与发券字段是必填契约，后端、后台与小程序应一起更新并重新编译；先备份再升级已有业务库。

```bash
cd admin-web && pnpm install --frozen-lockfile
cd ../miniprogram && pnpm install --frozen-lockfile
cd ..
```

## 启动顺序

1. 基础设施：`docker compose up -d`。
2. 后端：`cd backend && mvn spring-boot:run`，默认监听 `http://localhost:8081`。
3. 管理后台：新终端执行 `cd admin-web && pnpm dev`。
4. 小程序：微信开发者工具导入仓库中的 `miniprogram/` 目录，也支持直接导入仓库根目录 `Weixin_Shop/`。

小程序源码位于 `miniprogram/miniprogram/`，`app.json` 也在该目录。两处
`project.config.json` 分别通过 `miniprogramRoot` 指向同一份源码，并启用 TypeScript 编译。
如果旧项目提示“在项目根目录未找到 app.json”，更新配置后关闭该项目并重新打开、编译；
也可以重新导入上述 `miniprogram/` 目录，无需移动或复制 `app.json`。

管理后台打开 `http://localhost:5173`，默认账号为 `admin / Admin@123`。
默认演示数据由 Flyway 自动写入。

## 双击一键启动

macOS 用户可以直接在 Finder 中双击项目根目录的 `启动管理后台.command`。该文件会依次启动 MySQL、Redis、Spring Boot 后端和 Vue 管理后台，等待服务就绪后自动打开：

`http://localhost:5173`

默认账号为 `admin / Admin@123`。运行日志和进程信息保存在项目根目录的 `.local-run/`，本地 AI 加密主密钥保存在 `.local-secrets/`；两个目录都不会进入 Git。

启动成功后请保持自动打开的 Terminal 窗口开启；该窗口负责维持并监测本地后端和管理后台。需要结束本地开发会话时，关闭这个 Terminal 窗口即可。

后端端口固定为 8081，不扫描或改用其他端口。若 8081 上已经是健康且包含当前 AI 加密能力的本项目后端，启动脚本会直接复用；若识别到同一项目的旧后端缺少该能力，则只重启该项目进程。其他程序占用端口时会明确失败，不会结束占用进程；长期运行在 8080 的 Jenkins 也不会被触碰。5173 同样固定为管理后台地址，被其他非本项目服务占用时脚本会停止并给出提示。

小程序开发工具中已关闭合法域名校验，默认调用 `http://localhost:8081/api`。
后端、MySQL 和 Redis 默认只监听本机。真机调试不能使用手机自身的 `localhost`：
先在 `.env` 中临时设置 `BACKEND_ADDRESS=0.0.0.0` 并重启后端，再把
`miniprogram/miniprogram/app.ts` 中的地址改为电脑局域网 IP。联调前替换演示密码和密钥，
通过防火墙限制访问来源；结束后恢复 `BACKEND_ADDRESS=127.0.0.1`。MySQL 和 Redis 不需要对手机开放。
正式发布必须使用已备案 HTTPS 域名，并完成部署文档中的真实身份、支付和生产环境改造。

### DIY 交互与预览优化更新（2026-10-20）

更新后先重启后端，再在微信开发者工具中重新编译小程序。此次无数据库迁移，后端新增
`POST /api/app/diy/schemes/{id}/previews/{version}`，用于异步上传指定设计版本的预览图；
原 `/preview` 接口继续兼容。旧后端不支持新入口时，方案数据仍可正常保存，预览不会误用无版本接口覆盖。

编辑时同一帧内的画布重绘合并，草稿每 250 ms 最多写入一次，并在隐藏、退出及提交保存前补存。
预览在编辑空闲时提前生成；保存、去下单只等待方案数据，图片随后补传。方案更新会清除旧预览，
后台通过版本条件拒绝迟到图片，补图不推进设计版本。上传失败任务在回到小程序或相关页面时重试；
若图片未生成或临时文件已失效，可重新打开已保存方案生成预览。

真机验收建议：连续加减珠子后立即返回再恢复草稿；断网保存失败后重试；保存成功后在图片上传中继续编辑并再保存；
快速进入确认订单并提交，确认预览晚到不影响订单创建。自动化测试覆盖合并重绘、退出补存、异步上传和版本竞态，
实际响应时间仍以真机测量为准。

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

脚本会通过后台 API 保存官方 `https://api.deepseek.com`、`deepseek-v4-flash`、推荐提示词、推荐套数、启用状态和超时配置，再调用连接测试与多套库存材质推荐。可用 `RECOMMENDATION_PLAN_COUNT=1..5` 覆盖默认 3 套。正式推荐显式使用非思考 JSON Output，并按配置套数提供完整结构示例。不得在共享终端历史或日志中粘贴真实密钥；更推荐先在安全 Shell 环境中导出变量。当前已完成 Key 保存、连接测试和真实多套调用；最近一次 Provider 输出未通过每套种类数校验，成功结果仍待验收。

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

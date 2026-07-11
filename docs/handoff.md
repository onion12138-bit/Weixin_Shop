# Codex 项目交接说明

最后更新：2026-07-11 22:22（Asia/Shanghai）

## 1. 新任务接手方式

新 Codex 任务应以 `/Users/onion/Desktop/AI_Testing/Weixin_Shop` 作为项目根目录，并按以下顺序读取：

1. `docs/handoff.md`：任务上下文、关键决策、当前阻塞与接手步骤；
2. `docs/progress.md`：模块完成度、验证证据和待办；
3. `README.md`、`docs/overview.md`、`docs/setup.md`、`docs/testing.md`：项目总览、启动和验证方式；
4. `git status`、`git log --oneline --decorate -20`：未提交变更与最近提交；
5. `docs/superpowers/specs/` 和 `docs/superpowers/plans/`：已经确认的详细设计与实施计划。

建议新任务的首条提示：

> 请先读取 `docs/handoff.md`、`docs/progress.md`、`README.md` 和最近 20 条 Git 日志，核对工作树状态后，从交接文档“下一步执行顺序”继续。保留所有用户改动，不覆盖 `miniprogram/project.config.json` 的未提交差异。

当前位于 `TestCase` 分组的旧 Codex 任务保留为完整对话和工具执行记录；新任务不会自动继承全部聊天历史，因此本文件是开发上下文的权威交接入口。

## 2. 仓库与版本状态

- 本地根目录：`/Users/onion/Desktop/AI_Testing/Weixin_Shop`
- GitHub：`https://github.com/onion12138-bit/Weixin_Shop`
- 当前主分支：`main`
- 当前基线提交：`2370ed8fd26db79983d313364ca55e9da9b93b90`
- 当前提交说明：`feat: complete order and mock payment vertical slice`
- `origin/main` 已与上述提交核对一致；曾从 GitHub 全新克隆并比较 Tree 哈希及 265 个受 Git 管理文件，结果一致。
- 本仓库仅配置 `http.version=HTTP/1.1`，用于规避当前网络环境下 Git HTTPS/2 的 TLS 中断；这不是代理配置。

### 2.1 必须保留的未提交改动

微信开发者工具打开项目后自动修改了 `miniprogram/project.config.json`：

- `appid` 从 `touristappid` 改成实际小程序 AppID；
- 增加 `minifyWXML: true`；
- 自动格式化 `useCompilerPlugins`。

该文件是开始编写交接文档前唯一由用户或开发者工具产生的未提交项目改动。接手后必须先执行 `git diff -- miniprogram/project.config.json`，由用户确认是否提交，不得擅自还原或覆盖。

## 3. 产品范围与已确认决策

项目目标是一个可实际使用的完整三端 DIY 手串电商产品：

- Spring Boot 后端；
- Vue 管理后台；
- 原生微信小程序；
- MySQL、Redis 和本地文件存储。

已经确认的产品决策：

1. 当前阶段不接入真实微信支付，使用幂等模拟支付，但保留支付网关替换能力；
2. 当前使用本地文件存储，通过 `FileStorage` 端口保留对象存储替换能力；
3. 产品必须包含完整后台和小程序业务闭环，不是静态展示稿；
4. 同一种珠子只有 SKU/品种级 ID，不为每颗实体珠子建立业务 ID 或独立数据库行；
5. DIY 方案保存有序 SKU ID 数组，前端展示时临时生成序号；计价、库存和 BOM 按 SKU 聚合数量；
6. 前端显示预估价格，保存方案和下单前必须由后端重新权威计价；
7. 用户偏好采用推荐方案推进；只有确实需要视觉比较时才启用可视化设计；
8. 需求、设计、开发、测试和进度文档持续保存最新版本；关键代码需要清晰、完整注释。

## 4. 当前完成度与验证证据

`docs/progress.md` 记录的产品范围覆盖率为 **86%**。

已完成的主要闭环：

- 工程基础、三端骨架和本地基础设施；
- 商品分类、商品、SKU、套餐和本地素材；
- 管理员认证；
- DIY 编辑、尺寸校验、后端权威报价；
- 方案保存、恢复、复制、删除、预览和乐观锁；
- 库存预占、支付转已售和取消释放；
- 顾客下单、查询、模拟支付和取消；
- 后台制作、BOM、发货和完成履约。

最近一次完整自动化验证：

- 后端：52 项测试通过；
- 管理后台：8 项测试、类型检查和生产构建通过；
- 小程序：8 个测试套件、13 项测试和 TypeScript 检查通过；
- 商品、DIY、订单真实 HTTP 冒烟通过。

尚未完成的一级模块：

- 规则推荐：0%，需要实现预算、色系、材质和场景推荐；
- 第三方能力替换：50%，真实微信登录、微信支付和对象存储留待上线前接入。

## 5. 本机环境与端口注意事项

- JDK：Temurin 17.0.19；
- Maven：项目根目录 `apache-maven-3.9.16`；
- Docker Desktop：MySQL 8.0、Redis 7；
- Node.js 20+、pnpm 11.7.0；
- 微信开发者工具：`/Applications/wechatwebdevtools.app`。

本机 Jenkins 长期占用 `127.0.0.1:8080`，进程来源为：

`/opt/homebrew/opt/jenkins-lts/libexec/jenkins.war --httpListenAddress=127.0.0.1 --httpPort=8080`

不得误杀 Jenkins。此前 Spring Boot 监听 IPv6 通配地址时，`http://localhost:8080` 解析到 `::1`，可以访问本项目；`http://127.0.0.1:8080` 会命中 Jenkins 并返回 403。接手后启动服务必须重新核对此行为，长期建议把项目开发端口改为不冲突端口并同步三端配置。

交接时 Spring Boot 和 Vue 开发服务器未确认仍在运行；新任务必须按 `docs/setup.md` 重新检查和启动。Docker 容器此前显示 MySQL、Redis 均为 healthy，但也应重新执行 `docker compose ps` 验证。

## 6. 微信开发者工具现状

已经完成：

- 服务端口已由用户手动开启；
- `.ide` 文件曾显示本机服务端口 `56879`；
- CLI 成功连接 IDE；
- `cli auto` 曾成功识别 `touristappid` 并返回 `✔ auto`；
- 项目目录为 `/Users/onion/Desktop/AI_Testing/Weixin_Shop/miniprogram`，不能误选内层 `miniprogram/miniprogram`。

尚未完成：

- 微信开发者工具中的首页、商品、DIY、方案和订单人工/自动化页面验收；
- 自动化客户端未成功取得调试 WebSocket，等待超过 120 秒后已主动终止；
- 开发者工具日志曾出现 `需要重新登录, access_token missing` 和微信服务 TLS 连接中断；
- 用户截图显示“调试基础库”推送失败，基础库列表不可用；
- 用户说明没有主动使用代理，并要求如果存在由 Codex 写入的代理记录则删除。代理排查在旧任务中被中断，尚未完成系统代理、开发者工具代理和残留配置的完整核对。

临时目录 `/private/tmp/weixin-shop-automator` 曾安装 `miniprogram-automator@0.12.1` 用于连接验证。该依赖没有写入项目仓库；其依赖链报告 10 个漏洞，不应直接纳入产品或 CI。临时脚本没有产生有效页面截图。

## 7. 代理与网络排查边界

目前没有证据表明 Codex 为本项目写入过代理：

- 当时 Shell 环境没有发现 `HTTP_PROXY`、`HTTPS_PROXY`、`ALL_PROXY` 等变量；
- Git 配置检查只发现 macOS 钥匙串、HTTP 缓冲/低速设置和本仓库 `HTTP/1.1`，未发现 Git proxy；
- `HTTP/1.1` 是传输协议设置，不是代理。

仍需核对：

1. `scutil --proxy` 和各网络服务的 macOS 系统代理；
2. npm、pnpm 的 `proxy` / `https-proxy`；
3. 微信开发者工具本地设置中的代理项；
4. 是否存在网络扩展、VPN/TUN 或 DNS Fake-IP 行为。

只删除明确存在、来源可确认且属于本项目/Codex 的代理记录。不得因为日志出现 TLS 错误就删除未知系统或第三方网络配置。

## 8. 下一步执行顺序

1. 在新的 `Weixin_Shop` Codex 项目中读取本交接文档并执行 `git status`；
2. 保护 `miniprogram/project.config.json` 的未提交差异，向用户确认是否提交真实 AppID 配置；
3. 完成代理与网络配置的只读核对，发现明确残留后再做最小删除；
4. 确认微信开发者工具已登录、基础库可以下载/编译；
5. 启动并验证 MySQL、Redis、Spring Boot 和管理后台；
6. 导入小程序并验收首页、商品目录、详情、DIY、方案、下单、模拟支付和订单；
7. 修复验收发现的问题并复跑 `./scripts/verify.sh` 与真实 HTTP 冒烟；
8. 更新 `docs/progress.md`，提交并推送验收结果；
9. 按 Superpowers 流程设计并实现规则推荐模块；
10. 增加 GitHub Actions 自动验证；
11. 微信登录、真实支付和对象存储继续留在上线准备阶段。

## 9. 常用命令

```bash
cd /Users/onion/Desktop/AI_Testing/Weixin_Shop
git status --short --branch
git log --oneline --decorate -20

source ./scripts/project-env.sh
docker compose up -d
docker compose ps

cd backend
mvn spring-boot:run

cd ../admin-web
pnpm dev

cd ..
./scripts/verify.sh
```

微信开发者工具 CLI：

```bash
/Applications/wechatwebdevtools.app/Contents/MacOS/cli open \
  --project /Users/onion/Desktop/AI_Testing/Weixin_Shop/miniprogram \
  --lang zh
```

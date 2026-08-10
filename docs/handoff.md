# Codex 项目交接说明

最后更新：2026-07-20 01:54（Asia/Shanghai）

## 0. 2026-07-20 Excel 批量导入与拖拽上传摘要

散珠商品页新增“Excel 批量导入”：管理员可下载由后端根据当前分类生成的 `.xlsx` 模板，也可点击或拖拽上传填写后的文件。模板不包含图片列；导入按行复用既有商品创建服务，每个成功行独立创建商品、库存余额和非零 `INIT` 流水，失败行返回 Excel 行号、SKU 和具体原因，不影响其他成功行。限制为 `.xlsx`、10 MB、1000 行。散珠与成品搭配封面现在都支持点击或拖拽 PNG/JPEG/WebP，继续执行 5 MB 限制。

实现使用 Apache POI 5.4.1；为消除其压缩依赖与 Spring Boot 3.2 管理版本之间的 `ArrayFill` 冲突，显式固定 `commons-lang3` 3.20.0。全仓验证通过：后端 200 项、管理后台 61 项与生产构建、小程序 93 项与类型检查。真实 MySQL 导入验证为 2 行成功、1 行缺珠径精确报错；真实管理员接口下载模板返回 HTTP 200、xlsx 包含“商品数据”和“填写说明”两个工作表。当前后端已在 8081 持续运行，后台仍为 5173；改动尚未提交。

## 0.1 2026-07-19 商品库存、类型与 API Key 掩码摘要

后台 AI 配置页加载到“已配置”状态后，API Key 输入框默认显示固定掩码，不再呈现空框；普通配置读取仍不包含明文，也不会为了绘制掩码自动调用密钥接口。管理员点击眼睛时才通过受保护的 no-store 接口读取明文，直接保存掩码会提交空 Key 以保留现有密钥。

商品新增原本已有 `initialStock` 和同事务库存初始化，本轮补齐后台列表库存读取：分页 DTO 返回可用、预占、已售、预警值和 `NORMAL/RESTOCK`，`availableQty <= stockAlertThreshold` 时页面显示“补货”。V8 为分类增加 `BEAD/CORD/ACCESSORY` 类型并新增“绳类”“其他饰品”分类；绳类和其他饰品不显示或保存珠径。公共珠子目录、详情、DIY 可售查询、套餐校验和 AI 候选均只接受 `BEAD`。

全仓验证已通过：后端 198 项（含真实 Docker/MySQL 8 和 V1～V8 迁移）、管理后台 7 个文件 56 项及生产构建、小程序 14 个套件 93 项及 TypeScript 检查。开发数据库已升级到 V8，后端健康检查为 `UP`；管理后台浏览器验收确认 API Key 默认以 16 位掩码和 `password` 类型呈现、库存与预警列正常显示，并且绳类新增表单保留初始库存但不显示珠径。当前改动尚未提交。

## 0.3 2026-08-09 AI 软偏好与调优日志摘要

推荐候选不再只发送 `skuId/name/material`：每次请求都会从数据库重新查询当前在售且有库存的珠子，并向模型提供 `skuId/name/material/color/diameterMm/tags`。后台增加可编辑的软偏好提示词与开关，指导模型理解明确的材质、颜色和标签偏好；偏好命中只用于排序倾向和运营观察，不构成后端强制约束，后端仍只严格校验输出结构、套数、SKU、当前库存和文案安全。

V15 新增管理员专用 `ai_recommendation_log`。推荐原始输入、无密钥配置/提示词快照、候选快照、模型请求和原始响应、可选偏好分析、最终结果或错误默认保存；后台“AI 调用日志”支持状态/关键词筛选、完整详情和单条删除，并显示“已识别偏好｜匹配候选｜最终采用套数”。API Key、密钥密文、`Authorization` 和其他认证头永不进入日志。`./scripts/verify.sh` 已完整通过：后端 240 项、管理端 89 项与构建、小程序 175 项与类型检查，以及冒烟静态契约。

## 0.2 2026-07-19 AI 多套推荐改造摘要

本轮已把旧的单套推荐重构为生日驱动的多套推荐：小程序只保留必填生日和选填期望；Spring Boot 统一计算星座、生肖和星座元素；后台可以配置业务提示词及 1～5 套推荐数量（默认 3 套）。模型只接收后台提示词、生日画像、期望和当前在售有库存珠子的 `skuId/name/material`，不接收手围、松紧、价格、数量、排列或绳材。每套返回 3～5 种不同珠子，顾客选定后只把去重 SKU 类型一次性带入 DIY，由既有 DIY 默认值和计价规则继续处理。

公开 `GET /api/public/recommendations/profile?birthDate=YYYY-MM-DD` 与状态接口不依赖访客会话，正式 `POST /api/app/recommendations` 仍要求顾客 JWT。模型输出需要通过严格 JSON、套数、每套数量、同套 SKU 去重、当前库存和文案安全校验；仅输出不合规时允许同一个 DeepSeek 模型纠正一次，不启用本地或其他模型降级。不同故障使用不同错误码，小程序保留生日和期望并直接显示可操作的具体错误。后台已保存 API Key 的显隐接口继续保持管理员专用、显式点击和 `Cache-Control: no-store`，普通配置响应不包含密钥。

真实证据：最新全仓回归为管理后台 56 项和生产构建、小程序 93 项和 TypeScript 检查、后端 198 项（含 Docker/MySQL 与 V8 迁移）全部通过。真实 DeepSeek 多套推荐请求已经执行：配置、顾客会话、生日画像、库存读取和 Provider 调用均走通。早期请求分别触发同套重复 SKU、纠正后 0 套等精确错误；对照官方文档补充非思考模式和按配置套数动态生成的完整 JSON 示例后，模型已不再只返回 1 套，但某套少于 3 种且纠正后仍不合规，接口最终准确返回 `AI_RESULT_ITEM_COUNT_INVALID`，没有再被包装成“暂不可用”。因此“错误分流闭环”已验收，但“成功生成 3 套可用方案”的 live 验收仍未完成，不能记为成功。

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
- 当前本地 `main` 提交：`8a4d9c7 fix: improve catalog admin and AI configuration startup`
- `origin/main`：`13fea1c fix: close milestone review gaps`
- 本地 `main` 当前领先远端 1 个提交；本轮 AI 状态与密钥查看修复尚未提交或推送。
- `2370ed8 feat: complete order and mock payment vertical slice` 是更早的订单里程碑，不是当前基线。
- 本仓库仅配置 `http.version=HTTP/1.1`，用于规避当前网络环境下 Git HTTPS/2 的 TLS 中断；这不是代理配置。
- 当前直接在主工作树 `main` 上工作，没有额外功能 worktree。

### 2.1 必须保留的未提交改动

当前主工作树存在以下必须保护的用户/工具变更：

- 已修改的 `miniprogram/project.config.json`；
- 未跟踪的 `.pnpm-store/`；
- 未跟踪的 `Pic/`；
- 未跟踪的 `Temporary review and recommendations/`、`backend/storage/` 和本机项目私有配置文件。

其中微信开发者工具对 `miniprogram/project.config.json` 的改动包括：

- `appid` 从 `touristappid` 改成实际小程序 AppID；
- 增加 `minifyWXML: true`；
- 自动格式化 `useCompilerPlugins`。

接手后必须先执行主工作树 `git status` 和 `git diff -- miniprogram/project.config.json`。未经用户明确确认，不得还原、覆盖、删除或提交上述三项，也不得把 `.pnpm-store/` 或 `Pic/` 带入本功能提交。

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

`docs/progress.md` 记录的产品范围覆盖率为 **95%**。

已完成的主要闭环：

- 工程基础、三端骨架和本地基础设施；
- 商品分类、商品、SKU、套餐和本地素材；
- 管理员认证；
- DIY 编辑、尺寸校验、后端权威报价；
- 方案保存、恢复、复制、删除、预览和乐观锁；
- 库存预占、支付转已售和取消释放；
- 顾客下单、查询、模拟支付和取消；
- 后台制作、BOM、发货和完成履约。
- DeepSeek 后台单例配置、AES-GCM Key 保存、生日画像、多套库存材质推荐严格校验和小程序一次性 DIY handoff。

最近一次完整自动化验证（2026-07-19 02:31）：

- 后端：198 项测试通过，包含 Docker/MySQL 集成测试和 V8 迁移；
- 管理后台：7 个文件、56 项测试、类型检查和生产构建通过；
- 小程序：14 个套件、93 项测试和 TypeScript 检查通过；
- 真实 HTTP：DeepSeek 请求和一次纠正均已实际执行，最终准确返回 `AI_RESULT_ITEM_COUNT_INVALID`；后台 5173、后端 8081 可访问。

尚未完成的关键外部验收与上线能力：

- DeepSeek 配置保存和连接测试已成功，多套推荐 live 已执行但模型两次输出均未通过结构校验，成功方案仍待验收；
- 第三方能力替换仍为 50%，真实微信登录、微信支付和对象存储留待上线前接入。

## 5. 本机环境与端口注意事项

- JDK：Temurin 17.0.19；
- Maven：项目根目录 `apache-maven-3.9.16`；
- Docker Desktop：MySQL 8.0、Redis 7；
- Node.js 20+、pnpm 11.7.0；
- 微信开发者工具：`/Applications/wechatwebdevtools.app`。

本机 Jenkins 长期占用 `127.0.0.1:8080`，进程来源为：

`/opt/homebrew/opt/jenkins-lts/libexec/jenkins.war --httpListenAddress=127.0.0.1 --httpPort=8080`

不得误杀 Jenkins，也不得再用 8080 启动或验证本项目。后端、管理后台默认 API、小程序 runtime 和 smoke 现在统一使用 `http://localhost:8081`；`BACKEND_BASE_URL` 只传 origin，不附加 `/api`。

交接时管理后台地址为 `http://localhost:5173`，后端通过当前 Codex 持续终端会话运行在 8081。Codex 执行环境会回收由短命令脱离的 `nohup` 子进程，这不代表用户双击 `.command` 的持久 Terminal 模式失败。新任务仍应先检查实际进程；当前持续会话结束后可重新双击 `启动管理后台.command`。

## 6. 微信开发者工具现状

已经完成：

- 服务端口已由用户手动开启；
- 当前 `.ide` 服务端口为 `44091`；
- CLI 已连接现有 IDE，识别真实 AppID `wx9864f5f5c7870c36` 并返回 `✔ auto`；
- 显式启用 9420 自动化端口后，`miniprogram-automator@0.12.1` 成功读取当前页 `pages/home/index`；
- 项目目录为 `/Users/onion/Desktop/AI_Testing/Weixin_Shop/miniprogram`，不能误选内层 `miniprogram/miniprogram`。

尚未完成：

- AI 咨询页最终界面验收：自动化 `reLaunch` 后会话卡住，需要用户在已经打开的工具中点一次“编译”，不要重新打开或导入项目；
- 首页、商品、DIY、方案和订单的完整人工/自动化页面验收；
- 开发者工具日志曾出现 `需要重新登录, access_token missing` 和微信服务 TLS 连接中断；
- 用户截图显示“调试基础库”推送失败，基础库列表不可用；
- 用户说明没有主动使用代理，并要求如果存在由 Codex 写入的代理记录则删除。代理排查在旧任务中被中断，尚未完成系统代理、开发者工具代理和残留配置的完整核对。

临时目录 `/private/tmp/weixin-shop-automator` 安装了 `miniprogram-automator@0.12.1` 用于连接验证，没有写入项目依赖。当前连接和首页读取成功，但 AI 页截图调用卡住并已终止。

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

1. 读取本交接文档与 `docs/progress.md`，执行 `git status`，保护 `miniprogram/project.config.json` 和所有用户改动；
2. 保持 Docker 的 MySQL/Redis、后端 8081 和后台 5173 运行；不要删除 `.local-secrets/ai-config-encryption-key`；
3. 请用户在已经打开的微信开发者工具中点击顶部“编译”，人工验收生日必填、期望选填、生日画像和具体错误展示；不要重新打开或导入项目；
4. 若继续处理 Provider 输出稳定性，应基于已记录的安全结构原因优化正式提示约束；不得输出模型原文或 API Key，也不得启用用户明确禁止的本地降级；
5. 再做一次真实多套推荐，以 `plans` 数量、每套 3～5 个唯一当前 SKU、免责声明和选择方案进入 DIY 为成功标准；
6. 用户确认后再决定是否提交/推送当前改动；不得顺带提交 `.pnpm-store/`、`Pic/`、本机配置或其他用户文件；
7. 增加 GitHub Actions；微信登录、真实支付和对象存储继续留在上线准备阶段。

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
BACKEND_BASE_URL=http://localhost:8081 ./scripts/catalog-smoke.sh
BACKEND_BASE_URL=http://localhost:8081 ./scripts/diy-smoke.sh
BACKEND_BASE_URL=http://localhost:8081 ./scripts/order-smoke.sh
BACKEND_BASE_URL=http://localhost:8081 ./scripts/recommendation-smoke.sh
```

最后一条无 Key 时只验证暂停路径。live 必须在安全 Shell 预先导出 `DEEPSEEK_API_KEY` 与 `AI_CONFIG_ENCRYPTION_KEY`；不要把真实值写入命令示例、脚本、日志或报告。

微信开发者工具 CLI：

```bash
/Applications/wechatwebdevtools.app/Contents/MacOS/cli open \
  --project /Users/onion/Desktop/AI_Testing/Weixin_Shop/miniprogram \
  --lang zh
```

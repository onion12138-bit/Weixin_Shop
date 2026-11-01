# 下一期需求脑暴与现有系统补强清单

日期：2026-10-23（Asia/Shanghai）

对象：拾珠作坊 / DIY 手串定制商城

状态：用户已确认下一期以“补齐业务闭环与日常运营能力”为目标；具体需求范围和排期仍为评审建议。

## 1. 建议先做什么

**建议下一期主题：让设计、下单和制作更可靠。**

现有系统已经具备商品、DIY、方案、地址、优惠券、库存、模拟订单和基本履约。下一期的价值主要来自补齐流程断点：结算时能正确选地址和识别加载失败，订单超时能释放库存，已领优惠券规则稳定，DIY 替换可控且能撤销，操作记录准确表达实际结果。

建议首批选择 **R01、R02、R03、R04、R06-A、R20**；若还有容量，增加 **R05 按序制作工单**作为版本亮点。R01 较大，内部必须按“结算体验”和“提交结果恢复”拆分交付。其余候选保留在需求池，不建议一次全部启动。

开发跟进：用户已授权按此首批范围实施，当前实现与验证结果见 [本轮交付记录](qa/2026-10-23/reliable-operations.md)。下文的系统不足保留为开发前盘点，不能当作开发后的现状；R05 及其余候选尚未实施。

用户已确认“优先补齐业务闭环与日常运营能力”，本稿采用路线 A，保留上述首批优先级。真实收款和差异化展示列为后续路线，见第 5 节。尚无用户访谈、经营数据或明确团队容量，以下价值是产品假设，规模是相对估计，不是收入预测和工期承诺。

## 2. 本轮范围与方法

- 已发现并加载已安装的 **Superpowers brainstorming** 技能，采用“项目事实 → 用户/商家任务 → 缺口 → 多路线比较 → MVP 与验收”的方式整理。
- 基线为本地当前工作区，包含已有未提交修改；HEAD 为 `9feb83b`。不以旧 README 或历史进度百分比代替当前代码事实。
- 对三端目录、页面、接口、迁移和测试做全量清点，按业务模块深读关键流程；小程序、后台、后端由三个并行审阅分别交叉核对。这不是所有源码逐行审计，也不是完整渗透测试。
- 清点到：后端 **11 个一级业务/基础模块、196 个 Java 主源码文件、25 个 Controller、17 个数据库迁移**；后台 **36 个 TS/Vue 非测试文件及 1 个样式文件、7 个工作区**；小程序 **42 个 TS 文件、14 个注册页面**；脚本 **15 个**。测试源码清点为后端 47 个 `*Test.java`、后台 21 个 `*.spec.ts`、小程序 32 个 `*.spec.ts`，文件数不等于测试执行数。
- 检查了当前需求/架构/部署/测试/进度文档及 2026-10-23 审查记录。排除第三方依赖、构建产物、历史工作树、论文与图片素材逐文件审阅。
- 本轮没有重新运行测试、启动系统、调用付费模型或写入运行数据库。下述确认结论来自源码，性能影响与体验提升仍需实测。
- 仅新增本报告；原有业务代码和用户未提交修改保持原状。

### 2.1 已有能力：这些不能重复算作新需求

| 业务模块 | 当前实际能力 | 下一步应补的部分 |
| --- | --- | --- |
| 商品与套餐 | 分类/珠子及其他商品类型维护、搜索分页、上下架、Excel 导入、封面、首页推荐套餐 | 大目录选材完整性、批量更新与运营配置 |
| 视觉素材 | 手工透明素材、版本、发布/回滚、小程序视觉降级 | 素材质量检查与批量制作；自动生成仍是候选项 |
| DIY 编辑 | Canvas 排珠、拖动、增减、替换、腕围/松紧/绳材、草稿保存恢复、服务端报价 | 指定珠子替换、撤销重做接入页面、缺货替换 |
| 我的方案 | 保存、恢复、复制、删除、预览、版本冲突保护；最多 3 个活动方案 | 作品归档、再来一单、作品级分享 |
| AI 灵感 | 生日/心愿输入、多套材质组合、库存 SKU 复核、可配置提示词、调用日志、并发保护 | 可制作成串、效果反馈、配额和日志治理 |
| 顾客与权益 | 资料、地址、默认地址、优惠券模板、单人/批量发券、结算核销、未支付取消退券 | 本单选址、已发券规则快照、权益效果分析 |
| 收藏 | 珠子收藏及收藏列表 | 当前存在本地缓存；跨设备同步尚需账号体系 |
| 交易规则 | 起购金额、包邮门槛、基础运费及后台配置 | 结算规则变更确认；复杂区域运费暂不必抢先做 |
| 库存 | 可售/预占/已售、初始库存、正数补库存、流水、行内低库存预警 | 盘点、损耗、流水查询、补货待办、超时释放 |
| 订单 | 创建与库存预占、价格快照、模拟支付幂等、取消、顾客本人查询、后台筛选统计/批量操作 | 提交结果恢复、到期关闭、真实支付、售后 |
| 制作发货 | 聚合 BOM、已有打印、完成制作、发货、确认收货、操作轨迹 | 按珠子顺序制作、质检、返工、物流节点 |
| 权限与部署 | 顾客/管理员 JWT 隔离、资源归属校验、本地文件、统一错误、部署保护 | 微信身份、多员工权限、对象存储、恢复演练 |

### 2.2 最近已修复，避免再次误报

2026-10-23 的已有审查记录包含：防重复点击、异步页面退出保护、安装标识安全随机数、迟到 401、后台 CSV 公式防护、部分批处理并发限制、订单关联查询批量化、AI 单进程并发限制、本机端口绑定等。当前代码仍保留这些修复。

因此本稿不把“按钮没有防重”“订单仍然逐条 N+1 查询”“AI 完全没有并发控制”等写作现存缺陷。历史测试结果如需引用，应标注来源日期，不能当作本次重跑结果。详见 [已有审查记录](/Users/onion/Desktop/AI_Testing/Weixin_Shop/docs/audit-2026-10-23.md)。

## 3. 系统不足与补充内容

标记：**确认**表示有明确源码路径；**设计取舍**表示现有规则成立但可改进；**待验证**表示影响程度缺少运行数据。优先级 P1 为建议近期补强，P2 为后续增强，G 为真实商业上线前的前置条件，不等于当前演示系统发生故障。

| 编号 | 状态 / 优先级 | 当前不足与用户影响 | 建议补充 | 证据 |
| --- | --- | --- | --- | --- |
| F01 | 确认 / P1 | 结算只取默认或第一条地址；跳地址管理不能单独选择“本次收货地址”，送礼需改默认地址 | 选择模式、返回所选地址 ID、保留本单选择，默认地址独立设置 | E01 |
| F02 | 确认 / P1 | 初次加载地址/券失败被转成空列表，用户可能误以为没有地址或没有券 | 区分加载中、真实为空、失败；分别重试；券加载失败时明确提示后再允许无券结算 | E01 |
| F03 | 确认 / P1 | DIY“替换”自动用目录首个不同 SKU；撤销/重做已有领域工具但未接到编辑页面 | 打开替换选材面板，显示差价与尺寸变化；接入撤销/重做和操作历史 | E02 |
| F04 | 确认 / P1 | 待支付预占只有主动取消释放，订单没有支付到期字段，未检出到期关闭调度；弃单可持续占库存/券 | 到期时间、可重试关闭任务、一次性释放库存/券、倒计时与关闭原因 | E03 |
| F05 | 确认缺少恢复契约；弱网影响待验证 / P1 | 创建请求没有业务幂等键/结果查询契约；服务端成功而响应丢失后，客户端只能再找订单。下单会重算价格，但请求未带用户确认的报价版本 | 提交令牌、重试返回同一结果、结果查询；价格/规则变化先提示再确认 | E03、E04 |
| F06 | 确认；权益规则需确定 / P1 | 已领券显示及结算读取当前模板金额/门槛；修改模板会改变已发券的规则；发券重试也缺少服务端批次幂等 | 发券规则快照、不可重复执行的发券批次；停发新券与撤销旧券分开 | E05 |
| F07 | 设计取舍 / P2 | 创建订单后方案被软删除，取消订单没有恢复方案；用户作品不能方便地再次购买 | 从订单快照恢复为新方案、作品归档/再来一单；重新校价与库存校验 | E03、E06 |
| F08 | 确认 / P1 | 付款提醒只记订单日志，后台 Toast 已正确说“已记录”，但服务端日志写“发送付款提醒”；MOCK 支付的日志又写“微信支付成功” | 先统一准确文案；未来通知需有渠道、发送状态、失败重试与去重 | E07 |
| F09 | 确认 / G、P2 | 有 AFTER_SALE/REFUNDED 枚举和统计，但没有顾客申请、审核、退款处理闭环；支付后不能走待支付取消 | 售后单独状态机，退款、退货验收、补发、处理轨迹；模拟/真实资金明确分离 | E08 |
| F10 | 确认 / P1 候选亮点 | 订单保存顺序，后台主要展示聚合 BOM 和预览；已有打印，但没有逐颗指引、质检/返工流程；绳材名称仍读取当前配置 | 冻结完整制造规格，按序图解工单、质检清单和返工原因、关联订单 | E09 |
| F11 | 确认 / P2 | 已能正数补库存并记流水，但缺盘点、损耗和流水查询工作台；低库存提示没有形成待办闭环 | 调整原因、盘点单、损耗、操作人、流水检索、补货状态与阈值去重提醒 | E10 |
| F12 | 确认 / P1–P2 | 套餐编辑只加载前 100 个 SKU；客户后台分页是在全量加载后切片，小程序订单亦为全量 | SKU 远程检索与按 ID 补齐；客户/订单服务端分页；详情按需读取 | E11 |
| F13 | 设计取舍 / P2 | AI 输出是珠子种类灵感，交给 DIY 时每种仅一颗，默认腕围/绳材并重置名称；不是完整可下单成串 | 增加独立的“辅助成串”能力，考虑预算/腕围/库存，保留灵感来源；最终报价仍由后端负责 | E12 |
| F14 | 确认 / P1、G | AI 已有单进程并发保护，但没有日次数/费用限额；日志保存请求及响应，只有单条删除，缺保留期 | 预算/次数配额、调用计量、清理任务、最小化记录、清楚说明后台留存范围 | E13 |
| F15 | 确认 / G | 当前安装身份、本地文件、单管理员、MOCK 支付；生产 profile 被主动禁止启动 | 将微信身份、支付退款、权限、存储、运维拆为正式上线项目，保留门禁直到验收 | E14 |
| F16 | 确认 / P1 | 旧文档仍称未实现优惠券/库存调整，DIY 撤销重做描述又领先于页面；“95%”是旧范围覆盖率 | 更新能力矩阵与需求/API/验收口径；分开代码存在、自动化通过、真机通过、外部服务验收 | E15 |
| F17 | 确认 / P1小修 | 成品搭配页可输入搜索词，但成品查询仅传分页参数，后台公共套餐接口也未接关键词 | 三端接通成品搜索；交付前至少不保留会让用户误以为生效的输入 | E16 |
| F18 | 确认流程；积累规模待验证 / P2、G | 方案预览先存文件再检查绑定，版本冲突等失败可能留下未引用文件；没有完整资源回收闭环 | 预检所有权、临时资源、失败补偿和定期回收；保留被订单引用的素材 | E17 |

F05 不是“已经证实会重复生成订单”：当前事务与方案软删除有冲突保护，支付也已幂等。本项要解决的是弱网下同次提交结果可找回，以及用户确认金额与服务端重算结果的一致性。

F13 也不是模型降级需求：保留当前“AI 未配置/失败就明确提示，不伪造推荐结果”的边界。辅助排珠应独立命名，由用户主动触发，不作为模型故障时的隐式替代。

F10 的边界：原方案下单后被软删除，不能声称顾客能任意改动它而影响旧订单。已确认的依赖是后台详情仍关联读取方案、绳材和预览，绳材配置可被后台修改；应冻结完整制造规格。下单与另一页面同时修改方案的竞态只列为后续验证案例，本轮未复现。

## 4. 下一期与后续需求池

规模 S/M/L 为相对规模：S 为小范围调整，M 为多端/数据契约变更，L 为新状态机或外部集成。它们不是人日。每项先验证业务价值，再定工程排期。

| ID | 需求与价值 | 最小可交付范围 / 验收要点 | 优先 / 规模 | 依赖 |
| --- | --- | --- | --- | --- |
| R01 | 可靠结算与提交恢复：减少下单困惑 | 本单选址、错误重试、金额变化确认、提交令牌与结果查询；同键重试回同单、改参数拒绝复用 | P1 / L（拆两步） | F01/02/05；订单事务 |
| R02 | 未支付超时关闭：避免库存长期被占 | 可配置到期时长、服务端到期时间、分批扫描关闭、一次释放、记录关闭原因 | P1 / M | R01结果语义；现有取消/退券 |
| R03 | 权益规则与发放可靠性：稳定客户权益 | 发券规则版本/快照、同批次重试不重发；模板修改仅影响后续发券，停发与撤销独立 | P1 / M | 迁移已有券、统一核销规则 |
| R04 | 可控 DIY 编辑：做错能恢复 | 指定目标珠子替换、前后差价/尺寸、撤销重做；保存/恢复后历史语义明确 | P1 / M | 现有编辑状态/历史工具 |
| R05 | 按序制作与质检：将设计准确做成实物 | 固化制造规格，打印带位置编号的珠序、物料数与预览；质检通过/返工后才能发货 | P1候选 / M–L | R04可并行；订单快照完整性 |
| R06 | 提醒与轨迹真实化：让运营知道动作是否生效 | A：修正 MOCK/提醒日志；B：消息事件、已记录/待发送/成功/失败、重试与去重 | A:P1/S；B:P2/L | B需通知渠道及身份/订阅条件核验 |
| R07 | 作品资产与再来一单：支持复购 | 从订单复制为新方案、保留名称/顺序；下架/涨价提示后重报价；与活动方案限额协调 | P2 / M | R01；当前3方案规则 |
| R08 | 库存与补货工作台：解决账实差异 | 流水查询、入库/损耗/盘点、原因与操作人；调整不可侵占预占数量 | P2 / M–L | 权限；现有库存行锁/流水 |
| R09 | 商品检索与大列表可用：避免搜索失效或漏选 | 成品关键词搜索端到端接通；套餐远程选材、已选SKU按ID补齐、客户与订单服务端分页 | 搜索/套餐P1，其余P2 / M | API与前端一起迁移 |
| R10 | AI 使用与数据治理：知道资源花在哪里 | 次数/预算限额、配置版本、失败分类、日志保留与清理；达限明确暂停 | P1治理、扩展P2 / M–L | 计量能力；多实例时统一配额 |
| R11 | 微信账号与资产迁移：换设备找回 | 正式身份绑定，访客方案/订单/地址/券迁移，本地收藏同步；防串号/重复合并 | G / L | 正式小程序配置；真实平台接口核验 |
| R12 | 真实支付、退款与对账：真实交易基础 | 下单/验签/幂等回调/查单/关单/退款/账单差异；明确未知结果与补偿 | G / L | R01/02/03/11；商户环境 |
| R13 | 多员工与生产运维：支持多人可靠运营 | 商品/客服/制作/财务权限，操作身份审计；上传配额/解码校验/孤儿回收、存储迁移、备份恢复、监控 | G / L（独立子项目） | 正式部署环境和职责定义；文件回收可提前小步做 |
| R14 | 售后工作台：客户出问题有入口 | 申请原因/图片、客服审核、仅退款/退货退款/返工补发、处理时限与轨迹 | P2；收款前G / L | R05；真实退款依赖R12 |
| R15 | 作品分享与同款再创作：形成传播 | 用户主动发布只读作品、可撤销分享、复制成自己的方案；不暴露地址/订单信息 | P2 / M | R07；分享快照与访问控制 |
| R16 | 缺货替换与辅助排珠：减少完成设计的门槛 | 同径/近色/预算范围替换，用户确认后应用；对称排珠或补足腕围，权威重算 | P2 / M–L | R04；SKU标签和尺寸质量 |
| R17 | AI 推荐效果闭环：衡量是否有帮助 | 喜欢/不喜欢原因、进入DIY/保存/下单关联；基于固定案例评估提示词版本 | P2 / M | R10；避免额外采集生日内容 |
| R18 | 送礼与成品快捷购买：服务明确场景 | 优先试点礼物卡片、赠言、本单选址；成品可从模板确认规格后快捷进入结算 | P2 / M | R01；真实送礼需求验证 |
| R19 | 经营与履约看板：指导运营取舍 | 浏览→DIY→报价→下单漏斗、超时关闭、库存缺货、制作耗时；模拟订单独立口径 | P2 / M | 事件契约和业务数据基线 |
| R20 | 文档与验收同步：减少后续误判 | 更新能力矩阵、API、需求边界、测试来源；每个新增需求关联验收证据 | P1 / S–M | 随各需求交付完成 |

### 4.1 六个首批需求的具体验收

**R01 可靠结算与提交恢复**

- A 步：本次选择地址 B，不改变默认地址 A；返回结算、选择优惠券后地址仍为 B；失效地址不能提交。地址与优惠券各自提供失败状态及重试，不能把接口失败当作空数据。
- B 步：客户端对一次确定提交持有稳定令牌，后端按“顾客 + 令牌”唯一约束保存结果。同键同内容返回原订单；同键不同内容返回冲突；订单记录和令牌结果同事务提交。
- 模拟响应丢失后重试、退出重进或按令牌查结果，只得到原订单，不重复预占或核券。不能用“同一方案只能下一次单”长期替代提交幂等，否则会阻碍复购。
- 展示价格之后如商品价、运费或优惠规则变化，返回新报价及差异，用户确认后才能创建；不接受客户端金额为权威。
- 建议观察：结算成功率、因地址/券加载失败退出次数、结果未知提交占比。先采基线，不先承诺提升百分比。

**R02 到期关闭与资源归还**

- 首版可采用“数据库到期字段 + 定时分批扫描”，暂不引入复杂消息队列。支付期限建议可配置，30 分钟可作为讨论默认值，并非已经确定的业务规则。
- 同一关闭任务重跑不会多退库存/券；多实例并发仅一个状态迁移成功；宕机重启后补扫到期单。
- 支付与关闭同时发生，订单状态、支付记录、库存和券只能形成一致结果。未来接真实支付时，应与上游关单/查单结果协调，不能仅看本地时钟释放后把迟到支付忽略。
- 小程序倒计时以服务端时间为准；已关闭订单可查看原因，并通过重新报价再次下单。验收包含到期前后、任务重试、任务中断和支付竞争。

**R03 已领优惠券规则冻结**

- 发券时固化金额、门槛、名称、有效期及必要适用规则，或保存不可变模板版本；下单和展示都使用同一份已发规则。
- 发券请求携带稳定批次 ID，冻结本次接收者集合，同批次重试返回原结果；批次与顾客组合唯一，不能把响应丢失后的重试变成再次发券。显式创建新批次仍可合法再次发放。这与已有前端按钮互斥是不同层面的保证。
- 修改模板不改变顾客已持有券；停止新发不等于撤销旧券。主动撤销需单独动作、原因和记录。
- 旧券迁移只能按可取得的历史证据处理；若没有原始版本，明确记录按迁移时模板回填，不能声称还原了真实发券历史。
- 验收覆盖：先领券再改模板、并发核销、取消后退回、退回时已过期，以及发券成功响应丢失后同批次重试不增加券数。

**R04 指定替换与撤销重做**

- 用户选中某个位置，点击替换后选择目标 SKU，再确认；一次操作只替换目标位置。默认不自动找目录第一颗。
- 提示直径变化、库存不足、价格变化；下架材料不可新加入。报价权威仍在服务端。
- 添加、删除、替换、拖动各算一个可撤销动作；连续操作撤销后可重做，产生新分支时清掉旧重做分支。
- 撤销后旧异步报价/预览不能覆盖新状态；恢复草稿、打开已保存方案和下单后的历史边界需一致。首版不增加自动排珠算法。

**R06-A 轨迹与真实动作一致**

- MOCK 支付应记录“模拟支付成功”；仅记提醒意图时写“已记录付款提醒”，不能声称已经通知到客户。保留后台当前准确的“已记录”反馈。
- 修正新事件生成；历史错误文案是否迁移需单独决定，保留事件来源，不伪造实际支付渠道。
- 后续 R06-B 再引入发送任务/状态/回执；必须区分渠道接受发送和用户实际已读，避免将两者混为一谈。

**R20 文档与验收口径**

- 同步修正“优惠券尚未实现”“没有库存调整”等过时内容；撤销工具存在但页面未接入应如实说明，完成 R04 后再更新为可用。
- 每项功能分别标注：已有源码、自动化验证、真机验证、外部服务验证。真实模型成功调用、微信身份和真实支付不能用本地 Mock 测试代替。
- 当前待提交工作与后续需求分开记录，避免覆盖已有修改。不得将旧 95% 范围覆盖率写成上线成熟度。

### 4.2 最值得做成产品亮点的三项

1. **按序制作工单（R05）**：让 DIY 设计可准确交付。工单含位置编号、顺序图、珠子规格、腕围、绳材、预览及版本，已有 BOM 打印作为基础；质检不通过进入返工。历史订单若缺制造字段，标注为关联数据回填，不能冒充原始快照。先验证制作人员是否仍需手工对图或外部表格。
2. **作品再来一单 + 私密分享（R07/R15）**：老客户可复购，朋友可复制后调整。分享的是用户选择发布的作品快照，复制方重新校库存/价格；原作者撤销后分享不可继续访问。首版不做公共社区和积分裂变。
3. **缺货替换 + 辅助成串（R16）**：选完材料后告诉用户还差多少长度，并给同径/近色/预算内替代建议；用户确认后应用。先做可解释的编辑辅助，保留现有 AI 灵感边界，再评估全自动设计。

这些亮点都有现成基础可复用，比同时引入购物车、分销、积分商城和大型社区更容易形成一个能验收的版本。

## 5. 三条版本路线与取舍

| 路线 | 主要目标 | 推荐范围 | 优点与代价 |
| --- | --- | --- | --- |
| A：可靠设计与运营（用户已选方向） | 当前用户和商家流程更完整 | 首批 R01/02/03/04/06-A/20；容量允许加 R05；成品搜索与套餐>100选材作为 R09 小切片插入 | 可依托现有本地能力交付，业务问题明确；仍不等于可以真实收款 |
| B：正式交易试点 | 建立真实账号、资金与履约闭环 | R01/02/03 → R11 → R12 + R14，并行 R10/R13，完善 R05/08 | 商业价值直接；依赖商户/微信环境、供应商与运维，必须拆多个里程碑 |
| C：DIY 差异化体验 | 提高设计完成、分享与再创作 | R04 → R07 → R15；R16 小范围试点，R17/R19验证效果 | 演示亮点强；若缺真实使用数据，不能直接证明转化提升 |

**路线 A 的完成条件**：顾客能够选对地址、知道券有没有加载成功、控制替换并撤销错误；弱网重试可找回订单；弃单资源可归还；已领券不随模板编辑变化；日志和文档与实际一致。若加 R05，则制作人员能依据工单独立完成一次制作与质检。

**路线 B 的上线门槛**：真实身份与访客迁移验证、真实支付/退款/对账一致性、售后处理、权限与审计、文件与数据库恢复、配额与告警，以及必要的真机与外部服务验收全部有证据。现有生产保护应在满足条件后有意调整，不用切换 profile 绕过。

**本轮暂缓**：多商家平台、分销佣金、复杂会员积分、全量公共作品社区、AR/3D 试戴、Blender 自动素材生产、多个 AI Provider 切换。它们不是没有价值，而是当前没有证据表明比结算/交付断点更紧迫。自动素材已有历史设计，也不应伪装成全新需求。

## 6. 需求间的数据与架构补充

| 领域 | 建议增加或明确的数据 | 与现有机制的关系 |
| --- | --- | --- |
| 提交与报价 | checkoutToken、请求摘要、报价版本、结果订单 ID | 保留权威重算；令牌唯一约束与订单同事务，防重不能只依赖按钮锁 |
| 订单到期 | expiresAt、关闭原因、关闭时间 | 重用库存释放/退券规则；任务幂等与支付状态竞争统一处理 |
| 顾客券 | 发放规则快照/模板版本、撤销原因 | 与订单已有折扣快照一致；明确未支付期间占券与使用状态语义 |
| 制造快照 | 有序珠子规格、腕围/松紧、绳材、预览版本、质检项 | 从订单冻结数据制作，不只显示SKU汇总，不依赖后续可变配置 |
| 售后 | 售后单、售后事件、退款单、收货质检/补发关联 | 售后不只是在订单上添加一个状态；资金与实物各有结果 |
| 通知 | eventId、渠道、接收身份、尝试次数、发送状态 | 先记录事件再可靠投递；无有效渠道时不产生“已通知”假成功 |
| 库存调整 | 类型、原因、数量、操作身份、关联盘点/订单 | 沿用余额+流水+行锁，不直接覆盖库存总量 |
| AI及经营分析 | requestId、配置版本、结果分类、用量、后续事件 | 不把生日/愿望复制到分析事件；先有保留策略和访问边界 |

这些是后续详细设计的输入，不是本轮直接建表或变更 API 的指令。优先沿用现有模块化单体、事务与版本机制；尚无证据要求拆微服务。

## 7. 验证与决策建议

### 7.1 最小验证样例

| 流程 | 必测情境 | 判定依据 |
| --- | --- | --- |
| 结算 | 地址/券接口失败；非默认地址；提交后断网；报价变化 | 不假空、不误用地址、不产生重复资源占用、结果可找回 |
| 订单 | 支付与到期并发；关闭任务重复/重启 | 状态、库存、券与支付一致，每项变化只发生一次 |
| 权益 | 发券后模板编辑；撤销；券过期后取消 | 冻结规则与明确退回策略一致 |
| DIY | 选定珠替换；多步撤销重做；请求乱序 | 珠序、报价、草稿和预览一致 |
| 制作 | 商品/配置后续修改；打印；质检失败 | 已下单规格不漂移，失败不能直接当成合格发货 |
| 数据规模 | 套餐选择第101个SKU；客户/订单多页 | 已选材料可见、检索完整、分页无重复遗漏 |
| 分享/身份 | 撤销链接；跨客户访问；迁移重试 | 不泄露私有订单/地址，不串号，不重复合并资产 |

### 7.2 产品验证比盲目增加功能更重要

- 邀请顾客完成一次“选材—替换—保存—给朋友下单”，记录是否依赖口头帮助，以及在哪步退出。
- 邀请制作人员只凭现有订单/BOM 完成一次模拟备料，确认最缺的是珠序、规格、图片还是质检记录，再决定 R05 细节。
- 向运营确认低库存、盘点、售后现在是否在表格/聊天中处理；据此裁剪 R08/R14，而不是先造一个通用 ERP。
- 首版看板先定义分母和去重规则：结算成功率按确定的提交意图计数；AI→DIY→订单转换按关联链路计数；模拟支付与真实支付分开。
- 版本首批之外，可用少量真实案例验证“成品按原搭配快捷购买、作品命名、礼物卡片、批量调价/导入预览、制作进度照片”是否值得提升优先级；先确认用户任务，不一并扩张成营销平台。
- 已确认首要路线为 A。尚需业务确定的事项：人员容量、付款期限、旧券迁移规则、售后适用场景、作品保存上限、通知渠道。这些不阻碍本稿整理，但会影响正式规格和排期。

## 8. 源码证据索引

以下行号对应本次审阅工作区，后续代码变化可能使行号移动。源码证据支持现有行为；需求价值与推荐排序是本次分析判断。

- **E01 结算地址与错误处理**：[confirm.ts:87](/Users/onion/Desktop/AI_Testing/Weixin_Shop/miniprogram/miniprogram/pages/order/confirm.ts:87) 中地址/券失败转空；[confirm.ts:124](/Users/onion/Desktop/AI_Testing/Weixin_Shop/miniprogram/miniprogram/pages/order/confirm.ts:124) 重新取默认地址；[confirm.ts:251](/Users/onion/Desktop/AI_Testing/Weixin_Shop/miniprogram/miniprogram/pages/order/confirm.ts:251) 默认/首条选择规则。
- **E02 DIY 替换与历史**：[editor.ts:745](/Users/onion/Desktop/AI_Testing/Weixin_Shop/miniprogram/miniprogram/pages/diy/editor.ts:745) 自动取首个不同 SKU；[editor.wxml:58](/Users/onion/Desktop/AI_Testing/Weixin_Shop/miniprogram/miniprogram/pages/diy/editor.wxml:58) 编辑操作栏；[editor-history.ts:1](/Users/onion/Desktop/AI_Testing/Weixin_Shop/miniprogram/miniprogram/domain/editor-history.ts:1) 已有历史工具；页面未引用该工具。
- **E03 创建、预占、方案消费、取消**：[OrderService.java:116](/Users/onion/Desktop/AI_Testing/Weixin_Shop/backend/src/main/java/com/diybracelet/mall/order/application/OrderService.java:116)、[OrderService.java:172](/Users/onion/Desktop/AI_Testing/Weixin_Shop/backend/src/main/java/com/diybracelet/mall/order/application/OrderService.java:172)、[OrderService.java:275](/Users/onion/Desktop/AI_Testing/Weixin_Shop/backend/src/main/java/com/diybracelet/mall/order/application/OrderService.java:275)；[CustomerOrder.java:54](/Users/onion/Desktop/AI_Testing/Weixin_Shop/backend/src/main/java/com/diybracelet/mall/order/domain/CustomerOrder.java:54) 日期字段。业务源码未检出订单到期调度入口。
- **E04 提交契约**：[CreateOrderRequest.java:17](/Users/onion/Desktop/AI_Testing/Weixin_Shop/backend/src/main/java/com/diybracelet/mall/order/api/dto/CreateOrderRequest.java:17)；[confirm.ts:215](/Users/onion/Desktop/AI_Testing/Weixin_Shop/miniprogram/miniprogram/pages/order/confirm.ts:215)。已有前端互斥及收到成功响应后的 createdOrderId 恢复，不包含服务器成功但响应丢失的同令牌查回契约。
- **E05 优惠券**：[CouponService.java:84](/Users/onion/Desktop/AI_Testing/Weixin_Shop/backend/src/main/java/com/diybracelet/mall/customer/application/CouponService.java:84)、[CouponService.java:292](/Users/onion/Desktop/AI_Testing/Weixin_Shop/backend/src/main/java/com/diybracelet/mall/customer/application/CouponService.java:292)、[OrderService.java:614](/Users/onion/Desktop/AI_Testing/Weixin_Shop/backend/src/main/java/com/diybracelet/mall/order/application/OrderService.java:614)；[CouponService.java:187](/Users/onion/Desktop/AI_Testing/Weixin_Shop/backend/src/main/java/com/diybracelet/mall/customer/application/CouponService.java:187) 批量发券直接插入；[V11 券表](/Users/onion/Desktop/AI_Testing/Weixin_Shop/backend/src/main/resources/db/migration/V11__customer_account_and_benefits.sql:52)。
- **E06 作品规则**：[DiySchemeService.java:45](/Users/onion/Desktop/AI_Testing/Weixin_Shop/backend/src/main/java/com/diybracelet/mall/diy/application/DiySchemeService.java:45) 活动方案上限；[DiySchemeMapper.java:98](/Users/onion/Desktop/AI_Testing/Weixin_Shop/backend/src/main/java/com/diybracelet/mall/diy/infrastructure/mapper/DiySchemeMapper.java:98) 软删除行为。再来一单需作为明确的规则改良。
- **E07 提醒/支付轨迹**：[AdminOrderService.java:201](/Users/onion/Desktop/AI_Testing/Weixin_Shop/backend/src/main/java/com/diybracelet/mall/order/application/AdminOrderService.java:201)、[OrderListView.vue:193](/Users/onion/Desktop/AI_Testing/Weixin_Shop/admin-web/src/views/OrderListView.vue:193)、[OrderService.java:260](/Users/onion/Desktop/AI_Testing/Weixin_Shop/backend/src/main/java/com/diybracelet/mall/order/application/OrderService.java:260)；[V14 历史支付日志](/Users/onion/Desktop/AI_Testing/Weixin_Shop/backend/src/main/resources/db/migration/V14__unified_order_fulfillment.sql:80) 也使用微信支付文案。
- **E08 售后边界**：[OrderStatus.java:6](/Users/onion/Desktop/AI_Testing/Weixin_Shop/backend/src/main/java/com/diybracelet/mall/order/domain/OrderStatus.java:6)、[AppOrderController.java](/Users/onion/Desktop/AI_Testing/Weixin_Shop/backend/src/main/java/com/diybracelet/mall/order/api/AppOrderController.java)、[AdminOrderController.java](/Users/onion/Desktop/AI_Testing/Weixin_Shop/backend/src/main/java/com/diybracelet/mall/order/api/AdminOrderController.java)；当前 API/迁移未形成售后申请、审核、退款子系统。
- **E09 制作信息**：[OrderService.java:456](/Users/onion/Desktop/AI_Testing/Weixin_Shop/backend/src/main/java/com/diybracelet/mall/order/application/OrderService.java:456) 保存顺序；[AdminOrderResponse.java:38](/Users/onion/Desktop/AI_Testing/Weixin_Shop/backend/src/main/java/com/diybracelet/mall/order/api/dto/AdminOrderResponse.java:38) 未向后台暴露逐颗珠序；[OrderDetailView.vue:279](/Users/onion/Desktop/AI_Testing/Weixin_Shop/admin-web/src/views/OrderDetailView.vue:279) 聚合 BOM 与现有打印；[AdminOrderService.java:336](/Users/onion/Desktop/AI_Testing/Weixin_Shop/backend/src/main/java/com/diybracelet/mall/order/application/AdminOrderService.java:336) 及 [AdminOrderService.java:423](/Users/onion/Desktop/AI_Testing/Weixin_Shop/backend/src/main/java/com/diybracelet/mall/order/application/AdminOrderService.java:423) 关联读取制造信息。
- **E10 库存现状**：[AdminCatalogService.java:238](/Users/onion/Desktop/AI_Testing/Weixin_Shop/backend/src/main/java/com/diybracelet/mall/catalog/application/AdminCatalogService.java:238)、[AddInventoryRequest.java:9](/Users/onion/Desktop/AI_Testing/Weixin_Shop/backend/src/main/java/com/diybracelet/mall/catalog/api/dto/AddInventoryRequest.java:9)、[BeadCatalogPanel.vue:682](/Users/onion/Desktop/AI_Testing/Weixin_Shop/admin-web/src/views/BeadCatalogPanel.vue:682)。已有正数库存调整、流水与行内预警。
- **E11 数据规模**：[PackageCatalogPanel.vue:112](/Users/onion/Desktop/AI_Testing/Weixin_Shop/admin-web/src/views/PackageCatalogPanel.vue:112)、[CustomerBenefitsView.vue:61](/Users/onion/Desktop/AI_Testing/Weixin_Shop/admin-web/src/views/CustomerBenefitsView.vue:61)、[CustomerBenefitsView.vue:118](/Users/onion/Desktop/AI_Testing/Weixin_Shop/admin-web/src/views/CustomerBenefitsView.vue:118)、[OrderService.java:188](/Users/onion/Desktop/AI_Testing/Weixin_Shop/backend/src/main/java/com/diybracelet/mall/order/application/OrderService.java:188)。订单查询已有批量优化，此处是响应规模问题。
- **E12 AI 与分享边界**：[RecommendationRequest.java:7](/Users/onion/Desktop/AI_Testing/Weixin_Shop/backend/src/main/java/com/diybracelet/mall/recommendation/api/dto/RecommendationRequest.java:7)、[recommendation-profile.ts:223](/Users/onion/Desktop/AI_Testing/Weixin_Shop/miniprogram/miniprogram/domain/recommendation-profile.ts:223)、[recommendation-profile.ts:256](/Users/onion/Desktop/AI_Testing/Weixin_Shop/miniprogram/miniprogram/domain/recommendation-profile.ts:256)；[home/index.ts:169](/Users/onion/Desktop/AI_Testing/Weixin_Shop/miniprogram/miniprogram/pages/home/index.ts:169) 已有首页分享，不能说完全没有分享。
- **E13 AI 治理**：[RecommendationConcurrencyGuard.java:13](/Users/onion/Desktop/AI_Testing/Weixin_Shop/backend/src/main/java/com/diybracelet/mall/recommendation/application/RecommendationConcurrencyGuard.java:13)、[AiRecommendationLogService.java:47](/Users/onion/Desktop/AI_Testing/Weixin_Shop/backend/src/main/java/com/diybracelet/mall/recommendation/application/AiRecommendationLogService.java:47)、[AiRecommendationLogService.java:144](/Users/onion/Desktop/AI_Testing/Weixin_Shop/backend/src/main/java/com/diybracelet/mall/recommendation/application/AiRecommendationLogService.java:144)。[推荐页说明:30](/Users/onion/Desktop/AI_Testing/Weixin_Shop/miniprogram/miniprogram/pages/recommendation/index.wxml:30) 的“不存为草稿”不等于后台不留日志，需澄清范围。
- **E14 身份、权限与生产**：[GuestSessionController.java:18](/Users/onion/Desktop/AI_Testing/Weixin_Shop/backend/src/main/java/com/diybracelet/mall/identity/api/GuestSessionController.java:18)、[AdminAuthService.java:15](/Users/onion/Desktop/AI_Testing/Weixin_Shop/backend/src/main/java/com/diybracelet/mall/auth/application/AdminAuthService.java:15)、[ProductionDeploymentGuard.java:9](/Users/onion/Desktop/AI_Testing/Weixin_Shop/backend/src/main/java/com/diybracelet/mall/shared/deployment/ProductionDeploymentGuard.java:9)、[部署说明](/Users/onion/Desktop/AI_Testing/Weixin_Shop/docs/deployment.md)。
- **E15 文档漂移**：[requirements.md](/Users/onion/Desktop/AI_Testing/Weixin_Shop/docs/requirements.md)、[progress.md](/Users/onion/Desktop/AI_Testing/Weixin_Shop/docs/progress.md)、[overview.md](/Users/onion/Desktop/AI_Testing/Weixin_Shop/docs/overview.md)，与 E02/E05/E10 对照。以当前工作区及有日期的验证证据为准。

- **E16 成品搜索**：[catalog/index.ts:75](/Users/onion/Desktop/AI_Testing/Weixin_Shop/miniprogram/miniprogram/pages/catalog/index.ts:75)、[catalog.ts:84](/Users/onion/Desktop/AI_Testing/Weixin_Shop/miniprogram/miniprogram/services/catalog.ts:84)、[PublicCatalogController.java:73](/Users/onion/Desktop/AI_Testing/Weixin_Shop/backend/src/main/java/com/diybracelet/mall/catalog/api/PublicCatalogController.java:73)。
- **E17 素材生命周期**：[AppDiySchemeController.java:90](/Users/onion/Desktop/AI_Testing/Weixin_Shop/backend/src/main/java/com/diybracelet/mall/diy/api/AppDiySchemeController.java:90)、[AppDiySchemeController.java:101](/Users/onion/Desktop/AI_Testing/Weixin_Shop/backend/src/main/java/com/diybracelet/mall/diy/api/AppDiySchemeController.java:101)、[FileStorageService.java:50](/Users/onion/Desktop/AI_Testing/Weixin_Shop/backend/src/main/java/com/diybracelet/mall/file/application/FileStorageService.java:50)、[FileStorage.java:19](/Users/onion/Desktop/AI_Testing/Weixin_Shop/backend/src/main/java/com/diybracelet/mall/file/domain/FileStorage.java:19)。

技能来源：[Superpowers brainstorming](/Users/onion/.codex/plugins/cache/openai-curated-remote/superpowers/6.4.2/skills/brainstorming/SKILL.md)。本轮执行的是用户要求的需求探索与整理，没有进入产品代码实施阶段。

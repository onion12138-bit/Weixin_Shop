# 可靠设计与运营 Implementation Plan

> **For agentic workers:** Use task ownership below to implement in parallel, then run integrated verification and an independent review. User explicitly requested development from the completed requirements report.

**Goal:** 完成 R01、R02、R03、R04、R06-A、R20，使结算、库存释放、权益与编辑操作可靠。

**Architecture:** 保留模块化单体、MySQL事务/行锁与现有Vue/微信页面；新增结算报价及提交恢复契约、到期关闭任务、券规则快照与发放批次。按文件分工避免覆盖已有未提交修复。

**Tech Stack:** Java 17 / Spring Boot / MyBatis / Flyway / MySQL；Vue 3；微信小程序 TypeScript；JUnit/Testcontainers、Vitest、Jest。

**Spec:** docs/next-iteration-brainstorm-2026-10-23.md，路线A首批六项。

## Global Constraints

- 本轮开发由用户明确授权；保留现有工作区改动，不重置、不提交混合变更、不部署或调用付费AI。
- R05制作工单、真实支付、分享营销留在后续候选，不作为本期必交付。
- 所有金额由后端计算；跨用户资源不可访问；库存、券、订单变化在事务内；Flyway仅新增迁移。
- 支付期限默认30分钟，以配置覆盖；历史待支付单以迁移时间起给予完整期限，不立刻批量关闭。
- 旧券按迁移时模板回填并记录来源，不冒称历史原始规则。
- 有意义的行为回归先写，分别跑针对性验证；集成后统一三端验证。

## Review Focus

- 提交结果已落库但响应丢失，重试必须返回原结果；相同键不同请求拒绝。
- 支付与到期关闭并发，不能既确认销售又重复退库存或券。
- 客户选择地址、券后回页面或请求乱序，不能被旧默认值覆盖。
- 模板改动、发券超时重试与批次参数变更，不得改变已发权益或重复发放。
- DIY撤销/重做及替换后，异步报价和预览不能恢复过时设计。

## Task 1: 后端结算、到期与准确轨迹（order_backend）

**Files:** backend order/**、相关新增migration V18、必要shared配置/错误；order测试。

**Interfaces:** POST /api/app/orders/quote {schemeId,couponId?} → {quoteFingerprint,schemeVersion,beadAmount,craftFee,cordFee,discountAmount,shippingFee,totalAmount}; 创建订单增加checkoutToken、quoteFingerprint；GET /api/app/orders/by-checkout-token/{token}找回本人订单；OrderResponse增加expiresAt/closedReason/serverTime。精确接口由负责人与小程序负责人同步，统一后更新此计划。

- [x] 回归覆盖报价变化、同键重试/不同内容、跨顾客隔离、到期重跑/支付竞争。
- [x] 实现后端权威报价、请求摘要/唯一约束、事务锁、到期分批任务、时间字段、模拟支付与提醒准确文案。
- [x] 券计算使用顾客券冻结字段（由Task 4提供）。
- [x] 跑订单测试，更新受影响fixture与冒烟请求契约。

## Task 2: 小程序结算与订单到期体验（checkout_frontend）

**Files:** miniprogram pages/order/**、pages/profile/addresses.*、services/orders.ts及所需小型工具/类型；对应行为测试。

**Consumes:** Task 1精确报价/提交/找回/到期接口。

- [x] 地址B仅本单使用、地址/券失败重试、弱网恢复与金额变化确认测试。
- [x] 实现本单地址选择、独立加载状态、稳定且持久的提交令牌/请求、服务端报价展示和提交；报价变化再次确认。
- [x] 订单展示到期时间/原因与服务端时间倒计时，清理定时器。
- [x] 跑针对性Jest和类型检查。

## Task 3: DIY指定替换与历史（diy_editor）

**Files:** miniprogram pages/diy/editor.*、domain/editor-history.ts及新行为测试。

- [x] 先覆盖指定替换、撤销/重做、拖动单动作、分支清理、草稿/异步报价一致性。
- [x] 替换模式选择具体SKU、确认前展示差价/尺寸；接入有界历史，保存/重载清晰处理。
- [x] 跑DIY相关测试和类型检查，保持已有拖动、报价、草稿/预览行为。

## Task 4: 冻结优惠权益与发放批次（root）

**Files:** customer coupon DTO/domain/mapper/service、migration V19、admin-web customer api/view、相关测试。

**Produces:** CustomerCoupon冻结字段 couponCode/couponName/couponDescription/discountAmount/minimumSpend/snapshotSource；订单负责人以此核销，不再读取可变模板金额。

- [x] 先测先领后改、停发不撤销、同批次重试及参数冲突、单次和批量发放。
- [x] 新发券冻结规则，旧券迁移回填来源；批次事务锁和唯一键保证结果可恢复，后台失败重试复用请求键。
- [x] 跑客户/优惠券测试与后台组件测试；同步订单核销及名称快照读取。

## Task 5: 集成、独立审阅、文档与最终验证（root）

- [x] 检查接口、迁移顺序与全部测试fixture，复核用户原有修改未丢失。
- [x] 更新API、需求/概览/进度、测试与配置说明，明确本期范围和仍未接入的外部能力。
- [x] 运行后端完整测试、后台测试/构建、小程序测试/类型检查及脚本静态检查。
- [x] 独立审查关键流程，处理实质问题，留存验证结果与尚未做的真机验收。

## 实施补充

- 结算报价增加方案版本并要求与页面展示相同；设计变化重新刷新并确认，避免旧设计与新报价混用。
- 公开材料增加只读 `availableQty` 批量查询，用于编辑器缺货提示；旧方案下架材料可删/换出，不可继续增量。
- 独立复核补强后台发券的旧页面迟到响应、列表刷新失败与代理错误处理；只清理对应批次。
- 修正取消/超时退回时的过期券状态，不能将其恢复为可用。
- 验证脚本的密钥复用夹具使用相同隔离环境，避免继承全仓流程的外部配置而误报。
- 最终测试数量、运行边界与人工验收待办见 `docs/qa/2026-10-23/reliable-operations.md`。本计划的完成指开发与自动化交付，微信真机验收另列。

# 开发规范

最后更新：2026-07-04

## 1. 环境

进入项目后先执行 `source ./scripts/project-env.sh`，统一使用 JDK 17、
项目目录内 Maven 3.9.16 和 Docker Desktop CLI。Node 依赖必须通过锁文件安装。

## 2. 后端约定

- 包按业务模块划分，再分 `api/application/domain/infrastructure`。
- Controller 负责 HTTP 契约；应用服务负责事务和业务规则；Mapper 负责 SQL。
- 金额用 `BigDecimal`，时间使用明确时区的 Java Time 类型。
- 新表或字段只新增 Flyway 迁移，禁止修改已经在共享环境执行的迁移。
- 所有可预期失败转换为稳定 `ErrorCode`，禁止把数据库异常直接暴露给客户端。
- 公共 DTO 与管理 DTO 分离，避免成本价或内部字段泄漏。

## 3. 前端约定

- Vue 请求统一通过 `src/api/http.ts`，401 时清理令牌。
- 小程序请求统一通过 `services/request.ts`，页面不拼接后端响应协议。
- DIY 设计器的状态、计价、校验必须使用可独立测试的纯 TypeScript 模块；
  Canvas 只负责渲染和命中测试。
- 图片使用后端公开 URL，不依赖本地文件绝对路径。

## 4. 注释

- Java 类、接口和公共方法使用中文 Javadoc。
- 计价、库存、订单、状态机、支付和文件安全说明原因、边界和失败策略。
- TypeScript 公共函数、组件职责和 Canvas 算法提供说明。
- 不添加只重复代码字面含义的注释。

## 5. 增加可替换实现

以对象存储为例：

1. 实现 `FileStorage`，不修改商品模块。
2. 增加配置属性和条件装配。
3. 保持 `StoredFile` 与公开 URL 契约不变。
4. 为路径、大小、MIME、读取失败和替换配置补测试。
5. 更新架构、部署和测试文档。

支付、微信身份和智能推荐采用相同的端口/适配器方式。

## 6. 提交与验证

每个提交只完成一个可验证目标，提交前运行对应模块测试。
较大功能应先更新设计和实施计划，并使用测试驱动开发。合入前执行
`./scripts/verify.sh`，数据库集成测试必须连接真实 Docker MySQL，而不是 H2。

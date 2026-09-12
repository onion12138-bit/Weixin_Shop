# 后端安全与逻辑审查记录（2026-09-12）

## 范围

审查后端 auth、identity、file、catalog/visual、diy、commerce、order、inventory、customer、recommendation、shared 各业务域的关键 Controller、请求 DTO、Service、Mapper，以及数据库迁移与运行配置。重点验证权限边界、文件路径、输入验证、优惠券状态、方案容量和地址并发。运行数据库、8081 服务、真实上传文件和本地密钥未被修改；集成测试使用独立 MySQL 8 Testcontainers 实例。

## 已修复

| 问题 | 根因与最小修复 | 失败回归证据 |
| --- | --- | --- |
| 文件符号链接越界 | 原路径检查只做 `normalize/startsWith`，仍会跟随链接读写根目录外文件。逐段拒绝存储目录内符号链接，最终读写使用 `NOFOLLOW_LINKS`。 | 三个测试原均未抛异常：文件链接读取、目录链接读取、目录链接写入。 |
| 并发创建方案突破 3 个上限 | 查询数量与插入之间没有顾客级锁。新增/复制先锁顾客行，再建立数量查询快照。 | 8 个并发请求原全部返回 201；修复后仅 3 个创建成功，其余返回 409。 |
| 优惠券列表覆盖核销状态 | 根据已读 `UNUSED` 快照更新整个实体为 `EXPIRED`，会覆盖期间已提交的 `USED`。过期状态改为只读响应投影。 | 真实 Mapper/数据库插入确定性交错后，原持久化状态从 `USED` 被覆盖成 `EXPIRED`。 |
| 取消订单后优惠券核销时间残留 | `updateById` 默认跳过 null，`usedAt=null` 没有落库。顾客和管理员取消统一执行按券、所有者和 `USED` 状态约束的原子更新，并显式清空 `used_at`；已撤销的券保持撤销，但不阻止取消及库存释放。 | 取消后数据库 `used_at` 原仍有时间值；覆盖顾客/管理员两条取消路径、再下单复用和已撤销券的取消兼容性。 |
| 头像无法清除 | 同一 MyBatis 非 null 更新策略使头像 ID 保留。仅为可清除头像字段启用显式 null 更新。 | 已有头像调用保存 null 后原响应和数据库仍返回旧头像 ID。 |
| 请求集合中的 null 绕过验证 | `@Positive` 和 `@Valid` 均不拒绝 null 集合项，导致业务层解引用失败。为报价、保存方案、工艺费、套餐明细和发券列表补充元素级 `@NotNull`。 | 五种请求原均通过 Bean Validation，新增测试同时捕获五个缺口。 |
| 多个默认地址与地址更新竞态 | 同一顾客地址事务读取过期快照后逐行写回，缺少共享串行化点。所有地址写入先锁顾客行，写入前读取地址使用当前加锁读取；管理员状态修改遵循相同顺序。 | 预热独立数据库连接后，8 个并发新增默认地址原留下 8 个默认值；修复后只保留 1 个。 |
| Excel 超行时先写入后整体报错 | 每行商品独立提交，到第 1001 行才检测整表上限，前 1000 行已经落库。现在在任何商品写入前检查有效行数。 | 1001 行输入原触发 1000 次独立商品创建后才报错；修复后在创建前拒绝。 |
| 演示配置误用于生产 | 应用曾允许任意 profile 继续用默认管理员、JWT 和 MOCK 支付。现在 `prod`/`production` 在 BeanFactoryPostProcessor 阶段明确拒绝启动，早于数据源/Flyway 等业务单例初始化。 | 两种生产 profile 的上下文原均可继续启动；测试要求明确失败并保证应用单例未实例化，默认演示上下文仍通过。 |

## 验证

每项业务修复均先运行失败回归，再修改实现。运行文件边界单元测试、请求 Bean Validation、真实 MySQL 优惠券/头像回归、方案并发、地址并发、订单/用户中心既有流程，以及 Excel 导入测试。执行的最终命令为：

```bash
source ../scripts/project-env.sh
mvn -q -Dspring.test.mockmvc.print=NONE verify
```

命令从 `backend/` 运行，最终退出码 **0**：**43 个测试套件、263 个测试，0 失败、0 错误、0 跳过**，并成功构建可执行 JAR。`git diff --check` 通过。测试包含真实 MySQL 8 并发与迁移验证，没有因 Docker 不可用而跳过集成测试。

另已用构建出的 JAR 启用 `prod` 验证：进程以退出码 1 拒绝启动，明确提示尚未接入真实微信身份和支付，未初始化 Hikari/Flyway/Tomcat。该检查禁用 Flyway 且使用不可连接的本机测试端口，不接触运行数据库。

## 依赖核查

原依赖树包含 Spring Boot 3.2.12、Spring Framework 6.1.15、Spring Security 6.2.8、Tomcat 10.1.33、POI 5.4.1。已按官方发布和安全公告更新 Spring Boot 到 3.5.16、Tomcat 到 10.1.59、POI 到 5.5.1，未迁移到 Spring Boot 4 的应用/测试新结构。

`mvn dependency:tree` 核对最终解析为 Spring Framework 6.2.19、Spring Security 6.5.11、Logback 1.5.34、Flyway 11.7.2、MySQL Connector/J 9.7.0。Flyway 和驱动升级已经通过现有 17 份迁移与 MySQL 8 集成测试；现有运行数据库未被迁移，应在下一次正式启动升级前备份并预演。

- [Spring Boot 3.5.16 发布及支持结束说明](https://spring.io/blog/2026/06/25/spring-boot-3-5-16-available-now/)
- [Spring Framework 6.2.19 安全修复说明](https://spring.io/blog/2026/06/08/spring-framework-7-0-8-and-6-2-19-available-now/)
- [Tomcat 10.1 安全公告](https://tomcat.apache.org/security-10)
- [POI 5.5.1 变更记录](https://poi.apache.org/changes.html)

本次是人工针对重点依赖的核查，不是覆盖所有传递依赖和漏洞数据库的完整 SCA。Spring Boot 3.5 已结束开源支持，后续 Spring 6 部分安全修复仅商业可用；需要持续扫描及受支持版本迁移。公告列出的组件受影响范围不等同于本应用已确认可利用，例如项目不使用 WebAuthn、WebFlux、SAML 或用户输入的 SpEL。未作“所有漏洞清零”的结论。

## 剩余边界

- 默认管理员和 JWT/pepper 配置仍为兼容本地演示保留；`prod`/`production` 已强制拒绝启动。该保护不防止操作者错误地将默认 profile 暴露公网，部署平台也应强制明确环境配置。
- guest 安装 UUID 能恢复身份；未接微信身份、设备吊销、管理员多账户或完整登录防爆破策略。
- 模拟支付接口仍保留，不能用于真实交易；未添加待支付超时关单/库存释放、退款和对账流程。
- 当前不存在覆盖身份、AI、上传、下单的跨实例频率/配额限制，文件元数据失败后的磁盘清理与历史孤儿文件回收也尚未实现。
- 文件链接保护不代替操作系统目录权限；拥有本地写权限的攻击进程仍可能制造目录替换竞态。上传类型白名单不等同于完整图片解码、重编码与内容检测。
- 本次未对全部管理员配置同时修改、业务配置动态变更及真实网络负载进行穷举并发/压力测试，也未验证真实微信或支付服务。

部署要求见 [部署说明](deployment.md)。

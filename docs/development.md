# 开发规范

## 环境

进入项目后先执行 `source ./scripts/project-env.sh`，统一使用 JDK 17、
项目目录内的 Maven 3.9.16 和 Docker Desktop CLI。

## 注释

- Java 类、接口和公共方法使用中文 Javadoc。
- 计价、库存、订单、状态机、支付和文件安全说明原因、边界和失败策略。
- TypeScript 公共函数、组件职责和 Canvas 算法提供说明。
- 不添加只重复代码字面含义的注释。

## 提交

每个提交只完成一个可验证目标，提交前运行对应模块测试。

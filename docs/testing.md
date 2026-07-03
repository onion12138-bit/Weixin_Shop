# 测试说明

- 后端：`cd backend && mvn test`
- 管理后台：`cd admin-web && pnpm test`
- 小程序逻辑：`cd miniprogram && pnpm test`
- 全仓验证：`./scripts/verify.sh`

每个功能遵循先写失败测试、再写最小实现、最后重构的顺序。

## 工程基础阶段基线

- 后端健康接口 MockMvc 测试
- 后端统一业务异常契约测试
- 管理后台项目状态组件测试与生产构建
- 小程序业务摘要纯函数测试

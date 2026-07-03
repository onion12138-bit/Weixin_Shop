# 测试说明

- 后端：`cd backend && mvn test`
- 管理后台：`cd admin-web && pnpm test`
- 小程序逻辑：`cd miniprogram && pnpm test`
- 全仓验证：`./scripts/verify.sh`

每个功能遵循先写失败测试、再写最小实现、最后重构的顺序。

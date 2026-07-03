# API 约定

- 小程序接口：`/api/app/**`
- 管理后台接口：`/api/admin/**`
- 公共接口：`/api/public/**`
- 健康检查：`/actuator/health`

业务接口统一返回 `code`、`message`、`data`、`traceId` 和 `timestamp`。

# 系统架构

当前架构为 Spring Boot 模块化单体。小程序与管理后台共享 REST API，
但使用独立身份和权限。MySQL 保存业务数据，Redis 提供缓存和分布式协调，
文件先保存到本地目录。

完整模块边界与外部适配设计见
`docs/superpowers/specs/2026-07-04-diy-bracelet-mall-design.md`。

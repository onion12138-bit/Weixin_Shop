# 前端依赖安全验证

日期：2026-09-12。审计来源为官方 npm registry；检查完整依赖树，包含开发依赖。

## 结果

| 项目 | 升级前 | 升级后 | 回归验证 |
| --- | --- | --- | --- |
| 管理后台 | 23 项：2 critical、7 high、12 moderate、2 low | 0 项 | Vitest 18 文件、165 测试通过；vue-tsc 与 Vite 生产构建通过 |
| 小程序 | 11 项：10 high、1 moderate | 0 项 | Jest 27 套件、222 测试通过；运行时 TypeScript 检查通过 |

两端 `pnpm audit --registry=https://registry.npmjs.org --json` 均退出 0，
返回 `info/low/moderate/high/critical` 全部为 0。两端冻结锁文件离线安装也通过。
这表示本次官方审计未报告已知漏洞，不代表应用已通过完整安全评估。

小程序没有独立 `build` 脚本；本次执行其既有 `typecheck` 和测试，未宣称完成微信开发者工具的打包、真机运行或发布验证。

## 版本选择

仅将直接依赖升级至本次漏洞要求的最低安全版本；其他直接依赖保持不变。
传递依赖仅定向刷新受影响包及其依赖，按原有兼容范围解析，没有使用全量 `--latest` 或跨主版本强制覆盖。

| 位置 | 包 | 原版本 | 锁定后版本 |
| --- | --- | --- | --- |
| 管理端直接依赖 | vite | 5.4.14 | 6.4.3 |
| 管理端直接依赖 | vitest | 2.1.8 | 4.1.11 |
| 管理端传递依赖 | esbuild | 0.21.5 | 0.25.12 |
| 管理端传递依赖 | postcss | 8.5.16 | 8.5.28 |
| 管理端传递依赖 | nanoid | 3.3.15 | 3.3.19 |
| 两端传递依赖 | brace-expansion | 2.1.1 | 2.1.4 |
| 小程序传递依赖 | brace-expansion | 1.1.15 | 1.1.18 |
| 小程序传递依赖 | js-yaml | 3.15.0 | 3.15.2 |
| 小程序传递依赖 | browserslist | 4.28.4 | 4.28.9 |
| 小程序传递依赖 | baseline-browser-mapping | 2.10.41 | 2.11.22 |

小程序的 package.json 无需修改：Jest 29.7.0、ts-jest 29.2.5、TypeScript 5.7.3 保持不变。
管理端 Vue 3.5.13、@vitejs/plugin-vue 5.2.1、jsdom 25.0.1、TypeScript 5.7.3 同样保持不变。
Vue 插件的 peerDependencies 支持 Vite 5/6，因此无需再升级插件主版本。

## 官方依据与运行环境

- [Vite 6.4.3 发布](https://github.com/vitejs/vite/releases/tag/v6.4.3)及其 [registry 元数据](https://registry.npmjs.org/vite/6.4.3)：修补 Windows 路径/文件限制绕过和编辑器相关漏洞；支持 Node 18、20、22+。
- [Vitest 4.1.11 发布](https://github.com/vitest-dev/vitest/releases/tag/v4.1.11)及其 [registry 元数据](https://registry.npmjs.org/vitest/4.1.11)：固定版本包含 redirect mock 文件允许列表校验。[对应公告](https://github.com/advisories/GHSA-82fw-gwwq-j7x9)说明旧 2.x/3.x 不再获得此补丁，因此无法仅升级到 2.1.9 或 3.2.6。
- [Vitest 迁移指南](https://vitest.dev/guide/migration.html)：4.x 要求 Vite 6+。4.1.11 实际发布包的 Node engines 为 `^20.0.0 || ^22.0.0 || >=24.0.0`，不包含 Node 23。
- 其余补丁依据：[brace-expansion](https://github.com/advisories/GHSA-rgw5-rvv9-x895)、[PostCSS](https://github.com/advisories/GHSA-fxqj-rqcc-2cmp)、[nanoid](https://github.com/advisories/GHSA-2v37-7h3g-55p8)、[js-yaml](https://github.com/advisories/GHSA-2883-xcg3-v3hh)、[browserslist](https://github.com/advisories/GHSA-c83g-rgw3-j3cx)、[baseline-browser-mapping](https://github.com/advisories/GHSA-w5vr-8v7q-w6rv)。

本次验证使用 Node.js 24.19.0、pnpm 11.7.0。项目根目录新增 `.nvmrc` 推荐 Node 24 LTS，
pnpm 11.7.0 发布包的 engines 为 `>=22.13`，因此管理端 engines 声明完整工具链的交集
`^22.13.0 || >=24.0.0`，并同步 `docs/setup.md`。Vitest 自身支持 Node 20，
但本项目锁定的 pnpm 版本已不支持；已有 Node 20/23 环境应切换到 24 LTS。

Vitest 4 的回归检查发现两处测试兼容性问题，已在 `OrderView.spec.ts` 修正：
显式清理 mock 调用记录，并将 SVG 断言改为比较实际导入的图标 URL，兼容 Vite 6 的内联 data URL。
没有为消除漏洞而更改业务实现或禁用测试。

## 复现

使用 Node 24 LTS，在仓库根目录执行：

```bash
nvm install
nvm use
corepack enable
cd admin-web
corepack pnpm install --frozen-lockfile
corepack pnpm audit --registry=https://registry.npmjs.org
corepack pnpm test
corepack pnpm build
cd ../miniprogram
corepack pnpm install --frozen-lockfile
corepack pnpm audit --registry=https://registry.npmjs.org
corepack pnpm test
corepack pnpm typecheck
cd ..
```

安装与审计过程中未引入 package-lock.json，未配置忽略漏洞、关闭审计或更改 registry 为第三方源。
原始本机审计输出保存在 `/tmp/weixin-admin-audit.json`、`/tmp/weixin-miniprogram-audit-before.json`、
`/tmp/weixin-admin-audit-after.json`、`/tmp/weixin-miniprogram-audit-after.json`；以上结果与版本表作为仓库内的持久证据。

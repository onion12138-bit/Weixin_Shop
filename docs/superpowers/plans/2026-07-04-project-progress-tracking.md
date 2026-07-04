# Project Progress Tracking Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 建立 `docs/progress.md` 统一记录项目模块完成度、状态、验证证据、待办和更新时间，并接入现有文档索引。

**Architecture:** `docs/progress.md` 是进度状态的唯一权威来源，使用固定状态和五档完成度。README、文档总览和变更记录只保存入口或里程碑，不复制主表数据，避免多份进度互相冲突。

**Tech Stack:** Markdown, Git, shell verification

---

### Task 1: 创建统一项目进度表

**Files:**

- Create: `docs/progress.md`

- [ ] **Step 1: 写入当前阶段和计算口径**

文件顶部必须包含：

```markdown
# 项目完成进度

最后更新：2026-07-04 18:42（Asia/Shanghai）

## 当前阶段

- 当前阶段：商品目录纵向链路已完成，等待合并后进入 DIY 设计阶段。
- 当前分支：`codex/catalog-vertical-slice`
- 最近功能提交：`7937f6e feat: add catalog vertical slice`
- 下一步：合并商品目录功能，设计并实现 DIY 排珠、实时计价和方案草稿。

## 整体进度

当前产品范围覆盖率：**39%**

计算方式：11 个一级模块完成度的算术平均值，结果四舍五入到整数。
该数值表示已覆盖的产品范围，不代表已消耗工时、剩余工期或商业上线成熟度。
```

- [ ] **Step 2: 写入模块进度主表**

主表必须使用以下列：

```markdown
| 模块 | 完成度 | 状态 | 验证结果 | 待办 | 更新时间 |
| --- | ---: | --- | --- | --- | --- |
```

主表写入 11 个一级模块：

1. 工程基础 `100% / 已完成`
2. 商品目录 `100% / 已完成`
3. 本地素材 `100% / 已完成`
4. 管理员认证 `100% / 已完成`
5. DIY 设计与计价 `0% / 待开发`
6. 方案保存与预览 `0% / 待开发`
7. 规则推荐 `0% / 待开发`
8. 库存与预占 `0% / 待开发`
9. 订单与模拟支付 `0% / 待开发`
10. 履约与生产 BOM `0% / 待开发`
11. 第三方能力替换 `25% / 进行中`

商品目录验证列必须记录：后端 24 项、管理后台 4 项、小程序 2 项、生产构建、
类型检查、真实 HTTP 冒烟和浏览器烟测全部通过。

- [ ] **Step 3: 写入阶段记录和维护规则**

阶段记录按时间倒序写入：

```markdown
| 时间 | 阶段 | 结果 | 验证证据 | 提交 |
| --- | --- | --- | --- | --- |
| 2026-07-04 | 商品目录纵向链路 | 功能完成，待合并 | 全仓验证、HTTP 冒烟、浏览器烟测通过 | `7937f6e` |
| 2026-07-04 | 工程基础 | 完成 | 三端骨架和基础验证通过 | `8ea61fb` |
```

维护规则必须明确：阶段完成时同步更新、验证只能记录实际结果、整体进度按主表重算、
需求变化先更新需求文档。

- [ ] **Step 4: 校验表头、模块数量和完成度**

Run:

```bash
rg -n "^# 项目完成进度|^最后更新：|^\\| 模块 \\| 完成度 \\| 状态 \\| 验证结果 \\| 待办 \\| 更新时间 \\|" docs/progress.md
rg -c "^\\| (工程基础|商品目录|本地素材|管理员认证|DIY 设计与计价|方案保存与预览|规则推荐|库存与预占|订单与模拟支付|履约与生产 BOM|第三方能力替换) \\|" docs/progress.md
```

Expected: 第一条命令找到标题、更新时间和主表表头；第二条输出 `11`。

### Task 2: 接入文档入口并验证

**Files:**

- Modify: `README.md`
- Modify: `docs/overview.md`
- Modify: `CHANGELOG.md`

- [ ] **Step 1: 在 README 增加进度入口**

将文档总览段落调整为：

```markdown
[`docs/overview.md`](docs/overview.md) 是文档入口，
[`docs/progress.md`](docs/progress.md) 是当前完成进度的唯一权威记录。
```

- [ ] **Step 2: 在文档总览增加索引**

在 `docs/overview.md` 文档索引表首行增加：

```markdown
| [progress.md](progress.md) | 模块完成度、状态、验证结果、待办和阶段记录 |
```

- [ ] **Step 3: 更新变更记录**

在 `CHANGELOG.md` 的 `Unreleased` 中增加：

```markdown
- 增加统一项目进度表及状态、完成度和验证证据维护规则。
```

- [ ] **Step 4: 验证链接和 Markdown 差异**

Run:

```bash
rg -n "docs/progress.md|progress.md" README.md docs/overview.md
git diff --check
git status --short
```

Expected: README 和文档总览均包含进度表入口；`git diff --check` 无输出；
状态只包含计划内文档。

- [ ] **Step 5: 提交**

```bash
git add docs/progress.md README.md docs/overview.md CHANGELOG.md \
  docs/superpowers/plans/2026-07-04-project-progress-tracking.md
git commit -m "docs: add project progress tracker"
```

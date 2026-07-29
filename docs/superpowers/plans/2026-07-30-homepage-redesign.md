# 微信小程序首页视觉改版 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将原生微信小程序首页还原为用户确认的粉色水晶手串首页，并让所有明确入口连接到现有页面或微信原生分享能力。

**Architecture:** 保留 `pages/home/index` 的原生页面边界，在页面 TypeScript 中维护静态卡片数据与轻量交互，在 WXML/WXSS 中按视觉区块实现响应式页面。图片和开源图标全部随小程序本地打包；不新增后端调用、商品 ID 映射或不存在的业务页面。

**Tech Stack:** 微信小程序原生 TypeScript、WXML、WXSS、Jest 29、Remix Icon 本地 SVG、项目现有 pnpm 工具链

## Global Constraints

- 只修改原生微信小程序首页、首页行为测试、首页静态资源和视觉验收报告。
- 不修改后端、管理后台、商品数据结构或现有业务页面。
- 使用 `Pic/bracelet_homepage_assets_named` 中的主视觉、4 张心情图和 4 张成品图。
- 商品卡不绑定具体后端商品 ID；收藏不做持久化。
- 心情选择与“换一换”只维护当前页面生命周期内的状态。
- 页面使用小程序原生导航栏显示“AI 灵感咨询”。

---

## File Map

- `miniprogram/miniprogram/pages/home/index.ts`：首页卡片数据、路由、心情轮换、分享配置和错误提示。
- `miniprogram/miniprogram/pages/home/index.wxml`：首页可交互语义结构。
- `miniprogram/miniprogram/pages/home/index.wxss`：首页视觉布局、状态、响应式和安全区。
- `miniprogram/miniprogram/pages/home/index.json`：标题、背景色和分享配置。
- `miniprogram/miniprogram/assets/images/home/*.webp`：参考图提供的 9 张首页图片素材和分享礼盒插画的体积优化版本。
- `miniprogram/miniprogram/assets/icons/home/*.svg`：从 Remix Icon 开源库选取的标准界面图标。
- `miniprogram/src/home-page-behavior.spec.ts`：页面状态、路由、失败提示和分享行为测试。
- `design-qa.md`：参考图与实现截图的视觉差异和最终验收结果。

---

### Task 1: 首页状态与行为

**Files:**
- Create: `miniprogram/src/home-page-behavior.spec.ts`
- Modify: `miniprogram/miniprogram/pages/home/index.ts`

**Interfaces:**
- Consumes: 微信小程序全局 `Page`、`wx.navigateTo`、`wx.pageScrollTo` 和 `wx.showToast`。
- Produces: `selectMood(event)`、`rotateMoods()`、`openDiy()`、`openRecommendation()`、`openCatalog()`、`openSchemes()`、`backToTop()`、`onShareAppMessage()`。

- [ ] **Step 1: 写出失败的页面行为测试**

```ts
declare function require(name: string): unknown

function loadPage() {
  let definition: Record<string, any> | null = null
  const wxMock = {
    navigateTo: jest.fn(),
    pageScrollTo: jest.fn(),
    showToast: jest.fn(),
  }
  ;(globalThis as any).wx = wxMock
  ;(globalThis as any).Page = (options: Record<string, any>) => {
    definition = options
  }
  jest.isolateModules(() => require('../miniprogram/pages/home/index'))
  if (!definition) throw new Error('Page definition was not captured')
  const page: Record<string, any> = { ...definition, data: { ...definition.data } }
  page.setData = jest.fn((patch: Record<string, unknown>) => {
    Object.assign(page.data, patch)
  })
  return { page, wxMock }
}

describe('home Page behavior', () => {
  it.each([
    ['openDiy', '/pages/diy/editor'],
    ['openRecommendation', '/pages/recommendation/index'],
    ['openCatalog', '/pages/catalog/index'],
    ['openSchemes', '/pages/diy/schemes'],
  ])('%s opens the existing route', (method, url) => {
    const { page, wxMock } = loadPage()
    page[method]()
    expect(wxMock.navigateTo).toHaveBeenCalledWith(expect.objectContaining({ url }))
  })

  it('selects a mood and rotates the recommendation order', () => {
    const { page } = loadPage()
    page.selectMood({ currentTarget: { dataset: { moodId: 'energy' } } })
    expect(page.data.selectedMoodId).toBe('energy')
    const firstMood = page.data.moods[0]
    page.rotateMoods()
    expect(page.data.moods[page.data.moods.length - 1]).toEqual(firstMood)
    expect(page.data.selectedMoodId).toBe(page.data.moods[0].id)
  })

  it('returns share copy and hero image', () => {
    const { page } = loadPage()
    expect(page.onShareAppMessage()).toEqual({
      title: '一颗珠子，一种心情｜来设计你的能量手串',
      path: '/pages/home/index',
      imageUrl: '/assets/images/home/hero-bracelet-lotus.png',
    })
  })
})
```

- [ ] **Step 2: 运行行为测试并确认失败**

Run: `cd miniprogram && ./node_modules/.bin/jest --runInBand src/home-page-behavior.spec.ts`

Expected: FAIL，因为首页尚未提供 `moods`、`selectMood`、`rotateMoods` 和新分享配置。

- [ ] **Step 3: 用最小实现补齐首页数据与行为**

在 `index.ts` 中定义 4 张心情卡和 4 张成品卡；所有路由统一通过一个内部 `navigate(url: string)` 方法调用 `wx.navigateTo`，其 `fail` 回调显示 `{ title: '页面暂时无法打开', icon: 'none' }`。`rotateMoods` 将第一项移动到末尾并选中新首项；`selectMood` 从 `event.currentTarget.dataset.moodId` 更新状态；`backToTop` 调用 `wx.pageScrollTo({ scrollTop: 0, duration: 300 })`。

- [ ] **Step 4: 运行行为测试和类型检查**

Run: `cd miniprogram && ./node_modules/.bin/jest --runInBand src/home-page-behavior.spec.ts && ./node_modules/.bin/tsc -p tsconfig.runtime.json`

Expected: 新行为测试 PASS，TypeScript 无错误。

- [ ] **Step 5: 提交行为实现**

```bash
git add miniprogram/src/home-page-behavior.spec.ts miniprogram/miniprogram/pages/home/index.ts
git commit -m "feat: add interactive homepage behavior"
```

---

### Task 2: 首页视觉结构与本地素材

**Files:**
- Create: `miniprogram/miniprogram/assets/images/home/hero-bracelet-lotus.webp`
- Create: `miniprogram/miniprogram/assets/images/home/mood-{gentle,luck,energy,relax}.webp`
- Create: `miniprogram/miniprogram/assets/images/home/product-{gentle-guardian,lucky-blessing,confidence-light,cloud-breath}.webp`
- Create: `miniprogram/miniprogram/assets/images/home/gift-box.webp`
- Create: `miniprogram/miniprogram/assets/icons/home/*.svg`
- Modify: `miniprogram/miniprogram/pages/home/index.wxml`
- Modify: `miniprogram/miniprogram/pages/home/index.wxss`
- Modify: `miniprogram/miniprogram/pages/home/index.json`

**Interfaces:**
- Consumes: Task 1 的 `moods`、`products`、`selectedMoodId` 和页面事件方法。
- Produces: 带 `hero-section`、`mood-section`、`product-section`、`scheme-section`、`share-card`、`bottom-nav` 的完整首页视图。

- [ ] **Step 1: 接入图片和 Remix Icon 图标**

将 `Pic/bracelet_homepage_assets_named` 的 9 张独立素材缩放并转换为 WebP 后放入 `assets/images/home`，使用 kebab-case 文件名，把首页图片资源控制在 500KB 内。参考设计稿生成分享卡缺少的粉色礼盒插画并移除背景。将 Remix Icon 的 `magic-line`、`heart-3-line`、`shopping-bag-line`、`refresh-line`、`arrow-right-s-line`、`home-5-fill`、`sparkling-line`、`palette-line`、`user-3-line`、`share-line` 与 `heart-line` 作为本地 SVG 放入 `assets/icons/home`；保留原始开源图标路径，不手绘 SVG。

- [ ] **Step 2: 实现 WXML 与页面配置**

按参考图顺序构建主视觉、三个操作入口、心情卡、商品卡、我的方案、分享卡和固定底部导航。按钮使用 Task 1 的事件方法，商品卡与查看更多复用 `openCatalog`，分享按钮设置 `open-type="share"`，页面分享内容由 Task 1 的 `onShareAppMessage` 提供。将页面标题改为“AI 灵感咨询”，导航栏与页面背景色设为 `#fff7f9`。

- [ ] **Step 3: 实现 WXSS 视觉系统**

以 750rpx 设计宽度实现：主视觉 620–680rpx 高度、32rpx 页面横向留白、32–44rpx 卡片圆角、深紫 `#321044` 文字、强调粉 `#f36fa8`、白色半透明卡片和轻粉阴影。图片全部使用 `<image>`，图标全部使用本地图标文件；正文底部预留固定导航与 `env(safe-area-inset-bottom)`。

- [ ] **Step 4: 运行完整小程序测试和类型检查**

Run: `cd miniprogram && ./node_modules/.bin/jest --runInBand && ./node_modules/.bin/tsc -p tsconfig.runtime.json`

Expected: 全部测试 PASS，TypeScript 无错误。

- [ ] **Step 5: 提交页面视觉实现**

```bash
git add miniprogram/miniprogram/pages/home miniprogram/miniprogram/assets/images/home miniprogram/miniprogram/assets/icons/home
git commit -m "feat: recreate crystal bracelet homepage"
```

---

### Task 3: 视觉与交互验收

**Files:**
- Create: `design-qa.md`
- Modify: `miniprogram/miniprogram/pages/home/index.wxml`
- Modify: `miniprogram/miniprogram/pages/home/index.wxss`

**Interfaces:**
- Consumes: Task 2 的完整首页与 `Pic/bracelet_homepage_assets_named/homepage_full_mockup.png`。
- Produces: `design-qa.md` 中的 `final result: passed` 以及通过验收的最终页面。

- [ ] **Step 1: 启动可用的小程序预览并打开首页**

优先使用本机微信开发者工具打开仓库根目录的 `project.config.json`。确认首页显示、无 WXML/WXSS 编译错误，并将视口调整到接近参考图的 821:1916 比例。

- [ ] **Step 2: 验证核心交互**

依次点击“开始 DIY”“AI 情绪推荐”“成品精选”“我的方案”和底部导航，确认到达设计说明约定的现有路由；点击心情卡和“换一换”确认选中态与顺序变化；触发分享按钮确认微信分享面板可用。

- [ ] **Step 3: 截图并执行视觉对比**

将同一视口状态的首页截图与 `homepage_full_mockup.png` 对比，检查主视觉裁切、标题换行、卡片尺寸、分区间距、底部导航和安全区。把发现记录到 `design-qa.md`，格式包含 `P0`–`P3` 严重度和 `final result: failed`。

- [ ] **Step 4: 修复全部 P0/P1/P2 并重复截图**

每次只修改 `index.wxml` 或 `index.wxss` 中对应问题；重新截图和对比，直到报告没有 P0/P1/P2。剩余 P3 只作为后续可选润色记录。

- [ ] **Step 5: 写入最终验收结论**

将 `design-qa.md` 结论更新为 `final result: passed`，并列出已验证的视口、交互与自动化命令。

- [ ] **Step 6: 最终验证并提交**

Run: `cd miniprogram && ./node_modules/.bin/jest --runInBand && ./node_modules/.bin/tsc -p tsconfig.runtime.json`

Expected: 全部测试 PASS，TypeScript 无错误，`design-qa.md` 为 `final result: passed`。

```bash
git add miniprogram/miniprogram/pages/home/index.wxml miniprogram/miniprogram/pages/home/index.wxss design-qa.md
git commit -m "test: verify homepage visual fidelity"
```

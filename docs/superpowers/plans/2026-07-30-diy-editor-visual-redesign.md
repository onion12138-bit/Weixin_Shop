# DIY 串珠编辑页视觉改版 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不修改后端的前提下，把 DIY 串珠编辑页改为可折叠预览、三列珠子库、本地喜欢与喜欢筛选的粉色首页同系界面。

**Architecture:** 保留 `pages/diy/editor` 的现有编辑状态、Canvas、报价和保存流程；新增纯函数模块负责喜欢 ID 校验、切换和当前目录过滤，页面只负责本地存储与 UI 事件。全局背景通过 `app.json`、`app.wxss` 和各页面根容器统一为 `#fff8fa`，不平铺纹理图片。

**Tech Stack:** 微信小程序原生 TypeScript、WXML、WXSS、Jest 29、现有 Canvas 渲染器与目录服务

## Global Constraints

- 后端、数据库、管理后台、接口参数、报价规则和方案数据结构零改动。
- 喜欢记录只使用 `wx.getStorageSync` / `wx.setStorageSync`，键名固定为 `diy_favorite_bead_ids`。
- 预览展开状态不持久化，每次进入页面默认展开。
- 尺寸区只显示“目标 N mm”和“当前 N mm”，目标在上、当前在下，不显示尺寸建议。
- 选中珠子操作只显示右对齐的减号和加号。
- 珠子库为三列向下网格；卡片主体加珠，爱心 `catchtap` 只切换喜欢。
- 小程序所有页面根背景与原生导航背景统一为 `#fff8fa`。
- 不修改当前工作区中已有的无关脏文件；只暂存本计划列出的文件。

---

## File Map

- Create `miniprogram/miniprogram/domain/diy-catalog-presentation.ts`：校验喜欢 ID、切换喜欢和过滤当前目录结果。
- Create `miniprogram/src/diy-catalog-presentation.spec.ts`：纯函数单元测试。
- Create `miniprogram/src/diy-editor-presentation-behavior.spec.ts`：页面预览、喜欢存储、喜欢筛选和 Canvas 重绘行为测试。
- Modify `miniprogram/miniprogram/pages/diy/editor.ts`：新增前端状态、目录结果派生、预览折叠和喜欢事件。
- Modify `miniprogram/miniprogram/utils/diy-view-model.ts`：为现有摘要补充“目标”和“当前”两条独立文案。
- Modify `miniprogram/src/diy-view-model.spec.ts`：验证独立尺寸文案。
- Modify `miniprogram/miniprogram/pages/diy/editor.wxml`：重排尺寸、选中操作、搜索、三列卡片和折叠态结构。
- Modify `miniprogram/miniprogram/pages/diy/editor.wxss`：实现参考图中的白色卡片、折叠预览、珠子网格与粉色视觉。
- Create `miniprogram/miniprogram/assets/icons/diy/{add,subtract,arrow-up-s,arrow-down-s,arrow-go-back,arrow-go-forward}-line.svg`：从 Remix Icon 官方资源复制的操作图标。
- Modify `miniprogram/miniprogram/app.json`：统一窗口和原生导航背景。
- Modify `miniprogram/miniprogram/app.wxss`：统一 `page` 根背景。
- Modify `miniprogram/miniprogram/pages/{recommendation,catalog,diy,order}/*.wxss`：统一各页面可见根容器背景。
- Modify `design-qa.md`：记录四张参考图与微信开发者工具实现截图的最终视觉验收。
- Create `design-qa-evidence/diy-reference-{summary,collapsed,grid,search}.png`：把用户提供的临时参考图复制为稳定的项目证据。
- Create `design-qa-evidence/diy-*.png`：保存展开态、收起态、喜欢筛选态与并排对比证据。

---

### Task 1: 喜欢状态与目录过滤纯函数

**Files:**
- Create: `miniprogram/miniprogram/domain/diy-catalog-presentation.ts`
- Create: `miniprogram/src/diy-catalog-presentation.spec.ts`

**Interfaces:**
- Consumes: `unknown` 本地存储值、珠子 `{ id: number }[]`、喜欢 ID 和筛选开关。
- Produces: `DIY_FAVORITE_STORAGE_KEY`、`normalizeFavoriteBeadIds(value)`、`toggleFavoriteBead(ids, skuId)`、`filterFavoriteCatalog(items, favoriteIds, favoritesOnly)`。

- [ ] **Step 1: 写出失败的纯函数测试**

```ts
import {
  DIY_FAVORITE_STORAGE_KEY,
  filterFavoriteCatalog,
  normalizeFavoriteBeadIds,
  toggleFavoriteBead,
} from '../miniprogram/domain/diy-catalog-presentation'

describe('DIY catalog presentation', () => {
  it('normalizes storage to unique positive integer sku ids', () => {
    expect(normalizeFavoriteBeadIds([2002, 2001, 2002, -1, 0, 1.5, '2003']))
      .toEqual([2002, 2001])
    expect(normalizeFavoriteBeadIds('{broken')).toEqual([])
    expect(DIY_FAVORITE_STORAGE_KEY).toBe('diy_favorite_bead_ids')
  })

  it('toggles one sku without duplicates', () => {
    expect(toggleFavoriteBead([2001], 2002)).toEqual([2001, 2002])
    expect(toggleFavoriteBead([2001, 2002], 2001)).toEqual([2002])
  })

  it('filters only the current query result when favorites-only is active', () => {
    const result = [{ id: 2001 }, { id: 2002 }, { id: 2003 }]
    expect(filterFavoriteCatalog(result, [2002, 9999], true))
      .toEqual([{ id: 2002 }])
    expect(filterFavoriteCatalog(result, [2002], false)).toEqual(result)
  })
})
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `cd miniprogram && ./node_modules/.bin/jest --runInBand src/diy-catalog-presentation.spec.ts`

Expected: FAIL，模块 `diy-catalog-presentation` 尚不存在。

- [ ] **Step 3: 实现最小纯函数模块**

```ts
export const DIY_FAVORITE_STORAGE_KEY = 'diy_favorite_bead_ids'

export function normalizeFavoriteBeadIds(value: unknown): number[] {
  const candidate = typeof value === 'string'
    ? parseStoredArray(value)
    : value
  if (!Array.isArray(candidate)) return []
  return Array.from(new Set(candidate.filter((item): item is number => (
    typeof item === 'number'
    && Number.isInteger(item)
    && item > 0
  ))))
}

export function toggleFavoriteBead(ids: number[], skuId: number): number[] {
  if (!Number.isInteger(skuId) || skuId <= 0) return [...ids]
  return ids.includes(skuId)
    ? ids.filter((item) => item !== skuId)
    : [...ids, skuId]
}

export function filterFavoriteCatalog<T extends { id: number }>(
  items: T[],
  favoriteIds: number[],
  favoritesOnly: boolean,
): T[] {
  if (!favoritesOnly) return [...items]
  const favorites = new Set(favoriteIds)
  return items.filter((item) => favorites.has(item.id))
}

function parseStoredArray(value: string): unknown {
  try {
    return JSON.parse(value)
  } catch {
    return []
  }
}
```

- [ ] **Step 4: 运行单测和类型检查**

Run: `cd miniprogram && ./node_modules/.bin/jest --runInBand src/diy-catalog-presentation.spec.ts && ./node_modules/.bin/tsc -p tsconfig.runtime.json`

Expected: 纯函数测试 PASS，TypeScript 无错误。

- [ ] **Step 5: 提交纯函数与测试**

```bash
git add miniprogram/miniprogram/domain/diy-catalog-presentation.ts miniprogram/src/diy-catalog-presentation.spec.ts
git commit -m "feat: add local diy favorite filtering"
```

---

### Task 2: DIY 页面前端状态与交互

**Files:**
- Create: `miniprogram/src/diy-editor-presentation-behavior.spec.ts`
- Modify: `miniprogram/miniprogram/pages/diy/editor.ts`
- Modify: `miniprogram/miniprogram/utils/diy-view-model.ts`
- Modify: `miniprogram/src/diy-view-model.spec.ts`

**Interfaces:**
- Consumes: Task 1 的四个导出、现有 `CatalogItem`、`drawBracelet()`、`listBeads()` 和 `setData()`。
- Produces: `previewExpanded: boolean`、`favoriteBeadIds: number[]`、`favoritesOnly: boolean`、`catalogResults: CatalogItem[]`、`visibleCatalog: CatalogDisplayItem[]`、`targetLengthText`、`currentLengthText`、`togglePreview()`、`toggleFavorite()`、`toggleFavoriteFilter()`、`refreshVisibleCatalog()`。

- [ ] **Step 1: 写出失败的页面行为测试**

建立与现有页面测试一致的 `Page` 捕获和 `wx` mock，新增以下断言：

```ts
it('starts expanded and toggles preview with one redraw', () => {
  const { page } = loadEditorPage()
  page.drawBracelet = jest.fn()

  expect(page.data.previewExpanded).toBe(true)
  page.togglePreview()

  expect(page.data.previewExpanded).toBe(false)
  expect(page.drawBracelet).toHaveBeenCalledTimes(1)
})

it('loads, toggles and persists favorites without adding a bead', () => {
  const { page, wxMock } = loadEditorPage([2001])
  page.data.catalogResults = [{ id: 2001 }, { id: 2002 }]
  page.data.visibleCatalog = [...page.data.catalogResults]

  page.loadFavoriteBeads()
  page.toggleFavorite({ currentTarget: { dataset: { id: 2002 } } })

  expect(page.data.favoriteBeadIds).toEqual([2001, 2002])
  expect(wxMock.setStorageSync).toHaveBeenCalledWith(
    'diy_favorite_bead_ids',
    JSON.stringify([2001, 2002]),
  )
})

it('filters current catalog results and restores them when disabled', () => {
  const { page } = loadEditorPage([2002])
  page.data.favoriteBeadIds = [2002]
  page.data.catalogResults = [
    { id: 2001, salePriceCents: 680 },
    { id: 2002, salePriceCents: 980 },
  ]

  page.toggleFavoriteFilter()
  expect(page.data.visibleCatalog.map((item: { id: number }) => item.id))
    .toEqual([2002])

  page.toggleFavoriteFilter()
  expect(page.data.visibleCatalog.map((item: { id: number }) => item.id))
    .toEqual([2001, 2002])
})

it('builds target and current length as separate lines', () => {
  expect(buildEditorSummary({
    beadCount: 5,
    currentLengthMm: 40,
    targetLengthMm: 185,
    sizeStatus: 'TOO_SHORT',
    estimatedPriceCents: 4460,
    authoritative: false,
  })).toMatchObject({
    targetLengthText: '目标 185 mm',
    currentLengthText: '当前 40 mm',
  })
})
```

测试辅助函数必须 mock 当前文件导入的 `services/catalog`、`services/diy`，让 `setData(patch, callback)` 在合并 patch 后执行 callback，并让 `drawBracelet` 在实例化后可覆盖；不要修改当前已有脏状态的 `diy-ai-handoff-behavior.spec.ts`。

- [ ] **Step 2: 运行测试并确认失败**

Run: `cd miniprogram && ./node_modules/.bin/jest --runInBand src/diy-editor-presentation-behavior.spec.ts`

Expected: FAIL，因为页面尚无预览和喜欢状态方法。

- [ ] **Step 3: 增加页面状态并在加载时读取喜欢**

在 `editor.ts` 导入 Task 1 的纯函数，向 `data` 增加：

```ts
previewExpanded: true,
favoriteBeadIds: [] as number[],
favoritesOnly: false,
catalogResults: [] as CatalogItem[],
visibleCatalog: [] as CatalogDisplayItem[],
targetLengthText: '目标 160 mm',
currentLengthText: '当前 0 mm',
```

`CatalogDisplayItem` 只负责 WXML 展示，不替换 Canvas 使用的 `CatalogItem`：

```ts
interface CatalogDisplayItem extends CatalogItem {
  favorite: boolean
  salePriceText: string
}
```

在 `onLoad` 初始化编辑器状态后、调用 `bootstrap` 前执行 `this.loadFavoriteBeads()`：

```ts
loadFavoriteBeads() {
  let favoriteBeadIds: number[] = []
  try {
    favoriteBeadIds = normalizeFavoriteBeadIds(
      wx.getStorageSync(DIY_FAVORITE_STORAGE_KEY),
    )
  } catch {
    favoriteBeadIds = []
  }
  this.setData({ favoriteBeadIds })
  this.refreshVisibleCatalog()
},
```

- [ ] **Step 4: 实现预览与喜欢事件**

```ts
togglePreview() {
  this.setData(
    { previewExpanded: !this.data.previewExpanded },
    () => {
      this.resetCanvasForResize()
      this.drawBracelet()
    },
  )
},

toggleFavorite(event: WechatMiniprogram.TouchEvent) {
  const skuId = Number(event.currentTarget.dataset.id || 0)
  const favoriteBeadIds = toggleFavoriteBead(
    this.data.favoriteBeadIds,
    skuId,
  )
  this.setData({ favoriteBeadIds })
  this.refreshVisibleCatalog()
  try {
    wx.setStorageSync(
      DIY_FAVORITE_STORAGE_KEY,
      JSON.stringify(favoriteBeadIds),
    )
  } catch {
    wx.showToast({ title: '喜欢记录未保存', icon: 'none' })
  }
},

toggleFavoriteFilter() {
  this.setData({ favoritesOnly: !this.data.favoritesOnly })
  this.refreshVisibleCatalog()
},

refreshVisibleCatalog() {
  this.setData({
    visibleCatalog: filterFavoriteCatalog(
      this.data.catalogResults,
      this.data.favoriteBeadIds,
      this.data.favoritesOnly,
    ),
  })
},
```

`resetCanvasForResize()` 清除 Canvas 尺寸引用和进行中的选择器查询，但不清空序列、历史或图片缓存：

```ts
resetCanvasForResize() {
  this.canvasNode = null
  this.canvasContext = null
  this.canvasLogicalWidth = 0
  this.canvasLogicalHeight = 0
  this.canvasQueryPending = false
},
```

- [ ] **Step 5: 让目录查询维护完整目录和当前结果**

`bootstrap` 首次拿到 beads 后同时设置 `catalogResults` 和 `visibleCatalog`。`searchCatalog` 使用当前查询返回值覆盖 `catalogResults`，同时通过 `mergeCatalog` 保留完整 `catalog` 供 Canvas 和方案使用：

```ts
const catalogResults = page.items.map(toCatalogItem)
this.mergeCatalog(catalogResults)
this.setData({ catalogResults })
this.refreshVisibleCatalog()
```

`mergeCatalog` 只更新 `catalog`，不得用喜欢筛选结果覆盖它。

同时扩展 `EditorSummary` 和 `buildEditorSummary`，保留现有 `lengthText` 兼容字段并新增独立两行：

```ts
export interface EditorSummary {
  countText: string
  lengthText: string
  targetLengthText: string
  currentLengthText: string
  sizeText: string
  priceText: string
  priceBadge: string
}

targetLengthText: `目标 ${formatMm(input.targetLengthMm)} mm`,
currentLengthText: `当前 ${formatMm(input.currentLengthMm)} mm`,
```

`refreshDerivedState` 与 `applyQuote` 都从 summary 写入 `targetLengthText` 和 `currentLengthText`。`refreshVisibleCatalog()` 生成展示字段：

```ts
const favorites = new Set(this.data.favoriteBeadIds)
const filtered = filterFavoriteCatalog(
  this.data.catalogResults,
  this.data.favoriteBeadIds,
  this.data.favoritesOnly,
)
this.setData({
  visibleCatalog: filtered.map((item) => ({
    ...item,
    favorite: favorites.has(item.id),
    salePriceText: (item.salePriceCents / 100).toFixed(2),
  })),
})
```

- [ ] **Step 6: 运行页面行为测试和相关回归测试**

Run: `cd miniprogram && ./node_modules/.bin/jest --runInBand src/diy-editor-presentation-behavior.spec.ts src/diy-catalog-presentation.spec.ts src/diy-view-model.spec.ts src/diy-editor-state.spec.ts`

Expected: 新测试和现有 DIY 状态测试全部 PASS。

- [ ] **Step 7: 提交页面行为实现**

```bash
git add miniprogram/src/diy-editor-presentation-behavior.spec.ts miniprogram/miniprogram/pages/diy/editor.ts miniprogram/miniprogram/utils/diy-view-model.ts miniprogram/src/diy-view-model.spec.ts
git commit -m "feat: add collapsible diy preview and favorites"
```

---

### Task 3: DIY 页面结构和粉色视觉重排

**Files:**
- Modify: `miniprogram/miniprogram/pages/diy/editor.wxml`
- Modify: `miniprogram/miniprogram/pages/diy/editor.wxss`
- Create: `miniprogram/miniprogram/assets/icons/diy/add-line.svg`
- Create: `miniprogram/miniprogram/assets/icons/diy/subtract-line.svg`
- Create: `miniprogram/miniprogram/assets/icons/diy/arrow-up-s-line.svg`
- Create: `miniprogram/miniprogram/assets/icons/diy/arrow-down-s-line.svg`
- Create: `miniprogram/miniprogram/assets/icons/diy/arrow-go-back-line.svg`
- Create: `miniprogram/miniprogram/assets/icons/diy/arrow-go-forward-line.svg`

**Interfaces:**
- Consumes: Task 2 的 `previewExpanded`、`favoriteBeadIds`、`favoritesOnly`、`visibleCatalog` 和事件方法。
- Produces: 展开/收起预览、两行尺寸、右侧加减、三列珠子卡和喜欢筛选的完整 DIY 页面。

- [ ] **Step 1: 重排预览和尺寸区 WXML**

将 Canvas 面板增加状态 class 和折叠按钮；删除 `sizeText` 可见节点：

```xml
<view class="canvas-panel {{previewExpanded ? 'is-expanded' : 'is-collapsed'}}">
  <button class="preview-toggle" bindtap="togglePreview">
    <text>{{previewExpanded ? '收起预览' : '展开预览'}}</text>
    <image
      class="preview-chevron"
      src="{{previewExpanded ? '/assets/icons/diy/arrow-up-s-line.svg' : '/assets/icons/diy/arrow-down-s-line.svg'}}"
      mode="aspectFit"
    />
  </button>
  <view class="preview-layout">
    <canvas
      id="bracelet-canvas"
      type="2d"
      class="bracelet-canvas"
      bindtouchstart="onCanvasTouchStart"
      bindtouchmove="onCanvasTouchMove"
      bindtouchend="onCanvasTouchEnd"
    />
    <view class="editor-summary">
      <text class="count-text">{{countText}}</text>
      <view class="length-stack">
        <text>{{targetLengthText}}</text>
        <text>{{currentLengthText}}</text>
      </view>
    </view>
  </view>
  <view wx:if="{{previewExpanded}}" class="history-actions">
    <button disabled="{{!canUndo}}" bindtap="undo" aria-label="撤销">
      <image src="/assets/icons/diy/arrow-go-back-line.svg" mode="aspectFit" />
    </button>
    <button disabled="{{!canRedo}}" bindtap="redo" aria-label="重做">
      <image src="/assets/icons/diy/arrow-go-forward-line.svg" mode="aspectFit" />
    </button>
  </view>
</view>
```

Task 2 同步把原来的 `lengthText` 拆为 `targetLengthText` 和 `currentLengthText`，值由已有 summary 的 current/target 数字生成；不要在 WXML 中用字符串切割。`buildLocalSummary` 和 `applyQuote` 都更新这两个字段。

- [ ] **Step 2: 精简选中操作**

```xml
<view wx:if="{{selectedIndex >= 0}}" class="selected-actions">
  <button aria-label="减少一颗" bindtap="decreaseSelectedSku">
    <image src="/assets/icons/diy/subtract-line.svg" mode="aspectFit" />
  </button>
  <button aria-label="增加一颗" bindtap="increaseSelectedSku">
    <image src="/assets/icons/diy/add-line.svg" mode="aspectFit" />
  </button>
</view>
```

不渲染第几颗、替换或删除按钮；保留对应 TypeScript 方法供现有其他调用兼容，不删除业务能力。

- [ ] **Step 3: 实现搜索、喜欢筛选和三列卡片 WXML**

```xml
<view class="bead-search">
  <input value="{{keyword}}" bindinput="onKeywordInput" placeholder="搜索珠子" />
  <button
    class="favorite-filter {{favoritesOnly ? 'active' : ''}}"
    bindtap="toggleFavoriteFilter"
  >
    <image src="/assets/icons/home/heart-line.svg" mode="aspectFit" />
    <text>喜欢</text>
  </button>
  <button class="search-button" bindtap="searchCatalog">搜索</button>
</view>

<view wx:if="{{visibleCatalog.length}}" class="bead-grid">
  <view
    wx:for="{{visibleCatalog}}"
    wx:key="id"
    class="bead-card"
    data-id="{{item.id}}"
    bindtap="addCatalogBead"
    aria-role="button"
    aria-label="添加 {{item.name}}"
  >
    <button
      class="favorite-button {{item.favorite ? 'active' : ''}}"
      data-id="{{item.id}}"
      catchtap="toggleFavorite"
      aria-label="{{item.favorite ? '取消喜欢' : '加入喜欢'}}"
    >
      <image src="/assets/icons/home/heart-line.svg" mode="aspectFit" />
    </button>
    <image
      wx:if="{{item.trayImageUrl}}"
      class="tray-bead tray-bead-image"
      src="{{item.trayImageUrl}}"
      mode="aspectFit"
      data-id="{{item.id}}"
      data-visual-key="{{item.visualVersion}}:{{item.trayImageUrl}}"
      binderror="onTrayImageError"
    />
    <view wx:else class="tray-bead" style="background: {{item.colorHex}};" />
    <text class="bead-name">{{item.name}} · {{item.diameterMm}}mm</text>
    <text class="bead-price">¥{{item.salePriceText}}</text>
  </view>
</view>
<view wx:else class="catalog-empty">
  {{favoritesOnly ? '还没有喜欢的珠子' : '没有找到珠子'}}
</view>
```

`visibleCatalog` 已由 Task 2 增加 `favorite: boolean` 和 `salePriceText: string` 展示字段；WXML 使用 `item.favorite`，不直接调用数组方法。

- [ ] **Step 4: 实现对应 WXSS**

关键尺寸和布局：

```css
.diy-page {
  min-height: 100vh;
  padding: 24rpx 24rpx 180rpx;
  background: #fff8fa;
}

.canvas-panel {
  position: relative;
  border: 1rpx solid rgba(236, 205, 217, .72);
  border-radius: 34rpx;
  background: rgba(255, 255, 255, .92);
  box-shadow: 0 18rpx 48rpx rgba(174, 101, 131, .10);
}

.canvas-panel.is-expanded .bracelet-canvas { height: 620rpx; }
.canvas-panel.is-collapsed .preview-layout {
  display: grid;
  grid-template-columns: 300rpx 1fr;
  min-height: 220rpx;
  align-items: center;
}
.canvas-panel.is-collapsed .bracelet-canvas { height: 210rpx; }
.canvas-panel.is-collapsed .editor-summary { text-align: left; }

.selected-actions { justify-content: flex-end; }
.selected-actions button {
  width: 84rpx;
  height: 58rpx;
  border-radius: 29rpx;
  background: #f8eaf0;
}

.bead-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16rpx;
}
.bead-card {
  position: relative;
  min-width: 0;
  height: 220rpx;
  border-radius: 28rpx;
  background: rgba(255, 255, 255, .94);
}
.favorite-button {
  position: absolute;
  top: 12rpx;
  right: 12rpx;
  width: 48rpx;
  height: 48rpx;
  border-radius: 24rpx;
}
.favorite-button.active { background: #f176a8; }
```

所有触控按钮保持至少约 44 CSS px 的可点击区域。珠子卡主体固定使用可点击 `<view aria-role="button">`，爱心使用独立 `<button>`，避免嵌套按钮。

六个 DIY 图标必须从 Remix Icon 官方包复制原始 SVG 路径，只修改 `fill` 颜色；不得手绘 SVG 或使用文字字符替代。

- [ ] **Step 5: 运行行为测试和类型检查**

Run: `cd miniprogram && ./node_modules/.bin/jest --runInBand src/diy-editor-presentation-behavior.spec.ts src/diy-catalog-presentation.spec.ts && ./node_modules/.bin/tsc -p tsconfig.runtime.json`

Expected: 交互测试 PASS，TypeScript 无错误。

- [ ] **Step 6: 提交 DIY 页面视觉结构**

```bash
git add miniprogram/miniprogram/pages/diy/editor.wxml miniprogram/miniprogram/pages/diy/editor.wxss miniprogram/miniprogram/assets/icons/diy
git commit -m "feat: redesign diy editor workspace"
```

---

### Task 4: 全小程序浅丝绸粉背景

**Files:**
- Modify: `miniprogram/miniprogram/app.json`
- Modify: `miniprogram/miniprogram/app.wxss`
- Modify: `miniprogram/miniprogram/pages/recommendation/index.wxss`
- Modify: `miniprogram/miniprogram/pages/catalog/index.wxss`
- Modify: `miniprogram/miniprogram/pages/catalog/detail.wxss`
- Modify: `miniprogram/miniprogram/pages/diy/schemes.wxss`
- Modify: `miniprogram/miniprogram/pages/order/confirm.wxss`
- Modify: `miniprogram/miniprogram/pages/order/list.wxss`
- Modify: `miniprogram/miniprogram/pages/order/detail.wxss`

**Interfaces:**
- Consumes: 首页已确认的根背景颜色 `#fff8fa`。
- Produces: 原生导航和所有页面根容器一致的浅丝绸粉纯色背景。

- [ ] **Step 1: 更新全局窗口背景**

```json
"window": {
  "navigationBarTitleText": "DIY 手串",
  "navigationBarBackgroundColor": "#fff8fa",
  "navigationBarTextStyle": "black",
  "backgroundColor": "#fff8fa"
}
```

```css
page {
  color: #2f2430;
  background: #fff8fa;
  font-family: "PingFang SC", sans-serif;
}
```

- [ ] **Step 2: 更新各页面根容器**

只替换以下根选择器的背景值，不改内部业务卡片和状态色：

```css
.recommendation-page,
.catalog-page,
.detail-page,
.schemes-page,
.order-page,
.orders-page {
  background: #fff8fa;
}
```

每个选择器保留在自己的 WXSS 文件中；目录详情和订单详情都叫 `.detail-page`，分别修改各自文件。

`pages/recommendation/index.wxss` 在实施前已经存在用户的未提交改动。只修改 `.recommendation-page` 的根背景行；提交时使用 `git add -p` 选择该单独 hunk，并在提交前用 `git diff --cached -- miniprogram/miniprogram/pages/recommendation/index.wxss` 确认没有带入其他用户改动。

- [ ] **Step 3: 检查样式差异和配置语法**

Run: `git diff --check -- miniprogram/miniprogram/app.json miniprogram/miniprogram/app.wxss miniprogram/miniprogram/pages`

Expected: 无空白错误；JSON 由微信开发者工具成功编译。

- [ ] **Step 4: 提交全局背景统一**

```bash
git add miniprogram/miniprogram/app.json miniprogram/miniprogram/app.wxss miniprogram/miniprogram/pages/recommendation/index.wxss miniprogram/miniprogram/pages/catalog/index.wxss miniprogram/miniprogram/pages/catalog/detail.wxss miniprogram/miniprogram/pages/diy/schemes.wxss miniprogram/miniprogram/pages/order/confirm.wxss miniprogram/miniprogram/pages/order/list.wxss miniprogram/miniprogram/pages/order/detail.wxss
git commit -m "style: unify mini program blush background"
```

---

### Task 5: 微信开发者工具视觉与交互验收

**Files:**
- Modify: `design-qa.md`
- Create: `design-qa-evidence/diy-reference-summary.png`
- Create: `design-qa-evidence/diy-reference-collapsed.png`
- Create: `design-qa-evidence/diy-reference-grid.png`
- Create: `design-qa-evidence/diy-reference-search.png`
- Create: `design-qa-evidence/diy-expanded.png`
- Create: `design-qa-evidence/diy-collapsed.png`
- Create: `design-qa-evidence/diy-favorites.png`
- Create: `design-qa-evidence/diy-comparison.png`
- Modify if required: `miniprogram/miniprogram/pages/diy/editor.wxml`
- Modify if required: `miniprogram/miniprogram/pages/diy/editor.wxss`

**Interfaces:**
- Consumes: 用户提供的四张参考图和 Task 1–4 的实现。
- Produces: `design-qa.md` 中准确的 `final result: passed` 或 `blocked`。

- [ ] **Step 1: 运行完整自动化验证**

Run: `cd miniprogram && ./node_modules/.bin/jest --runInBand && ./node_modules/.bin/tsc -p tsconfig.runtime.json`

Expected: 全部 Jest 测试 PASS，TypeScript 无错误。

- [ ] **Step 2: 在微信开发者工具打开 DIY 编辑页**

打开 `/pages/diy/editor`，确认页面能加载现有目录和 Canvas；不得仅以 HTTP 状态或编译成功替代可见页面检查。

- [ ] **Step 3: 验证核心前端交互**

按顺序验证：

1. “收起预览”切换为窄卡并变成“展开预览”。
2. 再次展开后 Canvas 内容和选中珠子保持不变。
3. 点击珠子卡主体会加珠，点击爱心不会加珠。
4. 重开页面后喜欢状态仍存在。
5. “喜欢”筛选只显示喜欢项，关闭后恢复当前查询结果。
6. 分类、关键词和喜欢筛选可以组合。
7. 加号和减号继续改变对应 SKU 数量、价格和尺寸。

- [ ] **Step 4: 保存参考图并捕获同状态视觉证据**

复制以下临时参考图为稳定证据：

- `/var/folders/s7/cwsk3y_552dbp6gfk95h2qtc0000gn/T/codex-clipboard-8d1a6fd3-9162-4a45-a383-9e8f793e7fd2.png` → `diy-reference-summary.png`
- `/var/folders/s7/cwsk3y_552dbp6gfk95h2qtc0000gn/T/codex-clipboard-233cb4e8-2836-44c3-9f57-82ba678f5761.png` → `diy-reference-collapsed.png`
- `/var/folders/s7/cwsk3y_552dbp6gfk95h2qtc0000gn/T/codex-clipboard-cb37b941-11d1-475f-bf33-66232e12d56c.png` → `diy-reference-grid.png`
- `/var/folders/s7/cwsk3y_552dbp6gfk95h2qtc0000gn/T/codex-clipboard-e835822a-b02a-4052-8219-fccecbd28cbc.png` → `diy-reference-search.png`

再捕获展开、收起和喜欢筛选态，使用项目已有的图像合成方式把四张参考图的相应区域与实现截图放入同一张 `diy-comparison.png`。检查：字体、间距、颜色、图片清晰度、文案、折叠比例、三列网格、爱心位置、搜索行和底部固定按钮。

- [ ] **Step 5: 修复全部 P0/P1/P2 并重复截图**

每轮在 `design-qa.md` 记录问题、修复和新证据；只有没有剩余 P0/P1/P2 时才能写：

```markdown
final result: passed
```

如果微信开发者工具无法打开、截图或完成同状态比较，写：

```markdown
final result: blocked
```

并明确阻塞原因，不宣称完成。

- [ ] **Step 6: 最终验证并提交 QA**

Run: `cd miniprogram && ./node_modules/.bin/jest --runInBand && ./node_modules/.bin/tsc -p tsconfig.runtime.json && cd .. && git diff --check`

Expected: 完整测试和类型检查通过，`design-qa.md` 最后一行是 `final result: passed`。

```bash
git add miniprogram/miniprogram/pages/diy/editor.wxml miniprogram/miniprogram/pages/diy/editor.wxss design-qa.md design-qa-evidence/diy-reference-summary.png design-qa-evidence/diy-reference-collapsed.png design-qa-evidence/diy-reference-grid.png design-qa-evidence/diy-reference-search.png design-qa-evidence/diy-expanded.png design-qa-evidence/diy-collapsed.png design-qa-evidence/diy-favorites.png design-qa-evidence/diy-comparison.png
git commit -m "test: verify diy editor visual redesign"
```

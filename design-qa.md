# 首页视觉验收报告

## 对比基准

- Source visual truth: `Pic/bracelet_homepage_assets_named/homepage_full_mockup.png`
- Rendered implementation: 微信开发者工具中 `pages/home/index`
- Implementation screenshots:
  - `design-qa-evidence/homepage-final-top.png`
  - `design-qa-evidence/homepage-final-bottom.png`
  - `design-qa-evidence/homepage-final-stitched.png`
- Full-view comparison evidence: `design-qa-evidence/comparison-final-full.png`
- Earlier comparison evidence: `design-qa-evidence/comparison-initial-top.png`
- Viewport: 860 × 1668 px simulator capture，约等于 430 × 834 CSS px，device scale factor 2。
- Pixel normalization: 参考图原始尺寸为 821 × 1916 px；对比时移除顶部 92 px 原生导航区域。实现的两张 860 × 1668 px 视口截图拼接为 860 × 2048 px，再等比缩放到 821 × 1955 px，与 821 px 宽的参考内容并排比较。
- State: 首页默认状态，选中“温柔”心情；原生顶部导航不纳入内容区截图，固定底部导航仅保留一次。

## Findings

- 无剩余 P0、P1 或 P2 问题。最终并排证据中，主视觉、心情推荐、成品精选、我的方案、分享卡和底部导航的层级、顺序与整体密度均与参考稿一致。
- [P3] 主视觉手串的珠子排列与参考稿存在轻微角度差异。
  Location: `.hero-image`。
  Evidence: 两者使用同一风格的粉紫水晶手串，但独立素材的构图与完整参考图中的原始合成角度不完全相同。
  Impact: 不影响标题阅读、核心操作或页面识别。
  Fix: 如后续获得参考稿的原始分层文件，可直接替换主视觉手串图并微调定位。

## Required Fidelity Surfaces

- Fonts and typography: 标题使用宋体类展示字体，正文使用苹方类无衬线字体；字号、字重、换行、行距和层级与参考稿一致，无截断或不可读文本。
- Spacing and layout rhythm: 区块顺序、卡片圆角、横向留白、网格间距和固定底部导航均已对齐；滚动页面没有内容横向溢出。
- Colors and visual tokens: 深紫文字、强调粉、柔和粉白背景、浅边框与低对比阴影均映射到参考稿色彩关系；选中态保持清晰但不过度突出。
- Image quality and asset fidelity: 九张用户素材均转换为本地 WebP；礼盒和丝绸背景由图片生成后落为本地 WebP；界面图标使用本地 Remix Icon SVG。图片清晰、裁切稳定，无透明边缘或拉伸问题。
- Copy and content: 所有固定文案与参考稿一致；动态卡片内容为本地真实展示数据，没有占位符。
- Icons and accessibility: 图标风格、线宽和尺寸统一；主要可交互项使用按钮或可点击容器，并提供图片语义标签或空装饰标签。

## Comparison History

1. Initial pass — blocked
   - [P1] 主视觉手串尺寸过大并遮挡标题。
   - Fix: 重设 `.hero-image` 的宽度、透明度和定位，并调整标题字号与正文宽度。
   - Post-fix evidence: `design-qa-evidence/homepage-final-top.png`。
2. Density pass — blocked
   - [P2] 首屏纵向密度偏松，底部导航高度过大，方案卡和分享卡在常用视口中过早被遮挡。
   - Fix: 压缩主视觉、心情卡、商品卡、方案卡和分享卡尺寸；修正底部导航盒模型与安全区高度。
   - Post-fix evidence: `design-qa-evidence/homepage-final-bottom.png`。
3. Asset-fidelity pass — passed
   - [P2] 主视觉背景过于平坦，缺少参考稿的柔光丝绸纹理。
   - Fix: 依据参考稿生成无文字、无首饰的粉色丝绸背景，作为独立 WebP 分层，并再次调整手串位置避免标题碰撞。
   - Post-fix evidence: `design-qa-evidence/comparison-final-full.png`。

## Interaction and Runtime Checks

- Jest 行为测试覆盖：四个页面入口、导航失败提示、心情选择、心情轮换、返回顶部和分享配置。
- 微信开发者工具成功编译并打开 `pages/home/index`；自动化重启页面、滚动页面和截图均成功。
- 受当前微信开发者工具与官方 `miniprogram-automator@0.12.1` 元素 API 兼容性影响，真实元素点击自动化在连接后会中止；macOS 辅助功能权限也未开放，因此点击链路以页面行为单测验证。这是非视觉测试缺口，不构成视觉验收阻塞。
- 截图与滚动过程中未观察到 WXML/WXSS 编译错误；官方自动化 SDK 未能稳定返回完整 console 事件流，因此未声明完整控制台日志覆盖。

## Open Questions

- 参考稿是完整长图，而实现使用固定底部导航和滚动内容；最终证据通过两张同视口截图拼接来还原全页，因此页面总高度存在少量预期差异。

## Implementation Checklist

- [x] 主视觉构图与丝绸质感对齐。
- [x] 心情、成品、方案和分享区块的比例与间距对齐。
- [x] 底部导航、安全区和滚动内容通过可视检查。
- [x] 明确入口、心情状态和分享配置通过自动化测试。
- [x] 完整测试与 TypeScript 类型检查通过。

## Follow-up Polish

- 如获得参考稿的主视觉原始分层文件，可消除当前 P3 级手串角度差异。

final result: passed

---

# DIY 串珠编辑页视觉验收报告

## 对比基准

- Source visual truth:
  - `design-qa-evidence/diy-reference-summary.png` (670 × 1394 px)
  - `design-qa-evidence/diy-reference-collapsed.png` (882 × 432 px)
  - `design-qa-evidence/diy-reference-grid.png` (824 × 422 px)
  - `design-qa-evidence/diy-reference-search.png` (710 × 254 px)
- Rendered implementation: 用户已打开的微信开发者工具中 `pages/diy/editor`。本轮未新建、未重启开发者工具窗口。
- Implementation screenshots:
  - `design-qa-evidence/diy-expanded.png`
  - `design-qa-evidence/diy-collapsed.png`
  - `design-qa-evidence/diy-favorites.png`
- Full-view and focused comparison evidence: `design-qa-evidence/diy-comparison.png`
- Viewport: iPhone 12/13 (Pro) 模拟器，390 × 844 CSS px，`devicePixelRatio: 3`。
- Pixel normalization: 原生开发者工具窗口中的手机画面以窗口 ID 直接截取，336 × 727 px 裁剪后等比归一为 390 × 844 px。参考稿由一张全屏图和三张局部图组成，因此对应区域在同一 `diy-comparison.png` 中并置，不宣称局部参考图与全屏实现是 1:1 像素尺寸。
- States: 预览展开态、预览收起态、喜欢筛选态。截图使用临时的确定性页面数据初始化；所有临时 fixture 钩子在最终验证前已完全移除，未创建、未保留任何 mock 图片资产。

## Findings

- 无剩余 P0、P1 或 P2 问题。最终原生窗口证据同时显示 Canvas 手串、“收起预览”、撤销/重做、尺寸摘要、右侧加减、三列珠子、爱心与“喜欢”筛选。
- [P3] QA 确定性数据使用了页面已有的珠子颜色回退绘制，未代表生产 API 返回的每一张水晶图的清晰度。
  - Location: Canvas 珠子和 `.tray-bead` catalog 图片。
  - Evidence: 最终图中是真实 Canvas/DOM 回退渲染，参考稿是写实水晶图。
  - Impact: 不影响本次要求的结构、操作、排版与本地喜欢逻辑；线上图片的逐张清晰度需用真实目录数据单独抽查。

## Required Fidelity Surfaces

- Fonts and typography: 字体继承小程序已有的苹方/中文无衬线栈；计数、长度、价格、分类、搜索和底部按钮层级清晰，无截断。目标长度在上、当前长度在下，且取消了原先的“偏短”建议文案。
- Spacing and layout rhythm: 展开预览中工具栏转为 Canvas 上方的正常文档流；收起态保持窄卡比例；三列网格、搜索行和固定底部按钮无重叠。
- Colors and visual tokens: 页面与原生导航使用 `#fff8fa` 浅丝绸粉纯色背景，白色卡片、深玫瑰主操作和粉色喜欢选中态与首页统一。
- Image quality and asset fidelity: 界面图标使用项目内 Remix Icon SVG，无 Unicode/表情符号/内联 SVG/CSS 绘制替代。Canvas 在原生窗口截图中可见；自动化 SDK 直接 screenshot 会漏掉原生 Canvas 层，因此未将空白 SDK 截图误判为生产缺陷。
- Copy and content: 预览按钮精确使用“收起预览”/“展开预览”；搜索旁为“喜欢”；选中珠子行只保留加减。
- States, interactions, and accessibility: 所有主操作使用可点击控件或声明按钮语义的容器；加减和爱心是约 44 CSS px 点击区；爱心使用 `catchtap` 不触发卡片加珠。

## Comparison History

1. Initial pass — blocked
   - [P1] 原生 Canvas 层遮住展开态中绝对定位的“收起预览”和撤销/重做控件，用户无法从展开态收起。
   - Fix: 将 `.preview-toolbar` 放到 Canvas 上方的正常文档流，展开态同时缩短 Canvas 高度以维持卡片总比例；收起态仍在右侧的非 Canvas 区域显示开关。
   - Post-fix evidence: `design-qa-evidence/diy-expanded.png` 同时显示 Canvas、收起按钮和撤销/重做。
2. Post-fix native-window pass — passed
   - 展开、收起和喜欢筛选三个状态在同一已打开的微信开发者工具窗口重新截取。
   - 未发现新的 P0/P1/P2；对比证据已更新为 `design-qa-evidence/diy-comparison.png`。

## Interaction and Runtime Checks

- 微信开发者工具当前基础库 3.16.2 与 `miniprogram-automator@0.12.1` 的页面级 RPC 不兼容：`Page.data`、`Page.callMethod` 和元素查询会挂起。验收改用已验证的顶层页面跳转、滚动与截图，不再等待超时 RPC。
- SDK 自身的 screenshot 不包含原生 Canvas 层；最终证据使用同一时刻、同一已打开窗口的指定 window-ID 截图，所以可见 Canvas 与 DOM 同屏。
- Jest 覆盖预览开关与 Canvas 重绘、爱心不加珠、本地喜欢存储、喜欢/分类/关键词组合筛选、珠子卡加珠和加减数量逻辑。
- 最终自动化：22 个 Jest suites、137 个 tests 全部通过；`tsc -p tsconfig.runtime.json` 通过。

## Open Questions

- 无阻塞问题。生产珠子图片清晰度作为 P3 后续抽查，不影响本次前端布局与交互验收。

## Implementation Checklist

- [x] 尺寸摘要左对齐，目标在上、当前在下。
- [x] 选中珠子行只保留右侧加减。
- [x] 展开和窄卡收起态均可见且可操作。
- [x] 三列下排珠子、爱心和搜索旁喜欢筛选已对齐。
- [x] `#fff8fa` 全局浅粉背景、底部固定操作和安全区无冲突。
- [x] 临时 fixture 与自动化钩子已移除，项目配置未改动。

## Follow-up Polish

- 后续可在真实后端目录数据下抽查每类水晶的裁切与清晰度，不需要改动当前结构。

final result: passed

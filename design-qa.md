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

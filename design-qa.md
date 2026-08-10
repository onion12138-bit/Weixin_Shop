# 设计视觉验收报告

## 客户地址、批量发券与订单状态增强复核（2026-08-08）

- Source visual truth:
  - 客户与地址合并：`codex-clipboard-d00461d3-cbb7-45ee-9d2c-0bb45bcb8eac.png`（2980 × 1512 px）。
  - 发券抽屉：`codex-clipboard-fa556064-82b2-4cd5-9c40-662087b2a334.png`（2448 × 1230 px）。
  - 全文悬浮：`codex-clipboard-aec204c1-1263-4bfc-9523-73195438cb78.png`（2972 × 1656 px）。
  - 订单统计区：`codex-clipboard-f1a90ec5-63e9-4929-ab67-b5cad62ac310.png`（3048 × 1244 px）及 `ChatGPT Image 2026年8月8日 03_42_06.png`（1672 × 941 px）。
  - 手机号显隐：`codex-clipboard-ad9ccda8-9266-4d3-b4c4-d2ec19d1af24.png`（2918 × 1612 px）。
- Rendered implementation: 本地管理后台 `http://127.0.0.1:5173/`，CSS 视口 1280 × 720，devicePixelRatio 2；浏览器截图内容为 1265 × 712 px。
- Implementation evidence:
  - `design-qa-evidence/customer-benefits-merged-addresses.png`
  - `design-qa-evidence/customer-address-drawer.png`
  - `design-qa-evidence/coupon-batch-drawer.png`
  - `design-qa-evidence/order-list-status-icons.png`
  - `design-qa-evidence/order-list-overflow-tooltip.png`
- Full-view comparison evidence:
  - `design-qa-evidence/compare-customer-address-merge.png`（左侧参考，右侧实现）。
  - `design-qa-evidence/compare-order-status-icons.png`（左侧参考，右侧实现）。
- State: 客户列表、单客户地址抽屉、多选发券抽屉、订单列表默认状态、手机号全量显示状态、长方案名称悬浮状态。
- Pixel normalization: 两组并排证据均将参考与实现分别等比放入 900 × 560 px 内容框；保留完整页面比例，未按像素差伪判由原始视口不同造成的留白。

### Findings

- 无剩余 P0、P1 或 P2 问题。客户页已去掉独立“地址管理”标签，在每位客户所在行提供明确的地址数量和查看按钮；地址抽屉容纳多地址、默认状态和停用/恢复操作，主列表上下文不会丢失。
- 发券抽屉把发送范围拆成单个、多选、全量三个互斥状态；手机号搜索结果限制为 50 位并由服务端检索，全量模式不把完整客户列表送入浏览器，适合大量客户场景。
- 订单统计卡使用同一套 Remix Icon 线性图标和八种低饱和语义色；1280 px 视口下按 4 × 2 自适应排列，宽屏恢复单行八卡，层级与参考图一致且无拥挤。
- 顾客信息表头的眼睛按钮可一次切换当前列表全部手机号；浏览器实测由 `138****0000` 切换为 `13800000000`，按钮可访问名称随状态变为“隐藏全部手机号”。
- 长方案名称、商品名、珠子摘要、地址和优惠券说明统一使用可键盘聚焦的悬浮全文组件；实测提示内容完整换行，最大宽度受视口约束，不会撑破表格或被滚动容器裁切。

### Required Fidelity Surfaces

- Fonts and typography: 继续使用项目既有宋体展示标题与无衬线正文；表头、辅助文本、数值层级保持一致。两行截断与悬浮全文均采用稳定行高和任意长词换行。
- Spacing and layout rhythm: 客户行新增“地址簿”列后仍保持六列节奏；右侧抽屉使用 28 px 内边距和独立滚动。统计卡在当前视口无碰撞或裁切。
- Colors and visual tokens: 沿用深莓色主操作、浅粉页面底色和白色卡片；订单状态图标按莓红、琥珀、蓝、橙、绿、灰、紫区分，但保持统一柔和饱和度。
- Image quality and asset fidelity: 状态卡与眼睛图标均来自 Remix Icon 官方矢量资产，按 20 px/17 px 槽位呈现，缩放清晰、线性风格一致；本轮没有新增照片或插画。
- Copy and content: “单个发送 / 多选发送 / 全量发送”“按客户手机号模糊搜索”“查看 N 个地址”和手机号显隐提示均与操作结果一致，不使用开发占位文案。
- Accessibility and interaction: 抽屉使用 `dialog`/`aria-modal`，搜索框有可见标签，禁用发送态明确；全文提示支持鼠标悬浮和键盘聚焦，手机号按钮有动态 `aria-label`。

### Comparison History

1. Requirement alignment pass — passed：按用户新要求删除参考中的独立地址标签，改为客户行内地址簿入口；并排证据显示表格列与顶部标签仍保持对齐。
2. Status-icon pass — passed：订单统计卡由纯文字数字改为统一图标圆底；并排证据显示语义颜色和卡片密度与参考方向一致。
3. Interaction pass — passed：地址抽屉、三种发券范围、手机号显隐和悬浮全文均在浏览器实际触发；未发现遮挡、横向溢出或控制失效。

### Runtime Verification

- 管理后台：10 个测试文件、83 项测试全部通过；`vue-tsc --noEmit` 与 Vite 生产构建通过。
- 后端：`CustomerAccountControllerTest` 在 MySQL Testcontainers 中通过，覆盖按手机号搜索、单客户地址过滤、多选发券和全量发券；Maven 编译通过。
- Browser console: 目标页面与交互状态检查中错误日志为 0。

final result: passed

## 四项界面细节修复复核（2026-08-08）

- Source visual truth:
  - 订单进度：`codex-clipboard-c5751708-b004-4292-8d56-c809764d9b29.png`（2982 × 1334 px）。
  - 首页实际方案：`codex-clipboard-a224e44c-1cd7-44d4-868d-e7832eb72b23.png`（736 × 1478 px）。
  - 首页心愿间距：`codex-clipboard-ecee9b23-de3e-48d8-b595-d674850d9071.png`（834 × 1440 px）。
  - 后台筛选框：`codex-clipboard-019d6bb8-1c45-4053-afad-db8d4e6045f8.png`（2988 × 896 px）。
- Rendered admin implementation: 本地管理后台 `http://127.0.0.1:5173/`，CSS 视口 1280 × 720，devicePixelRatio 2；浏览器截图内容为 1265 × 712 px。
- Admin implementation evidence:
  - `design-qa-evidence/order-detail-checkmarks.png`
  - `design-qa-evidence/catalog-select-arrow-inset.png`
  - `design-qa-evidence/fix-checkmarks-reference-vs-build.png`（上方参考、下方实现）。
  - `design-qa-evidence/fix-select-reference-vs-build.png`（上方参考、下方实现）。
- State: 已完成订单详情、散珠商品默认筛选状态；后台仅只读检查，未改变订单或商品数据。

### Findings

- 后台订单的已完成节点已全部显示为白色勾号；浏览器实测 5 个节点均使用勾号图标，不再出现“已”字符或末尾数字。
- 后台原生 `select` 改为统一的 16 px 下拉图标，并向内保留 16 px 右侧距离；参考标注中的贴边问题已消除。
- 首页“我的方案”已改为读取实际方案列表中的最新方案；名称、素材摘要、创建日期和继续编辑目标均来自方案数据。预览按实际 `sequence` 中的珠子围成手串，不再使用带灰色中部阴影的旧预览图。
- 心愿卡改为 4 列定宽网格：`4 × 160rpx + 3 × 18rpx = 694rpx`，与 28rpx 页面边距精确闭合，左右留白一致，卡片空隙比原来的 12rpx 更舒展。

### Required Fidelity Surfaces

- Fonts and typography: 本轮未改变字号、字重或文案层级；后台勾号是图像资产，不再受字体回退影响。
- Spacing and layout rhythm: 心愿网格左右边界严格对称；筛选箭头有稳定内边距；后台两张同状态实现截图无横向溢出。
- Colors and visual tokens: 继续使用既有深莓色、浅丝绸粉和白色卡片体系；勾号使用白色，筛选箭头使用中性灰。
- Image quality and asset fidelity: 后台勾号与下拉箭头使用本地矢量图标；首页方案预览使用方案实际珠子图片，单颗珠子仅保留轻微投影，中心区域为纯净浅白粉底。
- Copy and content: 后台订单节点、首页方案名称/素材/日期均来自实际状态或接口数据，没有新增占位文案。

### Verification

- 管理后台：9 个测试文件、81 项测试通过，生产构建通过；浏览器实测已完成订单包含 5 个 `.step-check`。
- 小程序：22 个测试套件、157 项测试通过，TypeScript 类型检查通过；新增测试覆盖实际方案读取、珠子预览来源、无旧灰影预览和继续编辑跳转。
- Console/errors: 管理后台目标页面加载与交互正常，浏览器检查过程中未出现可见运行时错误。
- Mini-program visual capture: 按用户要求未新开或重启第二个微信开发者工具实例；本轮没有取得修改后的同视口小程序截图，因此首页两项只能完成代码与行为测试验收，仍需在用户现有开发者工具编译后做最后一眼视觉确认。

### Comparison History

1. Admin detail pass — passed：参考图中的“已/数字”节点与实现不符；改为完成状态统一勾号，复核截图中 5 个节点全部正确。
2. Admin filter pass — passed：原生箭头贴边；改为本地图标与 16 px inset，复核截图中箭头位置平衡。
3. Mini-program source/test pass — blocked：实现与行为测试已通过，但缺少修改后的同视口模拟器截图，无法形成参考与实现的最终并排视觉证据。

final result: blocked

## 后台订单列表与订单详情复核（2026-08-08）

- Source visual truth: `Pic/订单详情.png`（1678 × 943 px）与 `订单管理页面_Codex执行说明.md`。
- Rendered implementation: 管理后台订单列表与订单详情，浏览器视口固定为 1678 × 943 px。
- Implementation evidence:
  - `design-qa-evidence/order-list-1678x943.png`
  - `design-qa-evidence/order-detail-1678x943.png`
  - `design-qa-evidence/order-detail-reference-vs-build.png`（左侧参考图，右侧当前实现）。
- Interaction state: 本地数据库中的制作中订单；只读打开列表与详情，未更改真实订单状态。

### Latest Findings

- 无剩余 P0、P1 或 P2 问题。详情页的信息层级、五段履约进度、上下两组双栏/三栏卡片、BOM、支付信息和操作记录与参考图一致。
- 列表初次复核发现表格在目标视口出现横向滚动，操作列末项被挤出可视区；已改为固定列比例与紧凑操作布局，复核后 `scrollWidth = clientWidth = 1289`，无横向溢出。
- [P3] 本地演示订单的顾客姓名、手机号和地址是历史测试数据“1”，信息密度低于参考图。
  Impact: 不影响字段结构、遮罩规则或页面布局；换成正式订单数据后会按真实内容展示。

### Fidelity and Runtime Checks

- Fonts and hierarchy: 沿用后台既有宋体展示标题、无衬线正文和深莓色主操作；状态标签与金额层级清晰。
- Spacing and layout: 1678 × 943 目标视口内列表、分页、详情进度与全部信息卡完整可见，无裁切或横向滚动。
- Data and interactions: 列表查询、状态统计、分页、导出、刷新、动态操作、详情返回、打印、编辑地址和发货弹层均接入真实 API。
- State sync: 后台和小程序共用 `PENDING_PAYMENT → IN_PRODUCTION → PENDING_SHIPMENT → SHIPPED → COMPLETED`；小程序将 `SHIPPED` 展示为“待收货”，确认收货后进入已完成。
- Verification: 后端 230 项测试、管理后台 81 项测试、小程序 157 项测试全部通过；后台生产构建和小程序 TypeScript 类型检查通过；Flyway 本地数据库已应用到 V14。

final result: passed

## DIY 设计页与下单页复核（2026-08-08）

- Source visual truth: `Pic/下单页.png`（852 × 1846 px）与用户提供的 DIY 设计页标注图。
- Rendered implementation: `pages/diy/editor`、`pages/order/confirm`，沿用现有小程序浅丝绸粉色板和卡片系统。
- Implementation state: 设计页双主按钮、珠子展开列表、无中心椭圆阴影；下单页包含地址、商品珠子摘要、优惠券选择弹层、金额汇总和固定支付栏。
- Interaction verification: Jest 行为测试与 TypeScript 类型检查通过；优惠券核销、取消退券和再次使用由 MySQL 集成测试覆盖。

### Latest Findings

- [P0] 无代码或自动化测试阻塞；小程序 156 项测试、后台 80 项测试、后端 230 项测试全部通过。
- [P2] 本轮未取得目标页面的同视口实现截图，无法完成参考图与实现图并排像素复核。
  Cause: 用户要求只使用当前已打开的微信开发者工具；该实例当前缺少有效 access token，并出现网络连接重置，自动化无法切换到目标页面。macOS 辅助功能权限也未开放，不能代替用户点击现有模拟器。
  Scope: 未新开、未重启第二个微信开发者工具实例；代码构建和行为验证不受影响。
  Follow-up: 在现有开发者工具恢复登录后手动点击“编译”，分别打开设计页与下单页即可完成最后一轮视觉对比。

### Fidelity Surfaces Checked in Source

- Layout: 下单页按参考图采用纵向卡片结构，地址与商品信息优先，优惠券和金额信息随后，支付操作固定在底部。
- Content density: 商品珠子摘要最多展示三种，存在更多类型时以“...”收束，避免挤压订单信息。
- Interaction states: 优惠券弹层展示全部优惠券，已选项使用原生单选勾选状态，不可用券保留原因说明。
- Design consistency: 页面继续使用项目既有深莓色主按钮、浅粉背景、白色圆角卡片和低对比边框，不引入新的图标或伪造素材。

## 本次心愿 DIY 对齐复核（2026-08-06）

- Source visual truth: `Pic/bracelet_homepage_assets_named/homepage_full_mockup.png` 中“推荐／成品精选”的网格与节奏。
- Rendered implementation: 当前已打开的微信开发者工具 `pages/home/index`。
- Implementation screenshot: `design-qa-evidence/homepage-wish-diy-current.png`（632 × 1350 px）。
- Focused region: `design-qa-evidence/homepage-wish-diy-focus.png`（632 × 660 px）。
- Combined comparison: `design-qa-evidence/comparison-wish-diy-current.png`；参考区域与实现区域统一到 632 px 宽后并排检查。
- Viewport and density: iPhone 12/13 模拟器；Retina 桌面截屏中模拟器内容裁切为 632 px 宽。参考图原始宽度 821 px，对比时等比缩放到 632 px；未以浏览器 CSS 像素替代原生模拟器尺寸。
- State: 首页第二组心愿（平安、活力、相守、家庭），成品精选默认状态。

### Latest Findings

- 无剩余 P0、P1 或 P2 问题。心愿灵感与成品精选现在共用 28rpx 外边距、160rpx 卡片宽度、12rpx 卡片间距、相同标题基线和右侧操作边线。
- 心愿区通过独立的“选心愿，生成专属 DIY”引导条和 `AI × DIY` 标识建立主层级；成品区保持纯浏览层级，两者视觉重量有区分但不失衡。
- [P3] 心愿名称与说明属于用户提供的方形图片内容，在 160rpx 卡片内比成品卡的原生文字略小。
  Impact: 当前模拟器截图中仍可识别，不影响点击与分组切换。
  Follow-up: 若未来需要进一步提升小字号清晰度，可提供去文字版本图片，将标题改为原生文本；当前不需要更长图片。

### Latest Fidelity Surfaces

- Fonts and typography: 两区标题字号、字重和基线一致；DIY 引导使用 23rpx 主文案与 18rpx 说明，层级清晰且无截断。
- Spacing and layout rhythm: 两区左右边线、卡片宽度、间距与标题操作区完全共用同一套尺寸；心愿引导条与卡片之间保持 14rpx 节奏。
- Colors and visual tokens: 心愿区使用纯色浅粉引导条和强调粉标签，延续首页色板；成品区保持白底，主次关系明确。
- Image quality and asset fidelity: 心愿与成品均保持方形裁切，无拉伸；用户提供的 1254 × 1254 心愿图片在当前尺寸下清晰。
- Copy and content: “选心愿，生成专属 DIY”准确表达点击心愿后进入 AI 推荐并继续 DIY 的主路径。

## 对比基准

- Source visual truth: `Pic/bracelet_homepage_assets_named/homepage_full_mockup.png`
- Rendered implementation: 微信开发者工具中 `pages/home/index`
- Implementation screenshots:
  - `design-qa-evidence/homepage-final-top.png`
  - `design-qa-evidence/homepage-final-bottom.png`
  - `design-qa-evidence/homepage-final-stitched.png`
  - `design-qa-evidence/homepage-wish-diy-current.png`
- Full-view comparison evidence: `design-qa-evidence/comparison-final-full.png`
- Focused wish/product comparison evidence: `design-qa-evidence/comparison-wish-diy-current.png`
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
4. Wish-to-DIY hierarchy pass — passed
   - [P1] 心愿区和成品区使用不同外边距、不同卡片宽度，标题与内容边线错位。
   - Fix: 两区统一为 28rpx 外边距、160rpx 卡片、12rpx 间距和相同标题内边距；增加 DIY 引导条，不再依靠单纯放大心愿图片制造重点。
   - Post-fix evidence: `design-qa-evidence/comparison-wish-diy-current.png`。

## Interaction and Runtime Checks

- Jest 行为测试覆盖：四个页面入口、导航失败提示、心情选择、心情轮换、返回顶部和分享配置。
- 最新首页契约测试额外覆盖：心愿区与成品区使用同一 160rpx 卡片宽度、同一页面网格，并存在明确的 DIY 引导文案。
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

## Previous checkout visual task result

final result: blocked

---

# DIY 串珠编辑页视觉验收报告（历史记录）

> 合并说明：本节记录视觉分支当时的验收结果和截图。合并时，主线中更新的紧凑预览、完整选珠操作栏与收藏流程已作为最终交互保留，因此本节提到的撤销/重做、仅加减操作和对应截图不代表当前界面。

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
- Evidence provenance: these native screenshots were captured from the same
  user-opened Developer Tools window immediately before evidence commit
  `a8d5867`; they remain the last available native layout evidence. The final
  HEAD's active favorite-filter icon changed afterward from outline to the
  local solid `heart-fill.svg` asset and is covered by the WXML visual-contract
  test. The earlier window and automation endpoint had exited before final
  review, so no replacement window was opened or restarted and the screenshots
  are not represented as a fresh capture of that icon-only change.
- Viewport: iPhone 12/13 (Pro) 模拟器，390 × 844 CSS px，`devicePixelRatio: 3`。
- Pixel normalization: 原生开发者工具窗口中的手机画面以窗口 ID 直接截取，336 × 727 px 裁剪后等比归一为 390 × 844 px。参考稿由一张全屏图和三张局部图组成，因此对应区域在同一 `diy-comparison.png` 中并置，不宣称局部参考图与全屏实现是 1:1 像素尺寸。
- States: 预览展开态、预览收起态、喜欢筛选态。截图使用临时的确定性页面数据初始化；所有临时 fixture 钩子在最终验证前已完全移除，未创建、未保留任何 mock 图片资产。

## Findings

- 无剩余 P0、P1 或 P2 问题。最终原生窗口证据同时显示 Canvas 手串、“收起预览”、撤销/重做、尺寸摘要、右侧加减、三列珠子、爱心与“喜欢”筛选。
- [Evidence timing] `diy-favorites.png` 与 `diy-comparison.png` 验证的是最终布局和喜欢筛选激活状态，但图中筛选心仍是当时的线框资源；最终 HEAD 的实心心由本地 SVG 资源与 WXML 契约测试验证。本限制不代表运行时功能缺陷，也不提升为 P0/P1/P2。
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
3. Final icon-contract pass — passed with evidence timing noted
   - 最终 HEAD 将喜欢筛选激活态切换为本地 `heart-fill.svg`，并由 `diy-editor-visual-contract.spec.ts` 锁定激活/未激活资源映射。
   - 原窗口与 9430 automation endpoint 在最终复审时均已退出；遵循“不新开、不重启”要求，保留上一次原生布局证据并明确其时点。

## Interaction and Runtime Checks

- 微信开发者工具当前基础库 3.16.2 与 `miniprogram-automator@0.12.1` 的页面级 RPC 不兼容：`Page.data`、`Page.callMethod` 和元素查询会挂起。验收改用已验证的顶层页面跳转、滚动与截图，不再等待超时 RPC。
- SDK 自身的 screenshot 不包含原生 Canvas 层；最终证据使用同一时刻、同一已打开窗口的指定 window-ID 截图，所以可见 Canvas 与 DOM 同屏。
- Jest 覆盖预览开关与 Canvas 重绘、爱心不加珠、本地喜欢存储、喜欢/分类/关键词组合筛选、珠子卡加珠和加减数量逻辑。
- 最终自动化：22 个 Jest suites、148 个 tests 全部通过；`tsc -p tsconfig.runtime.json` 通过。

## Open Questions

- 无阻塞问题。生产珠子图片清晰度作为 P3 后续抽查，不影响本次前端布局与交互验收。

## Implementation Checklist

- [x] 尺寸摘要左对齐，目标在上、当前在下。
- [x] 选中珠子行只保留右侧加减。
- [x] 展开和窄卡收起态均可见且可操作。
- [x] 三列下排珠子、爱心和搜索旁喜欢筛选已对齐。
- [x] `#fff8fa` 全局浅粉背景、底部固定操作和安全区无冲突。
- [x] 临时 fixture 与自动化钩子已移除，项目配置未改动。
- [x] 最终 HEAD 的喜欢筛选激活态使用实心心，并由 WXML 视觉契约测试覆盖；旧原生证据的时点限制已明确记录。

## Follow-up Polish

- 后续可在真实后端目录数据下抽查每类水晶的裁切与清晰度，不需要改动当前结构。

final result: passed

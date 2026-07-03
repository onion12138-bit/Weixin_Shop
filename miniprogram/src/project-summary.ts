/**
 * 返回小程序首屏使用的客户核心流程摘要。
 *
 * <p>该函数保持无微信运行时依赖，使核心展示文案可以在普通 Jest 环境中验证。
 * 后续首屏流程发生调整时，测试会同步约束用户看到的操作顺序。</p>
 *
 * @returns 从选珠到生产的完整流程摘要
 */
export function projectSummary(): string {
  return '选珠 · DIY · 保存 · 下单 · 生产'
}

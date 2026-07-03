import { projectSummary } from './project-summary'

/**
 * 验证小程序首屏展示的客户核心操作链路。
 */
describe('projectSummary', () => {
  it('describes the customer journey', () => {
    expect(projectSummary()).toBe('选珠 · DIY · 保存 · 下单 · 生产')
  })
})

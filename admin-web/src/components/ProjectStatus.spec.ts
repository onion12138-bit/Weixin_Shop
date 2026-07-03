import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ProjectStatus from './ProjectStatus.vue'

/**
 * 验证管理后台首页能够明确展示完整三端项目组成。
 */
describe('ProjectStatus', () => {
  it('shows the three project applications', () => {
    const wrapper = mount(ProjectStatus)

    expect(wrapper.text()).toContain('Spring Boot 后端')
    expect(wrapper.text()).toContain('Vue 管理后台')
    expect(wrapper.text()).toContain('微信小程序')
  })
})

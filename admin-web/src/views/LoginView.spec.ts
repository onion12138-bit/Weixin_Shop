import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { loginAdmin } from '../api/auth'
import { useAuthStore } from '../stores/auth'
import LoginView from './LoginView.vue'

vi.mock('../api/auth', () => ({ loginAdmin: vi.fn() }))

describe('LoginView credentials and submission', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    useAuthStore().logout()
  })

  it('starts with empty credentials and ignores a repeated submission while signing in', async () => {
    let finish!: (value: Awaited<ReturnType<typeof loginAdmin>>) => void
    vi.mocked(loginAdmin).mockReturnValueOnce(new Promise((resolve) => { finish = resolve }))
    const wrapper = mount(LoginView)
    expect(wrapper.get<HTMLInputElement>('input[name="username"]').element.value).toBe('')
    expect(wrapper.get<HTMLInputElement>('input[name="password"]').element.value).toBe('')
    await wrapper.get('input[name="username"]').setValue('operator')
    await wrapper.get('input[name="password"]').setValue('test-only-password')
    await wrapper.get('form').trigger('submit')
    await wrapper.get('form').trigger('submit')
    expect(loginAdmin).toHaveBeenCalledTimes(1)
    finish({ accessToken: 'test-token', tokenType: 'Bearer', expiresInSeconds: 60 })
    await flushPromises()
    expect(wrapper.emitted('success')).toHaveLength(1)
    wrapper.unmount()
    useAuthStore().logout()
  })
})

import { LOCAL_API_BASE_URL } from '../miniprogram/config/runtime'

describe('mini program runtime config', () => {
  it('uses the dedicated local backend instead of Jenkins', () => {
    expect(LOCAL_API_BASE_URL).toBe('http://localhost:8081/api')
  })
})

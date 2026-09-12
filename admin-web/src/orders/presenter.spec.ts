import { describe, expect, it } from 'vitest'
import type { AdminOrder } from '../api/orders'
import { paymentLabel } from './presenter'

describe('paymentLabel', () => {
  it.each([
    [null, '未支付'],
    [{ channel: 'MOCK' }, '模拟支付'],
    [{ channel: 'WECHAT' }, '微信支付'],
  ])('distinguishes a simulated payment from a real payment: %j', (payment, expected) => {
    expect(paymentLabel({ payment } as AdminOrder)).toBe(expected)
  })
})

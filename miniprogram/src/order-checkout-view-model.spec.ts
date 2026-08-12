import {
  buildCheckoutBeads,
  buildCheckoutCoupons,
  couponDiscountCents,
  maskPhone,
  shippingFeeCents,
} from '../miniprogram/utils/order-checkout-view-model'
import type { CustomerCoupon } from '../miniprogram/services/account'
import type { DiySchemeResponse } from '../miniprogram/services/diy'

declare function require(name: string): unknown
const { readFileSync } = require('fs') as {
  readFileSync(path: string, encoding: string): string
}

describe('order checkout view model', () => {
  it('shows at most three distinct bead rows in first-appearance order', () => {
    const scheme = {
      sequence: [2, 1, 2, 3, 4],
      skuQuantities: { 1: 1, 2: 2, 3: 1, 4: 1 },
      catalogSnapshot: [1, 2, 3, 4].map((skuId) => ({
        skuId, name: `珠子${skuId}`, diameterMm: 8, coverUrl: null,
        diyVisual: null, color: '粉色', skuCode: `S${skuId}`, status: 'ON_SALE',
      })),
    } as unknown as DiySchemeResponse

    expect(buildCheckoutBeads(scheme)).toMatchObject({
      hasMore: true,
      rows: [
        { skuId: 2, quantityText: '× 2' },
        { skuId: 1, quantityText: '× 1' },
        { skuId: 3, quantityText: '× 1' },
      ],
    })
  })

  it('only applies an unused, unexpired coupon that meets the threshold', () => {
    const coupon = {
      id: 9, status: 'UNUSED', discountAmount: 20, minimumSpend: 100,
      expiresAt: '2030-01-01T00:00:00', issuedAt: '2029-01-01T00:00:00',
      templateId: 1, code: 'SAVE-20', name: '立减券', description: '', note: null,
    } as CustomerCoupon
    const coupons = buildCheckoutCoupons([coupon], 16800, 9, Date.parse('2029-06-01'))

    expect(coupons[0]).toMatchObject({ usable: true, selected: true })
    expect(couponDiscountCents(coupons, 9, 16800)).toBe(2000)
    expect(buildCheckoutCoupons([coupon], 9000, 9, Date.parse('2029-06-01'))[0])
      .toMatchObject({ usable: false, selected: false })
  })

  it('masks standard mobile numbers', () => {
    expect(maskPhone('13812345678')).toBe('138****5678')
  })

  it('charges base shipping below the threshold after coupon discount', () => {
    expect(shippingFeeCents(6800, 1000, 6000, 800)).toBe(800)
    expect(shippingFeeCents(6800, 0, 6000, 800)).toBe(0)
  })

  it('enforces the configurable minimum at every checkout entry and displays shipping', () => {
    const editor = readFileSync('miniprogram/pages/diy/editor.ts', 'utf8')
    const schemes = readFileSync('miniprogram/pages/diy/schemes.ts', 'utf8')
    const confirm = readFileSync('miniprogram/pages/order/confirm.ts', 'utf8')
    const markup = readFileSync('miniprogram/pages/order/confirm.wxml', 'utf8')

    expect(editor).toMatch(/goCheckout\(\)[\s\S]*minimumOrderAmountCents/)
    expect(schemes).toMatch(/createOrder\([\s\S]*minimumOrderAmountCents/)
    expect(confirm).toMatch(/submitOrder\(\)[\s\S]*minimumOrderCents/)
    expect(markup).toContain('{{shippingFeeText}}')
    expect(markup).toContain('{{shippingHint}}')
  })
})

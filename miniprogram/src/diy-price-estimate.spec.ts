import {
  countSkuQuantities,
  estimatePriceCents,
} from '../miniprogram/domain/price-estimate'

describe('DIY price estimate', () => {
  it('counts sku quantities from the ordered sequence', () => {
    expect(countSkuQuantities([2001, 2001, 2002])).toEqual({
      2001: 2,
      2002: 1,
    })
  })

  it('uses integer cents to avoid decimal drift', () => {
    expect(estimatePriceCents(
      [2001, 2001, 2002],
      {
        2001: { salePriceCents: 1280 },
        2002: { salePriceCents: 1680 },
      },
      0,
      0,
    )).toBe(4240)
  })
})

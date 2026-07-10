import {
  buildEditorSummary,
  restorePackageSequence,
} from '../miniprogram/utils/diy-view-model'

describe('DIY view model', () => {
  it('expands package quantities in stable order', () => {
    expect(restorePackageSequence([
      { beadSkuId: 2001, quantity: 2, sortOrder: 10 },
      { beadSkuId: 2002, quantity: 1, sortOrder: 20 },
    ])).toEqual([2001, 2001, 2002])
  })

  it('labels invalid draft without hiding its arrangement', () => {
    expect(buildEditorSummary({
      beadCount: 21,
      currentLengthMm: 168,
      targetLengthMm: 170,
      sizeStatus: 'FIT',
      estimatedPriceCents: 31480,
      authoritative: false,
    })).toEqual({
      countText: '21 颗',
      lengthText: '当前 168 mm / 目标 170 mm',
      sizeText: '尺寸合适',
      priceText: '预估 ¥314.80',
      priceBadge: '预估',
    })
  })
})

import { toBeadCard } from './catalog-view-model'

/**
 * 商品接口数据必须经过纯函数转换后再进入页面，统一金额、规格和缺图展示。
 */
describe('catalog view model', () => {
  it('formats price and missing image consistently', () => {
    expect(
      toBeadCard({
        id: 1001,
        skuCode: 'BEAD-CRYSTAL-001',
        name: '白水晶 8mm',
        material: '水晶',
        color: '透明',
        diameterMm: 8,
        salePrice: 12.8,
        coverUrl: null,
        tags: ['净化'],
      }),
    ).toEqual({
      id: 1001,
      title: '白水晶 8mm',
      specification: '水晶 · 透明 · 8mm',
      priceText: '¥12.80 / 颗',
      imageUrl: '/assets/images/bead-placeholder.svg',
      tags: ['净化'],
    })
  })
})

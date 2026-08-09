jest.mock('../miniprogram/services/request', () => ({
  absolutePublicUrl: jest.fn((url: string | null) => (
    url ? `https://shop.test${url}` : url
  )),
  request: jest.fn(),
}))

import {
  listAllPublicBeads,
  listBeads,
  listRecommendedPackages,
} from '../miniprogram/services/catalog'
import { getScheme } from '../miniprogram/services/diy'
import { request } from '../miniprogram/services/request'
import { toBeadCard } from './catalog-view-model'

const requestMock = request as jest.MockedFunction<typeof request>

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
        diyVisual: null,
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

  it('normalizes cover, tray and canvas URLs from the public catalog', async () => {
    requestMock.mockResolvedValueOnce({
      items: [{
        id: 2001,
        skuCode: 'BEAD-CRYSTAL-001',
        name: '白水晶 8mm',
        material: '水晶',
        color: '透明',
        diameterMm: 8,
        salePrice: 12.8,
        coverUrl: '/api/public/files/cover.png',
        diyVisual: {
          trayUrl: '/api/public/files/tray.webp',
          canvasUrl: '/api/public/files/canvas.webp',
          version: 3,
        },
        tags: ['净化'],
      }],
      total: 1,
      page: 1,
      size: 20,
    })

    const page = await listBeads({ page: 1, size: 20 })

    expect(page.items[0]).toMatchObject({
      coverUrl: 'https://shop.test/api/public/files/cover.png',
      diyVisual: {
        trayUrl: 'https://shop.test/api/public/files/tray.webp',
        canvasUrl: 'https://shop.test/api/public/files/canvas.webp',
        version: 3,
      },
    })
  })

  it('loads every filtered bead page for the expanded DIY tray', async () => {
    requestMock.mockClear()
    const bead = (id: number) => ({
      id,
      skuCode: `BEAD-${id}`,
      name: `测试珠子 ${id}`,
      material: '天然水晶',
      color: '透明',
      diameterMm: 8,
      salePrice: 12.8,
      coverUrl: null,
      diyVisual: null,
      tags: [],
    })
    requestMock
      .mockResolvedValueOnce({
        items: [bead(1)],
        total: 101,
        page: 1,
        size: 100,
      })
      .mockResolvedValueOnce({
        items: [bead(2)],
        total: 101,
        page: 2,
        size: 100,
      })

    const items = await listAllPublicBeads({
      categoryId: 7,
      keyword: '月光',
    })

    expect(items.map((item) => item.id)).toEqual([1, 2])
    expect(requestMock).toHaveBeenNthCalledWith(
      1,
      '/public/catalog/beads?categoryId=7&keyword=%E6%9C%88%E5%85%89&page=1&size=100',
    )
    expect(requestMock).toHaveBeenNthCalledWith(
      2,
      '/public/catalog/beads?categoryId=7&keyword=%E6%9C%88%E5%85%89&page=2&size=100',
    )
  })

  it('loads and normalizes the backend-configured home recommendations', async () => {
    requestMock.mockResolvedValueOnce([{
      id: 3001,
      packageCode: 'PACKAGE-CALM-001',
      name: '澄心日常手串',
      description: '日常搭配',
      salePrice: 168,
      coverUrl: '/api/public/files/package.png',
    }])

    const items = await listRecommendedPackages(4)

    expect(requestMock).toHaveBeenCalledWith(
      '/public/catalog/packages/recommended?limit=4',
    )
    expect(items[0].coverUrl).toBe(
      'https://shop.test/api/public/files/package.png',
    )
  })

  it('normalizes cover, tray and canvas URLs in scheme snapshots', async () => {
    requestMock.mockResolvedValueOnce({
      id: 91,
      name: '快照方案',
      wristCircumferenceMm: 160,
      fitType: 'STANDARD',
      cordOptionId: 6001,
      sequence: [2001],
      beadCount: 1,
      catalogSnapshot: [{
        skuId: 2001,
        skuCode: 'BEAD-CRYSTAL-001',
        name: '白水晶 8mm',
        color: '透明',
        diameterMm: 8,
        coverUrl: '/api/public/files/cover.png',
        status: 'ACTIVE',
        diyVisual: {
          trayUrl: '/api/public/files/tray.webp',
          canvasUrl: '/api/public/files/canvas.webp',
          version: 3,
        },
      }],
      quoteStatus: 'NOT_QUOTED',
      previewAssetId: null,
      previewUrl: null,
      version: 1,
      createdAt: '2026-07-25T00:00:00',
      updatedAt: '2026-07-25T00:00:00',
      quotable: false,
      issues: [],
      skuQuantities: { 2001: 1 },
      currentLengthMm: 8,
      targetLengthMm: 170,
      deviationMm: -162,
      sizeStatus: 'TOO_SHORT',
      beadAmount: 12.8,
      craftFee: 0,
      cordFee: 0,
      totalAmount: 12.8,
      quoteFingerprint: null,
    })

    const scheme = await getScheme(91)

    expect(scheme.catalogSnapshot[0]).toMatchObject({
      coverUrl: 'https://shop.test/api/public/files/cover.png',
      diyVisual: {
        trayUrl: 'https://shop.test/api/public/files/tray.webp',
        canvasUrl: 'https://shop.test/api/public/files/canvas.webp',
        version: 3,
      },
    })
  })
})

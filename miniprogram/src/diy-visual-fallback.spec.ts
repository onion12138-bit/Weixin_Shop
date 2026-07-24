import {
  resolveCanvasUrl,
  resolveTrayUrl,
} from '../miniprogram/domain/diy-visual-fallback'
import type { PublicBead } from '../miniprogram/utils/catalog-view-model'

const bead: PublicBead = {
  id: 2001,
  skuCode: 'BEAD-CRYSTAL-001',
  name: '白水晶 8mm',
  material: '水晶',
  color: '透明',
  diameterMm: 8,
  salePrice: 12.8,
  coverUrl: 'https://shop.test/api/public/files/cover.png',
  diyVisual: {
    trayUrl: 'https://shop.test/api/public/files/tray.webp',
    canvasUrl: 'https://shop.test/api/public/files/canvas.webp',
    version: 3,
  },
  tags: ['净化'],
}

describe('DIY visual fallback', () => {
  it('uses each published visual independently before the cover', () => {
    expect(resolveTrayUrl(bead)).toBe(bead.diyVisual?.trayUrl)
    expect(resolveCanvasUrl(bead)).toBe(bead.diyVisual?.canvasUrl)

    expect(resolveTrayUrl({
      ...bead,
      diyVisual: {
        trayUrl: null,
        canvasUrl: bead.diyVisual?.canvasUrl || null,
        version: 3,
      },
    })).toBe(bead.coverUrl)
    expect(resolveCanvasUrl({
      ...bead,
      diyVisual: {
        trayUrl: bead.diyVisual?.trayUrl || null,
        canvasUrl: null,
        version: 3,
      },
    })).toBe(bead.coverUrl)
  })

  it('falls back through the cover to an empty color-placeholder URL', () => {
    expect(resolveCanvasUrl({ ...bead, diyVisual: null })).toBe(bead.coverUrl)
    expect(resolveTrayUrl({ ...bead, diyVisual: null })).toBe(bead.coverUrl)
    expect(resolveCanvasUrl({
      ...bead,
      diyVisual: null,
      coverUrl: null,
    })).toBe('')
    expect(resolveTrayUrl({
      ...bead,
      diyVisual: null,
      coverUrl: null,
    })).toBe('')
  })
})

declare function require(name: string): unknown
export {}

const { readFileSync } = require('fs') as {
  readFileSync(path: string, encoding: string): string
}

const editorSource = readFileSync(
  'miniprogram/pages/diy/editor.ts',
  'utf8',
)

import { toSchemeCard } from '../miniprogram/utils/diy-view-model'

jest.mock('../miniprogram/services/diy', () => ({
  deleteScheme: jest.fn(),
  listSchemes: jest.fn(),
}))
jest.mock('../miniprogram/services/commerce', () => ({
  getCommerceRule: jest.fn(),
}))

function loadSchemesPage() {
  let definition: Record<string, any> | null = null
  const wxMock = {
    getStorageSync: jest.fn(() => ''),
    navigateTo: jest.fn(),
    showToast: jest.fn(),
  }
  ;(globalThis as any).wx = wxMock
  ;(globalThis as any).Page = (options: Record<string, any>) => {
    definition = options
  }

  jest.isolateModules(() => {
    require('../miniprogram/pages/diy/schemes')
  })
  if (!definition) throw new Error('Schemes Page definition was not captured')
  const captured = definition as Record<string, any>
  const page: Record<string, any> = {
    ...captured,
    data: { ...captured.data },
  }
  page.setData = jest.fn((patch: Record<string, unknown>) => {
    Object.assign(page.data, patch)
  })
  return { page, wxMock }
}

describe('DIY schemes page behavior', () => {
  it('asks the customer to choose beads before saving an empty design', () => {
    expect(editorSource).toMatch(
      /async saveDraft\(\)[\s\S]*!this\.data\.sequence\.length[\s\S]*请选择您喜欢的珠子/,
    )
  })

  it('keeps the full bracelet visible inside the fixed-size preview area', () => {
    const template = readFileSync('miniprogram/pages/diy/schemes.wxml', 'utf8')
    const styles = readFileSync('miniprogram/pages/diy/schemes.wxss', 'utf8')

    expect(template).toContain('class="scheme-preview"')
    expect(template).toContain('wx:for="{{item.previewBeads}}"')
    expect(template).not.toContain('src="{{item.previewUrl}}"')
    expect(styles).toMatch(/\.scheme-preview[\s\S]*background:\s*#fff;/)
  })

  it('rebuilds each saved preview from bead images without the legacy center shadow', () => {
    const card = toSchemeCard({
      id: 18,
      name: '静心手串',
      beadCount: 2,
      totalAmount: 176.4,
      quoteStatus: 'VALID',
      previewUrl: 'https://shop.test/legacy-center-shadow.png',
      updatedAt: '2026-08-08T10:00:00',
      version: 1,
      sequence: [2001, 2002],
      catalogSnapshot: [
        {
          skuId: 2001,
          name: '白水晶',
          coverUrl: 'https://shop.test/white.png',
          diyVisual: { canvasUrl: 'https://shop.test/white-canvas.png' },
        },
        {
          skuId: 2002,
          name: '孔雀石',
          coverUrl: 'https://shop.test/green.png',
          diyVisual: null,
        },
      ],
    } as any) as any

    expect(card.previewBeads).toHaveLength(2)
    expect(card.previewBeads.map((bead: { imageUrl: string }) => bead.imageUrl))
      .toEqual(['https://shop.test/white-canvas.png', 'https://shop.test/green.png'])
    expect(card).not.toHaveProperty('previewUrl')
  })

  it('uses a toast instead of opening a fourth DIY design', () => {
    const { page, wxMock } = loadSchemesPage()
    page.data.schemes = [{ id: 1 }, { id: 2 }, { id: 3 }]

    page.createScheme()

    expect(wxMock.showToast).toHaveBeenCalledWith({
      title: '最多保存 3 个 DIY 方案',
      icon: 'none',
    })
    expect(wxMock.navigateTo).not.toHaveBeenCalled()
  })
})

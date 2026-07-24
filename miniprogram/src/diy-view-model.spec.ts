import {
  buildEditorSummary,
  restorePackageSequence,
} from '../miniprogram/utils/diy-view-model'

declare function require(name: string): unknown

function loadEditorDefinition(): Record<string, any> {
  let definition: Record<string, any> | null = null
  ;(globalThis as any).Page = (pageOptions: Record<string, any>) => {
    definition = pageOptions
  }
  jest.isolateModules(() => {
    require('../miniprogram/pages/diy/editor')
  })
  if (!definition) throw new Error('Editor Page definition was not captured')
  return definition
}

function instantiate(definition: Record<string, any>): Record<string, any> {
  const instance: Record<string, any> = {
    ...definition,
    data: { ...definition.data },
    canvasImages: {},
  }
  instance.setData = jest.fn((patch: Record<string, unknown>) => {
    Object.assign(instance.data, patch)
  })
  return instance
}

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

describe('DIY published visual integration', () => {
  it('renders Task 6 against logical dimensions before DPR scaling', () => {
    const page = instantiate(loadEditorDefinition())
    page.config = {
      visualConfig: {
        canvasBackgroundColor: '#f8f3ef',
        braceletShadowOpacity: 0.18,
        beadShadowOpacity: 0.22,
        beadOverlapRatio: 0.08,
        configVersion: 4,
      },
    }
    page.data.catalog = [{
      id: 2001,
      name: '白水晶',
      material: '水晶',
      color: '透明',
      diameterMm: 8,
      salePriceCents: 1280,
      trayImageUrl: '',
      canvasImageUrl: '',
      coverImageUrl: '',
      visualVersion: 0,
      colorHex: '#dfe8ea',
    }]
    page.data.sequence = [2001]
    page.data.selectedIndex = 0
    page.layoutPoints = [{
      index: 0,
      skuId: 2001,
      colorHex: '#dfe8ea',
      x: 160,
      y: 60,
      radius: 12,
      angle: -Math.PI / 2,
    }]
    const ctx = {
      clearRect: jest.fn(),
      fillRect: jest.fn(),
      save: jest.fn(),
      restore: jest.fn(),
      translate: jest.fn(),
      scale: jest.fn(),
      beginPath: jest.fn(),
      arc: jest.fn(),
      fill: jest.fn(),
      stroke: jest.fn(),
      drawImage: jest.fn(),
      fillText: jest.fn(),
      fillStyle: '',
      globalAlpha: 1,
      lineWidth: 1,
      strokeStyle: '',
      font: '',
      textAlign: 'center',
      textBaseline: 'middle',
    }

    page.renderCanvas(ctx, {}, 320, 240)

    expect(ctx.fillRect).toHaveBeenCalledWith(0, 0, 320, 240)
    expect(ctx.save).toHaveBeenCalled()
  })

  it('keys images by visual version and falls through one failed URL once', () => {
    const page = instantiate(loadEditorDefinition())
    page.drawBracelet = jest.fn()
    const images: Array<{
      src: string
      onload: () => void
      onerror: () => void
    }> = []
    const canvas = {
      createImage: jest.fn(() => {
        const image = {
          src: '',
          onload: () => undefined,
          onerror: () => undefined,
        }
        images.push(image)
        return image
      }),
    }
    const item = {
      canvasImageUrl: 'https://shop.test/canvas.webp',
      coverImageUrl: 'https://shop.test/cover.png',
      visualVersion: 3,
    }
    page.editorActive = true
    page.editorGeneration = 1
    page.canvasNode = canvas

    expect(page.resolveCanvasImage(canvas, item)).toBeNull()
    expect(Object.keys(page.canvasImages)).toContain(
      '3:https://shop.test/canvas.webp',
    )
    images[0].onerror()

    expect(page.resolveCanvasImage(canvas, item)).toBeNull()
    expect(images[1].src).toBe('https://shop.test/cover.png')
    images[1].onerror()

    expect(page.resolveCanvasImage(canvas, item)).toBeNull()
    expect(canvas.createImage).toHaveBeenCalledTimes(2)
  })

  it('scopes selector queries and ignores image callbacks from an old editor', () => {
    const page = instantiate(loadEditorDefinition())
    page.editorActive = true
    page.editorGeneration = 7
    const query: Record<string, jest.Mock> = {
      exec: jest.fn(),
      in: jest.fn(),
      select: jest.fn(),
    }
    const node = {
      fields: jest.fn(() => query),
    }
    query.in.mockReturnValue(query)
    query.select.mockReturnValue(node)
    ;(globalThis as any).wx = {
      createSelectorQuery: jest.fn(() => query),
    }

    page.drawBracelet()

    expect(query.in).toHaveBeenCalledWith(page)

    const images: Array<{
      src: string
      onload: () => void
      onerror: () => void
    }> = []
    const canvas = {
      createImage: jest.fn(() => {
        const image = {
          src: '',
          onload: () => undefined,
          onerror: () => undefined,
        }
        images.push(image)
        return image
      }),
    }
    page.canvasNode = canvas
    page.drawBracelet = jest.fn()
    page.resolveCanvasImage(canvas, {
      canvasImageUrl: 'https://shop.test/canvas.webp',
      coverImageUrl: 'https://shop.test/cover.png',
      visualVersion: 3,
    })

    page.onUnload()
    images[0].onload()

    expect(page.drawBracelet).not.toHaveBeenCalled()
  })

  it('ignores an image callback from a Canvas that is no longer current', () => {
    const page = instantiate(loadEditorDefinition())
    page.editorActive = true
    page.editorGeneration = 3
    page.drawBracelet = jest.fn()
    const images: Array<{
      src: string
      onload: () => void
      onerror: () => void
    }> = []
    const initiatingCanvas = {
      createImage: jest.fn(() => {
        const image = {
          src: '',
          onload: () => undefined,
          onerror: () => undefined,
        }
        images.push(image)
        return image
      }),
    }
    page.canvasNode = initiatingCanvas
    page.resolveCanvasImage(initiatingCanvas, {
      canvasImageUrl: 'https://shop.test/canvas.webp',
      coverImageUrl: 'https://shop.test/cover.png',
      visualVersion: 3,
    })

    page.canvasNode = {}
    images[0].onerror()

    expect(page.drawBracelet).not.toHaveBeenCalled()
  })

  it('falls the tray image through the cover and then the color placeholder', () => {
    const page = instantiate(loadEditorDefinition())
    page.data.catalog = [{
      id: 2001,
      trayImageUrl: 'https://shop.test/tray.webp',
      coverImageUrl: 'https://shop.test/cover.png',
      visualVersion: 3,
    }]
    const trayEvent = {
      currentTarget: {
        dataset: {
          id: 2001,
          visualKey: '3:https://shop.test/tray.webp',
        },
      },
    }

    page.onTrayImageError(trayEvent)
    expect(page.data.catalog[0].trayImageUrl)
      .toBe('https://shop.test/cover.png')

    page.onTrayImageError({
      currentTarget: {
        dataset: {
          id: 2001,
          visualKey: '3:https://shop.test/cover.png',
        },
      },
    })
    expect(page.data.catalog[0].trayImageUrl).toBe('')
  })

  it('ignores a tray error from a visual that has already changed', () => {
    const page = instantiate(loadEditorDefinition())
    page.data.catalog = [{
      id: 2001,
      trayImageUrl: 'https://shop.test/new-tray.webp',
      coverImageUrl: 'https://shop.test/new-cover.png',
      visualVersion: 4,
    }]

    page.onTrayImageError({
      currentTarget: {
        dataset: {
          id: 2001,
          visualKey: '3:https://shop.test/old-tray.webp',
        },
      },
    })

    expect(page.data.catalog[0].trayImageUrl)
      .toBe('https://shop.test/new-tray.webp')
  })

  it('exports the same live canvas node used by the editor preview', async () => {
    const page = instantiate(loadEditorDefinition())
    const canvas = {}
    page.canvasNode = canvas
    const canvasToTempFilePath = jest.fn((options: {
      canvas?: unknown
      success(response: { tempFilePath: string }): void
    }) => {
      options.success({ tempFilePath: '/tmp/diy-preview.png' })
    })
    ;(globalThis as any).wx = { canvasToTempFilePath }

    await expect(page.exportPreview()).resolves.toBe('/tmp/diy-preview.png')
    expect(canvasToTempFilePath).toHaveBeenCalledWith(expect.objectContaining({
      canvas,
      fileType: 'png',
      quality: 0.9,
    }))
  })
})

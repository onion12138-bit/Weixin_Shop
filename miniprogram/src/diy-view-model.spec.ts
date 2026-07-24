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
    canvasImageQueue: [],
    activeCanvasImageLoads: 0,
    canvasRedrawScheduled: false,
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

  it('limits image loading to four requests and advances the queued remainder', () => {
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
    page.editorActive = true
    page.editorGeneration = 1
    page.canvasNode = canvas

    for (let index = 0; index < 6; index += 1) {
      page.resolveCanvasImage(canvas, {
        canvasImageUrl: `https://shop.test/canvas-${index}.webp`,
        coverImageUrl: '',
        visualVersion: index,
      })
    }

    expect(canvas.createImage).toHaveBeenCalledTimes(4)
    expect(images.map((image) => image.src)).toEqual([
      'https://shop.test/canvas-0.webp',
      'https://shop.test/canvas-1.webp',
      'https://shop.test/canvas-2.webp',
      'https://shop.test/canvas-3.webp',
    ])

    images[0].onload()

    expect(canvas.createImage).toHaveBeenCalledTimes(5)
    expect(images[4].src).toBe('https://shop.test/canvas-4.webp')
  })

  it('coalesces a burst of image completions into one scheduled redraw', async () => {
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
    page.editorActive = true
    page.editorGeneration = 1
    page.canvasNode = canvas

    for (let index = 0; index < 4; index += 1) {
      page.resolveCanvasImage(canvas, {
        canvasImageUrl: `https://shop.test/canvas-${index}.webp`,
        coverImageUrl: '',
        visualVersion: index,
      })
    }
    images.forEach((image) => image.onload())
    await Promise.resolve()

    expect(page.drawBracelet).toHaveBeenCalledTimes(1)
  })

  it('reuses the current page canvas context and logical dimensions', () => {
    const page = instantiate(loadEditorDefinition())
    page.editorActive = true
    page.editorGeneration = 1
    page.data.sequence = [2001]
    page.data.catalog = [{
      id: 2001,
      name: '白水晶',
      diameterMm: 8,
      salePriceCents: 1280,
      colorHex: '#dfe8ea',
    }]
    const canvas = {}
    const context = {}
    page.canvasNode = canvas
    page.canvasContext = context
    page.canvasLogicalWidth = 320
    page.canvasLogicalHeight = 240
    page.renderCanvas = jest.fn()
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
    page.drawBracelet()

    expect((globalThis as any).wx.createSelectorQuery).not.toHaveBeenCalled()
    expect(page.renderCanvas).toHaveBeenNthCalledWith(
      1,
      context,
      canvas,
      320,
      240,
    )
    expect(page.renderCanvas).toHaveBeenCalledTimes(2)
    expect(page.layoutPoints).toHaveLength(1)
  })

  it('keeps the four-request ceiling while replacing the current canvas', () => {
    const page = instantiate(loadEditorDefinition())
    page.editorActive = true
    page.editorGeneration = 1
    const oldImages: Array<{
      src: string
      onload: () => void
      onerror: () => void
    }> = []
    const oldCanvas = {
      createImage: jest.fn(() => {
        const image = {
          src: '',
          onload: () => undefined,
          onerror: () => undefined,
        }
        oldImages.push(image)
        return image
      }),
    }
    const newCanvas = {
      width: 0,
      height: 0,
      getContext: jest.fn(() => ({
        scale: jest.fn(),
      })),
      createImage: jest.fn(() => ({
        src: '',
        onload: () => undefined,
        onerror: () => undefined,
      })),
    }
    page.canvasNode = oldCanvas
    for (let index = 0; index < 4; index += 1) {
      page.resolveCanvasImage(oldCanvas, {
        canvasImageUrl: `https://shop.test/old-${index}.webp`,
        coverImageUrl: '',
        visualVersion: index,
      })
    }
    const query: Record<string, jest.Mock> = {
      exec: jest.fn(),
      in: jest.fn(),
      select: jest.fn(),
    }
    const node = {
      fields: jest.fn((
        _fields: Record<string, boolean>,
        callback: (result: unknown) => void,
      ) => {
        callback({ node: newCanvas, width: 320, height: 240 })
        return query
      }),
    }
    query.in.mockReturnValue(query)
    query.select.mockReturnValue(node)
    ;(globalThis as any).wx = {
      createSelectorQuery: jest.fn(() => query),
      getSystemInfoSync: jest.fn(() => ({ pixelRatio: 2 })),
    }

    page.drawBracelet()
    page.resolveCanvasImage(newCanvas, {
      canvasImageUrl: 'https://shop.test/new.webp',
      coverImageUrl: '',
      visualVersion: 5,
    })

    expect(newCanvas.createImage).not.toHaveBeenCalled()

    oldImages[0].onload()

    expect(newCanvas.createImage).toHaveBeenCalledTimes(1)
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

  it('renders a changed cord color and exports that same live canvas', async () => {
    const page = instantiate(loadEditorDefinition())
    page.config = {
      visualConfig: {
        canvasBackgroundColor: '#fffdfb',
        braceletShadowOpacity: 0.16,
        beadShadowOpacity: 0.2,
        beadOverlapRatio: 0.06,
        configVersion: 1,
      },
    }
    page.data.cordOptions = [
      { id: 6001, name: '透明绳', colorHex: '#e8e4dc', extraFeeCents: 0 },
      { id: 6002, name: '蓝色绳', colorHex: '#234567', extraFeeCents: 0 },
    ]
    page.data.cordOptionId = 6001
    page.data.sequence = []
    page.layoutPoints = []
    page.refreshDerivedState = jest.fn()
    const liveCanvas = {}
    page.canvasNode = liveCanvas
    const cordStrokes: string[] = []
    const ctx: Record<string, any> = {
      save: jest.fn(),
      restore: jest.fn(),
      translate: jest.fn(),
      scale: jest.fn(),
      beginPath: jest.fn(),
      arc: jest.fn(),
      fillRect: jest.fn(),
      fill: jest.fn(),
      stroke: jest.fn(),
      drawImage: jest.fn(),
      fillStyle: '',
      globalAlpha: 1,
      lineWidth: 1,
      strokeStyle: '',
    }
    ctx.stroke.mockImplementation(() => cordStrokes.push(ctx.strokeStyle))
    const canvasToTempFilePath = jest.fn((options: {
      canvas?: unknown
      success(response: { tempFilePath: string }): void
    }) => {
      options.success({ tempFilePath: '/tmp/diy-blue-cord.png' })
    })
    ;(globalThis as any).wx = { canvasToTempFilePath }

    page.changeCord({ detail: { value: '1' } })
    page.renderCanvas(ctx, liveCanvas, 320, 240)
    const exported = await page.exportPreview()

    expect(cordStrokes[0]).toBe('#234567')
    expect(exported).toBe('/tmp/diy-blue-cord.png')
    expect(canvasToTempFilePath).toHaveBeenCalledWith(expect.objectContaining({
      canvas: liveCanvas,
    }))
  })
})

import {
  buildEditorSummary,
  restorePackageSequence,
  sizeStatusText,
} from '../miniprogram/utils/diy-view-model'

declare function require(name: string): unknown

const { readFileSync } = require('fs') as {
  readFileSync(path: string, encoding: string): string
}

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
    canvasRedrawTimer: null,
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

  it('presents an overlong design as an optional adjustment', () => {
    expect(sizeStatusText('TOO_LONG'))
      .toBe('偏长，可减少珠子或调整腕围')
  })
})

describe('Saved DIY scheme count', () => {
  it('shows the saved bead total even when no individual bead is selected', () => {
    const template = readFileSync('miniprogram/pages/diy/editor.wxml', 'utf8')
    const page = instantiate(loadEditorDefinition())
    page.drawBracelet = jest.fn()

    page.refreshDerivedState(
      Array.from({ length: 9 }, () => 2001),
      {
        authoritative: false,
        persistDraft: false,
        selectedIndex: -1,
      },
    )

    expect(template).toContain('共 {{totalBeadCount}} 颗 · 未选珠子')
    expect(page.data.totalBeadCount).toBe(9)
    expect(page.data.selectedIndex).toBe(-1)
    expect(page.data.selectedNumber).toBe(0)
  })
})

describe('DIY preview controls', () => {
  it('keeps the preview collapse control in the canvas toolbar', () => {
    const template = readFileSync('miniprogram/pages/diy/editor.wxml', 'utf8')
    const styles = readFileSync('miniprogram/pages/diy/editor.wxss', 'utf8')

    expect(template).toContain('class="preview-toggle"')
    expect(template).toContain('bindtap="togglePreview"')
    expect(template).toContain("previewExpanded ? '收起预览' : '展开预览'")
    expect(template).not.toContain('class="history-actions"')
    expect(template).not.toContain('bindtap="undo"')
    expect(template).not.toContain('bindtap="redo"')
    expect(styles).toMatch(
      /\.preview-toolbar\s*{[^}]*position:\s*absolute;[^}]*right:\s*16rpx;/,
    )
    expect(styles).toMatch(
      /\.preview-toggle\s*{[^}]*margin:\s*0 0 0 auto;/,
    )
  })

  it('settles the preview without replacing the live canvas', () => {
    jest.useFakeTimers()
    try {
      const page = instantiate(loadEditorDefinition())
      page.data.previewExpanded = true
      page.drawBracelet = jest.fn()
      page.setData = jest.fn((patch: Record<string, unknown>, callback?: () => void) => {
        Object.assign(page.data, patch)
        callback?.()
      })

      page.togglePreview()

      expect(page.data.previewExpanded).toBe(true)
      expect(page.data.previewCanvasHeightRpx).toBe(196)
      expect(page.data.previewSummaryHeightRpx).toBe(0)

      jest.advanceTimersByTime(240)

      expect(page.data.previewExpanded).toBe(false)
      expect(page.drawBracelet).not.toHaveBeenCalled()
    } finally {
      jest.useRealTimers()
    }
  })

  it('keeps a 20px dead zone, then shrinks smoothly and settles at 120px', () => {
    jest.useFakeTimers()
    try {
      const page = instantiate(loadEditorDefinition())
      page.data.previewExpanded = true
      page.previewExpandedScrollTop = 0
      page.lastPageScrollTop = 0
      page.lastPageScrollTimestamp = Date.now()
      page.previewAutoCollapseTriggered = false
      page.drawBracelet = jest.fn()
      page.setData = jest.fn((patch: Record<string, unknown>, callback?: () => void) => {
        Object.assign(page.data, patch)
        callback?.()
      })

      page.onPageScroll({ scrollTop: 20 })
      expect(page.data.previewCollapseProgress).toBe(0)
      expect(page.data.previewExpanded).toBe(true)

      jest.advanceTimersByTime(100)
      page.onPageScroll({ scrollTop: 40 })
      expect(page.data.previewCollapseProgress).toBeCloseTo(0.2)
      expect(page.data.previewExpanded).toBe(true)

      jest.advanceTimersByTime(100)
      page.onPageScroll({ scrollTop: 70 })
      expect(page.data.previewCollapseProgress).toBeCloseTo(0.5)
      expect(page.data.previewSecondaryOpacity).toBeLessThan(1)

      jest.advanceTimersByTime(100)
      page.onPageScroll({ scrollTop: 100 })
      jest.advanceTimersByTime(100)
      page.onPageScroll({ scrollTop: 120 })

      expect(page.data.previewExpanded).toBe(true)
      expect(page.data.previewCanvasHeightRpx).toBe(196)
      expect(page.data.previewSummaryHeightRpx).toBe(0)

      jest.advanceTimersByTime(240)

      expect(page.data.previewExpanded).toBe(false)
      expect(page.drawBracelet).not.toHaveBeenCalled()
    } finally {
      jest.useRealTimers()
    }
  })

  it('finishes the collapse early for a clearly fast upward swipe', () => {
    jest.useFakeTimers()
    try {
      const page = instantiate(loadEditorDefinition())
      page.data.previewExpanded = true
      page.previewExpandedScrollTop = 0
      page.lastPageScrollTop = 0
      page.lastPageScrollTimestamp = Date.now()
      page.previewAutoCollapseTriggered = false
      page.drawBracelet = jest.fn()
      page.setData = jest.fn((patch: Record<string, unknown>, callback?: () => void) => {
        Object.assign(page.data, patch)
        callback?.()
      })

      jest.advanceTimersByTime(30)
      page.onPageScroll({ scrollTop: 48 })
      page.onPageScroll({ scrollTop: 52 })

      expect(page.previewAutoCollapseTriggered).toBe(true)
      expect(jest.getTimerCount()).toBe(1)

      jest.advanceTimersByTime(240)

      expect(page.data.previewExpanded).toBe(false)
      expect(page.drawBracelet).not.toHaveBeenCalled()
    } finally {
      jest.useRealTimers()
    }
  })

  it('does not auto-collapse again when the preview is already compact', () => {
    const page = instantiate(loadEditorDefinition())
    page.data.previewExpanded = false
    page.startPreviewCollapse = jest.fn()

    page.onPageScroll({ scrollTop: 180 })

    expect(page.startPreviewCollapse).not.toHaveBeenCalled()
  })
})

describe('DIY editor compact layout', () => {
  it('lets the expanded page scroll and only makes compact controls sticky', () => {
    const template = readFileSync('miniprogram/pages/diy/editor.wxml', 'utf8')
    const styles = readFileSync('miniprogram/pages/diy/editor.wxss', 'utf8')
    const config = readFileSync('miniprogram/pages/diy/editor.json', 'utf8')

    expect(template).toContain(
      'class="diy-controls {{previewExpanded ? \'\' : \'is-sticky\'}}"',
    )
    expect(template).not.toContain('scroll-y')
    expect(template).not.toContain('class="bead-list-scroll"')
    expect(template).toContain('previewCanvasHeightRpx')
    expect(template).toContain('previewCanvasScale')
    expect(template).toContain('previewSummaryHeightRpx')
    expect(template.indexOf('class="bead-search"'))
      .toBeLessThan(template.indexOf('class="category-tabs"'))
    expect(styles).toMatch(
      /\.diy-controls\.is-sticky\s*{[^}]*position:\s*sticky;[^}]*top:\s*0;/,
    )
    expect(styles).toMatch(
      /\.bracelet-canvas-viewport\s*{[^}]*transition:\s*height 240ms ease-out;/,
    )
    expect(styles).toMatch(
      /\.bracelet-canvas\s*{[^}]*transition:\s*transform 240ms ease-out;/,
    )
    expect(styles).toMatch(
      /\.canvas-panel\.is-collapsed \.bracelet-canvas\s*{[^}]*height:\s*660rpx;[^}]*transform:\s*scale\(\.297\);/,
    )
    expect(styles).toMatch(
      /\.canvas-panel\.is-collapsed \.editor-summary\s*{[^}]*position:\s*absolute;[^}]*height:\s*196rpx;/,
    )
    expect(config).not.toContain('"disableScroll"')
  })

  it('renders published bead images without a circular white decoration', () => {
    const styles = readFileSync('miniprogram/pages/diy/editor.wxss', 'utf8')

    expect(styles).toMatch(
      /\.tray-bead-image\s*{[^}]*border:\s*0;[^}]*border-radius:\s*0;[^}]*box-shadow:\s*none;/,
    )
  })

  it('combines the estimated price with checkout and removes the price strip', () => {
    const template = readFileSync('miniprogram/pages/diy/editor.wxml', 'utf8')

    expect(template).not.toContain('class="price-strip"')
    expect(template).toContain('{{priceText}} · 去下单')
  })

  it('uses compact vertical spacing between editor controls', () => {
    const styles = readFileSync('miniprogram/pages/diy/editor.wxss', 'utf8')

    expect(styles).toMatch(
      /\.configuration-strip\s*{[^}]*margin-bottom:\s*10rpx;/,
    )
    expect(styles).toMatch(
      /\.selected-actions\s*{[^}]*margin:\s*10rpx 0;/,
    )
    expect(styles).toMatch(
      /\.bead-search\s*{[^}]*margin-bottom:\s*8rpx;/,
    )
    expect(styles).toMatch(
      /\.category-tabs\s*{[^}]*margin-bottom:\s*8rpx;/,
    )
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

  it('coalesces image completions across microtasks into one frame redraw', async () => {
    jest.useFakeTimers()
    try {
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
      images[0].onload()
      await Promise.resolve()
      images[1].onerror()
      await Promise.resolve()
      images[2].onload()
      await Promise.resolve()

      expect(page.drawBracelet).not.toHaveBeenCalled()
      expect(jest.getTimerCount()).toBe(1)

      jest.advanceTimersByTime(16)

      expect(page.drawBracelet).toHaveBeenCalledTimes(1)
    } finally {
      jest.useRealTimers()
    }
  })

  it('cancels the pending frame redraw on editor unload', () => {
    jest.useFakeTimers()
    try {
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
      page.resolveCanvasImage(canvas, {
        canvasImageUrl: 'https://shop.test/canvas.webp',
        coverImageUrl: '',
        visualVersion: 3,
      })
      images[0].onload()

      expect(jest.getTimerCount()).toBe(1)

      page.onUnload()

      expect(jest.getTimerCount()).toBe(0)
      jest.advanceTimersByTime(16)
      expect(page.drawBracelet).not.toHaveBeenCalled()
    } finally {
      jest.useRealTimers()
    }
  })

  it('cancels the pending frame redraw when the canvas is replaced', () => {
    jest.useFakeTimers()
    try {
      const page = instantiate(loadEditorDefinition())
      page.editorActive = true
      page.editorGeneration = 1
      page.renderCanvas = jest.fn()
      const drawBracelet = page.drawBracelet.bind(page)
      page.drawBracelet = jest.fn()
      const images: Array<{
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
          images.push(image)
          return image
        }),
      }
      const newCanvas = {
        width: 0,
        height: 0,
        getContext: jest.fn(() => ({
          scale: jest.fn(),
        })),
      }
      page.canvasNode = oldCanvas
      page.resolveCanvasImage(oldCanvas, {
        canvasImageUrl: 'https://shop.test/canvas.webp',
        coverImageUrl: '',
        visualVersion: 3,
      })
      images[0].onload()

      expect(jest.getTimerCount()).toBe(1)

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

      drawBracelet()

      expect(jest.getTimerCount()).toBe(0)
      jest.advanceTimersByTime(16)
      expect(page.renderCanvas).toHaveBeenCalledTimes(1)
    } finally {
      jest.useRealTimers()
    }
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

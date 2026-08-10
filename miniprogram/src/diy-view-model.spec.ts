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
  instance.setData = jest.fn((patch: Record<string, unknown>, callback?: () => void) => {
    Object.assign(instance.data, patch)
    callback?.()
  })
  return instance
}

function createRenderContext(onArc?: (...args: unknown[]) => void) {
  return {
    save: jest.fn(),
    restore: jest.fn(),
    translate: jest.fn(),
    scale: jest.fn(),
    beginPath: jest.fn(),
    arc: jest.fn((...args: unknown[]) => onArc?.(...args)),
    fillRect: jest.fn(),
    fill: jest.fn(),
    stroke: jest.fn(),
    drawImage: jest.fn(),
    fillStyle: '',
    globalAlpha: 1,
    lineWidth: 1,
    strokeStyle: '',
  }
}

function createExportCanvasMock(onArc?: (...args: unknown[]) => void) {
  const images: Array<{
    src: string
    onload: () => void
    onerror: () => void
  }> = []
  const context = createRenderContext(onArc)
  const canvas = {
    width: 620,
    height: 620,
    getContext: jest.fn(() => context),
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
  return { canvas, context, images }
}

function configureExportPage(
  page: Record<string, any>,
  sequence: number[],
  catalog: Array<Record<string, unknown>>,
) {
  page.config = {
    visualConfig: {
      canvasBackgroundColor: '#fffdfb',
      braceletShadowOpacity: 0.16,
      beadShadowOpacity: 0.2,
      beadOverlapRatio: 0.06,
      configVersion: 1,
    },
  }
  page.data.sequence = sequence
  page.data.catalog = catalog
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
      targetLengthText: '目标 170 mm',
      currentLengthText: '当前 168 mm',
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
  it('renders the preview control above the native canvas', () => {
    const template = readFileSync('miniprogram/pages/diy/editor.wxml', 'utf8')
    const styles = readFileSync('miniprogram/pages/diy/editor.wxss', 'utf8')

    expect(template).toContain(
      '<view wx:if="{{!loading}}" class="preview-toolbar">',
    )
    expect(template).toContain('<button class="preview-toggle"')
    expect(template).toContain('bindtap="togglePreview"')
    expect(template).toContain("previewExpanded ? '收起预览' : '展开预览'")
    expect(template).not.toContain('class="history-actions"')
    expect(template).not.toContain('bindtap="undo"')
    expect(template).not.toContain('bindtap="redo"')
    expect(styles).toMatch(
      /\.preview-toolbar\s*{[^}]*position:\s*absolute;[^}]*right:\s*16rpx;/,
    )
    expect(styles).toMatch(
      /\.preview-toolbar\s*{[^}]*z-index:\s*30;/,
    )
    const viewportStart = template.indexOf('class="bracelet-canvas-viewport"')
    const toolbarStart = template.indexOf('class="preview-toolbar"')
    expect(toolbarStart).toBeGreaterThan(0)
    expect(toolbarStart).toBeLessThan(viewportStart)
    expect(styles).toMatch(
      /\.bracelet-canvas-viewport\s*{[^}]*top:\s*72rpx;[^}]*height:\s*588rpx;/,
    )
    expect(styles).toMatch(
      /\.canvas-panel\.is-collapsed \.bracelet-canvas-viewport\s*{[^}]*top:\s*0;[^}]*right:\s*auto;/,
    )
  })

  it('collapses the upper preview immediately when its own control is tapped', () => {
    const page = instantiate(loadEditorDefinition())
    page.data.previewExpanded = true
    page.drawBracelet = jest.fn()
    page.setData = jest.fn((
      patch: Record<string, unknown>,
      callback?: () => void,
    ) => {
      Object.assign(page.data, patch)
      callback?.()
    })

    page.togglePreview()

    expect(page.data.previewExpanded).toBe(false)
    expect(page.previewAutoCollapseTriggered).toBe(true)
    expect(page.drawBracelet).not.toHaveBeenCalled()
  })

  it('captures the expanded canvas once and uses that fixed image when compact', async () => {
    const page = instantiate(loadEditorDefinition())
    const canvas = { width: 640, height: 588 }
    const canvasToTempFilePath = jest.fn((options: Record<string, any>) => {
      options.success({ tempFilePath: 'wxfile://fixed-bracelet.png' })
    })
    ;(globalThis as any).wx = { canvasToTempFilePath }
    page.editorActive = true
    page.editorGeneration = 3
    page.canvasNode = canvas
    page.canvasRenderRevision = 7
    page.compactPreviewCapturedRevision = -1

    await page.captureCompactPreview()

    expect(canvasToTempFilePath).toHaveBeenCalledWith(expect.objectContaining({
      canvas,
      fileType: 'png',
    }))
    expect(page.data.compactPreviewUrl).toBe('wxfile://fixed-bracelet.png')
    expect(page.compactPreviewCapturedRevision).toBe(7)
  })

  it('scrolls the compact preview into place before expanding it again', () => {
    jest.useFakeTimers()
    try {
      const page = instantiate(loadEditorDefinition())
      const pageScrollTo = jest.fn()
      ;(globalThis as any).wx = { pageScrollTo }
      page.data.previewExpanded = false
      page.currentPageScrollTop = 480
      page.previewAutoCollapseTriggered = true

      page.togglePreview()

      expect(pageScrollTo).toHaveBeenCalledWith({
        scrollTop: 0,
        duration: 240,
      })
      expect(page.data.previewExpanded).toBe(false)

      jest.advanceTimersByTime(240)

      expect(page.data.previewExpanded).toBe(true)
      expect(page.previewAutoCollapseTriggered).toBe(false)
    } finally {
      jest.useRealTimers()
    }
  })

  it('auto-collapses only after the bracelet image has left the viewport', () => {
    const page = instantiate(loadEditorDefinition())
    page.data.previewExpanded = true
    page.previewAutoCollapseTriggered = false
    let bottom = 1
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
        callback({ bottom })
        return query
      }),
    }
    query.in.mockReturnValue(query)
    query.select.mockReturnValue(node)
    ;(globalThis as any).wx = {
      createSelectorQuery: jest.fn(() => query),
    }

    page.onPageScroll({ scrollTop: 300 })
    expect(page.data.previewExpanded).toBe(true)

    bottom = 0
    page.onPageScroll({ scrollTop: 520 })

    expect(page.data.previewExpanded).toBe(false)
    expect(page.previewAutoCollapseTriggered).toBe(true)
    expect(node.fields).toHaveBeenCalledWith(
      { rect: true },
      expect.any(Function),
    )
  })

  it('does not auto-collapse again when the preview is already compact', () => {
    const page = instantiate(loadEditorDefinition())
    page.data.previewExpanded = false
    page.startPreviewCollapse = jest.fn()

    page.onPageScroll({ scrollTop: 180 })

    expect(page.startPreviewCollapse).not.toHaveBeenCalled()
  })

  it('always renders every bead in the expanded grid without a tray toggle', () => {
    const template = readFileSync('miniprogram/pages/diy/editor.wxml', 'utf8')
    const source = readFileSync('miniprogram/pages/diy/editor.ts', 'utf8')
    const definition = loadEditorDefinition()

    expect(definition.data).not.toHaveProperty('trayExpanded')
    expect(definition.toggleTrayExpanded).toBeUndefined()
    expect(template).toContain('<view class="bead-grid">')
    expect(template).toContain('class="bead-card expanded"')
    expect(template).not.toContain('class="bead-tray"')
    expect(template).not.toContain('bindtap="toggleTrayExpanded"')
    expect(template).not.toContain('展开全部珠子')
    expect(template).not.toContain('收起全部珠子')
    expect(source).toContain('const beadsPromise = listAllPublicBeads()')
    expect(source).not.toContain('listBeads(')
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
    expect(template).not.toContain('previewCanvasHeightRpx')
    expect(template).not.toContain('previewCanvasScale')
    expect(template).not.toContain('previewSummaryHeightRpx')
    expect(template.indexOf('class="bead-search"'))
      .toBeLessThan(template.indexOf('class="category-tabs"'))
    expect(styles).toMatch(
      /\.diy-controls\.is-sticky\s*{[^}]*position:\s*sticky;[^}]*top:\s*0;/,
    )
    expect(styles).toMatch(
      /\.preview-layout\s*{[^}]*height:\s*792rpx;/,
    )
    expect(styles).not.toContain('transform: scale(.297)')
    expect(styles).toMatch(
      /\.canvas-panel\.is-collapsed \.bracelet-canvas-viewport\s*{[^}]*width:\s*226rpx;[^}]*height:\s*196rpx;/,
    )
    expect(template).toContain('hidden="{{!previewExpanded}}"')
    expect(template).toContain(
      'wx:if="{{!previewExpanded && compactPreviewUrl}}"',
    )
    expect(template).toContain('src="{{compactPreviewUrl}}"')
    expect(styles).toMatch(
      /\.compact-preview-image\s*{[^}]*width:\s*226rpx;[^}]*height:\s*196rpx;/,
    )
    expect(styles).not.toMatch(
      /\.canvas-panel\.is-collapsed \.bracelet-canvas\s*{/,
    )
    expect(styles).toMatch(
      /\.canvas-panel\.is-collapsed \.editor-summary\s*{[^}]*left:\s*226rpx;[^}]*height:\s*196rpx;/,
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

  it('requeues the same image for a new canvas when an old load finishes after reset', () => {
    const page = instantiate(loadEditorDefinition())
    const oldImages: Array<{
      src: string
      onload: () => void
      onerror: () => void
    }> = []
    const newImages: Array<{
      src: string
      onload: () => void
      onerror: () => void
    }> = []
    const oldCanvas = {
      createImage: jest.fn(() => {
        const image = { src: '', onload: () => undefined, onerror: () => undefined }
        oldImages.push(image)
        return image
      }),
    }
    const context = { scale: jest.fn() }
    const newCanvas = {
      width: 0,
      height: 0,
      getContext: jest.fn(() => context),
      createImage: jest.fn(() => {
        const image = { src: '', onload: () => undefined, onerror: () => undefined }
        newImages.push(image)
        return image
      }),
    }
    const item = {
      canvasImageUrl: 'https://shop.test/canvas.webp',
      coverImageUrl: '',
      visualVersion: 3,
    }
    const query: Record<string, jest.Mock> = {
      exec: jest.fn(),
      in: jest.fn(),
      select: jest.fn(),
    }
    const selection = {
      fields: jest.fn((_: unknown, callback: (result: unknown) => void) => {
        callback({ node: newCanvas, width: 320, height: 240 })
        return query
      }),
    }
    query.in.mockReturnValue(query)
    query.select.mockReturnValue(selection)
    ;(globalThis as any).wx = {
      createSelectorQuery: jest.fn(() => query),
      getSystemInfoSync: jest.fn(() => ({ pixelRatio: 1 })),
    }
    page.editorActive = true
    page.editorGeneration = 1
    page.canvasNode = oldCanvas
    page.renderCanvas = jest.fn()

    expect(page.resolveCanvasImage(oldCanvas, item)).toBeNull()
    expect(page.activeCanvasImageLoads).toBe(1)

    page.resetCanvasForResize()
    oldImages[0].onload()
    page.drawBracelet()

    expect(page.canvasNode).toBe(newCanvas)
    expect(page.resolveCanvasImage(newCanvas, item)).toBeNull()
    expect(newCanvas.createImage).toHaveBeenCalledTimes(1)
    expect(newImages[0].src).toBe('https://shop.test/canvas.webp')

    newImages[0].onload()
    expect(page.resolveCanvasImage(newCanvas, item)).toBe(newImages[0])
    page.onUnload()
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
    const newImages: Array<{
      src: string
      onload: () => void
      onerror: () => void
    }> = []
    const newCanvas = {
      width: 0,
      height: 0,
      getContext: jest.fn(() => ({
        scale: jest.fn(),
      })),
      createImage: jest.fn(() => {
        const image = {
          src: '',
          onload: () => undefined,
          onerror: () => undefined,
        }
        newImages.push(image)
        return image
      }),
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
    const repeatedItem = {
      canvasImageUrl: 'https://shop.test/old-0.webp',
      coverImageUrl: '',
      visualVersion: 0,
    }
    page.resolveCanvasImage(newCanvas, repeatedItem)

    expect(newCanvas.createImage).not.toHaveBeenCalled()
    expect(page.activeCanvasImageLoads).toBe(4)

    oldImages[0].onload()

    expect(newCanvas.createImage).toHaveBeenCalledTimes(1)
    expect(newImages[0].src).toBe('https://shop.test/old-0.webp')
    expect(page.activeCanvasImageLoads).toBe(4)

    newImages[0].onload()

    expect(page.activeCanvasImageLoads).toBe(3)
    expect(page.resolveCanvasImage(newCanvas, repeatedItem)).toBe(newImages[0])
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

  it('exports a collapsed design from an independent 620 square canvas', async () => {
    const page = instantiate(loadEditorDefinition())
    const layoutSentinel = [{ index: 99, skuId: 999 }]
    page.layoutPoints = layoutSentinel
    page.canvasNode = { collapsed: true }
    page.canvasLogicalWidth = 300
    page.canvasLogicalHeight = 210
    page.data.previewExpanded = false
    page.data.sequence = [2001, 2002]
    page.data.selectedIndex = 1
    page.data.catalog = [
      {
        id: 2001,
        name: '紫水晶',
        diameterMm: 8,
        salePriceCents: 1280,
        colorHex: '#b895d8',
        canvasImageUrl: '',
        coverImageUrl: '',
        visualVersion: 1,
      },
      {
        id: 2002,
        name: '石榴石',
        diameterMm: 8,
        salePriceCents: 980,
        colorHex: '#8d2f48',
        canvasImageUrl: '',
        coverImageUrl: '',
        visualVersion: 1,
      },
    ]
    page.data.cordOptions = [
      { id: 6001, name: '透明绳', colorHex: '#e8e4dc', extraFeeCents: 0 },
    ]
    page.data.cordOptionId = 6001
    page.config = {
      visualConfig: {
        canvasBackgroundColor: '#fffdfb',
        braceletShadowOpacity: 0.16,
        beadShadowOpacity: 0.2,
        beadOverlapRatio: 0.06,
        configVersion: 1,
      },
    }
    const ctx = createRenderContext()
    const exportCanvas = {
      width: 620,
      height: 620,
      getContext: jest.fn(() => ctx),
    }
    const createOffscreenCanvas = jest.fn(() => exportCanvas)
    const canvasToTempFilePath = jest.fn((options: {
      canvas?: unknown
      success(response: { tempFilePath: string }): void
    }) => {
      options.success({ tempFilePath: '/tmp/diy-preview.png' })
    })
    ;(globalThis as any).wx = { createOffscreenCanvas, canvasToTempFilePath }

    await expect(page.exportPreview()).resolves.toBe('/tmp/diy-preview.png')
    expect(createOffscreenCanvas).toHaveBeenCalledWith({
      type: '2d',
      width: 620,
      height: 620,
    })
    expect(ctx.fillRect).toHaveBeenCalledWith(0, 0, 620, 620)
    expect(canvasToTempFilePath).toHaveBeenCalledWith(expect.objectContaining({
      canvas: exportCanvas,
      fileType: 'png',
      quality: 0.9,
    }))
    expect(page.layoutPoints).toBe(layoutSentinel)
  })

  it('uses the same canonical layout when saving immediately after collapsing', async () => {
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
    page.data.sequence = [2001]
    page.data.catalog = [{
      id: 2001,
      name: '紫水晶',
      diameterMm: 8,
      salePriceCents: 1280,
      colorHex: '#b895d8',
      canvasImageUrl: '',
      coverImageUrl: '',
      visualVersion: 1,
    }]
    page.canvasNode = null
    page.drawBracelet = jest.fn()
    const arcCalls: unknown[][][] = []
    const exportCanvases = [0, 1].map(() => {
      const calls: unknown[][] = []
      arcCalls.push(calls)
      const ctx = createRenderContext((...args) => calls.push(args))
      return { width: 620, height: 620, getContext: jest.fn(() => ctx) }
    })
    const createOffscreenCanvas = jest.fn()
      .mockReturnValueOnce(exportCanvases[0])
      .mockReturnValueOnce(exportCanvases[1])
    const canvasToTempFilePath = jest.fn((options: {
      canvas?: unknown
      success(response: { tempFilePath: string }): void
    }) => {
      options.success({ tempFilePath: '/tmp/diy-blue-cord.png' })
    })
    ;(globalThis as any).wx = { createOffscreenCanvas, canvasToTempFilePath }

    page.data.previewExpanded = false
    await page.exportPreview()
    page.data.previewExpanded = true
    page.togglePreview()
    await page.exportPreview()

    expect(page.data.previewExpanded).toBe(false)
    expect(page.canvasNode).toBeNull()
    expect(createOffscreenCanvas).toHaveBeenNthCalledWith(1, {
      type: '2d', width: 620, height: 620,
    })
    expect(createOffscreenCanvas).toHaveBeenNthCalledWith(2, {
      type: '2d', width: 620, height: 620,
    })
    expect(arcCalls[0]).toEqual(arcCalls[1])
    expect(canvasToTempFilePath).toHaveBeenNthCalledWith(
      2,
      expect.objectContaining({ canvas: exportCanvases[1] }),
    )
  })

  it('waits for one export-local image per unique bead before saving a collapsed preview', async () => {
    const page = instantiate(loadEditorDefinition())
    configureExportPage(page, [2001, 2001], [{
      id: 2001,
      name: '紫水晶',
      diameterMm: 8,
      salePriceCents: 1280,
      colorHex: '#b895d8',
      canvasImageUrl: 'https://shop.test/amethyst-canvas.webp',
      coverImageUrl: 'https://shop.test/amethyst-cover.png',
      visualVersion: 2,
    }])
    page.data.previewExpanded = false
    page.canvasNode = { previousCanvas: true }
    page.canvasImages = { '2:https://shop.test/amethyst-canvas.webp': 'loading' }
    page.activeCanvasImageLoads = 1
    page.resetCanvasForResize()
    const { canvas, context, images } = createExportCanvasMock()
    const canvasToTempFilePath = jest.fn((options: {
      success(response: { tempFilePath: string }): void
    }) => options.success({ tempFilePath: '/tmp/export-with-image.png' }))
    ;(globalThis as any).wx = {
      createOffscreenCanvas: jest.fn(() => canvas),
      canvasToTempFilePath,
    }

    const exported = page.exportPreview()
    await Promise.resolve()

    expect(canvas.createImage).toHaveBeenCalledTimes(1)
    expect(images[0].src).toBe('https://shop.test/amethyst-canvas.webp')
    expect(canvasToTempFilePath).not.toHaveBeenCalled()
    expect(context.drawImage).not.toHaveBeenCalled()

    images[0].onload()

    await expect(exported).resolves.toBe('/tmp/export-with-image.png')
    expect(context.drawImage).toHaveBeenCalledTimes(2)
    expect(context.drawImage).toHaveBeenCalledWith(
      images[0],
      expect.any(Number),
      expect.any(Number),
      expect.any(Number),
      expect.any(Number),
    )
    expect(page.canvasImages).toEqual({})
    expect(page.activeCanvasImageLoads).toBe(1)
  })

  it('falls an export-local canvas URL through to its cover URL', async () => {
    const page = instantiate(loadEditorDefinition())
    configureExportPage(page, [2001], [{
      id: 2001,
      name: '紫水晶',
      diameterMm: 8,
      salePriceCents: 1280,
      colorHex: '#b895d8',
      canvasImageUrl: 'https://shop.test/broken.webp',
      coverImageUrl: 'https://shop.test/cover.png',
      visualVersion: 2,
    }])
    const { canvas, context, images } = createExportCanvasMock()
    const canvasToTempFilePath = jest.fn((options: {
      success(response: { tempFilePath: string }): void
    }) => options.success({ tempFilePath: '/tmp/export-cover.png' }))
    ;(globalThis as any).wx = {
      createOffscreenCanvas: jest.fn(() => canvas),
      canvasToTempFilePath,
    }

    const exported = page.exportPreview()
    await Promise.resolve()
    images[0].onerror()

    expect(canvas.createImage).toHaveBeenCalledTimes(2)
    expect(images[1].src).toBe('https://shop.test/cover.png')
    expect(canvasToTempFilePath).not.toHaveBeenCalled()

    images[1].onload()

    await expect(exported).resolves.toBe('/tmp/export-cover.png')
    expect(context.drawImage).toHaveBeenCalledWith(
      images[1],
      expect.any(Number),
      expect.any(Number),
      expect.any(Number),
      expect.any(Number),
    )
  })

  it('uses the bead color when every export-local URL fails', async () => {
    const page = instantiate(loadEditorDefinition())
    configureExportPage(page, [2001], [{
      id: 2001,
      name: '紫水晶',
      diameterMm: 8,
      salePriceCents: 1280,
      colorHex: '#b895d8',
      canvasImageUrl: 'https://shop.test/broken.webp',
      coverImageUrl: 'https://shop.test/broken-cover.png',
      visualVersion: 2,
    }])
    const { canvas, context, images } = createExportCanvasMock()
    const canvasToTempFilePath = jest.fn((options: {
      success(response: { tempFilePath: string }): void
    }) => options.success({ tempFilePath: '/tmp/export-color.png' }))
    ;(globalThis as any).wx = {
      createOffscreenCanvas: jest.fn(() => canvas),
      canvasToTempFilePath,
    }

    const exported = page.exportPreview()
    await Promise.resolve()
    images[0].onerror()
    images[1].onerror()

    await expect(exported).resolves.toBe('/tmp/export-color.png')
    expect(context.drawImage).not.toHaveBeenCalled()
    expect(canvasToTempFilePath).toHaveBeenCalledTimes(1)
  })

  it('limits export-local image loading to four and advances on completion or failure', async () => {
    const page = instantiate(loadEditorDefinition())
    const catalog = Array.from({ length: 6 }, (_, index) => ({
      id: 2001 + index,
      name: `珠子 ${index + 1}`,
      diameterMm: 8,
      salePriceCents: 980,
      colorHex: '#b895d8',
      canvasImageUrl: `https://shop.test/bead-${index + 1}.webp`,
      coverImageUrl: '',
      visualVersion: 1,
    }))
    configureExportPage(page, catalog.map((item) => item.id), catalog)
    const { canvas, images } = createExportCanvasMock()
    const canvasToTempFilePath = jest.fn((options: {
      success(response: { tempFilePath: string }): void
    }) => options.success({ tempFilePath: '/tmp/export-limited.png' }))
    ;(globalThis as any).wx = {
      createOffscreenCanvas: jest.fn(() => canvas),
      canvasToTempFilePath,
    }

    const exported = page.exportPreview()
    await Promise.resolve()

    expect(canvas.createImage).toHaveBeenCalledTimes(4)
    expect(canvasToTempFilePath).not.toHaveBeenCalled()

    images[0].onload()
    expect(canvas.createImage).toHaveBeenCalledTimes(5)

    images[1].onerror()
    expect(canvas.createImage).toHaveBeenCalledTimes(6)

    images.slice(2).forEach((image) => image.onload())
    await expect(exported).resolves.toBe('/tmp/export-limited.png')
    expect(canvasToTempFilePath).toHaveBeenCalledTimes(1)
  })

  it('cancels active and queued export images after one bounded wait', async () => {
    jest.useFakeTimers()
    try {
      const page = instantiate(loadEditorDefinition())
      const catalog = Array.from({ length: 5 }, (_, index) => ({
        id: 2001 + index,
        name: `珠子 ${index + 1}`,
        diameterMm: 8,
        salePriceCents: 1280,
        colorHex: '#b895d8',
        canvasImageUrl: `https://shop.test/stalled-${index + 1}.webp`,
        coverImageUrl: `https://shop.test/stalled-cover-${index + 1}.png`,
        visualVersion: 2,
      }))
      configureExportPage(page, catalog.map((item) => item.id), catalog)
      const { canvas, context, images } = createExportCanvasMock()
      const canvasToTempFilePath = jest.fn((options: {
        success(response: { tempFilePath: string }): void
      }) => options.success({ tempFilePath: '/tmp/export-color.png' }))
      ;(globalThis as any).wx = {
        createOffscreenCanvas: jest.fn(() => canvas),
        canvasToTempFilePath,
      }

      const exported = page.exportPreview()
      await Promise.resolve()

      expect(canvas.createImage).toHaveBeenCalledTimes(4)
      expect(canvasToTempFilePath).not.toHaveBeenCalled()
      const lateLoad = images[0].onload
      const lateError = images[1].onerror
      jest.runOnlyPendingTimers()
      await expect(exported).resolves.toBe('/tmp/export-color.png')

      expect(images).toHaveLength(4)
      expect(images.every((image) => image.src === '')).toBe(true)
      expect(images[0].onload).not.toBe(lateLoad)
      expect(images[1].onerror).not.toBe(lateError)
      expect(context.drawImage).not.toHaveBeenCalled()
      expect(canvasToTempFilePath).toHaveBeenCalledTimes(1)
      expect(jest.getTimerCount()).toBe(0)

      lateLoad()
      lateError()
      await Promise.resolve()
      expect(canvas.createImage).toHaveBeenCalledTimes(4)
      expect(canvasToTempFilePath).toHaveBeenCalledTimes(1)
    } finally {
      jest.useRealTimers()
    }
  })

  it('rejects preview export clearly when offscreen canvas is unavailable', async () => {
    const page = instantiate(loadEditorDefinition())
    ;(globalThis as any).wx = { canvasToTempFilePath: jest.fn() }

    await expect(page.exportPreview()).rejects.toThrow(
      '当前微信版本不支持生成方案预览',
    )
  })

  it('rejects preview export clearly when offscreen canvas creation fails', async () => {
    const page = instantiate(loadEditorDefinition())
    ;(globalThis as any).wx = {
      createOffscreenCanvas: jest.fn(() => {
        throw new Error('platform failure')
      }),
      canvasToTempFilePath: jest.fn(),
    }

    await expect(page.exportPreview()).rejects.toThrow(
      '方案预览画布创建失败，请重试',
    )
  })
})

import {
  renderBraceletCanvas,
  type BraceletDrawingContext,
  type CanvasImageLike,
} from '../miniprogram/domain/bracelet-canvas-renderer'
import { buildBraceletRenderPlan } from '../miniprogram/domain/bracelet-render-plan'
import type { BeadLayoutPoint } from '../miniprogram/domain/diy-types'

class RecordingContext implements BraceletDrawingContext {
  width = 200
  height = 200
  fillStyle = 'sentinel-fill'
  globalAlpha = 0.73
  lineWidth = 7
  strokeStyle = 'sentinel-stroke'
  operations: string[] = []
  strokeStyles: string[] = []
  shadowAlphas: number[] = []
  private scaled = false
  private readonly states: Array<{
    fillStyle: string
    globalAlpha: number
    lineWidth: number
    scaled: boolean
    strokeStyle: string
  }> = []

  save() {
    this.states.push({
      fillStyle: this.fillStyle,
      globalAlpha: this.globalAlpha,
      lineWidth: this.lineWidth,
      scaled: this.scaled,
      strokeStyle: this.strokeStyle,
    })
  }

  restore() {
    const state = this.states.pop()
    if (!state) throw new Error('restore without save')
    this.fillStyle = state.fillStyle
    this.globalAlpha = state.globalAlpha
    this.lineWidth = state.lineWidth
    this.scaled = state.scaled
    this.strokeStyle = state.strokeStyle
  }

  translate() {}

  scale() {
    this.scaled = true
  }

  beginPath() {}

  arc() {}

  fillRect() {
    this.operations.push('background')
  }

  fill() {
    if (this.scaled) {
      this.operations.push('bracelet-shadow')
    } else if (this.globalAlpha < 1) {
      this.shadowAlphas.push(this.globalAlpha)
      this.operations.push(`bead-shadow(${this.operations.filter((item) => item.startsWith('bead-shadow')).length})`)
    } else {
      this.operations.push(`bead-fallback(${this.operations.filter((item) => item.startsWith('bead-fallback')).length}:${this.fillStyle})`)
    }
  }

  stroke() {
    const hasCord = this.operations.includes('cord')
    this.strokeStyles.push(this.strokeStyle)
    this.operations.push(hasCord ? 'selection' : 'cord')
  }

  drawImage(_image: CanvasImageLike, x: number) {
    this.operations.push(`bead-image(${x > 50 ? 1 : 0})`)
  }
}

describe('bracelet canvas renderer', () => {
  it('draws the fixed-view layers in depth order and selection last', () => {
    const firstPoint: BeadLayoutPoint & { colorHex: string } = {
      index: 0,
      skuId: 101,
      x: 40,
      y: 40,
      radius: 10,
      angle: 0,
      colorHex: '#cc8899',
    }
    const plan = buildBraceletRenderPlan(
      [
        firstPoint,
        { index: 1, skuId: 102, x: 100, y: 160, radius: 10, angle: Math.PI },
      ],
      {
        canvasBackgroundColor: '#fffdfb',
        braceletShadowOpacity: 0.16,
        beadShadowOpacity: 0.20,
        beadOverlapRatio: 0.10,
      },
      1,
    )
    const ctx = new RecordingContext()
    const image = {} as CanvasImageLike

    renderBraceletCanvas(ctx, plan, (item) => item.index === 1 ? image : null)

    expect(ctx.operations).toEqual([
      'background',
      'cord',
      'bead-shadow(0)',
      'bead-fallback(0:#cc8899)',
      'bead-shadow(1)',
      'bead-image(1)',
      'selection',
    ])
    expect(ctx.shadowAlphas).toEqual([0.20, 0.20])
  })

  it('restores caller canvas state after a normal render', () => {
    const ctx = new RecordingContext()
    const plan = buildBraceletRenderPlan(
      [{ index: 0, skuId: 101, x: 100, y: 100, radius: 10, angle: 0 }],
      {
        canvasBackgroundColor: '#fffdfb',
        braceletShadowOpacity: 0.16,
        beadShadowOpacity: 0.20,
        beadOverlapRatio: 0.10,
      },
      -1,
    )

    renderBraceletCanvas(ctx, plan, () => null)

    expect({
      globalAlpha: ctx.globalAlpha,
      fillStyle: ctx.fillStyle,
      strokeStyle: ctx.strokeStyle,
      lineWidth: ctx.lineWidth,
    }).toEqual({
      globalAlpha: 0.73,
      fillStyle: 'sentinel-fill',
      strokeStyle: 'sentinel-stroke',
      lineWidth: 7,
    })
  })

  it('restores caller canvas state when image resolution throws', () => {
    const ctx = new RecordingContext()
    const plan = buildBraceletRenderPlan(
      [{ index: 0, skuId: 101, x: 100, y: 100, radius: 10, angle: 0 }],
      {
        canvasBackgroundColor: '#fffdfb',
        braceletShadowOpacity: 0.16,
        beadShadowOpacity: 0.20,
        beadOverlapRatio: 0.10,
      },
      -1,
    )
    const failure = new Error('image lookup failed')

    expect(() => renderBraceletCanvas(ctx, plan, () => {
      throw failure
    })).toThrow(failure)
    expect({
      globalAlpha: ctx.globalAlpha,
      fillStyle: ctx.fillStyle,
      strokeStyle: ctx.strokeStyle,
      lineWidth: ctx.lineWidth,
    }).toEqual({
      globalAlpha: 0.73,
      fillStyle: 'sentinel-fill',
      strokeStyle: 'sentinel-stroke',
      lineWidth: 7,
    })
  })

  it('draws the cord with the selected cord color', () => {
    const ctx = new RecordingContext()
    const plan = buildBraceletRenderPlan(
      [],
      {
        canvasBackgroundColor: '#fffdfb',
        braceletShadowOpacity: 0.16,
        beadShadowOpacity: 0.20,
        beadOverlapRatio: 0.10,
      },
      -1,
      { cordColor: '#234567' },
    )

    renderBraceletCanvas(ctx, plan, () => null)

    expect(ctx.strokeStyles[0]).toBe('#234567')
  })
})

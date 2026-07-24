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
  fillStyle = ''
  globalAlpha = 1
  lineWidth = 1
  strokeStyle = ''
  operations: string[] = []
  private scaleDepth = 0
  private lastArcRadius = 0

  save() {
    this.scaleDepth += 1
  }

  restore() {
    this.scaleDepth -= 1
  }

  translate() {}

  scale() {}

  beginPath() {}

  arc(_x: number, _y: number, radius: number) {
    this.lastArcRadius = radius
  }

  fillRect() {
    this.operations.push('background')
  }

  fill() {
    if (this.scaleDepth > 0) {
      this.operations.push('bracelet-shadow')
    } else if (this.globalAlpha < 1) {
      this.operations.push(`bead-shadow(${this.operations.filter((item) => item.startsWith('bead-shadow')).length})`)
    } else {
      this.operations.push(`bead-fallback(${this.operations.filter((item) => item.startsWith('bead-fallback')).length}:${this.fillStyle})`)
    }
  }

  stroke() {
    const hasCord = this.operations.includes('cord')
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
      'bracelet-shadow',
      'cord',
      'bead-shadow(0)',
      'bead-fallback(0:#cc8899)',
      'bead-shadow(1)',
      'bead-image(1)',
      'selection',
    ])
  })
})

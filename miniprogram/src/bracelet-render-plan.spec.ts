import { buildBraceletRenderPlan } from '../miniprogram/domain/bracelet-render-plan'

describe('bracelet render plan', () => {
  it('sorts beads by depth and marks the selected bead', () => {
    const plan = buildBraceletRenderPlan(
      [
        { index: 0, skuId: 101, x: 100, y: 40, radius: 10, angle: -Math.PI / 2 },
        { index: 1, skuId: 102, x: 100, y: 160, radius: 10, angle: Math.PI / 2 },
      ],
      {
        canvasBackgroundColor: '#fffdfb',
        braceletShadowOpacity: 0.16,
        beadShadowOpacity: 0.20,
        beadOverlapRatio: 0.10,
      },
      1,
    )

    expect(plan.beads.map((item) => item.index)).toEqual([0, 1])
    expect(plan.beads[0].drawRadius).toBeCloseTo(11)
    expect(plan.beads[1].selected).toBe(true)
  })

  it('keeps original index order for equal depths', () => {
    const plan = buildBraceletRenderPlan(
      [
        { index: 2, skuId: 102, x: 40, y: 100, radius: 10, angle: 0 },
        { index: 0, skuId: 100, x: 80, y: 100, radius: 10, angle: 0 },
        { index: 1, skuId: 101, x: 120, y: 100, radius: 10, angle: 0 },
      ],
      {
        canvasBackgroundColor: '#fffdfb',
        braceletShadowOpacity: 0.16,
        beadShadowOpacity: 0.20,
        beadOverlapRatio: 0.10,
      },
      -1,
    )

    expect(plan.beads.map((item) => item.index)).toEqual([0, 1, 2])
  })

  it('keeps a config snapshot for deterministic later rendering', () => {
    const config = {
      canvasBackgroundColor: '#fffdfb',
      braceletShadowOpacity: 0.16,
      beadShadowOpacity: 0.20,
      beadOverlapRatio: 0.10,
    }
    const plan = buildBraceletRenderPlan([], config, -1)

    config.canvasBackgroundColor = '#000000'

    expect(plan.config.canvasBackgroundColor).toBe('#fffdfb')
  })

  it('keeps the selected cord color in the render plan', () => {
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

    expect(plan.cordColor).toBe('#234567')
  })
})

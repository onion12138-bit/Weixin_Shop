import { layoutBracelet } from '../miniprogram/domain/bracelet-layout'

describe('DIY bracelet layout', () => {
  it('places four same-diameter beads one quarter turn apart', () => {
    const points = layoutBracelet(
      [101, 102, 103, 104],
      {
        101: { diameterMm: 8 },
        102: { diameterMm: 8 },
        103: { diameterMm: 8 },
        104: { diameterMm: 8 },
      },
      { width: 200, height: 200 },
    )

    expect(points).toHaveLength(4)
    for (let index = 1; index < points.length; index += 1) {
      expect(points[index].angle - points[index - 1].angle)
        .toBeCloseTo(Math.PI / 2, 5)
    }
  })
})

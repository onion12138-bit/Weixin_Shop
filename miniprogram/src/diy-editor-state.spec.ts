import {
  addBead,
  adjustSkuQuantity,
  moveBead,
  replaceBead,
} from '../miniprogram/domain/editor-state'
import { createHistory } from '../miniprogram/domain/editor-history'
import {
  PENDING_AI_RECOMMENDATION_KEY,
  consumePendingAiRecommendation,
  type PendingRecommendationStorage,
} from '../miniprogram/domain/recommendation-profile'

describe('DIY editor state', () => {
  it('preserves exact order without per-bead business ids', () => {
    let sequence = addBead([], 101, null)
    sequence = addBead(sequence, 202, 0)
    sequence = addBead(sequence, 101, 1)
    sequence = replaceBead(sequence, 1, 303)
    sequence = moveBead(sequence, 2, 0)

    expect(sequence).toEqual([101, 101, 303])
  })

  it('adjusts the last occurrence of one sku', () => {
    expect(adjustSkuQuantity([101, 202, 101], 101, -1))
      .toEqual([101, 202])
    expect(adjustSkuQuantity([101, 202], 101, 1))
      .toEqual([101, 101, 202])
  })

  it('keeps at most fifty undo snapshots', () => {
    const history = createHistory<number[]>([], 50)
    for (let index = 1; index <= 55; index += 1) {
      history.push([index])
    }
    for (let index = 0; index < 50; index += 1) history.undo()

    expect(history.current()).toEqual([5])
    expect(history.canUndo()).toBe(false)
  })
})

describe('one-time AI recommendation handoff', () => {
  const validation = {
    allowedSkuIds: [2001, 2002],
    allowedCordOptionIds: [6001],
    minWristCircumferenceMm: 120,
    maxWristCircumferenceMm: 220,
    maxSequenceLength: 30,
  }

  function storage(value: unknown): PendingRecommendationStorage & {
    get: jest.Mock
    remove: jest.Mock
  } {
    return {
      get: jest.fn().mockReturnValue(value),
      remove: jest.fn(),
    }
  }

  it('consumes a valid source=ai handoff as a new unsaved draft', () => {
    const values = storage({
      sequence: [2001, 2002, 2001],
      wristCircumferenceMm: 165,
      fitType: 'LOOSE',
      cordOptionId: 6001,
    })

    expect(consumePendingAiRecommendation('ai', values, validation)).toEqual({
      schemeId: 0,
      schemeVersion: 0,
      name: 'AI 推荐手串',
      wristCircumferenceMm: 165,
      fitType: 'LOOSE',
      cordOptionId: 6001,
      sequence: [2001, 2002, 2001],
      selectedIndex: -1,
    })
    expect(values.remove).toHaveBeenCalledWith(PENDING_AI_RECOMMENDATION_KEY)
  })

  it.each([
    {
      sequence: [9999],
      wristCircumferenceMm: 165,
      fitType: 'STANDARD',
      cordOptionId: 6001,
    },
    {
      sequence: [2001],
      wristCircumferenceMm: 999,
      fitType: 'STANDARD',
      cordOptionId: 6001,
    },
    {
      sequence: Array.from({ length: 31 }, () => 2001),
      wristCircumferenceMm: 165,
      fitType: 'STANDARD',
      cordOptionId: 6001,
    },
    {
      sequence: [2001],
      wristCircumferenceMm: 165,
      fitType: 'STANDARD',
      cordOptionId: 6001,
      birthDate: '1992-01-20',
    },
  ])('rejects invalid structure or IDs and still deletes it', (value) => {
    const values = storage(value)

    expect(consumePendingAiRecommendation('ai', values, validation)).toBeNull()
    expect(values.remove).toHaveBeenCalledWith(PENDING_AI_RECOMMENDATION_KEY)
  })

  it('does not read or delete the handoff without source=ai', () => {
    const values = storage({
      sequence: [2001],
      wristCircumferenceMm: 165,
      fitType: 'STANDARD',
      cordOptionId: 6001,
    })

    expect(consumePendingAiRecommendation(undefined, values, validation))
      .toBeNull()
    expect(values.get).not.toHaveBeenCalled()
    expect(values.remove).not.toHaveBeenCalled()
  })

  it('deletes a corrupt handoff even when reading throws', () => {
    const values = storage(null)
    values.get.mockImplementation(() => {
      throw new Error('corrupt storage')
    })

    expect(consumePendingAiRecommendation('ai', values, validation)).toBeNull()
    expect(values.remove).toHaveBeenCalledWith(PENDING_AI_RECOMMENDATION_KEY)
  })
})

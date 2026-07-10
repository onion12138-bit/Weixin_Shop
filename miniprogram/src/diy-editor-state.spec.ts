import {
  addBead,
  adjustSkuQuantity,
  moveBead,
  replaceBead,
} from '../miniprogram/domain/editor-state'
import { createHistory } from '../miniprogram/domain/editor-history'

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

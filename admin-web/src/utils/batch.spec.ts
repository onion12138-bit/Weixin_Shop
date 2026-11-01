import { describe, expect, it } from 'vitest'
import { settleBatch } from './batch'

describe('bounded batch requests', () => {
  it('limits active requests and retains successful and failed results in input order', async () => {
    let active = 0
    let peak = 0
    const tasks = Array.from({ length: 23 }, (_, index) => async () => {
      active++
      peak = Math.max(peak, active)
      await new Promise((resolve) => setTimeout(resolve, index % 3))
      active--
      if (index === 3) throw new Error('failed order')
      return index
    })
    const results = await settleBatch(tasks)
    expect(peak).toBe(4)
    expect(results).toHaveLength(23)
    expect(results[3]).toMatchObject({ status: 'rejected', reason: new Error('failed order') })
    expect(results[22]).toEqual({ status: 'fulfilled', value: 22 })
    expect(results.filter((result) => result.status === 'fulfilled')).toHaveLength(22)
  })

  it('handles empty input and synchronous task errors', async () => {
    expect(await settleBatch([])).toEqual([])
    expect(await settleBatch([() => { throw new Error('sync failure') }]))
      .toEqual([{ status: 'rejected', reason: new Error('sync failure') }])
  })
})

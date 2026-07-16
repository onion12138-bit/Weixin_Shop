import { describe, expect, it } from 'vitest'
import { generateBeadSku } from './generate-sku'

describe('generateBeadSku', () => {
  it('includes a normalized category, diameter, date and unique suffix', () => {
    expect(generateBeadSku(
      'natural-crystal',
      8.5,
      new Date(2026, 6, 17),
      0,
    )).toBe('BEAD-NATURAL-CRYSTAL-8P5MM-20260717-0000')
  })

  it('falls back safely and always satisfies the backend SKU format', () => {
    const sku = generateBeadSku('天然水晶 / 新品', 8, new Date(2026, 6, 17), 0.5)
    expect(sku).toMatch(/^[A-Z0-9]+(?:-[A-Z0-9]+)*$/)
    expect(sku).toContain('BEAD-GENERAL-8MM-20260717-')
    expect(sku.length).toBeLessThanOrEqual(64)
  })
})

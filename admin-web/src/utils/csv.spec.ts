import { describe, expect, it } from 'vitest'
import { csvCell } from './csv'

describe('CSV export safety', () => {
  it.each(['=1+1', '+SUM(1,1)', '-1+1', '@SUM(1,1)', ' =1+1', '\t=1+1', '\n=1+1', '\rtext', '＝1+1'])(
    'exports formula-like input as plain text: %s', (value) => {
      expect(csvCell(value)).toBe(`"'${value}"`)
    },
  )

  it('preserves ordinary text and escapes commas, quotes and line breaks', () => {
    expect(csvCell('张三,"备注"\n第二行')).toBe('"张三,""备注""\n第二行"')
    expect(csvCell('13812345678')).toBe('"13812345678"')
    expect(csvCell(null)).toBe('""')
  })
})

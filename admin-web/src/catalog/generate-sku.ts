/**
 * 生成可读且可编辑的散珠 SKU：业务前缀 + 分类 + 规格 + 日期 + 唯一尾码。
 * 后端仍负责格式与唯一性终检，前端不会在用户手动修改后自动覆盖。
 */
export function generateBeadSku(
  categorySlug: string,
  diameterMm: number,
  now = new Date(),
  randomValue = Math.random(),
): string {
  const category = categorySlug
    .normalize('NFKD')
    .toUpperCase()
    .replace(/[^A-Z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 24) || 'GENERAL'
  const numericDiameter = Number(diameterMm)
  const diameter = Number.isFinite(numericDiameter) && numericDiameter > 0
    ? numericDiameter
      .toFixed(2)
      .replace(/\.00$/, '')
      .replace(/(\.\d)0$/, '$1')
      .replace('.', 'P')
    : '8'
  const date = [
    now.getFullYear(),
    String(now.getMonth() + 1).padStart(2, '0'),
    String(now.getDate()).padStart(2, '0'),
  ].join('')
  const boundedRandom = Math.min(Math.max(randomValue, 0), 0.999999999)
  const suffix = Math.floor(boundedRandom * 36 ** 4)
    .toString(36)
    .toUpperCase()
    .padStart(4, '0')
  return `BEAD-${category}-${diameter}MM-${date}-${suffix}`
}

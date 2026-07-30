export const DIY_FAVORITE_STORAGE_KEY = 'diy_favorite_bead_ids'

export function normalizeFavoriteBeadIds(value: unknown): number[] {
  const candidate = typeof value === 'string'
    ? parseStoredArray(value)
    : value
  if (!Array.isArray(candidate)) return []
  return Array.from(new Set(candidate.filter((item): item is number => (
    typeof item === 'number'
    && Number.isInteger(item)
    && item > 0
  ))))
}

export function toggleFavoriteBead(ids: number[], skuId: number): number[] {
  if (!Number.isInteger(skuId) || skuId <= 0) return [...ids]
  return ids.includes(skuId)
    ? ids.filter((item) => item !== skuId)
    : [...ids, skuId]
}

export function filterFavoriteCatalog<T extends { id: number }>(
  items: T[],
  favoriteIds: number[],
  favoritesOnly: boolean,
): T[] {
  if (!favoritesOnly) return [...items]
  const favorites = new Set(favoriteIds)
  return items.filter((item) => favorites.has(item.id))
}

function parseStoredArray(value: string): unknown {
  try {
    return JSON.parse(value)
  } catch {
    return []
  }
}

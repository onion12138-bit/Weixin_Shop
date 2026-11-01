/** 持久化当前安装的珠子收藏，读取时去重并忽略损坏的缓存内容。 */
const FAVORITE_BEAD_IDS_KEY = 'diy_favorite_bead_ids'

export function readFavoriteBeadIds(): number[] {
  const raw = wx.getStorageSync(FAVORITE_BEAD_IDS_KEY)
  if (!raw) return []
  try {
    const parsed = JSON.parse(raw) as unknown
    if (!Array.isArray(parsed)) return []
    return Array.from(new Set(
      parsed.map(Number).filter((id) => Number.isInteger(id) && id > 0),
    ))
  } catch {
    return []
  }
}

export function isFavoriteBead(id: number): boolean {
  return readFavoriteBeadIds().includes(id)
}

export function toggleFavoriteBead(id: number): boolean {
  const favorites = readFavoriteBeadIds()
  const nextFavorite = !favorites.includes(id)
  const next = nextFavorite
    ? favorites.concat(id)
    : favorites.filter((item) => item !== id)
  wx.setStorageSync(FAVORITE_BEAD_IDS_KEY, JSON.stringify(next))
  return nextFavorite
}

export function removeFavoriteBead(id: number): void {
  const next = readFavoriteBeadIds().filter((item) => item !== id)
  wx.setStorageSync(FAVORITE_BEAD_IDS_KEY, JSON.stringify(next))
}

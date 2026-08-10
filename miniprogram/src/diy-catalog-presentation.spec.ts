import {
  DIY_FAVORITE_STORAGE_KEY,
  filterFavoriteCatalog,
  normalizeFavoriteBeadIds,
  toggleFavoriteBead,
} from '../miniprogram/domain/diy-catalog-presentation'

describe('DIY catalog presentation', () => {
  it('normalizes storage to unique positive integer sku ids', () => {
    expect(normalizeFavoriteBeadIds([2002, 2001, 2002, -1, 0, 1.5, '2003']))
      .toEqual([2002, 2001])
    expect(normalizeFavoriteBeadIds('{broken')).toEqual([])
    expect(DIY_FAVORITE_STORAGE_KEY).toBe('diy_favorite_bead_ids')
  })

  it('toggles one sku without duplicates', () => {
    expect(toggleFavoriteBead([2001], 2002)).toEqual([2001, 2002])
    expect(toggleFavoriteBead([2001, 2002], 2001)).toEqual([2002])
  })

  it('filters only the current query result when favorites-only is active', () => {
    const result = [{ id: 2001 }, { id: 2002 }, { id: 2003 }]
    expect(filterFavoriteCatalog(result, [2002, 9999], true))
      .toEqual([{ id: 2002 }])
    expect(filterFavoriteCatalog(result, [2002], false)).toEqual(result)
  })
})

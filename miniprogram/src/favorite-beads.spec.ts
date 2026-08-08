describe('favorite bead storage', () => {
  let storage = ''

  beforeEach(() => {
    storage = ''
    ;(globalThis as any).wx = {
      getStorageSync: jest.fn(() => storage),
      setStorageSync: jest.fn((_key: string, value: string) => { storage = value }),
    }
  })

  it('toggles bead ids without duplicates and persists locally', () => {
    jest.isolateModules(() => {
      const favorites = require('../miniprogram/domain/favorite-beads') as {
        toggleFavoriteBead(id: number): boolean
        readFavoriteBeadIds(): number[]
      }
      expect(favorites.toggleFavoriteBead(8)).toBe(true)
      expect(favorites.toggleFavoriteBead(8)).toBe(false)
      expect(favorites.toggleFavoriteBead(12)).toBe(true)
      expect(favorites.readFavoriteBeadIds()).toEqual([12])
    })
  })

  it('ignores malformed stored values', () => {
    storage = '{broken'
    jest.isolateModules(() => {
      const favorites = require('../miniprogram/domain/favorite-beads') as {
        readFavoriteBeadIds(): number[]
      }
      expect(favorites.readFavoriteBeadIds()).toEqual([])
    })
  })
})

declare const __dirname: string
declare function require(name: string): unknown

const { readFileSync } = require('fs') as {
  readFileSync(path: string, encoding: 'utf8'): string
}
const { resolve } = require('path') as {
  resolve(...paths: string[]): string
}

const editorStyles = readFileSync(
  resolve(__dirname, '..', 'miniprogram', 'pages', 'diy', 'editor.wxss'),
  'utf8',
)
const editorMarkup = readFileSync(
  resolve(__dirname, '..', 'miniprogram', 'pages', 'diy', 'editor.wxml'),
  'utf8',
)

describe('DIY editor visual contract', () => {
  it('keeps all selected-bead actions visible but disabled before selection', () => {
    expect(editorMarkup).toContain('<view wx:if="{{!loading}}" class="selected-actions">')
    expect(editorMarkup).toMatch(
      /disabled="{{selectedIndex < 0}}" bindtap="decreaseSelectedSku"/,
    )
    expect(editorMarkup).toMatch(
      /disabled="{{selectedIndex < 0}}" bindtap="increaseSelectedSku"/,
    )
    expect(editorMarkup).toMatch(
      /disabled="{{selectedIndex < 0}}" bindtap="replaceSelected"/,
    )
    expect(editorMarkup).toMatch(
      /disabled="{{selectedIndex < 0}}" bindtap="removeSelected"/,
    )
  })

  it('uses a cover-view overlay so controls remain available above native canvas', () => {
    expect(editorMarkup).toContain(
      '<cover-view hidden="{{loading}}" class="preview-toolbar">',
    )
    expect(editorMarkup).toContain('<cover-view class="preview-toggle"')
    expect(editorStyles).toMatch(
      /\.preview-toolbar\s*\{[^}]*position:\s*absolute;[^}]*z-index:\s*30;/s,
    )
  })

  it('keeps bead, favorite, and quantity actions bound to separate handlers', () => {
    expect(editorMarkup).toMatch(
      /class="bead-add"[^>]*bindtap="addCatalogBead"/,
    )
    expect(editorMarkup).toMatch(
      /class="tray-heart[^\"]*"[^>]*bindtap="toggleFavorite"/,
    )
    expect(editorMarkup).toMatch(
      /class="selected-actions"[\s\S]*bindtap="decreaseSelectedSku"[\s\S]*bindtap="increaseSelectedSku"/,
    )
  })

  it('shows the current favorite state in both filter and bead cards', () => {
    expect(editorMarkup).toContain(
      "{{favoritesOnly ? '♥ 喜欢' : '♡ 喜欢'}}",
    )
    expect(editorMarkup).toContain(
      "{{item.isFavorite ? '♥' : '♡'}}",
    )
  })
})

export {}

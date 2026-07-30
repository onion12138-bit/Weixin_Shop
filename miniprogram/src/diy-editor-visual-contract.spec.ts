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
  it('keeps the selected quantity controls grouped at the right edge', () => {
    expect(editorStyles).toMatch(
      /\.selected-actions\s*\{[^}]*justify-content:\s*flex-end;/s,
    )
    expect(editorStyles).toMatch(
      /\.selected-actions button\s*\{[^}]*margin:\s*0;/s,
    )
  })

  it('keeps expanded preview controls in normal flow above the native canvas', () => {
    expect(editorMarkup).toMatch(
      /<view class="preview-toolbar">[\s\S]*class="preview-toggle"[\s\S]*<\/view>\s*<view class="preview-layout">/,
    )
    expect(editorStyles).toMatch(
      /\.canvas-panel\.is-expanded \.preview-toolbar\s*\{[^}]*position:\s*relative;/s,
    )
    expect(editorStyles).not.toMatch(
      /\.canvas-panel\.is-expanded \.preview-toolbar\s*\{[^}]*position:\s*absolute;/s,
    )
  })
})

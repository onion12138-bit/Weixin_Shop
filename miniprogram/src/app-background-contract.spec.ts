declare const __dirname: string
declare function require(name: string): unknown

const { readFileSync } = require('fs') as {
  readFileSync(path: string, encoding: 'utf8'): string
}
const { resolve } = require('path') as {
  resolve(...paths: string[]): string
}

const root = resolve(__dirname, '..', 'miniprogram')
const blush = '#fff8fa'

function source(path: string): string {
  return readFileSync(resolve(root, path), 'utf8')
}

function rootBackground(path: string, selector: string): string | undefined {
  const match = source(path).match(new RegExp(`${selector.replace('.', '\\.') }\\s*\\{[^}]*background:\\s*([^;]+);`))
  return match?.[1].trim()
}

describe('mini-program blush background contract', () => {
  it('uses the homepage blush for the native window and global page root', () => {
    const app = JSON.parse(source('app.json')) as {
      window: { navigationBarBackgroundColor: string; backgroundColor: string }
    }

    expect(app.window.navigationBarBackgroundColor).toBe(blush)
    expect(app.window.backgroundColor).toBe(blush)
    expect(rootBackground('app.wxss', 'page')).toBe(blush)
  })

  it('uses the homepage blush on every listed page root', () => {
    const roots: Array<[string, string]> = [
      ['pages/recommendation/index.wxss', '.recommendation-page'],
      ['pages/catalog/index.wxss', '.catalog-page'],
      ['pages/catalog/detail.wxss', '.detail-page'],
      ['pages/diy/schemes.wxss', '.schemes-page'],
      ['pages/order/confirm.wxss', '.order-page'],
      ['pages/order/list.wxss', '.orders-page'],
      ['pages/order/detail.wxss', '.detail-page'],
    ]

    for (const [path, selector] of roots) {
      expect(rootBackground(path, selector)).toBe(blush)
    }
  })
})

export {}

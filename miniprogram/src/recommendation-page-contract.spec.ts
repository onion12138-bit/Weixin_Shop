declare const __dirname: string
declare function require(name: string): unknown

const { existsSync, readFileSync } = require('fs') as {
  existsSync(path: string): boolean
  readFileSync(path: string, encoding: 'utf8'): string
}
const { resolve } = require('path') as {
  resolve(...paths: string[]): string
}

const root = resolve(__dirname, '..', 'miniprogram')

function source(path: string): string {
  const target = resolve(root, path)
  return existsSync(target) ? readFileSync(target, 'utf8') : ''
}

describe('recommendation mini-program page contract', () => {
  it('registers the page and ships all four page files plus the guide image', () => {
    const app = JSON.parse(source('app.json')) as { pages: string[] }

    expect(app.pages).toContain('pages/recommendation/index')
    for (const extension of ['ts', 'wxml', 'wxss', 'json']) {
      expect(existsSync(resolve(root, `pages/recommendation/index.${extension}`)))
        .toBe(true)
    }
    expect(existsSync(resolve(root, 'assets/images/ai-water-guide.png')))
      .toBe(true)
  })

  it('adds the home consultation entry with a real tap handler', () => {
    const page = source('pages/home/index.ts')
    const template = source('pages/home/index.wxml')

    expect(page).toContain("url: '/pages/recommendation/index'")
    expect(template).toContain('bindtap="openRecommendation"')
    expect(template).toContain('AI 灵感咨询')
  })

  it('renders date, inputs, fit picker, submission and result handoff controls', () => {
    const template = source('pages/recommendation/index.wxml')

    expect(template).toContain('mode="date"')
    expect(template).toContain('bindchange="changeBirthDate"')
    expect(template).toContain('data-field="manualConstellation"')
    expect(template).toContain('data-field="manualZodiac"')
    expect(template).toContain('type="digit"')
    expect(template).toContain('bindtap="submitRecommendation"')
    expect(template).toContain('wx:if="{{available && !submitting}}"')
    expect(template).toContain('bindtap="enterDiy"')
  })
})

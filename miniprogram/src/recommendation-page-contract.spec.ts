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

  it('wires date, inputs, fit picker, submission and result handoff', () => {
    const page = source('pages/recommendation/index.ts')
    const template = source('pages/recommendation/index.wxml')

    expect(template).toContain('mode="date"')
    expect(template).toContain('bindchange="changeBirthDate"')
    expect(template).toContain('data-field="manualConstellation"')
    expect(template).toContain('data-field="manualZodiac"')
    expect(template).toContain('type="digit"')
    expect(template).toContain('bindtap="submitRecommendation"')
    expect(template).toContain('wx:if="{{available && !submitting}}"')
    expect(template).toContain('bindtap="enterDiy"')
    expect(page).toContain('PENDING_AI_RECOMMENDATION_KEY')
    expect(page).not.toMatch(/setStorageSync[\s\S]{0,120}(birthDate|freeText)/)
  })

  it('consumes AI handoff before legacy draft recovery only for source=ai', () => {
    const editor = source('pages/diy/editor.ts')
    const aiBranch = editor.indexOf("options.source === 'ai'")
    const legacyCache = editor.indexOf('const cached = readDraftCache()')
    const immediateRemoval = editor.indexOf(
      'wx.removeStorageSync(PENDING_AI_RECOMMENDATION_KEY)',
    )
    const bootstrap = editor.indexOf('void this.bootstrap(options)')

    expect(editor).toContain('consumePendingAiRecommendation')
    expect(aiBranch).toBeGreaterThan(-1)
    expect(legacyCache).toBeGreaterThan(aiBranch)
    expect(immediateRemoval).toBeGreaterThan(-1)
    expect(immediateRemoval).toBeLessThan(bootstrap)
    expect(editor).toContain("options.source === 'ai'\n        ? listAllPublicBeads()")
    expect(editor).toContain('listBeads({ page: 1, size: 30 })')
  })
})

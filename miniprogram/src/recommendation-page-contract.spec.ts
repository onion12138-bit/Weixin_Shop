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
  it('registers the page and ships all four page files plus wish assets', () => {
    const app = JSON.parse(source('app.json')) as { pages: string[] }

    expect(app.pages).toContain('pages/recommendation/index')
    for (const extension of ['ts', 'wxml', 'wxss', 'json']) {
      expect(existsSync(resolve(root, `pages/recommendation/index.${extension}`)))
        .toBe(true)
    }
    expect(existsSync(resolve(root, 'assets/images/wish-inspiration-hero.png')))
      .toBe(true)
    for (const image of [
      'wealth.png', 'romance.png', 'career.png', 'peace.png',
      'study.png', 'companionship.png', 'family.png', 'vitality.png',
    ]) {
      expect(existsSync(resolve(root, `assets/images/wishes/${image}`))).toBe(true)
    }
  })

  it('adds the home consultation entry with a real tap handler', () => {
    const page = source('pages/home/index.ts')
    const template = source('pages/home/index.wxml')

    expect(page).toContain("url: '/pages/recommendation/index'")
    expect(template).toContain('bindtap="openRecommendation"')
    expect(template).toContain('AI 灵感咨询')
  })

  it('aligns wish inspiration with products and presents it as the DIY entry', () => {
    const template = source('pages/home/index.wxml')
    const styles = source('pages/home/index.wxss')

    expect(template).toContain('class="mood-section page-section"')
    expect(template).toContain('选心愿，生成专属 DIY')
    expect(template).toContain('AI × DIY')
    expect(styles).toMatch(/\.mood-card\s*\{[^}]*width: 160rpx;/s)
    expect(styles).toMatch(/\.product-card\s*\{[^}]*width: 160rpx;/s)
    expect(styles).toMatch(/\.mood-grid\s*\{[^}]*gap: 18rpx;/s)
    expect(styles).toContain('.mood-header {\n  padding: 0 2rpx 12rpx;')
    expect(styles).toContain('.product-section .section-header,')
  })

  it('renders the birthday profile, optional wish inspiration and dynamic plan controls', () => {
    const template = source('pages/recommendation/index.wxml')

    expect(template).toContain('mode="date"')
    expect(template).toContain('bindchange="changeBirthDate"')
    expect(template).toContain('{{profile.constellation}}')
    expect(template).toContain('{{profile.zodiac}}')
    expect(template).toContain('{{profile.constellationElement}}')
    expect(template).toContain('bindinput="updateWish"')
    expect(template).toContain('心愿灵感（选填）')
    expect(template).toContain('wx:for="{{wishInspirationOptions}}"')
    expect(template).toContain('bindtap="selectWishInspiration"')
    expect(template).toContain('/assets/images/wish-inspiration-hero.png')
    expect(template).toContain('bindtap="submitRecommendation"')
    expect(template).toContain('wx:if="{{available && !submitting}}"')
    expect(template).toContain('wx:for="{{result.plans}}"')
    expect(template).toContain('data-index="{{planIndex}}"')
    expect(template).toContain('bindtap="enterDiy"')
    expect(template).not.toContain('预算（元）')
    expect(template).not.toContain('手围（毫米）')
    expect(template).not.toContain('松紧偏好')
    expect(template).not.toContain('服务端总价')
  })
})

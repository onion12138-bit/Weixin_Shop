declare function require(name: string): unknown
export {}

const { readFileSync } = require('fs') as {
  readFileSync(path: string, encoding: string): string
}

jest.mock('../miniprogram/services/diy', () => ({
  deleteScheme: jest.fn(),
  listSchemes: jest.fn(),
}))

function loadSchemesPage() {
  let definition: Record<string, any> | null = null
  const wxMock = {
    getStorageSync: jest.fn(() => ''),
    navigateTo: jest.fn(),
    showToast: jest.fn(),
  }
  ;(globalThis as any).wx = wxMock
  ;(globalThis as any).Page = (options: Record<string, any>) => {
    definition = options
  }

  jest.isolateModules(() => {
    require('../miniprogram/pages/diy/schemes')
  })
  if (!definition) throw new Error('Schemes Page definition was not captured')
  const captured = definition as Record<string, any>
  const page: Record<string, any> = {
    ...captured,
    data: { ...captured.data },
  }
  page.setData = jest.fn((patch: Record<string, unknown>) => {
    Object.assign(page.data, patch)
  })
  return { page, wxMock }
}

describe('DIY schemes page behavior', () => {
  it('keeps the full bracelet visible inside the fixed-size preview area', () => {
    const template = readFileSync('miniprogram/pages/diy/schemes.wxml', 'utf8')

    expect(template).toContain('class="scheme-preview"')
    expect(template).toContain('mode="aspectFit"')
  })

  it('uses a toast instead of opening a fourth DIY design', () => {
    const { page, wxMock } = loadSchemesPage()
    page.data.schemes = [{ id: 1 }, { id: 2 }, { id: 3 }]

    page.createScheme()

    expect(wxMock.showToast).toHaveBeenCalledWith({
      title: '最多保存 3 个 DIY 方案',
      icon: 'none',
    })
    expect(wxMock.navigateTo).not.toHaveBeenCalled()
  })
})

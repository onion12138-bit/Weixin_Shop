import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  downloadBeadImportTemplate,
  importAdminBeads,
} from './catalog'
import { request, requestBlob } from './http'

vi.mock('./http', () => ({
  request: vi.fn(),
  requestBlob: vi.fn(),
}))

describe('catalog Excel import API contract', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(request).mockResolvedValue(undefined)
    vi.mocked(requestBlob).mockResolvedValue(new Blob(['template']))
  })

  it('downloads the authenticated xlsx template as a blob', async () => {
    await downloadBeadImportTemplate()

    expect(requestBlob).toHaveBeenCalledWith(
      '/admin/catalog/beads/import-template',
    )
  })

  it('uploads the selected xlsx as multipart form data', async () => {
    const file = new File(['xlsx'], 'catalog.xlsx', {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    })

    await importAdminBeads(file)

    expect(request).toHaveBeenCalledOnce()
    const [path, init] = vi.mocked(request).mock.calls[0]
    expect(path).toBe('/admin/catalog/beads/import')
    expect(init?.method).toBe('POST')
    expect(init?.body).toBeInstanceOf(FormData)
    expect((init?.body as FormData).get('file')).toBe(file)
  })
})

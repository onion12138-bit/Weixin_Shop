import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  createManualBeadVisual,
  downloadBeadImportTemplate,
  importAdminBeads,
  listBeadVisuals,
  publishBeadVisual,
  rollbackBeadVisual,
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

describe('bead visual API contract', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(request).mockResolvedValue(undefined)
  })

  it('uses the admin visual endpoints for manual creation, history, publish and rollback', async () => {
    await createManualBeadVisual(2001, {
      trayAssetId: 901,
      canvasAssetId: 902,
    })
    await listBeadVisuals(2001)
    await publishBeadVisual(2001, 31)
    await rollbackBeadVisual(2001, 17)

    expect(request).toHaveBeenNthCalledWith(
      1,
      '/admin/catalog/beads/2001/visuals/manual',
      {
        method: 'POST',
        body: JSON.stringify({ trayAssetId: 901, canvasAssetId: 902 }),
      },
    )
    expect(request).toHaveBeenNthCalledWith(
      2,
      '/admin/catalog/beads/2001/visuals',
    )
    expect(request).toHaveBeenNthCalledWith(
      3,
      '/admin/catalog/beads/2001/visuals/31/publish',
      { method: 'POST' },
    )
    expect(request).toHaveBeenNthCalledWith(
      4,
      '/admin/catalog/beads/2001/visuals/17/rollback',
      { method: 'POST' },
    )
  })
})

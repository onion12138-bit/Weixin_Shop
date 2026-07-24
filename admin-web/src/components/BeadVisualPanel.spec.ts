import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/http'
import BeadVisualPanel from './BeadVisualPanel.vue'
import {
  createManualBeadVisual,
  listBeadVisuals,
  publishBeadVisual,
  rollbackBeadVisual,
  uploadCatalogFile,
} from '../api/catalog'

vi.mock('../api/catalog', () => ({
  createManualBeadVisual: vi.fn(),
  listBeadVisuals: vi.fn(),
  publishBeadVisual: vi.fn(),
  rollbackBeadVisual: vi.fn(),
  uploadCatalogFile: vi.fn(),
}))

const readyVersion = {
  id: 31,
  version: 2,
  sourceType: 'MANUAL' as const,
  status: 'READY' as const,
  trayAssetId: 901,
  trayUrl: '/api/public/files/tray.png',
  canvasAssetId: 902,
  canvasUrl: '/api/public/files/canvas.png',
  failureCode: null,
  failureMessage: null,
  createdBy: 'admin',
  publishedBy: null,
  publishedAt: null,
  createdAt: '2026-07-25T09:30:00',
}

async function setFile(
  wrapper: ReturnType<typeof mount>,
  selector: string,
  file: File,
): Promise<void> {
  const input = wrapper.get<HTMLInputElement>(selector)
  Object.defineProperty(input.element, 'files', {
    configurable: true,
    value: [file],
  })
  await input.trigger('change')
}

function deferred<T>(): {
  promise: Promise<T>
  resolve: (value: T) => void
} {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((resolvePromise) => {
    resolve = resolvePromise
  })
  return { promise, resolve }
}

describe('BeadVisualPanel', () => {
  const trayPng = new File(['tray'], 'tray.png', { type: 'image/png' })
  const canvasPng = new File(['canvas'], 'canvas.webp', { type: 'image/webp' })

  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(listBeadVisuals).mockResolvedValue({
      publishedVisualVersionId: null,
      versions: [],
    })
    vi.mocked(uploadCatalogFile)
      .mockResolvedValueOnce({
        id: 901,
        storageKey: 'tray.png',
        originalName: 'tray.png',
        contentType: 'image/png',
        sizeBytes: 4,
        publicUrl: '/api/public/files/tray.png',
      })
      .mockResolvedValueOnce({
        id: 902,
        storageKey: 'canvas.webp',
        originalName: 'canvas.webp',
        contentType: 'image/webp',
        sizeBytes: 6,
        publicUrl: '/api/public/files/canvas.webp',
      })
    vi.mocked(createManualBeadVisual).mockResolvedValue(readyVersion)
    vi.mocked(publishBeadVisual).mockResolvedValue({
      ...readyVersion,
      status: 'PUBLISHED',
    })
    vi.mocked(rollbackBeadVisual).mockResolvedValue({
      ...readyVersion,
      id: 32,
      version: 3,
      status: 'PUBLISHED',
    })
  })

  it('uploads two transparent assets and creates a ready manual version', async () => {
    const wrapper = mount(BeadVisualPanel, {
      props: { beadId: 2001, beadName: '紫水晶' },
    })
    await flushPromises()

    await setFile(wrapper, '[data-test="tray-file"]', trayPng)
    await setFile(wrapper, '[data-test="canvas-file"]', canvasPng)
    await wrapper.get('[data-test="create-manual-visual"]').trigger('click')
    await flushPromises()

    expect(createManualBeadVisual).toHaveBeenCalledWith(2001, {
      trayAssetId: 901,
      canvasAssetId: 902,
    })
    expect(wrapper.text()).toContain('已创建手工视觉版本')
  })

  it('only enables publish for a ready version and emits published after publishing', async () => {
    vi.mocked(listBeadVisuals).mockResolvedValue({
      publishedVisualVersionId: null,
      versions: [{ ...readyVersion, id: 30, status: 'DRAFT' }, readyVersion],
    })
    const wrapper = mount(BeadVisualPanel, {
      props: { beadId: 2001, beadName: '紫水晶' },
    })
    await flushPromises()

    expect(wrapper.get('[data-test="publish-visual-30"]').attributes('disabled'))
      .toBeDefined()
    await wrapper.get('[data-test="publish-visual-31"]').trigger('click')
    await flushPromises()

    expect(publishBeadVisual).toHaveBeenCalledWith(2001, 31)
    expect(wrapper.emitted('published')).toHaveLength(1)
  })

  it('keeps a newer refresh when an older history request resolves last', async () => {
    const initialHistory = deferred<Awaited<ReturnType<typeof listBeadVisuals>>>()
    const refreshedHistory = deferred<Awaited<ReturnType<typeof listBeadVisuals>>>()
    vi.mocked(listBeadVisuals)
      .mockReturnValueOnce(initialHistory.promise)
      .mockReturnValueOnce(refreshedHistory.promise)
    const wrapper = mount(BeadVisualPanel, {
      props: { beadId: 2001, beadName: '紫水晶' },
    })
    await flushPromises()

    await setFile(wrapper, '[data-test="tray-file"]', trayPng)
    await setFile(wrapper, '[data-test="canvas-file"]', canvasPng)
    await wrapper.get('[data-test="create-manual-visual"]').trigger('click')
    await flushPromises()

    refreshedHistory.resolve({
      publishedVisualVersionId: null,
      versions: [{ ...readyVersion, id: 42, version: 4 }],
    })
    await flushPromises()
    initialHistory.resolve({
      publishedVisualVersionId: null,
      versions: [{ ...readyVersion, id: 41, version: 3, status: 'DRAFT' }],
    })
    await flushPromises()

    expect(wrapper.text()).toContain('V4')
    expect(wrapper.text()).not.toContain('V3')
  })

  it('only offers rollback for archived historical versions', async () => {
    vi.mocked(listBeadVisuals).mockResolvedValue({
      publishedVisualVersionId: 31,
      versions: [
        { ...readyVersion, status: 'PUBLISHED' },
        { ...readyVersion, id: 17, version: 1, status: 'ARCHIVED' },
      ],
    })
    const wrapper = mount(BeadVisualPanel, {
      props: { beadId: 2001, beadName: '紫水晶' },
    })
    await flushPromises()

    expect(wrapper.get('[data-test="rollback-visual-31"]').attributes('disabled'))
      .toBeDefined()
    expect(wrapper.get('[data-test="rollback-visual-17"]').attributes('disabled'))
      .toBeUndefined()
  })

  it('blocks a second mutation while manual creation is unresolved', async () => {
    const pendingUploads = deferred<{
      id: number
      storageKey: string
      originalName: string
      contentType: string
      sizeBytes: number
      publicUrl: string
    }>()
    vi.mocked(listBeadVisuals).mockResolvedValue({
      publishedVisualVersionId: null,
      versions: [readyVersion],
    })
    vi.mocked(uploadCatalogFile).mockReset()
    vi.mocked(uploadCatalogFile).mockImplementation(() => pendingUploads.promise)
    const wrapper = mount(BeadVisualPanel, {
      props: { beadId: 2001, beadName: '紫水晶' },
    })
    await flushPromises()

    await setFile(wrapper, '[data-test="tray-file"]', trayPng)
    await setFile(wrapper, '[data-test="canvas-file"]', canvasPng)
    await wrapper.get('[data-test="create-manual-visual"]').trigger('click')
    await flushPromises()

    const publishButton = wrapper.get('[data-test="publish-visual-31"]')
    expect(publishButton.attributes('disabled')).toBeDefined()
    await publishButton.trigger('click')
    await flushPromises()

    expect(publishBeadVisual).not.toHaveBeenCalled()
  })

  it('keeps selected files and exposes the API trace ID when creation fails', async () => {
    vi.mocked(createManualBeadVisual).mockRejectedValue(
      new ApiError('素材不透明', 'CATALOG_VISUAL_INVALID', 422, 'trace-visual-9'),
    )
    const wrapper = mount(BeadVisualPanel, {
      props: { beadId: 2001, beadName: '紫水晶' },
    })
    await flushPromises()

    await setFile(wrapper, '[data-test="tray-file"]', trayPng)
    await setFile(wrapper, '[data-test="canvas-file"]', canvasPng)
    await wrapper.get('[data-test="create-manual-visual"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('素材不透明（追踪号：trace-visual-9）')
    expect(wrapper.text()).toContain('tray.png')
    expect(wrapper.text()).toContain('canvas.webp')
  })

  it('confirms rollback and sends the selected historical version ID', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    vi.mocked(listBeadVisuals).mockResolvedValue({
      publishedVisualVersionId: 31,
      versions: [{ ...readyVersion, id: 17, status: 'ARCHIVED' }],
    })
    const wrapper = mount(BeadVisualPanel, {
      props: { beadId: 2001, beadName: '紫水晶' },
    })
    await flushPromises()

    await wrapper.get('[data-test="rollback-visual-17"]').trigger('click')
    await flushPromises()

    expect(window.confirm).toHaveBeenCalled()
    expect(rollbackBeadVisual).toHaveBeenCalledWith(2001, 17)
  })
})

#!/usr/bin/env node

import { readFile } from 'node:fs/promises'
import { basename, dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const scriptDirectory = dirname(fileURLToPath(import.meta.url))
const repositoryRoot = resolve(scriptDirectory, '..')
const manifestPath = resolve(
  repositoryRoot,
  process.argv.find((argument) => argument.endsWith('.json'))
    || 'assets/imported-beads-20260809/manifest.json',
)
const onlySlug = process.argv
  .find((argument) => argument.startsWith('--only='))
  ?.slice('--only='.length)
const apiBaseUrl = (process.env.ADMIN_API_BASE_URL || 'http://127.0.0.1:8081/api')
  .replace(/\/$/, '')
const adminUsername = process.env.ADMIN_USERNAME || 'admin'
const adminPassword = process.env.ADMIN_PASSWORD || 'Admin@123'

let accessToken = ''

async function request(path, options = {}) {
  const headers = new Headers(options.headers)
  if (accessToken) {
    headers.set('Authorization', `Bearer ${accessToken}`)
  }
  if (options.body && !(options.body instanceof FormData)) {
    headers.set('Content-Type', 'application/json')
  }

  const response = await fetch(`${apiBaseUrl}${path}`, {
    ...options,
    headers,
  })
  const envelope = await response.json()
  if (!response.ok || envelope.code !== 'OK') {
    const trace = envelope.traceId ? `，traceId=${envelope.traceId}` : ''
    throw new Error(`${envelope.message || `HTTP ${response.status}`}${trace}`)
  }
  return envelope.data
}

async function login() {
  const result = await request('/admin/auth/login', {
    method: 'POST',
    body: JSON.stringify({
      username: adminUsername,
      password: adminPassword,
    }),
  })
  accessToken = result.accessToken
}

async function uploadPng(relativePath) {
  const absolutePath = resolve(repositoryRoot, relativePath)
  const bytes = await readFile(absolutePath)
  const form = new FormData()
  form.append('file', new Blob([bytes], { type: 'image/png' }), basename(absolutePath))
  return request('/admin/files', {
    method: 'POST',
    body: form,
  })
}

async function listAllBeads() {
  const beads = []
  let page = 1
  while (true) {
    const result = await request(`/admin/catalog/beads?page=${page}&size=100`)
    beads.push(...result.items)
    if (beads.length >= result.total) return beads
    page += 1
  }
}

async function main() {
  const manifest = JSON.parse(await readFile(manifestPath, 'utf8'))
  const beadsToImport = onlySlug
    ? manifest.beads.filter((bead) => bead.slug === onlySlug)
    : manifest.beads
  if (beadsToImport.length === 0) {
    throw new Error(`导入清单中不存在 slug=${onlySlug}`)
  }
  await login()

  const [categories, existingBeads] = await Promise.all([
    request('/admin/catalog/categories'),
    listAllBeads(),
  ])
  const categoryIds = new Map(
    categories
      .filter((category) => category.productType === 'BEAD')
      .map((category) => [category.name, category.id]),
  )
  const existingBySku = new Map(existingBeads.map((bead) => [bead.skuCode, bead]))
  const imported = []

  for (const bead of beadsToImport) {
    const categoryId = categoryIds.get(bead.categoryName)
    if (!categoryId) {
      throw new Error(`找不到散珠分类：${bead.categoryName}`)
    }

    const assetDirectory = `assets/imported-beads-20260809/${bead.slug}`
    const [coverAsset, trayAsset, canvasAsset] = await Promise.all([
      uploadPng(`${assetDirectory}/cover.png`),
      uploadPng(`${assetDirectory}/tray.png`),
      uploadPng(`${assetDirectory}/canvas.png`),
    ])
    const payload = {
      categoryId,
      skuCode: bead.skuCode,
      name: bead.name,
      material: bead.material,
      color: bead.color,
      diameterMm: bead.diameterMm,
      salePrice: bead.salePrice,
      costPrice: bead.costPrice,
      stockAlertThreshold: bead.stockAlertThreshold,
      coverAssetId: coverAsset.id,
      tags: bead.tags,
      status: 'DRAFT',
    }

    const existing = existingBySku.get(bead.skuCode)
    const savedBead = existing
      ? await request(`/admin/catalog/beads/${existing.id}`, {
          method: 'PUT',
          body: JSON.stringify(payload),
        })
      : await request('/admin/catalog/beads', {
          method: 'POST',
          body: JSON.stringify({
            ...payload,
            initialStock: 0,
          }),
        })

    const visual = await request(`/admin/catalog/beads/${savedBead.id}/visuals/manual`, {
      method: 'POST',
      body: JSON.stringify({
        trayAssetId: trayAsset.id,
        canvasAssetId: canvasAsset.id,
      }),
    })
    const published = await request(
      `/admin/catalog/beads/${savedBead.id}/visuals/${visual.id}/publish`,
      { method: 'POST' },
    )

    imported.push({
      id: savedBead.id,
      skuCode: savedBead.skuCode,
      name: savedBead.name,
      material: savedBead.material,
      status: savedBead.status,
      coverAssetId: savedBead.coverAssetId,
      publishedVisualVersionId: published.id,
    })
    console.log(`已导入 ${imported.length}/${beadsToImport.length}：${savedBead.name}`)
  }

  const allBeads = await listAllBeads()
  const verification = new Map(
    allBeads
      .filter((bead) => imported.some((item) => item.skuCode === bead.skuCode))
      .map((bead) => [bead.skuCode, bead]),
  )
  const missing = imported.filter((item) => !verification.has(item.skuCode))
  const invalid = imported.filter((item) => {
    const bead = verification.get(item.skuCode)
    return !bead || bead.material !== item.material || bead.status !== 'DRAFT'
      || !bead.coverAssetId || !bead.coverUrl
  })
  if (missing.length || invalid.length) {
    throw new Error(`导入校验失败：missing=${missing.length}, invalid=${invalid.length}`)
  }

  console.log(JSON.stringify({
    importedCount: imported.length,
    skuCodes: imported.map((item) => item.skuCode),
    visualVersionIds: imported.map((item) => item.publishedVisualVersionId),
  }, null, 2))
}

main().catch((error) => {
  console.error(`导入失败：${error.message}`)
  process.exitCode = 1
})

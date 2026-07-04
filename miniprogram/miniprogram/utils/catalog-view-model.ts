export interface PublicBead {
  id: number
  skuCode: string
  name: string
  material: string
  color: string
  diameterMm: number
  salePrice: number
  coverUrl: string | null
  tags: string[]
}

export interface BeadCard {
  id: number
  title: string
  specification: string
  priceText: string
  imageUrl: string
  tags: string[]
}

export interface PublicPackage {
  id: number
  packageCode: string
  name: string
  description: string
  salePrice: number
  coverUrl: string | null
}

export interface PackageCard {
  id: number
  title: string
  description: string
  priceText: string
  imageUrl: string
}

const PLACEHOLDER_IMAGE = '/assets/images/bead-placeholder.svg'

/**
 * 把公开散珠 DTO 转换为页面可直接渲染的卡片。
 */
export function toBeadCard(bead: PublicBead): BeadCard {
  return {
    id: bead.id,
    title: bead.name,
    specification: `${bead.material} · ${bead.color} · ${formatNumber(bead.diameterMm)}mm`,
    priceText: `¥${Number(bead.salePrice).toFixed(2)} / 颗`,
    imageUrl: bead.coverUrl || PLACEHOLDER_IMAGE,
    tags: bead.tags,
  }
}

/**
 * 把公开成品套餐转换为首页卡片。
 */
export function toPackageCard(item: PublicPackage): PackageCard {
  return {
    id: item.id,
    title: item.name,
    description: item.description,
    priceText: `¥${Number(item.salePrice).toFixed(2)}`,
    imageUrl: item.coverUrl || PLACEHOLDER_IMAGE,
  }
}

function formatNumber(value: number): string {
  return Number.isInteger(Number(value))
    ? String(Number(value))
    : String(Number(value)).replace(/0+$/, '').replace(/\.$/, '')
}

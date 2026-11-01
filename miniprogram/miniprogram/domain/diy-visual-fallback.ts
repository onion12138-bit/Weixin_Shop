/** 统一选择珠子素材及缺图占位，保证列表与画布在素材缺失时仍可展示。 */
import type { PublicBead } from '../utils/catalog-view-model'

type VisualBead = Pick<PublicBead, 'coverUrl' | 'diyVisual'>

export function resolveTrayUrl(bead: VisualBead): string {
  return bead.diyVisual?.trayUrl || bead.coverUrl || ''
}

export function resolveCanvasUrl(bead: VisualBead): string {
  return bead.diyVisual?.canvasUrl || bead.coverUrl || ''
}

import type { PublicBead } from '../utils/catalog-view-model'

type VisualBead = Pick<PublicBead, 'coverUrl' | 'diyVisual'>

export function resolveTrayUrl(bead: VisualBead): string {
  return bead.diyVisual?.trayUrl || bead.coverUrl || ''
}

export function resolveCanvasUrl(bead: VisualBead): string {
  return bead.diyVisual?.canvasUrl || bead.coverUrl || ''
}

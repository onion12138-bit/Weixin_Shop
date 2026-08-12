export function formatDateTime(value: string | null | undefined): string {
  if (!value) return '—'
  const normalized = value.replace('T', ' ')
  return normalized.length >= 19 ? normalized.slice(0, 19) : normalized
}

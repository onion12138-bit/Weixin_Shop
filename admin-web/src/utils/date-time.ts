// 保持后端返回的日期时间文本，仅调整展示分隔符，不隐式转换时区。
export function formatDateTime(value: string | null | undefined): string {
  if (!value) return '—'
  const normalized = value.replace('T', ' ')
  return normalized.length >= 19 ? normalized.slice(0, 19) : normalized
}

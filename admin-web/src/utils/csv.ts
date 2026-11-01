/** 引号仅解决 CSV 分隔；危险前缀还需按文本处理，防止表格软件执行用户输入的公式。 */
export function csvCell(value: unknown): string {
  const text = String(value ?? '')
  const escaped = /^[\t\r\n]|^[\s\u0000-\u001f]*[=+\-@＝＋－＠]/u.test(text) ? `'${text}` : text
  return `"${escaped.replaceAll('"', '""')}"`
}

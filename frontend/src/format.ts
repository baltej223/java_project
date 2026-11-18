export function formatDate(value?: string): string {
  if (!value) {
    return '—'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return date.toLocaleString()
}

export function shortId(value: string): string {
  return value.length > 12 ? `${value.slice(0, 8)}…` : value
}

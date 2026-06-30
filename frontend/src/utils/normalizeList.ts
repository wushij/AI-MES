export function normalizeList<T = unknown>(value: unknown): T[] {
  const payload = (value as { data?: unknown })?.data ?? value
  if (Array.isArray(payload)) return payload as T[]
  if (payload && typeof payload === 'object') {
    const record = payload as { records?: unknown; list?: unknown; items?: unknown }
    if (Array.isArray(record.records)) return record.records as T[]
    if (Array.isArray(record.list)) return record.list as T[]
    if (Array.isArray(record.items)) return record.items as T[]
  }
  return []
}

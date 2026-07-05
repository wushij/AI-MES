export function formatDurationMinutes(minutes?: number | null) {
  if (minutes == null || minutes <= 0) return '0分'
  const hours = Math.floor(minutes / 60)
  const remain = minutes % 60
  if (hours > 0 && remain > 0) return `${hours}小时${remain}分`
  if (hours > 0) return `${hours}小时`
  return `${remain}分`
}

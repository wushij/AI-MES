export function toCaptchaDataUrl(img?: string): string {
  if (!img) return ''
  if (img.startsWith('data:')) return img
  return `data:image/png;base64,${img}`
}

import { ref } from 'vue'

const ICON_PATH = '/theme-brand-icon-transparent.svg'
export const BRAND_ICON_PATH = ICON_PATH
const STORAGE_KEY = 'ai_mes_brand_icon_v1'

function readStored(): string {
  try {
    const cached = localStorage.getItem(STORAGE_KEY)
    if (cached?.startsWith('data:')) return cached
  } catch {
    /* ignore */
  }
  return ''
}

function blobToDataUrl(blob: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result))
    reader.onerror = () => reject(reader.error)
    reader.readAsDataURL(blob)
  })
}

export const brandIconSrc = ref(readStored() || ICON_PATH)

let warming: Promise<string> | null = null

export function applyFavicon(href: string = brandIconSrc.value) {
  if (typeof document === 'undefined') return
  let link = document.querySelector<HTMLLinkElement>('link[rel="icon"]')
  if (!link) {
    link = document.createElement('link')
    link.rel = 'icon'
    document.head.appendChild(link)
  }
  link.type = 'image/svg+xml'
  link.href = href
}

export function warmBrandIconCache(): Promise<string> {
  if (brandIconSrc.value.startsWith('data:')) {
    return Promise.resolve(brandIconSrc.value)
  }
  if (warming) return warming

  warming = fetch(ICON_PATH)
    .then((res) => {
      if (!res.ok) throw new Error('brand icon fetch failed')
      return res.blob()
    })
    .then(blobToDataUrl)
    .then((dataUrl) => {
      brandIconSrc.value = dataUrl
      applyFavicon(dataUrl)
      try {
        localStorage.setItem(STORAGE_KEY, dataUrl)
      } catch {
        /* quota exceeded */
      }
      return dataUrl
    })
    .catch(() => brandIconSrc.value)
    .finally(() => {
      warming = null
    })

  return warming
}

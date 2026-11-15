const eurFmt = new Intl.NumberFormat('it-IT', { style: 'currency', currency: 'EUR' })
const numFmt = new Intl.NumberFormat('it-IT', { maximumFractionDigits: 3 })

export const eur = (n: number) => eurFmt.format(n)
export const num = (n: number) => numFmt.format(n)
/** Quantità con la sua unità di misura (l'etichetta breve arriva già dal server: kg, pz, mazzo...). */
export const qty = (n: number, unit: string) => `${numFmt.format(n)} ${unit}`.trim()

export const dateIt = (iso: string) =>
  new Date(iso.length === 10 ? iso + 'T00:00:00' : iso).toLocaleDateString('it-IT', {
    day: '2-digit',
    month: 'short',
  })

export const dateTimeIt = (iso: string) =>
  new Date(iso).toLocaleString('it-IT', {
    day: '2-digit',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit',
  })

/** Converte un testo digitato ("1,5" oppure "1.5") in numero; NaN se non valido. */
export const parseDecimal = (s: string): number => {
  const t = s.trim().replace(',', '.')
  return t === '' ? NaN : Number(t)
}

const pad = (n: number) => String(n).padStart(2, '0')

/** Data locale in formato AAAA-MM-GG (non usa il fuso UTC, che a tarda sera darebbe il giorno dopo). */
export const toIsoDate = (d: Date) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
export const todayIso = () => toIsoDate(new Date())

export const tomorrowIso = () => {
  const d = new Date()
  d.setDate(d.getDate() + 1)
  return toIsoDate(d)
}

export function expiryLabel(days: number | null): string {
  if (days === null) return ''
  if (days < 0) return `scaduto da ${-days} g`
  if (days === 0) return 'scade oggi'
  if (days === 1) return 'scade domani'
  return `scade tra ${days} g`
}

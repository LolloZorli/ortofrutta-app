const eurFmt = new Intl.NumberFormat('it-IT', { style: 'currency', currency: 'EUR' })
const numFmt = new Intl.NumberFormat('it-IT', { maximumFractionDigits: 3 })

/** Arrotonda ai centesimi come BigDecimal con RoundingMode.HALF_UP nel backend. */
export function roundMoney(value: number): number {
  if (!Number.isFinite(value)) return value
  const negative = value < 0
  const [coefficient, exponentText] = Math.abs(value).toString().toLowerCase().split('e')
  const exponent = Number(exponentText ?? 0)
  const [whole, fraction = ''] = coefficient.split('.')
  const digits = BigInt(whole + fraction)
  const decimalPlaces = fraction.length - exponent
  const centPlaces = decimalPlaces - 2
  let cents: bigint

  if (centPlaces <= 0) {
    cents = digits * 10n ** BigInt(-centPlaces)
  } else {
    const divisor = 10n ** BigInt(centPlaces)
    cents = digits / divisor
    if ((digits % divisor) * 2n >= divisor) cents += 1n
  }

  if (cents === 0n) return 0
  const rounded = Number(cents) / 100
  return negative ? -rounded : rounded
}

export const eur = (n: number) => eurFmt.format(roundMoney(n))
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
  if (t === '') return NaN
  const value = Number(t)
  return Number.isFinite(value) ? value : NaN
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

import type { Product } from '../types'

interface Props {
  products: Product[]
  value: string
  onChange: (value: string) => void
}

export default function ProductSelect({ products, value, onChange }: Props) {
  return (
    <select value={value} onChange={(e) => onChange(e.target.value)} aria-label="Prodotto">
      <option value="">Prodotto…</option>
      {products.map((p) => (
        <option key={p.id} value={p.id}>
          {p.name} ({p.unit})
        </option>
      ))}
    </select>
  )
}

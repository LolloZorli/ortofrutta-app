import { useState, type FormEvent } from 'react'
import { api, errorMessage } from '../api'
import Modal from '../components/Modal'
import { Chip, Empty, ErrorBox, Field, Loading } from '../components/ui'
import { eur, parseDecimal } from '../format'
import { useLoad } from '../hooks'
import { optionsWithCurrent, useLookups } from '../lookups'
import { useToast } from '../toast'
import type { Product } from '../types'

export default function ProductsPage() {
  const toast = useToast()
  const [showInactive, setShowInactive] = useState(false)
  const [search, setSearch] = useState('')
  const [editing, setEditing] = useState<Product | 'new' | null>(null)
  const { data, loading, error, reload } = useLoad<Product[]>(
    () => api.get(`/products?includeInactive=${showInactive}`),
    [showInactive],
  )

  if (loading && !data) return <Loading />
  if (error || !data) return <ErrorBox message={error ?? 'Nessun dato'} onRetry={reload} />

  const q = search.trim().toLowerCase()
  const rows = data.filter((p) => !q || p.name.toLowerCase().includes(q) || p.category.toLowerCase().includes(q))

  async function toggleActive(p: Product) {
    try {
      await api.put(`/products/${p.id}`, { ...toRequest(p), active: !p.active })
      toast.ok(p.active ? 'Prodotto disattivato' : 'Prodotto riattivato')
      reload()
    } catch (e) {
      toast.error(errorMessage(e))
    }
  }

  return (
    <div className="stack">
      <div className="row">
        <input className="grow" placeholder="Cerca prodotto…" value={search} onChange={(e) => setSearch(e.target.value)} />
        <button className="btn btn-primary" onClick={() => setEditing('new')}>
          + Nuovo
        </button>
      </div>
      <label className="row muted">
        <input
          type="checkbox"
          style={{ width: 20, minHeight: 20 }}
          checked={showInactive}
          onChange={(e) => setShowInactive(e.target.checked)}
        />
        Mostra anche i disattivati
      </label>

      {rows.length === 0 ? (
        <Empty>Nessun prodotto</Empty>
      ) : (
        <div className="list">
          {rows.map((p) => (
            <div key={p.id} className="card">
              <div className="row between">
                <div>
                  <div className="item-title">{p.name}</div>
                  <div className="item-sub">
                    {p.category}
                    {p.origin ? ` · ${p.origin}` : ''}
                    {p.vatRate !== null ? ` · IVA ${p.vatRate}%` : ''}
                  </div>
                </div>
                <div style={{ textAlign: 'right' }}>
                  <strong>
                    {eur(p.salePrice)}/{p.unit}
                  </strong>
                  {!p.active && (
                    <div>
                      <Chip tone="gray">disattivato</Chip>
                    </div>
                  )}
                </div>
              </div>
              <div className="row" style={{ marginTop: 10 }}>
                <button className="btn btn-sm" onClick={() => setEditing(p)}>
                  Modifica
                </button>
                <button className="btn btn-sm" onClick={() => toggleActive(p)}>
                  {p.active ? 'Disattiva' : 'Riattiva'}
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      {editing && (
        <ProductForm
          product={editing === 'new' ? null : editing}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null)
            reload()
          }}
        />
      )}
    </div>
  )
}

function toRequest(p: Product) {
  return {
    name: p.name,
    categoryId: p.categoryId,
    unitId: p.unitId,
    salePrice: p.salePrice,
    minStock: p.minStock,
    originId: p.originId,
    vatRateId: p.vatRateId,
  }
}

function ProductForm({ product, onClose, onSaved }: { product: Product | null; onClose: () => void; onSaved: () => void }) {
  const toast = useToast()
  const { lookups } = useLookups()
  const [name, setName] = useState(product?.name ?? '')
  const [categoryId, setCategoryId] = useState<number | ''>(product?.categoryId ?? lookups.CATEGORY[0]?.id ?? '')
  const [unitId, setUnitId] = useState<number | ''>(product?.unitId ?? lookups.UNIT[0]?.id ?? '')
  const [vatRateId, setVatRateId] = useState<number | ''>(product?.vatRateId ?? lookups.VAT_RATE[0]?.id ?? '')
  const [originId, setOriginId] = useState<number | ''>(product?.originId ?? '')
  const [price, setPrice] = useState(product ? String(product.salePrice) : '')
  const [minStock, setMinStock] = useState(product ? String(product.minStock) : '0')
  const [busy, setBusy] = useState(false)

  const categories = optionsWithCurrent(lookups.CATEGORY, product?.categoryId, product?.category)
  const units = optionsWithCurrent(lookups.UNIT, product?.unitId, product?.unit)
  const rates = lookups.VAT_RATE.map((r) => ({ id: r.id, name: r.name }))
  if (product && !rates.some((r) => r.id === product.vatRateId)) {
    rates.push({ id: product.vatRateId, name: `IVA ${product.vatRate ?? '?'}% (non più attiva)` })
  }
  const origins = optionsWithCurrent(lookups.ORIGIN, product?.originId, product?.origin)

  async function submit(e: FormEvent) {
    e.preventDefault()
    const salePrice = parseDecimal(price)
    const min = parseDecimal(minStock)
    if (!(salePrice >= 0)) return toast.error('Prezzo non valido')
    if (!(min >= 0)) return toast.error('Scorta minima non valida')
    if (categoryId === '' || unitId === '' || vatRateId === '') return toast.error('Scegli categoria, unità e aliquota IVA')
    setBusy(true)
    try {
      const body = {
        name: name.trim(),
        categoryId,
        unitId,
        salePrice,
        minStock: min,
        originId: originId === '' ? null : originId,
        vatRateId,
      }
      if (product) await api.put(`/products/${product.id}`, { ...body, active: product.active })
      else await api.post('/products', body)
      toast.ok('Prodotto salvato')
      onSaved()
    } catch (err) {
      toast.error(errorMessage(err))
      setBusy(false)
    }
  }

  const select = (value: number | '', set: (v: number | '') => void, options: { id: number; name: string }[], empty?: string) => (
    <select value={value} onChange={(e) => set(e.target.value === '' ? '' : Number(e.target.value))}>
      {empty !== undefined && <option value="">{empty}</option>}
      {options.map((o) => (
        <option key={o.id} value={o.id}>
          {o.name}
        </option>
      ))}
    </select>
  )

  return (
    <Modal title={product ? 'Modifica prodotto' : 'Nuovo prodotto'} onClose={onClose}>
      <form className="stack" onSubmit={submit}>
        <Field label="Nome">
          <input value={name} onChange={(e) => setName(e.target.value)} required autoFocus />
        </Field>
        <Field label="Categoria">{select(categoryId, setCategoryId, categories)}</Field>
        <div className="row">
          <div className="grow">
            <Field label="Venduto">{select(unitId, setUnitId, units)}</Field>
          </div>
          <div className="grow">
            <Field label="Prezzo (€, IVA inclusa)">
              <input value={price} onChange={(e) => setPrice(e.target.value)} inputMode="decimal" required />
            </Field>
          </div>
        </div>
        <Field label="Aliquota IVA" hint="Da concordare con il commercialista">
          {select(vatRateId, setVatRateId, rates)}
        </Field>
        <Field label="Scorta minima" hint="Sotto questa quantità il prodotto compare tra quelli da riordinare">
          <input value={minStock} onChange={(e) => setMinStock(e.target.value)} inputMode="decimal" />
        </Field>
        <Field label="Origine (facoltativa)">{select(originId, setOriginId, origins, '—')}</Field>
        <button className="btn btn-primary btn-block" disabled={busy}>
          Salva
        </button>
      </form>
    </Modal>
  )
}

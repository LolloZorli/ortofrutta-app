import { useState } from 'react'
import { api } from '../api'
import { Chip, Empty, ErrorBox, Loading, Stat } from '../components/ui'
import { eur, num, qty } from '../format'
import { useLoad } from '../hooks'
import type { ProductReport } from '../types'

const RANGES = [
  { days: 7, label: '7 giorni' },
  { days: 30, label: '30 giorni' },
  { days: 90, label: '90 giorni' },
]

export default function ReportPage() {
  const [days, setDays] = useState(30)
  const { data, loading, error, reload } = useLoad<ProductReport[]>(() => api.get(`/reports/products?days=${days}`), [days])

  const totals = (data ?? []).reduce(
    (t, r) => ({
      revenue: t.revenue + r.revenue,
      margin: t.margin + r.margin,
      waste: t.waste + r.wasteCost,
    }),
    { revenue: 0, margin: 0, waste: 0 },
  )

  return (
    <div className="stack">
      <div className="tabs">
        {RANGES.map((r) => (
          <button key={r.days} className={days === r.days ? 'active' : ''} onClick={() => setDays(r.days)}>
            {r.label}
          </button>
        ))}
      </div>

      {loading && !data ? (
        <Loading />
      ) : error || !data ? (
        <ErrorBox message={error ?? 'Nessun dato'} onRetry={reload} />
      ) : data.length === 0 ? (
        <Empty>Nessuna vendita o scarto in questo periodo.</Empty>
      ) : (
        <>
          <div className="stats">
            <Stat label="Incasso" value={eur(totals.revenue)} />
            <Stat label="Margine" value={eur(totals.margin)} tone={totals.margin >= 0 ? 'good' : 'bad'} />
            <Stat label="Costo scarti" value={eur(totals.waste)} tone={totals.waste > 0 ? 'bad' : undefined} />
            <Stat
              label="Margine %"
              value={totals.revenue > 0 ? `${num(Math.round((totals.margin / totals.revenue) * 1000) / 10)}%` : '—'}
            />
          </div>

          <h3 className="section-title">Per prodotto</h3>
          <div className="list">
            {data.map((r) => (
              <div key={r.productId} className="card">
                <div className="row between">
                  <div className="item-title">{r.productName}</div>
                  <strong>{eur(r.revenue)}</strong>
                </div>
                <div className="price-line">
                  <span className="muted">Venduto</span>
                  <span>{qty(r.soldQuantity, r.unit)}</span>
                </div>
                <div className="price-line">
                  <span className="muted">Margine</span>
                  <span className={r.margin >= 0 ? 'tone-good' : 'tone-bad'}>
                    {eur(r.margin)} ({num(r.marginPercent)}%)
                  </span>
                </div>
                {r.wasteQuantity > 0 && (
                  <div className="price-line">
                    <span className="muted">Scarto</span>
                    <span>
                      {qty(r.wasteQuantity, r.unit)} · {eur(r.wasteCost)}{' '}
                      <Chip tone={r.wastePercent > 10 ? 'red' : 'orange'}>{num(r.wastePercent)}%</Chip>
                    </span>
                  </div>
                )}
              </div>
            ))}
          </div>
        </>
      )}
    </div>
  )
}

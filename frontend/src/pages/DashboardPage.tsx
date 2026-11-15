import { Link } from 'react-router-dom'
import { api } from '../api'
import { useAuth } from '../auth'
import { Chip, Empty, ErrorBox, Loading, Stat } from '../components/ui'
import { eur, expiryLabel, qty } from '../format'
import { useLoad } from '../hooks'
import type { Dashboard } from '../types'

export default function DashboardPage() {
  const { can } = useAuth()
  const { data, loading, error, reload } = useLoad<Dashboard>(() => api.get('/reports/dashboard'))

  if (loading && !data) return <Loading />
  if (error || !data) return <ErrorBox message={error ?? 'Nessun dato'} onRetry={reload} />

  // Il server manda null per gli importi che l'utente non può vedere: li mostro solo se presenti
  const stats: { label: string; value: number | null; tone?: 'good' | 'bad' }[] = [
    { label: 'Incasso di oggi', value: data.todayRevenue },
    { label: 'Valore magazzino', value: data.stockValue },
    { label: 'Margine 30 giorni', value: data.margin30, tone: (data.margin30 ?? 0) >= 0 ? 'good' : 'bad' },
    { label: 'Scarti 30 giorni', value: data.waste30Cost, tone: (data.waste30Cost ?? 0) > 0 ? 'bad' : undefined },
  ]
  const visibleStats = stats.filter((s) => s.value !== null)
  const showTop = data.topProducts.length > 0 || can('REPORTS_VIEW')

  return (
    <div className="stack">
      {visibleStats.length > 0 && (
        <div className="stats">
          {visibleStats.map((s) => (
            <Stat key={s.label} label={s.label} value={eur(s.value ?? 0)} tone={s.tone} />
          ))}
        </div>
      )}

      <div className="fab-row">
        {can('SALES_CREATE') && (
          <Link to="/cassa" className="btn btn-primary grow center">
            Cassa
          </Link>
        )}
        {can('RESERVATIONS_MANAGE') && (
          <Link to="/prenotazioni" className="btn grow center">
            Prenotazioni ({data.openReservations})
          </Link>
        )}
      </div>

      <h3 className="section-title">Da riordinare ({data.lowStock.length})</h3>
      {data.lowStock.length === 0 ? (
        <Empty>Tutte le scorte sono sopra il minimo 👍</Empty>
      ) : (
        <div className="list">
          {data.lowStock.map((i) => (
            <div key={i.productId} className="card row between">
              <div>
                <div className="item-title">{i.productName}</div>
                <div className="item-sub">
                  Giacenza {qty(i.stock, i.unit)} · minimo {qty(i.minStock, i.unit)}
                </div>
              </div>
              <Chip tone="red">+{qty(i.suggestedQuantity, i.unit)}</Chip>
            </div>
          ))}
          {can('SUPPLIER_ORDERS_MANAGE') ? (
            <Link to="/ordini" className="btn center">
              Crea ordine al fornitore
            </Link>
          ) : (
            <p className="muted center">Segnala a chi gestisce gli ordini cosa va riordinato.</p>
          )}
        </div>
      )}

      <h3 className="section-title">In scadenza (3 giorni)</h3>
      {data.expiring.length === 0 ? (
        <Empty>Nessun lotto in scadenza</Empty>
      ) : (
        <div className="list">
          {data.expiring.map((l) => (
            <div key={l.lotId} className="card row between">
              <div>
                <div className="item-title">{l.productName}</div>
                <div className="item-sub">{qty(l.remainingQuantity, l.unit)} rimasti</div>
              </div>
              <Chip tone={l.daysToExpiry <= 0 ? 'red' : 'orange'}>{expiryLabel(l.daysToExpiry)}</Chip>
            </div>
          ))}
        </div>
      )}

      {showTop && (
        <>
          <h3 className="section-title">Più venduti (30 giorni)</h3>
          {data.topProducts.length === 0 ? (
            <Empty>Ancora nessuna vendita</Empty>
          ) : (
            <div className="list">
              {data.topProducts.map((t) => (
                <div key={t.productId} className="card row between">
                  <div>
                    <div className="item-title">{t.productName}</div>
                    <div className="item-sub">{qty(t.quantity, t.unit)}</div>
                  </div>
                  <strong>{eur(t.revenue)}</strong>
                </div>
              ))}
            </div>
          )}
        </>
      )}

      {can('SUPPLIER_ORDERS_MANAGE') && (
        <p className="muted center">
          Ordini aperti ai fornitori: <Link to="/ordini">{data.openSupplierOrders}</Link>
        </p>
      )}
    </div>
  )
}

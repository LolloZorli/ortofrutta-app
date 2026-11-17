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
  const now = new Date()
  const today = new Intl.DateTimeFormat('it-IT', { weekday: 'long', day: 'numeric', month: 'long' }).format(now)
  const dateTime = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`

  return (
    <div className="stack">
      <section className="dashboard-hero" aria-labelledby="dashboard-title">
        <div>
          <p className="dashboard-eyebrow">PANORAMICA DEL NEGOZIO</p>
          <h2 id="dashboard-title">Il banco, a colpo d’occhio</h2>
          <p>Incassi, scorte e attività da tenere sotto controllo.</p>
        </div>
        <time className="dashboard-date" dateTime={dateTime}>{today}</time>
      </section>

      {visibleStats.length > 0 && (
        <div className="stats">
          {visibleStats.map((s) => (
            <Stat key={s.label} label={s.label} value={eur(s.value ?? 0)} tone={s.tone} />
          ))}
        </div>
      )}

      <div className="fab-row dashboard-actions" aria-label="Azioni rapide">
        {can('SALES_CREATE') && (
          <Link to="/cassa" className="btn btn-primary grow center">
            <span aria-hidden="true">＋</span> Nuova vendita
          </Link>
        )}
        {can('RESERVATIONS_MANAGE') && (
          <Link to="/prenotazioni" className="btn grow center">
            Prenotazioni <span className="action-count">{data.openReservations}</span>
          </Link>
        )}
      </div>

      <div className="dashboard-grid">
        <section className="card dashboard-panel" aria-labelledby="low-stock-title">
          <div className="dashboard-panel-head">
            <div>
              <p className="dashboard-eyebrow">SCORTE</p>
              <h3 id="low-stock-title">Da riordinare</h3>
            </div>
            <span className={`dashboard-count${data.lowStock.length ? ' is-alert' : ''}`}>{data.lowStock.length}</span>
          </div>
          {data.lowStock.length === 0 ? (
            <p className="dashboard-empty">Tutte le scorte sono sopra il minimo.</p>
          ) : (
            <div className="dashboard-list">
              {data.lowStock.map((i) => (
                <div key={i.productId} className="dashboard-row">
                  <div className="dashboard-item-copy">
                    <div className="item-title">{i.productName}</div>
                    <div className="item-sub">Disponibili {qty(i.stock, i.unit)} · minimo {qty(i.minStock, i.unit)}{i.daysOfStock !== null ? ` · circa ${i.daysOfStock} giorni` : ''}</div>
                  </div>
                  <Chip tone="red">+{qty(i.suggestedQuantity, i.unit)}</Chip>
                </div>
              ))}
              {can('SUPPLIER_ORDERS_MANAGE') && (
                <Link to="/ordini" className="btn dashboard-link">Prepara un ordine <span aria-hidden="true">→</span></Link>
              )}
            </div>
          )}
        </section>

        <section className="card dashboard-panel" aria-labelledby="expiring-title">
          <div className="dashboard-panel-head">
            <div>
              <p className="dashboard-eyebrow">LOTTI</p>
              <h3 id="expiring-title">In scadenza</h3>
            </div>
            <span className={`dashboard-count${data.expiring.length ? ' is-warn' : ''}`}>{data.expiring.length}</span>
          </div>
          {data.expiring.length === 0 ? (
            <p className="dashboard-empty">Nessun lotto in scadenza nei prossimi 3 giorni.</p>
          ) : (
            <div className="dashboard-list">
              {data.expiring.map((l) => (
                <div key={l.lotId} className="dashboard-row">
                  <div className="dashboard-item-copy">
                    <div className="item-title">{l.productName}</div>
                    <div className="item-sub">{qty(l.remainingQuantity, l.unit)} rimasti</div>
                  </div>
                  <Chip tone={l.daysToExpiry <= 0 ? 'red' : 'orange'}>{expiryLabel(l.daysToExpiry)}</Chip>
                </div>
              ))}
            </div>
          )}
        </section>
      </div>

      {showTop && (
        <>
          <h3 className="section-title">Più venduti <span>ultimi 30 giorni</span></h3>
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

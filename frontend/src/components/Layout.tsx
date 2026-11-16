import { NavLink, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '../auth'

interface Tab {
  to: string
  label: string
  icon: string
  end?: boolean
  /** Il tab compare solo a chi ha questo permesso. */
  perm?: string
}

const TABS: Tab[] = [
  { to: '/', label: 'Home', icon: '🏠', end: true },
  { to: '/cassa', label: 'Cassa', icon: '💶', perm: 'SALES_CREATE' },
  { to: '/magazzino', label: 'Magazzino', icon: '📦', perm: 'STOCK_READ' },
  { to: '/ordini', label: 'Ordini', icon: '🚚', perm: 'SUPPLIER_ORDERS_MANAGE' },
  { to: '/prenotazioni', label: 'Prenota', icon: '🛍️', perm: 'RESERVATIONS_MANAGE' },
  { to: '/altro', label: 'Altro', icon: '⋯' },
]

const TITLES: Record<string, string> = {
  '/': 'Ortofrutta',
  '/cassa': 'Cassa',
  '/magazzino': 'Magazzino',
  '/ordini': 'Ordini ai fornitori',
  '/prenotazioni': 'Prenotazioni clienti',
  '/altro': 'Altro',
  '/prodotti': 'Prodotti e prezzi',
  '/fornitori': 'Fornitori',
  '/clienti': 'Clienti',
  '/report': 'Report',
  '/corrispettivi': 'Corrispettivi',
  '/registro': 'Registro attività',
  '/utenti': 'Utenti',
  '/ruoli': 'Ruoli e permessi',
  '/tipologiche': 'Tipologiche',
}

export default function Layout() {
  const { pathname } = useLocation()
  const { can, profile } = useAuth()
  const tabs = TABS.filter((t) => !t.perm || can(t.perm))
  return (
    <div className="app">
      <header className="topbar">
        <h1>
          <img src="/icon.svg" alt="" />
          {TITLES[pathname] ?? 'Ortofrutta'}
        </h1>
        {profile && (
          <div className="topbar-user">
            <span className="user-avatar" aria-hidden="true">{profile.username.slice(0, 1).toUpperCase()}</span>
            <span className="user-label">
              <strong>{profile.username}</strong>
              <small>{profile.roleName}</small>
            </span>
          </div>
        )}
      </header>
      <main className="content">
        <Outlet />
      </main>
      <nav className="tabbar" aria-label="Navigazione principale">
        <div className="nav-brand">
          <img src="/icon.svg" alt="" />
          <span>Ortofrutta<small>Gestione negozio</small></span>
        </div>
        {tabs.map((t) => (
          <NavLink key={t.to} to={t.to} end={t.end} className={({ isActive }) => (isActive ? 'tab active' : 'tab')}>
            <span className="tab-icon" aria-hidden="true">
              {t.icon}
            </span>
            <span>{t.label}</span>
          </NavLink>
        ))}
        {profile && (
          <div className="nav-user">
            <span className="user-avatar" aria-hidden="true">{profile.username.slice(0, 1).toUpperCase()}</span>
            <span><strong>{profile.username}</strong><small>{profile.roleName}</small></span>
          </div>
        )}
      </nav>
    </div>
  )
}

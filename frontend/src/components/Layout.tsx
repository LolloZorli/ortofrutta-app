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
  const { can } = useAuth()
  const tabs = TABS.filter((t) => !t.perm || can(t.perm))
  return (
    <div className="app">
      <header className="topbar">
        <h1>{TITLES[pathname] ?? 'Ortofrutta'}</h1>
      </header>
      <main className="content">
        <Outlet />
      </main>
      <nav className="tabbar" aria-label="Navigazione principale">
        {tabs.map((t) => (
          <NavLink key={t.to} to={t.to} end={t.end} className={({ isActive }) => (isActive ? 'tab active' : 'tab')}>
            <span className="tab-icon" aria-hidden="true">
              {t.icon}
            </span>
            <span>{t.label}</span>
          </NavLink>
        ))}
      </nav>
    </div>
  )
}

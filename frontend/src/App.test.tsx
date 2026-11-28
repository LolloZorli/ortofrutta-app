// @vitest-environment jsdom
import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import App from './App'

const authState = vi.hoisted(() => {
  const state = {
    profile: null as null | {
      username: string
      displayName: string | null
      email: string | null
      roleCode: string
      roleName: string
      permissions: string[]
      mustChangePassword: boolean
    },
    ready: true,
    permissions: [] as string[],
    logout: vi.fn(),
  }
  return {
    ...state,
    can: (permission: string) => state.permissions.includes(permission),
    canAny: (...permissions: string[]) => permissions.some((permission) => state.permissions.includes(permission)),
    get profile() { return state.profile },
    set profile(value) { state.profile = value },
    get ready() { return state.ready },
    set ready(value) { state.ready = value },
    get permissions() { return state.permissions },
    set permissions(value) { state.permissions = value },
  }
})

vi.mock('./auth', () => ({ useAuth: () => authState }))
vi.mock('./lookups', () => ({ LookupsProvider: ({ children }: { children: React.ReactNode }) => children }))
vi.mock('./pages/LoginPage', () => ({ default: () => <div data-testid="login-page" /> }))
vi.mock('./pages/ForcePasswordPage', () => ({ default: () => <div data-testid="force-password-page" /> }))

vi.mock('./pages/DashboardPage', () => ({ default: () => <div data-testid="page-dashboard" /> }))
vi.mock('./pages/CassaPage', () => ({ default: () => <div data-testid="page-cassa" /> }))
vi.mock('./pages/StockPage', () => ({ default: () => <div data-testid="page-stock" /> }))
vi.mock('./pages/SupplierOrdersPage', () => ({ default: () => <div data-testid="page-supplier-orders" /> }))
vi.mock('./pages/ReservationsPage', () => ({ default: () => <div data-testid="page-reservations" /> }))
vi.mock('./pages/ContactsPage', () => ({
  default: ({ kind }: { kind: string }) => <div data-testid={`page-${kind}-contacts`} />,
}))
vi.mock('./pages/ProductsPage', () => ({ default: () => <div data-testid="page-products" /> }))
vi.mock('./pages/ReportPage', () => ({ default: () => <div data-testid="page-report" /> }))
vi.mock('./pages/CorrispettiviPage', () => ({ default: () => <div data-testid="page-corrispettivi" /> }))
vi.mock('./pages/AuditPage', () => ({ default: () => <div data-testid="page-audit" /> }))
vi.mock('./pages/UsersPage', () => ({ default: () => <div data-testid="page-users" /> }))
vi.mock('./pages/RolesPage', () => ({ default: () => <div data-testid="page-roles" /> }))
vi.mock('./pages/LookupsPage', () => ({ default: () => <div data-testid="page-lookups" /> }))
vi.mock('./pages/MorePage', () => ({ default: () => <div data-testid="page-more" /> }))
vi.mock('./pages/CustomerPortalPage', () => ({ default: () => <div data-testid="page-customer-portal" /> }))
vi.mock('./pages/DeliverySettingsPage', () => ({ default: () => <div data-testid="page-delivery-settings" /> }))

const guardedRoutes = [
  ['/area-clienti', 'page-customer-portal', ['CUSTOMER_ORDER_READ']],
  ['/cassa', 'page-cassa', ['SALES_CREATE']],
  ['/magazzino', 'page-stock', ['STOCK_READ']],
  ['/ordini', 'page-supplier-orders', ['SUPPLIER_ORDER_CREATE', 'SUPPLIER_ORDERS_MANAGE']],
  ['/prenotazioni', 'page-reservations', ['RESERVATIONS_MANAGE']],
  ['/consegne', 'page-delivery-settings', ['RESERVATIONS_MANAGE']],
  ['/clienti', 'page-customer-contacts', ['CUSTOMERS_MANAGE', 'RESERVATIONS_MANAGE']],
  ['/fornitori', 'page-supplier-contacts', ['SUPPLIERS_READ', 'SUPPLIERS_MANAGE']],
  ['/prodotti', 'page-products', ['PRODUCTS_WRITE']],
  ['/report', 'page-report', ['REPORTS_VIEW']],
  ['/corrispettivi', 'page-corrispettivi', ['REPORTS_VIEW']],
  ['/registro', 'page-audit', ['AUDIT_VIEW']],
  ['/utenti', 'page-users', ['USERS_READ', 'USERS_APPROVE', 'USERS_MANAGE']],
  ['/ruoli', 'page-roles', ['ROLES_MANAGE']],
  ['/tipologiche', 'page-lookups', ['LOOKUPS_MANAGE']],
] as const

function openRoute(path: string, permissions: string[] = [], roleCode = 'STAFF', mustChangePassword = false) {
  authState.permissions = permissions
  authState.profile = {
    username: 'test-user',
    displayName: null,
    email: null,
    roleCode,
    roleName: 'Test',
    permissions,
    mustChangePassword,
  }
  return render(<MemoryRouter initialEntries={[path]}><App /></MemoryRouter>)
}

describe('application routing', () => {
  beforeEach(() => {
    authState.ready = true
    authState.permissions = []
    authState.profile = null
    authState.logout.mockReset()
  })
  afterEach(cleanup)

  it.each(guardedRoutes)('%s renders with one of its required permissions', async (path, page, permissions) => {
    openRoute(path, [permissions[permissions.length - 1]])
    expect(await screen.findByTestId(page)).toBeTruthy()
  })

  it.each(guardedRoutes)('%s redirects to the safe menu without its permission', async (path, page) => {
    openRoute(path)
    expect(await screen.findByTestId('page-more')).toBeTruthy()
    expect(screen.queryByTestId(page)).toBeNull()
  })

  it.each([
    ['/ordini', 'SUPPLIER_ORDER_CREATE'],
    ['/ordini', 'SUPPLIER_ORDERS_MANAGE'],
    ['/clienti', 'CUSTOMERS_MANAGE'],
    ['/clienti', 'RESERVATIONS_MANAGE'],
    ['/fornitori', 'SUPPLIERS_READ'],
    ['/fornitori', 'SUPPLIERS_MANAGE'],
    ['/utenti', 'USERS_READ'],
    ['/utenti', 'USERS_APPROVE'],
    ['/utenti', 'USERS_MANAGE'],
  ])('%s accepts alternative permission %s', async (path, permission) => {
    openRoute(path, [permission])
    const page = path === '/ordini' ? 'page-supplier-orders'
      : path === '/clienti' ? 'page-customer-contacts'
        : path === '/fornitori' ? 'page-supplier-contacts' : 'page-users'
    expect(await screen.findByTestId(page)).toBeTruthy()
  })

  it('shows login when signed out and waits while auth is being restored', () => {
    authState.ready = false
    const { rerender } = render(<MemoryRouter><App /></MemoryRouter>)
    expect(screen.queryByTestId('login-page')).toBeNull()

    authState.ready = true
    rerender(<MemoryRouter><App /></MemoryRouter>)
    expect(screen.getByTestId('login-page')).toBeTruthy()
  })

  it('forces password change before rendering protected pages', () => {
    openRoute('/cassa', ['SALES_CREATE'], 'STAFF', true)
    expect(screen.getByTestId('force-password-page')).toBeTruthy()
    expect(screen.queryByTestId('page-cassa')).toBeNull()
  })

  it('navigates through permitted tabs and hides unavailable tabs', async () => {
    const user = userEvent.setup()
    openRoute('/', ['STOCK_READ'])
    expect(await screen.findByTestId('page-dashboard')).toBeTruthy()
    expect(screen.queryByRole('link', { name: /Cassa/ })).toBeNull()
    await user.click(screen.getByRole('link', { name: /Magazzino/ }))
    expect(await screen.findByTestId('page-stock')).toBeTruthy()
  })

  it('routes customers to their order portal and unknown paths to home', async () => {
    openRoute('/', ['CUSTOMER_ORDER_READ'], 'CUSTOMER')
    expect(await screen.findByTestId('page-customer-portal')).toBeTruthy()

    cleanup()
    openRoute('/', [], 'CUSTOMER')
    expect(await screen.findByTestId('page-more')).toBeTruthy()

    cleanup()
    openRoute('/not-a-page', ['STOCK_READ'])
    expect(await screen.findByTestId('page-dashboard')).toBeTruthy()
  })
})

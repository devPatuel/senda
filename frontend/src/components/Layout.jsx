import { useEffect, useState } from 'react'
import { NavLink, Outlet, useLocation } from 'react-router-dom'
import { getMe } from '../api/admin'
import { listSpaces } from '../api/spaces'
import { useAuth } from '../auth/useAuth'

const svg = (...paths) => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="h-5 w-5" aria-hidden="true">
    {paths.map((d, i) => (
      <path key={i} d={d} />
    ))}
  </svg>
)

const ICONS = {
  inicio: svg('M3 10.5 12 3l9 7.5', 'M5 9.5V21h14V9.5'),
  pareja: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="h-5 w-5" aria-hidden="true">
      <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" />
      <circle cx="9" cy="7" r="4" />
      <path d="M22 21v-2a4 4 0 0 0-3-3.87" />
      <path d="M16 3.13a4 4 0 0 1 0 7.75" />
    </svg>
  ),
  movimientos: svg('M7 4v13', 'm3.5 13.5 3.5 3.5 3.5-3.5', 'M17 20V7', 'm13.5 10.5 3.5-3.5 3.5 3.5'),
  reparto: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="h-5 w-5" aria-hidden="true">
      <circle cx="12" cy="12" r="9" />
      <path d="M12 3v9l6 4" />
    </svg>
  ),
  cuentas: svg('M3 21h18', 'M5 21V9l7-5 7 5v12', 'M9 21v-6h6v6'),
  contrasena: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="h-5 w-5" aria-hidden="true">
      <rect x="4" y="11" width="16" height="10" rx="2" />
      <path d="M8 11V7a4 4 0 0 1 8 0v4" />
    </svg>
  ),
  tokens: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="h-5 w-5" aria-hidden="true">
      <circle cx="12" cy="12" r="3" />
      <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.6 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.6a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z" />
    </svg>
  ),
}

// Each section opens on its first tab; `paths` keeps its entry lit on the other tabs.
const NAV_ITEMS = [
  { to: '/', label: 'Inicio', end: true, icon: ICONS.inicio },
  { to: '/cuentas', label: 'Activos', icon: ICONS.cuentas, paths: ['/cuentas', '/inversiones', '/deudas'] },
  {
    to: '/movimientos',
    label: 'Movimientos',
    icon: ICONS.movimientos,
    paths: ['/movimientos', '/recurrentes', '/importar'],
  },
  { to: '/categorias', label: 'Planificación', icon: ICONS.reparto, paths: ['/categorias', '/deseos', '/reparto'] },
  { to: '/pareja', label: 'Pareja', icon: ICONS.pareja },
]

const TOKENS_ITEM = { to: '/tokens', label: 'Tokens', icon: ICONS.tokens }
const PASSWORD_ITEM = { to: '/contrasena', label: 'Contraseña', icon: ICONS.contrasena }
const ADMIN_ITEM = {
  to: '/admin',
  label: 'Administración',
  icon: svg('M12 3l8 3v6c0 5-3.4 8-8 9-4.6-1-8-4-8-9V6z'),
}

const STORAGE_KEY = 'senda_sidebar_collapsed'

function readCollapsed() {
  try {
    return localStorage.getItem(STORAGE_KEY) === 'true'
  } catch {
    return false
  }
}

function navLinkClasses({ isActive }, base) {
  return [
    base,
    isActive ? 'text-emerald-600 font-semibold' : 'text-slate-500 hover:text-slate-800',
  ].join(' ')
}

function SidebarLink({ item, collapsed, onNavigate }) {
  const { pathname } = useLocation()
  return (
    <NavLink
      to={item.to}
      end={item.end}
      onClick={onNavigate}
      title={collapsed ? item.label : undefined}
      className={({ isActive }) =>
        navLinkClasses(
          { isActive: isActive || Boolean(item.paths?.includes(pathname)) },
          'flex items-center gap-3 rounded-lg px-3 py-2 text-sm transition-colors',
        )
      }
    >
      <span className="shrink-0">{item.icon}</span>
      {!collapsed && <span className="truncate">{item.label}</span>}
    </NavLink>
  )
}

function Brand({ collapsed }) {
  return (
    <NavLink to="/" className="flex items-center gap-2 px-1">
      {/* Collapsed, the logo is the link's only content, so it must carry the name. */}
      <img src="/logo.svg" alt={collapsed ? 'Senda' : ''} className="h-8 w-8 shrink-0 object-contain" />
      {!collapsed && <span className="text-lg font-semibold tracking-tight">Senda</span>}
    </NavLink>
  )
}

function NavGroups({ collapsed, showCouple, onNavigate }) {
  return (
    <nav className="flex-1 space-y-1 overflow-y-auto px-2 py-2" aria-label="Principal">
      {NAV_ITEMS.filter((item) => item.to !== '/pareja' || showCouple).map((item) => (
        <SidebarLink key={item.to} item={item} collapsed={collapsed} onNavigate={onNavigate} />
      ))}
    </nav>
  )
}

function SidebarFooter({ collapsed, user, isAdmin, logout, onNavigate }) {
  return (
    <div className="border-t border-slate-200 px-2 py-2">
      {isAdmin && <SidebarLink item={ADMIN_ITEM} collapsed={collapsed} onNavigate={onNavigate} />}
      <SidebarLink item={TOKENS_ITEM} collapsed={collapsed} onNavigate={onNavigate} />
      <SidebarLink item={PASSWORD_ITEM} collapsed={collapsed} onNavigate={onNavigate} />
      <div className={`flex items-center gap-2 px-3 py-2 ${collapsed ? 'justify-center' : 'justify-between'}`}>
        {!collapsed && <span className="truncate text-sm text-slate-500">{user?.name}</span>}
        <button
          type="button"
          onClick={logout}
          title="Salir"
          className="shrink-0 rounded-lg border border-slate-200 px-2.5 py-1.5 text-sm text-slate-600 transition-colors hover:border-slate-300 hover:text-slate-900"
        >
          {collapsed ? '↩' : 'Salir'}
        </button>
      </div>
    </div>
  )
}

export default function Layout() {
  const { user, logout } = useAuth()
  const [collapsed, setCollapsed] = useState(readCollapsed)
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [isAdmin, setIsAdmin] = useState(false)
  const [showCouple, setShowCouple] = useState(false)
  const { pathname } = useLocation()

  // Only adapts the menu: the server authorizes every admin and space request.
  useEffect(() => {
    let cancelled = false
    getMe()
      .then((me) => { if (!cancelled) setIsAdmin(Boolean(me?.admin)) })
      .catch(() => { if (!cancelled) setIsAdmin(false) })
    return () => {
      cancelled = true
    }
  }, [])

  // Re-read on navigation so an invitation shows up without logging in again. If the
  // request fails, keep the entry visible: a hiccup must not hide someone's space.
  useEffect(() => {
    let cancelled = false
    listSpaces()
      .then((spaces) => { if (!cancelled) setShowCouple(spaces.length > 0) })
      .catch(() => { if (!cancelled) setShowCouple(true) })
    return () => {
      cancelled = true
    }
  }, [pathname])

  useEffect(() => {
    try {
      localStorage.setItem(STORAGE_KEY, String(collapsed))
    } catch {
      // ignore: private mode / storage disabled
    }
  }, [collapsed])

  return (
    <div className="flex min-h-screen bg-slate-50 text-slate-900">
      {/* Desktop sidebar */}
      <aside
        className={`hidden shrink-0 flex-col border-r border-slate-200 bg-white transition-all md:flex ${
          collapsed ? 'w-16' : 'w-60'
        }`}
      >
        {/* A 64px rail cannot fit the logo and the button side by side: stack them. */}
        <div
          className={[
            'flex border-b border-slate-200',
            collapsed ? 'flex-col items-center gap-1 py-2' : 'h-14 items-center justify-between px-3',
          ].join(' ')}
        >
          <Brand collapsed={collapsed} />
          <button
            type="button"
            onClick={() => setCollapsed((c) => !c)}
            aria-label={collapsed ? 'Expandir menú' : 'Colapsar menú'}
            aria-expanded={!collapsed}
            className="rounded-lg p-1.5 text-slate-400 transition-colors hover:bg-slate-100 hover:text-slate-700"
          >
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="h-5 w-5" aria-hidden="true">
              <path d="M4 6h16" />
              <path d="M4 12h16" />
              <path d="M4 18h16" />
            </svg>
          </button>
        </div>
        <NavGroups collapsed={collapsed} showCouple={showCouple} />
        <SidebarFooter collapsed={collapsed} isAdmin={isAdmin} user={user} logout={logout} />
      </aside>

      {/* Mobile top bar */}
      <header className="fixed inset-x-0 top-0 z-20 flex h-14 items-center justify-between border-b border-slate-200 bg-white/90 px-4 backdrop-blur md:hidden">
        <button
          type="button"
          onClick={() => setDrawerOpen(true)}
          aria-label="Abrir menú"
          className="rounded-lg p-1.5 text-slate-500 transition-colors hover:bg-slate-100"
        >
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="h-6 w-6" aria-hidden="true">
            <path d="M4 6h16" />
            <path d="M4 12h16" />
            <path d="M4 18h16" />
          </svg>
        </button>
        <Brand collapsed={false} />
        <button
          type="button"
          onClick={logout}
          className="rounded-lg border border-slate-200 px-3 py-1.5 text-sm text-slate-600 transition-colors hover:border-slate-300 hover:text-slate-900"
        >
          Salir
        </button>
      </header>

      {/* Mobile drawer */}
      {drawerOpen && (
        <>
          <div
            className="fixed inset-0 z-40 bg-black/40 md:hidden"
            onClick={() => setDrawerOpen(false)}
            aria-hidden="true"
          />
          <aside className="fixed inset-y-0 left-0 z-50 flex w-64 flex-col border-r border-slate-200 bg-white md:hidden">
            <div className="flex h-14 items-center justify-between border-b border-slate-200 px-3">
              <Brand collapsed={false} />
              <button
                type="button"
                onClick={() => setDrawerOpen(false)}
                aria-label="Cerrar menú"
                className="rounded-lg p-1.5 text-slate-400 transition-colors hover:bg-slate-100 hover:text-slate-700"
              >
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="h-5 w-5" aria-hidden="true">
                  <path d="M18 6 6 18" />
                  <path d="m6 6 12 12" />
                </svg>
              </button>
            </div>
            <NavGroups collapsed={false} showCouple={showCouple} onNavigate={() => setDrawerOpen(false)} />
            <SidebarFooter
              collapsed={false} isAdmin={isAdmin}
              user={user}
              logout={logout}
              onNavigate={() => setDrawerOpen(false)}
            />
          </aside>
        </>
      )}

      {/* Content */}
      <main className="flex-1">
        {/* pt-14 clears the fixed mobile top bar; no offset needed on desktop */}
        <div className="mx-auto max-w-5xl px-4 py-6 pt-20 md:pt-6">
          <Outlet />
        </div>
      </main>
    </div>
  )
}

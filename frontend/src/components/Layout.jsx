import { NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'

const NAV_ITEMS = [
  {
    to: '/',
    label: 'Inicio',
    icon: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="h-5 w-5" aria-hidden="true">
        <path d="M3 10.5 12 3l9 7.5" />
        <path d="M5 9.5V21h14V9.5" />
      </svg>
    ),
  },
  {
    to: '/movimientos',
    label: 'Movimientos',
    icon: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="h-5 w-5" aria-hidden="true">
        <path d="M7 4v13" />
        <path d="m3.5 13.5 3.5 3.5 3.5-3.5" />
        <path d="M17 20V7" />
        <path d="m13.5 10.5 3.5-3.5 3.5 3.5" />
      </svg>
    ),
  },
  {
    to: '/categorias',
    label: 'Categorías',
    icon: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="h-5 w-5" aria-hidden="true">
        <path d="M4 7h16" />
        <path d="M4 12h16" />
        <path d="M4 17h10" />
      </svg>
    ),
  },
  {
    to: '/cuentas',
    label: 'Cuentas',
    icon: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="h-5 w-5" aria-hidden="true">
        <path d="M3 21h18" />
        <path d="M5 21V9l7-5 7 5v12" />
        <path d="M9 21v-6h6v6" />
      </svg>
    ),
  },
]

function navLinkClasses({ isActive }, base) {
  return [
    base,
    isActive
      ? 'text-emerald-600 font-semibold'
      : 'text-slate-500 hover:text-slate-800',
  ].join(' ')
}

export default function Layout() {
  const { user, logout } = useAuth()

  return (
    <div className="min-h-screen bg-slate-50 text-slate-900">
      {/* Top bar: brand always; nav links only on desktop */}
      <header className="sticky top-0 z-10 border-b border-slate-200 bg-white/90 backdrop-blur">
        <div className="mx-auto flex h-14 max-w-5xl items-center justify-between px-4">
          <NavLink to="/" className="flex items-center gap-2">
            <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-emerald-600 text-sm font-bold text-white">
              S
            </span>
            <span className="text-lg font-semibold tracking-tight">Senda</span>
          </NavLink>

          <nav className="hidden items-center gap-1 md:flex" aria-label="Principal">
            {NAV_ITEMS.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.to === '/'}
                className={(state) =>
                  navLinkClasses(
                    state,
                    'rounded-lg px-3 py-2 text-sm transition-colors',
                  )
                }
              >
                {item.label}
              </NavLink>
            ))}
          </nav>

          <div className="flex items-center gap-3">
            <span className="hidden text-sm text-slate-500 sm:inline">
              {user?.name}
            </span>
            <button
              type="button"
              onClick={logout}
              className="rounded-lg border border-slate-200 px-3 py-1.5 text-sm text-slate-600 transition-colors hover:border-slate-300 hover:text-slate-900"
            >
              Salir
            </button>
          </div>
        </div>
      </header>

      {/* pb-20 leaves room for the fixed bottom nav on mobile */}
      <main className="mx-auto max-w-5xl px-4 py-6 pb-24 md:pb-8">
        <Outlet />
      </main>

      {/* Bottom navigation, mobile only */}
      <nav
        className="fixed inset-x-0 bottom-0 z-10 border-t border-slate-200 bg-white md:hidden"
        aria-label="Principal móvil"
      >
        <div className="mx-auto flex max-w-5xl">
          {NAV_ITEMS.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.to === '/'}
              className={(state) =>
                navLinkClasses(
                  state,
                  'flex flex-1 flex-col items-center gap-1 py-2.5 text-xs transition-colors',
                )
              }
            >
              {item.icon}
              {item.label}
            </NavLink>
          ))}
        </div>
      </nav>
    </div>
  )
}

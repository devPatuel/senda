import { NavLink, Outlet } from 'react-router-dom'

// Horizontal tabs shared by the sections of the personal area. Each tab keeps its own
// address, so links, reloads and the browser's back button keep working.
export default function SectionTabs({ label, tabs }) {
  return (
    <div className="space-y-5">
      <nav aria-label={label} className="flex gap-1 rounded-2xl border border-slate-200 bg-white p-1">
        {tabs.map((tab) => (
          <NavLink
            key={tab.to}
            to={tab.to}
            className={({ isActive }) =>
              [
                'min-w-0 flex-1 truncate rounded-xl px-2 py-2 text-center text-sm font-medium transition-colors sm:px-3',
                isActive ? 'bg-emerald-600 text-white' : 'text-slate-600 hover:bg-slate-100 hover:text-slate-900',
              ].join(' ')
            }
          >
            {tab.label}
          </NavLink>
        ))}
      </nav>
      <Outlet />
    </div>
  )
}

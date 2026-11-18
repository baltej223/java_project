import { Link, Outlet, useLocation } from 'react-router-dom'
import { TokenBar } from './TokenBar'

const links = [
  { to: '/', label: 'Overview' },
  { to: '/tasks', label: 'Tasks' },
  { to: '/tasks/new', label: 'New task' },
  { to: '/dlq', label: 'DLQ' },
]

export function Layout() {
  const { pathname } = useLocation()

  function active(to: string): boolean {
    if (to === '/') return pathname === '/'
    if (to === '/tasks') return pathname === '/tasks' || (pathname.startsWith('/tasks/') && !pathname.startsWith('/tasks/new'))
    return pathname === to
  }

  return (
    <div className="min-h-screen">
      <header className="border-b border-slate-800 bg-slate-900 text-white">
        <div className="mx-auto flex max-w-6xl flex-col gap-3 px-4 py-4 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <p className="text-lg font-semibold tracking-tight">Crobat</p>
            <p className="text-xs text-slate-400">Cron and HTTP job console</p>
          </div>
          <nav className="flex flex-wrap gap-1">
            {links.map((link) => (
              <Link
                key={link.to}
                to={link.to}
                className={`rounded-lg px-3 py-2 text-sm font-medium ${
                  active(link.to)
                    ? 'bg-slate-700 text-white'
                    : 'text-slate-300 hover:bg-slate-800 hover:text-white'
                }`}
              >
                {link.label}
              </Link>
            ))}
          </nav>
        </div>
      </header>
      <TokenBar />
      <main className="mx-auto max-w-6xl px-4 py-8">
        <Outlet />
      </main>
    </div>
  )
}

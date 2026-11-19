import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { clearSession, getTenant, getUsername } from '../api'

const links = [
  { to: '/', label: 'Overview' },
  { to: '/tasks', label: 'Tasks' },
  { to: '/tasks/new', label: 'New task' },
  { to: '/dlq', label: 'DLQ' },
]

export function Layout() {
  const { pathname } = useLocation()
  const navigate = useNavigate()
  const tenant = getTenant()
  const username = getUsername()

  function active(to: string): boolean {
    if (to === '/') return pathname === '/'
    if (to === '/tasks') return pathname === '/tasks' || (pathname.startsWith('/tasks/') && !pathname.startsWith('/tasks/new'))
    return pathname === to
  }

  function logout() {
    clearSession()
    navigate('/login', { replace: true })
  }

  return (
    <div className="min-h-screen">
      <header className="border-b border-slate-800 bg-slate-900 text-white">
        <div className="mx-auto flex max-w-6xl flex-col gap-3 px-4 py-4 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <p className="text-lg font-semibold tracking-tight">Chroner</p>
            <p className="text-xs text-slate-400">Cron and HTTP job console</p>
          </div>
          <div className="flex flex-wrap items-center gap-3">
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
            <div className="flex items-center gap-2 border-l border-slate-700 pl-3 text-sm">
              <span className="text-slate-400">
                {username || 'user'}@{tenant || 'tenant'}
              </span>
              <button type="button" onClick={logout} className="rounded-lg border border-slate-600 px-2 py-1 text-xs text-slate-300 hover:bg-slate-800">
                Logout
              </button>
            </div>
          </div>
        </div>
      </header>
      <main className="mx-auto max-w-6xl px-4 py-8">
        <Outlet />
      </main>
    </div>
  )
}

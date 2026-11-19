import { useEffect, useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { getToken, login } from '../api'

export function LoginPage() {
  const navigate = useNavigate()
  const [tenant, setTenant] = useState('acme')
  const [username, setUsername] = useState('admin')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    if (getToken()) {
      navigate('/', { replace: true })
    }
  }, [navigate])

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError('')
    setBusy(true)
    try {
      await login(tenant, username, password)
      navigate('/', { replace: true })
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Login failed')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-100 px-4">
      <div className="w-full max-w-sm">
        <div className="mb-6 text-center">
          <h1 className="text-3xl font-semibold tracking-tight text-slate-900">Crobat</h1>
          <p className="mt-1 text-sm text-slate-500">Sign in to manage tasks and executions.</p>
        </div>
        <form onSubmit={(event) => void submit(event)} className="card space-y-4 p-6">
          {error && (
            <div className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-800">
              {error}
            </div>
          )}
          <label>
            <span className="label">Tenant</span>
            <input id="tenant" className="input" value={tenant} onChange={(e) => setTenant(e.target.value)} autoComplete="organization" />
          </label>
          <label>
            <span className="label">Username</span>
            <input id="username" className="input" value={username} onChange={(e) => setUsername(e.target.value)} autoComplete="username" />
          </label>
          <label>
            <span className="label">Password</span>
            <input id="password" type="password" className="input" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="current-password" required />
          </label>
          <button type="submit" className="btn-primary w-full" disabled={busy}>
            {busy ? 'Signing in…' : 'Sign in'}
          </button>
          <p className="text-center text-xs text-slate-500">
            Demo login: <span className="font-mono">acme / admin / admin123</span>
          </p>
        </form>
      </div>
    </div>
  )
}

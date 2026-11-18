import { useState } from 'react'
import { getToken, setToken } from '../api'

export function TokenBar() {
  const [value, setValue] = useState(getToken())
  const [saved, setSaved] = useState(Boolean(getToken()))

  function save() {
    setToken(value)
    setSaved(Boolean(value.trim()))
  }

  function clear() {
    setValue('')
    setToken('')
    setSaved(false)
  }

  return (
    <div className="border-b border-slate-200 bg-white">
      <div className="mx-auto flex max-w-6xl flex-col gap-2 px-4 py-3 sm:flex-row sm:items-center">
        <label htmlFor="token" className="text-sm font-medium text-slate-600">
          JWT token
        </label>
        <input
          id="token"
          type="password"
          value={value}
          onChange={(event) => setValue(event.target.value)}
          placeholder="Paste a tenant JWT"
          autoComplete="off"
          className="input sm:max-w-xl"
        />
        <button type="button" onClick={save} className="btn-primary">
          Save token
        </button>
        {saved && (
          <button type="button" onClick={clear} className="btn-secondary">
            Clear
          </button>
        )}
        <span className={`text-sm ${saved ? 'text-emerald-700' : 'text-amber-700'}`}>
          {saved ? 'Token set in this browser' : 'No token set'}
        </span>
      </div>
    </div>
  )
}

import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { api } from '../api'
import type { CreateTaskInput, Priority, TaskRequest } from '../types'

type ScheduleMode = 'now' | 'at' | 'cron'

function parsePriority(value: string): Priority {
  if (value === 'critical' || value === 'high' || value === 'normal' || value === 'low') {
    return value
  }
  return 'normal'
}

function parseSchedule(value: string): ScheduleMode {
  if (value === 'at' || value === 'cron') {
    return value
  }
  return 'now'
}

export function TaskFormPage() {
  const navigate = useNavigate()
  const [name, setName] = useState('')
  const [url, setUrl] = useState('')
  const [method, setMethod] = useState('POST')
  const [priority, setPriority] = useState<Priority>('normal')
  const [schedule, setSchedule] = useState<ScheduleMode>('now')
  const [at, setAt] = useState('')
  const [cron, setCron] = useState('')
  const [timezone, setTimezone] = useState('UTC')
  const [timeoutMs, setTimeoutMs] = useState(10000)
  const [maxAttempts, setMaxAttempts] = useState(3)
  const [backoffMs, setBackoffMs] = useState(1000)
  const [headersText, setHeadersText] = useState('')
  const [body, setBody] = useState('')
  const [idempotencyKey, setIdempotencyKey] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError('')
    setBusy(true)
    try {
      let headers: Record<string, string> = {}
      if (headersText.trim()) {
        const parsed: unknown = JSON.parse(headersText)
        if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
          throw new Error('Headers must be a JSON object.')
        }
        headers = {}
        for (const [key, value] of Object.entries(parsed)) {
          if (typeof value !== 'string') {
            throw new Error(`Header “${key}” must be a string.`)
          }
          headers[key] = value
        }
      }

      const request: TaskRequest = {
        method: method.toUpperCase(),
        url,
        headers,
        body,
      }

      const input: CreateTaskInput = {
        name,
        timezone,
        priority,
        request,
        timeout_ms: timeoutMs,
        retry: { max_attempts: maxAttempts, backoff_ms: backoffMs },
      }
      if (schedule === 'cron') {
        input.cron = cron
      }
      if (schedule === 'at') {
        if (!at) {
          throw new Error('Choose a date and time.')
        }
        input.at = new Date(at).toISOString()
      }

      const task = await api.createTask(input, idempotencyKey)
      navigate(`/tasks/${task.id}`)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not create task')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">New task</h1>
        <p className="text-sm text-slate-500">The endpoint is called now, once, or on a cron schedule.</p>
      </div>

      {error && (
        <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800">
          {error}
        </div>
      )}

      <form onSubmit={(event) => void submit(event)} className="card space-y-5 p-5">
        <div className="grid gap-4 sm:grid-cols-2">
          <label>
            <span className="label">Name</span>
            <input id="name" name="name" className="input" value={name} onChange={(e) => setName(e.target.value)} required maxLength={255} />
          </label>
          <label>
            <span className="label">Method</span>
            <select id="method" name="method" className="input" value={method} onChange={(e) => setMethod(e.target.value)}>
              {['GET', 'POST', 'PUT', 'PATCH', 'DELETE'].map((verb) => (
                <option key={verb} value={verb}>{verb}</option>
              ))}
            </select>
          </label>
        </div>

        <label>
          <span className="label">URL</span>
          <input id="url" name="url" className="input" type="url" value={url} onChange={(e) => setUrl(e.target.value)} placeholder="https://api.example.com/run" required />
        </label>

        <div className="grid gap-4 sm:grid-cols-3">
          <label>
            <span className="label">Priority</span>
            <select id="priority" name="priority" className="input" value={priority} onChange={(e) => setPriority(parsePriority(e.target.value))}>
              {['critical', 'high', 'normal', 'low'].map((value) => (
                <option key={value} value={value}>{value}</option>
              ))}
            </select>
          </label>
          <label>
            <span className="label">Schedule</span>
            <select id="schedule" name="schedule" className="input" value={schedule} onChange={(e) => setSchedule(parseSchedule(e.target.value))}>
              <option value="now">Now</option>
              <option value="at">At a time</option>
              <option value="cron">Cron</option>
            </select>
          </label>
          <label>
            <span className="label">Timezone</span>
            <input id="timezone" name="timezone" className="input" value={timezone} onChange={(e) => setTimezone(e.target.value)} />
          </label>
        </div>

        {schedule === 'at' && (
          <label>
            <span className="label">Run at</span>
            <input id="at" name="at" className="input" type="datetime-local" value={at} onChange={(e) => setAt(e.target.value)} />
          </label>
        )}

        {schedule === 'cron' && (
          <label>
            <span className="label">Cron expression</span>
            <input id="cron" name="cron" className="input" value={cron} onChange={(e) => setCron(e.target.value)} placeholder="0 9 * * *" />
            <span className="mt-1 block text-xs text-slate-500">Five fields: minute hour day month weekday.</span>
          </label>
        )}

        <div className="grid gap-4 sm:grid-cols-3">
          <label>
            <span className="label">Timeout (ms)</span>
            <input id="timeout" name="timeout" className="input" type="number" min={100} max={300000} value={timeoutMs} onChange={(e) => setTimeoutMs(Number(e.target.value))} />
          </label>
          <label>
            <span className="label">Max attempts</span>
            <input id="attempts" name="attempts" className="input" type="number" min={1} max={25} value={maxAttempts} onChange={(e) => setMaxAttempts(Number(e.target.value))} />
          </label>
          <label>
            <span className="label">Backoff (ms)</span>
            <input id="backoff" name="backoff" className="input" type="number" min={100} max={3600000} value={backoffMs} onChange={(e) => setBackoffMs(Number(e.target.value))} />
          </label>
        </div>

        <label>
          <span className="label">Headers (optional JSON)</span>
          <textarea id="headers" name="headers" className="input font-mono" rows={3} value={headersText} onChange={(e) => setHeadersText(e.target.value)} placeholder={'{"Authorization": "Bearer …"}'} />
          <span className="mt-1 block text-xs text-slate-500">Encrypted at rest. Never returned by the API.</span>
        </label>

        <label>
          <span className="label">Body (optional)</span>
          <textarea id="body" name="body" className="input font-mono" rows={3} value={body} onChange={(e) => setBody(e.target.value)} placeholder='{"hello":"world"}' />
        </label>

        <label>
          <span className="label">Idempotency key (optional)</span>
          <input id="idempotency" name="idempotency" className="input" value={idempotencyKey} onChange={(e) => setIdempotencyKey(e.target.value)} placeholder="hello-1" />
        </label>

        <div className="flex justify-end gap-2">
          <button type="button" className="btn-secondary" onClick={() => navigate('/tasks')}>Cancel</button>
          <button type="submit" className="btn-primary" disabled={busy}>
            {busy ? 'Creating…' : 'Create task'}
          </button>
        </div>
      </form>
    </div>
  )
}

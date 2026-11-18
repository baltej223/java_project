import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api'
import type { Job } from '../types'
import { formatDate, shortId } from '../format'
import { StatusBadge } from '../components/Badges'

export function DlqPage() {
  const [jobs, setJobs] = useState<Job[]>([])
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState('')

  async function load() {
    setLoading(true)
    setError('')
    try {
      setJobs(await api.dlq())
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not load the DLQ')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void load()
  }, [])

  async function retry(job: Job) {
    setBusy(job.id)
    try {
      await api.retryJob(job.id)
      await load()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not retry job')
    } finally {
      setBusy('')
    }
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Dead letter queue</h1>
        <p className="text-sm text-slate-500">Jobs that used all their attempts. Retry grants one more attempt.</p>
      </div>

      {error && (
        <div className="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900">{error}</div>
      )}

      <section className="card overflow-hidden">
        {loading ? (
          <p className="px-4 py-6 text-sm text-slate-500">Loading…</p>
        ) : jobs.length === 0 ? (
          <p className="px-4 py-6 text-sm text-slate-500">The DLQ is empty.</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr>
                  <th className="table-head">Job</th>
                  <th className="table-head">Task</th>
                  <th className="table-head">Status</th>
                  <th className="table-head">Attempt</th>
                  <th className="table-head">HTTP</th>
                  <th className="table-head">Finished</th>
                  <th className="table-head">Error</th>
                  <th className="table-head">Actions</th>
                </tr>
              </thead>
              <tbody>
                {jobs.map((job) => (
                  <tr key={job.id} className="hover:bg-slate-50">
                    <td className="table-cell font-mono text-xs" title={job.id}>{shortId(job.id)}</td>
                    <td className="table-cell">
                      <Link className="font-medium text-indigo-600 hover:text-indigo-800" to={`/tasks/${job.task_id}`}>
                        {shortId(job.task_id)}
                      </Link>
                    </td>
                    <td className="table-cell"><StatusBadge status={job.status} /></td>
                    <td className="table-cell">{job.attempt}</td>
                    <td className="table-cell">{job.http_status ?? '—'}</td>
                    <td className="table-cell text-slate-500">{formatDate(job.finished_at)}</td>
                    <td className="table-cell max-w-xs truncate text-red-700" title={job.error}>{job.error || '—'}</td>
                    <td className="table-cell">
                      <button className="btn-secondary" disabled={busy === job.id} onClick={() => void retry(job)}>
                        Retry
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  )
}

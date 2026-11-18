import { useEffect, useState, type ReactNode } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { api } from '../api'
import type { Job, Task } from '../types'
import { formatDate, shortId } from '../format'
import { PriorityBadge, StatusBadge } from '../components/Badges'

export function TaskDetailPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const [task, setTask] = useState<Task | null>(null)
  const [jobs, setJobs] = useState<Job[]>([])
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)

  async function load(taskId: string) {
    setLoading(true)
    setError('')
    try {
      const [current, history] = await Promise.all([api.getTask(taskId), api.taskJobs(taskId, 100)])
      setTask(current)
      setJobs(history)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not load task')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    if (!id) {
      return
    }
    void load(id)
  }, [id])

  async function toggle() {
    if (!task) {
      return
    }
    setBusy(true)
    try {
      if (task.status === 'active') {
        await api.pauseTask(task.id)
      } else {
        await api.resumeTask(task.id)
      }
      await load(task.id)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not update task')
    } finally {
      setBusy(false)
    }
  }

  async function remove() {
    if (!task || !window.confirm(`Delete “${task.name}”? History is kept.`)) {
      return
    }
    setBusy(true)
    try {
      await api.deleteTask(task.id)
      navigate('/tasks')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not delete task')
    } finally {
      setBusy(false)
    }
  }

  async function retry(job: Job) {
    setBusy(true)
    try {
      await api.retryJob(job.id)
      if (id) {
        await load(id)
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not retry job')
    } finally {
      setBusy(false)
    }
  }

  if (!id) {
    return <p className="text-sm text-red-700">Missing task id.</p>
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <Link to="/tasks" className="text-sm font-medium text-indigo-600 hover:text-indigo-800">← Tasks</Link>
          <h1 className="mt-1 text-2xl font-semibold tracking-tight">{task?.name ?? 'Task'}</h1>
          <p className="text-sm text-slate-500">{id}</p>
        </div>
        <div className="flex gap-2">
          <button className="btn-secondary" disabled={busy || !task} onClick={() => void toggle()}>
            {task?.status === 'active' ? 'Pause' : 'Resume'}
          </button>
          <button className="btn-danger" disabled={busy || !task} onClick={() => void remove()}>
            Delete
          </button>
        </div>
      </div>

      {error && (
        <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800">{error}</div>
      )}

      {loading ? (
        <p className="text-sm text-slate-500">Loading…</p>
      ) : task ? (
        <>
          <section className="card grid gap-4 p-5 sm:grid-cols-2 lg:grid-cols-4">
            <Info label="Status"><StatusBadge status={task.status} /></Info>
            <Info label="Priority"><PriorityBadge priority={task.priority} /></Info>
            <Info label="Next run">{formatDate(task.next_run_at)}</Info>
            <Info label="Version">{task.version}</Info>
            <Info label="Method"><code className="text-sm">{task.request.method}</code></Info>
            <Info label="URL" wide><code className="break-all text-sm">{task.request.url}</code></Info>
            <Info label="Schedule" wide>
              {task.cron ? `cron ${task.cron} · ${task.timezone}` : task.at ? `once · ${formatDate(task.at)}` : 'now'}
            </Info>
            <Info label="Timeout">{task.timeout_ms} ms</Info>
            <Info label="Retry">
              {task.retry.max_attempts} attempts · {task.retry.backoff_ms} ms backoff
            </Info>
            <Info label="Created">{formatDate(task.created_at)}</Info>
            <Info label="Updated">{formatDate(task.updated_at)}</Info>
          </section>

          <section className="card overflow-hidden">
            <div className="border-b border-slate-200 px-4 py-3">
              <h2 className="font-semibold">Executions</h2>
              <p className="text-sm text-slate-500">Hot jobs and archived history, newest first.</p>
            </div>
            {jobs.length === 0 ? (
              <p className="px-4 py-6 text-sm text-slate-500">No executions yet.</p>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full">
                  <thead>
                    <tr>
                      <th className="table-head">Job</th>
                      <th className="table-head">Status</th>
                      <th className="table-head">Attempt</th>
                      <th className="table-head">HTTP</th>
                      <th className="table-head">Scheduled</th>
                      <th className="table-head">Finished</th>
                      <th className="table-head">Error</th>
                      <th className="table-head">Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {jobs.map((job) => (
                      <tr key={job.id} className="hover:bg-slate-50">
                        <td className="table-cell font-mono text-xs" title={job.id}>{shortId(job.id)}</td>
                        <td className="table-cell"><StatusBadge status={job.status} /></td>
                        <td className="table-cell">{job.attempt}</td>
                        <td className="table-cell">{job.http_status ?? '—'}</td>
                        <td className="table-cell text-slate-500">{formatDate(job.scheduled_at)}</td>
                        <td className="table-cell text-slate-500">{formatDate(job.finished_at)}</td>
                        <td className="table-cell max-w-xs truncate text-red-700" title={job.error}>{job.error || '—'}</td>
                        <td className="table-cell">
                          {job.status === 'dead' && (
                            <button className="btn-secondary" disabled={busy} onClick={() => void retry(job)}>
                              Retry
                            </button>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </section>
        </>
      ) : (
        <p className="text-sm text-slate-500">Task not found.</p>
      )}
    </div>
  )
}

function Info({ label, children, wide = false }: { label: string; children: ReactNode; wide?: boolean }) {
  return (
    <div className={wide ? 'sm:col-span-2' : ''}>
      <p className="text-xs font-semibold uppercase tracking-wide text-slate-400">{label}</p>
      <div className="mt-1 text-sm text-slate-800">{children}</div>
    </div>
  )
}

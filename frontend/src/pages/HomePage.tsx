import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api'
import type { Job, Task } from '../types'
import { formatDate } from '../format'
import { PriorityBadge, StatusBadge } from '../components/Badges'

export function HomePage() {
  const [tasks, setTasks] = useState<Task[]>([])
  const [dlq, setDlq] = useState<Job[]>([])
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let cancelled = false
    async function load() {
      setLoading(true)
      setError('')
      try {
        const [taskList, deadJobs] = await Promise.all([api.listTasks(), api.dlq()])
        if (!cancelled) {
          setTasks(taskList)
          setDlq(deadJobs)
        }
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : 'Could not load data')
        }
      } finally {
        if (!cancelled) {
          setLoading(false)
        }
      }
    }
    void load()
    return () => {
      cancelled = true
    }
  }, [])

  const active = tasks.filter((task) => task.status === 'active').length
  const paused = tasks.filter((task) => task.status === 'paused').length
  const recent = tasks.slice(0, 5)

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-2 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Overview</h1>
          <p className="text-sm text-slate-500">A quick look at your tenant&apos;s work.</p>
        </div>
        <Link to="/tasks/new" className="btn-primary">
          Create task
        </Link>
      </div>

      {error && (
        <div className="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900">
          {error}
        </div>
      )}

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Stat label="Tasks" value={tasks.length} />
        <Stat label="Active" value={active} />
        <Stat label="Paused" value={paused} />
        <Stat label="In DLQ" value={dlq.length} tone={dlq.length > 0 ? 'danger' : 'normal'} />
      </div>

      <section className="card overflow-hidden">
        <div className="flex items-center justify-between border-b border-slate-200 px-4 py-3">
          <h2 className="font-semibold">Recent tasks</h2>
          <Link to="/tasks" className="text-sm font-medium text-indigo-600 hover:text-indigo-800">
            View all
          </Link>
        </div>
        {loading ? (
          <p className="px-4 py-6 text-sm text-slate-500">Loading…</p>
        ) : recent.length === 0 ? (
          <p className="px-4 py-6 text-sm text-slate-500">No tasks yet.</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr>
                  <th className="table-head">Name</th>
                  <th className="table-head">Status</th>
                  <th className="table-head">Priority</th>
                  <th className="table-head">Next run</th>
                </tr>
              </thead>
              <tbody>
                {recent.map((task) => (
                  <tr key={task.id} className="hover:bg-slate-50">
                    <td className="table-cell">
                      <Link className="font-medium text-indigo-600 hover:text-indigo-800" to={`/tasks/${task.id}`}>
                        {task.name}
                      </Link>
                    </td>
                    <td className="table-cell"><StatusBadge status={task.status} /></td>
                    <td className="table-cell"><PriorityBadge priority={task.priority} /></td>
                    <td className="table-cell text-slate-500">{formatDate(task.next_run_at)}</td>
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

function Stat({ label, value, tone = 'normal' }: { label: string; value: number; tone?: 'normal' | 'danger' }) {
  return (
    <div className="card p-4">
      <p className="text-sm text-slate-500">{label}</p>
      <p className={`mt-1 text-3xl font-semibold ${tone === 'danger' ? 'text-red-600' : 'text-slate-900'}`}>
        {value}
      </p>
    </div>
  )
}

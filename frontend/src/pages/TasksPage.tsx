import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api'
import type { Task } from '../types'
import { formatDate } from '../format'
import { PriorityBadge, StatusBadge } from '../components/Badges'

export function TasksPage() {
  const [tasks, setTasks] = useState<Task[]>([])
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState('')

  async function load() {
    setLoading(true)
    setError('')
    try {
      setTasks(await api.listTasks())
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not load tasks')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void load()
  }, [])

  async function pause(task: Task) {
    setBusy(task.id)
    try {
      await api.pauseTask(task.id)
      await load()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not pause task')
    } finally {
      setBusy('')
    }
  }

  async function resume(task: Task) {
    setBusy(task.id)
    try {
      await api.resumeTask(task.id)
      await load()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not resume task')
    } finally {
      setBusy('')
    }
  }

  async function remove(task: Task) {
    if (!window.confirm(`Delete “${task.name}”? History is kept.`)) {
      return
    }
    setBusy(task.id)
    try {
      await api.deleteTask(task.id)
      await load()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not delete task')
    } finally {
      setBusy('')
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-2 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Tasks</h1>
          <p className="text-sm text-slate-500">Configuration and next scheduled run.</p>
        </div>
        <Link to="/tasks/new" className="btn-primary">Create task</Link>
      </div>

      {error && (
        <div className="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900">
          {error}
        </div>
      )}

      <section className="card overflow-hidden">
        {loading ? (
          <p className="px-4 py-6 text-sm text-slate-500">Loading…</p>
        ) : tasks.length === 0 ? (
          <p className="px-4 py-6 text-sm text-slate-500">No tasks yet. Create your first one.</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr>
                  <th className="table-head">Name</th>
                  <th className="table-head">Status</th>
                  <th className="table-head">Priority</th>
                  <th className="table-head">Schedule</th>
                  <th className="table-head">Next run</th>
                  <th className="table-head">Actions</th>
                </tr>
              </thead>
              <tbody>
                {tasks.map((task) => (
                  <tr key={task.id} className="hover:bg-slate-50">
                    <td className="table-cell">
                      <Link className="font-medium text-indigo-600 hover:text-indigo-800" to={`/tasks/${task.id}`}>
                        {task.name}
                      </Link>
                      <p className="text-xs text-slate-400">{task.id}</p>
                    </td>
                    <td className="table-cell"><StatusBadge status={task.status} /></td>
                    <td className="table-cell"><PriorityBadge priority={task.priority} /></td>
                    <td className="table-cell text-slate-600">
                      {task.cron ? `cron ${task.cron} (${task.timezone})` : task.at ? `once ${formatDate(task.at)}` : 'now'}
                    </td>
                    <td className="table-cell text-slate-500">{formatDate(task.next_run_at)}</td>
                    <td className="table-cell">
                      <div className="flex gap-2">
                        <Link className="btn-secondary" to={`/tasks/${task.id}/edit`}>Edit</Link>
                        {task.status === 'active' ? (
                          <button className="btn-secondary" disabled={busy === task.id} onClick={() => void pause(task)}>
                            Pause
                          </button>
                        ) : (
                          <button className="btn-secondary" disabled={busy === task.id} onClick={() => void resume(task)}>
                            Resume
                          </button>
                        )}
                        <button className="btn-danger" disabled={busy === task.id} onClick={() => void remove(task)}>
                          Delete
                        </button>
                      </div>
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

import type { JobStatus, Priority, TaskStatus } from '../types'

const statusStyles: Record<string, string> = {
  active: 'bg-emerald-100 text-emerald-800',
  paused: 'bg-amber-100 text-amber-800',
  deleted: 'bg-slate-100 text-slate-600',
  pending: 'bg-sky-100 text-sky-800',
  running: 'bg-indigo-100 text-indigo-800',
  succeeded: 'bg-emerald-100 text-emerald-800',
  dead: 'bg-red-100 text-red-800',
}

const priorityStyles: Record<Priority, string> = {
  critical: 'bg-red-100 text-red-800',
  high: 'bg-orange-100 text-orange-800',
  normal: 'bg-slate-100 text-slate-700',
  low: 'bg-slate-100 text-slate-500',
}

export function StatusBadge({ status }: { status: TaskStatus | JobStatus }) {
  return (
    <span className={`inline-flex rounded-full px-2 py-0.5 text-xs font-medium ${statusStyles[status] ?? 'bg-slate-100 text-slate-700'}`}>
      {status}
    </span>
  )
}

export function PriorityBadge({ priority }: { priority: Priority }) {
  return (
    <span className={`inline-flex rounded-full px-2 py-0.5 text-xs font-medium ${priorityStyles[priority]}`}>
      {priority}
    </span>
  )
}

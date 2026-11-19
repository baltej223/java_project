export type Priority = 'critical' | 'high' | 'normal' | 'low'
export type TaskStatus = 'active' | 'paused' | 'deleted'
export type JobStatus = 'pending' | 'running' | 'succeeded' | 'dead'

export interface TaskRequest {
  method: string
  url: string
  headers?: Record<string, string>
  body?: string
}

export interface RetryPolicy {
  max_attempts: number
  backoff_ms: number
}

export interface Task {
  id: string
  tenant_id: string
  name: string
  cron: string
  timezone: string
  at?: string
  priority: Priority
  request: TaskRequest
  timeout_ms: number
  retry: RetryPolicy
  status: TaskStatus
  next_run_at?: string
  version: number
  created_at: string
  updated_at: string
}

export interface Job {
  id: string
  tenant_id: string
  task_id: string
  scheduled_at: string
  priority: Priority
  request: TaskRequest
  timeout_ms: number
  retry: RetryPolicy
  status: JobStatus
  attempt: number
  next_attempt_at?: string
  worker_id?: string
  lease_expires_at?: string
  http_status?: number
  error?: string
  response_body?: string
  created_at?: string
  started_at?: string
  finished_at?: string
}

export interface CreateTaskInput {
  name: string
  preserve_secrets?: boolean
  cron?: string
  at?: string
  timezone: string
  priority: Priority
  request: TaskRequest
  timeout_ms: number
  retry: RetryPolicy
}

export interface ApiError {
  error: string
}

export interface LoginResponse {
  token: string
  tenant_id: string
  username: string
  expires_at: string
}

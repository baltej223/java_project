import type { CreateTaskInput, Job, Task } from './types'

export const TOKEN_KEY = 'crobat_token'

export function getToken(): string {
  return localStorage.getItem(TOKEN_KEY) ?? ''
}

export function setToken(token: string): void {
  if (token.trim()) {
    localStorage.setItem(TOKEN_KEY, token.trim())
  } else {
    localStorage.removeItem(TOKEN_KEY)
  }
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const token = getToken()
  if (!token) {
    throw new Error('Add a JWT token first.')
  }
  const headers = new Headers(init.headers)
  headers.set('Authorization', `Bearer ${token}`)
  if (init.body) {
    headers.set('Content-Type', 'application/json')
  }

  const response = await fetch(path, { ...init, headers })
  const text = await response.text()
  let data: unknown = null
  if (text) {
    try {
      data = JSON.parse(text)
    } catch {
      data = { error: text }
    }
  }
  if (!response.ok) {
    let message = `HTTP ${response.status}`
    if (data && typeof data === 'object' && 'error' in data) {
      const errorValue = data.error
      if (typeof errorValue === 'string') {
        message = errorValue
      }
    }
    throw new Error(message)
  }
  return data as T
}

export const api = {
  listTasks(): Promise<Task[]> {
    return request<Task[]>('/tasks')
  },

  getTask(id: string): Promise<Task> {
    return request<Task>(`/tasks/${id}`)
  },

  createTask(input: CreateTaskInput, idempotencyKey?: string): Promise<Task> {
    const headers: Record<string, string> = {}
    if (idempotencyKey?.trim()) {
      headers['Idempotency-Key'] = idempotencyKey.trim()
    }
    return request<Task>('/tasks', {
      method: 'POST',
      headers,
      body: JSON.stringify(input),
    })
  },

  pauseTask(id: string): Promise<Task> {
    return request<Task>(`/tasks/${id}/pause`, { method: 'POST' })
  },

  resumeTask(id: string): Promise<Task> {
    return request<Task>(`/tasks/${id}/resume`, { method: 'POST' })
  },

  deleteTask(id: string): Promise<void> {
    return request<void>(`/tasks/${id}`, { method: 'DELETE' })
  },

  taskJobs(id: string, limit = 100): Promise<Job[]> {
    return request<Job[]>(`/tasks/${id}/jobs?limit=${limit}`)
  },

  dlq(): Promise<Job[]> {
    return request<Job[]>('/dlq')
  },

  retryJob(id: string): Promise<Job> {
    return request<Job>(`/jobs/${id}/retry`, { method: 'POST' })
  },
}

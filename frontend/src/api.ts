import type { CreateTaskInput, Job, LoginResponse, Task } from './types'

export const TOKEN_KEY = 'chroner_token'
const TENANT_KEY = 'chroner_tenant'
const USER_KEY = 'chroner_user'

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

export function getTenant(): string {
  const stored = localStorage.getItem(TENANT_KEY) ?? ''
  if (stored) return stored
  const token = getToken()
  if (!token) return ''
  try {
    const payload = token.split('.')[1]
    if (!payload) return ''
    const normalized = payload.replace(/-/g, '+').replace(/_/g, '/').padEnd(Math.ceil(payload.length / 4) * 4, '=')
    const claims: unknown = JSON.parse(atob(normalized))
    if (claims && typeof claims === 'object' && 'sub' in claims) {
      const sub = claims.sub
      return typeof sub === 'string' ? sub : ''
    }
  } catch {
    return ''
  }
  return ''
}

export function getUsername(): string {
  return localStorage.getItem(USER_KEY) ?? ''
}

export function setSession(token: string, tenant: string, username: string): void {
  setToken(token)
  localStorage.setItem(TENANT_KEY, tenant)
  localStorage.setItem(USER_KEY, username)
}

export function clearSession(): void {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(TENANT_KEY)
  localStorage.removeItem(USER_KEY)
}

export async function login(tenant: string, username: string, password: string): Promise<LoginResponse> {
  const response = await fetch('/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ tenant, username, password }),
  })
  const text = await response.text()
  let data: unknown = null
  if (text) {
    try {
      data = JSON.parse(text)
    } catch {
      data = { error: text }
    }
  }
  if (response.status === 401) {
    clearSession()
    if (window.location.pathname !== '/login') {
      window.location.assign('/login')
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
  const result = data as LoginResponse
  setSession(result.token, result.tenant_id, result.username)
  return result
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
  if (response.status === 401) {
    clearSession()
    if (window.location.pathname !== '/login') {
      window.location.assign('/login')
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

  updateTask(id: string, input: CreateTaskInput): Promise<Task> {
    return request<Task>(`/tasks/${id}`, {
      method: 'PUT',
      body: JSON.stringify(input),
    })
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

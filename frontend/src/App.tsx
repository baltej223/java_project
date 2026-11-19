import type { ReactNode } from 'react'
import { Link, Navigate, Route, Routes } from 'react-router-dom'
import { getToken } from './api'
import { Layout } from './components/Layout'
import { LoginPage } from './pages/LoginPage'
import { HomePage } from './pages/HomePage'
import { TasksPage } from './pages/TasksPage'
import { TaskFormPage } from './pages/TaskFormPage'
import { TaskDetailPage } from './pages/TaskDetailPage'
import { DlqPage } from './pages/DlqPage'

function RequireAuth({ children }: { children: ReactNode }) {
  return getToken() ? <>{children}</> : <Navigate to="/login" replace />
}

function NotFound() {
  return (
    <div className="card p-6">
      <h1 className="text-xl font-semibold">Page not found</h1>
      <Link className="mt-3 inline-block text-sm font-medium text-indigo-600" to="/">
        Back to overview
      </Link>
    </div>
  )
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route
        element={
          <RequireAuth>
            <Layout />
          </RequireAuth>
        }
      >
        <Route index element={<HomePage />} />
        <Route path="tasks" element={<TasksPage />} />
        <Route path="tasks/new" element={<TaskFormPage />} />
        <Route path="tasks/:id/edit" element={<TaskFormPage />} />
        <Route path="tasks/:id" element={<TaskDetailPage />} />
        <Route path="dlq" element={<DlqPage />} />
        <Route path="*" element={<NotFound />} />
      </Route>
    </Routes>
  )
}

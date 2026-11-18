import { Link, Route, Routes } from 'react-router-dom'
import { Layout } from './components/Layout'
import { HomePage } from './pages/HomePage'
import { TasksPage } from './pages/TasksPage'
import { TaskFormPage } from './pages/TaskFormPage'
import { TaskDetailPage } from './pages/TaskDetailPage'
import { DlqPage } from './pages/DlqPage'

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
      <Route element={<Layout />}>
        <Route index element={<HomePage />} />
        <Route path="tasks" element={<TasksPage />} />
        <Route path="tasks/new" element={<TaskFormPage />} />
        <Route path="tasks/:id" element={<TaskDetailPage />} />
        <Route path="dlq" element={<DlqPage />} />
        <Route path="*" element={<NotFound />} />
      </Route>
    </Routes>
  )
}

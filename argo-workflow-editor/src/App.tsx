import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import WorkflowListPage from './pages/WorkflowListPage'
import WorkflowEditorPage from './pages/WorkflowEditorPage'
import WorkflowDetailPage from './pages/WorkflowDetailPage'
import WorkflowRunPage from './pages/WorkflowRunPage'

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<WorkflowListPage />} />
        <Route path="/workflows/new" element={<WorkflowEditorPage />} />
        <Route path="/workflows/:name" element={<WorkflowDetailPage />} />
        <Route path="/workflows/:name/edit" element={<WorkflowEditorPage />} />
        <Route path="/workflows/:name/runs/:runId" element={<WorkflowRunPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  )
}
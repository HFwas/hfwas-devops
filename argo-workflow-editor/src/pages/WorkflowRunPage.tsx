import { useState, useEffect } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { ArrowLeft } from 'lucide-react'
import { getWorkflowRun, getWorkflowRunLogs, getWorkflowRunEvents } from '../api/workflowApi'
import type { WorkflowRun, WorkflowRunLog, WorkflowEvent } from '../types/workflow'

export default function WorkflowRunPage() {
  const { name, runId } = useParams<{ name: string; runId: string }>()
  const navigate = useNavigate()
  const [run, setRun] = useState<WorkflowRun | null>(null)
  const [logs, setLogs] = useState<WorkflowRunLog[]>([])
  const [events, setEvents] = useState<WorkflowEvent[]>([])
  const [activeTab, setActiveTab] = useState<'logs' | 'events'>('logs')
  const [loading, setLoading] = useState(false)
  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null)

  useEffect(() => {
    if (!runId) return
    setLoading(true)
    Promise.all([
      getWorkflowRun(runId).then(setRun),
      getWorkflowRunLogs(runId).then(setLogs),
      getWorkflowRunEvents(runId).then(setEvents),
    ]).catch((e) => alert(e instanceof Error ? e.message : '加载失败'))
      .finally(() => setLoading(false))
  }, [runId])

  const statusType = (s: string) => {
    if (s === 'Running') return 'bg-blue-100 text-blue-700'
    if (s === 'Succeeded') return 'bg-green-100 text-green-700'
    if (s === 'Failed' || s === 'Error') return 'bg-red-100 text-red-700'
    return 'bg-gray-100 text-gray-600'
  }

  if (loading) return <div className="p-6 text-center text-muted text-sm">加载中...</div>
  if (!run) return <div className="p-6 text-center text-muted text-sm">运行记录不存在</div>

  const nodes = run.nodeStatus
    ? Object.entries(run.nodeStatus).map(([id, node]) => ({
        id,
        ...(node as Record<string, unknown>),
        displayName: (node as Record<string, unknown>).displayName as string,
        name: (node as Record<string, unknown>).name as string,
        phase: (node as Record<string, unknown>).phase as string,
      }))
    : []

  return (
    <div className="p-6 max-w-5xl mx-auto">
      {/* Header */}
      <div className="flex items-center gap-3 mb-6">
        <button
          className="flex items-center justify-center p-1.5 rounded text-muted hover:text-gray-800 hover:bg-chip-bg transition-colors"
          onClick={() => navigate(`/workflows/${name}`)}
        >
          <ArrowLeft size={18} />
        </button>
        <div>
          <h1 className="text-lg font-semibold text-gray-800">运行 #{runId?.substring(0, 8)}</h1>
          <span className={`inline-block mt-1 px-2 py-0.5 text-[11px] font-medium rounded-full ${statusType(run.status)}`}>
            {run.status}
          </span>
        </div>
      </div>

      {/* Node Graph */}
      {nodes.length > 0 && (
        <div className="bg-white rounded-xl border border-border p-4 mb-6">
          <h2 className="text-sm font-semibold text-gray-800 mb-3">节点状态</h2>
          <div className="flex flex-wrap gap-3">
            {nodes.map((node) => (
              <button
                key={node.id}
                className={`px-3 py-2 rounded-lg text-xs border transition-colors ${
                  selectedNodeId === node.id
                    ? 'border-primary bg-blue-50'
                    : 'border-border hover:border-gray-300'
                }`}
                onClick={() => setSelectedNodeId(node.id)}
              >
                <div className="font-medium text-gray-800">{node.displayName || node.name || node.id}</div>
                <div className={`mt-1 ${statusType(node.phase as string)} inline-block px-1.5 py-0.5 rounded`}>
                  {node.phase as string}
                </div>
              </button>
            ))}
          </div>
        </div>
      )}

      {/* Tabs */}
      <div className="bg-white rounded-xl border border-border">
        <div className="flex border-b border-border">
          <TabButton active={activeTab === 'logs'} onClick={() => setActiveTab('logs')}>日志</TabButton>
          <TabButton active={activeTab === 'events'} onClick={() => setActiveTab('events')}>事件</TabButton>
        </div>
        <div className="p-4 max-h-[500px] overflow-y-auto">
          {activeTab === 'logs' && (
            logs.length === 0
              ? <p className="text-xs text-muted text-center py-8">暂无日志</p>
              : <pre className="text-xs font-mono text-gray-700 whitespace-pre-wrap">{logs[0]?.logs || '无日志内容'}</pre>
          )}
          {activeTab === 'events' && (
            events.length === 0
              ? <p className="text-xs text-muted text-center py-8">暂无事件</p>
              : <table className="w-full text-xs">
                  <thead>
                    <tr className="text-muted border-b border-border">
                      <th className="text-left py-2 pr-2">类型</th>
                      <th className="text-left py-2 pr-2">原因</th>
                      <th className="text-left py-2 pr-2">消息</th>
                      <th className="text-left py-2">时间</th>
                    </tr>
                  </thead>
                  <tbody>
                    {events.map((ev, i) => (
                      <tr key={i} className="border-b border-border/50">
                        <td className="py-2 pr-2">{ev.type}</td>
                        <td className="py-2 pr-2 text-gray-600">{ev.reason}</td>
                        <td className="py-2 pr-2 text-gray-600">{ev.message}</td>
                        <td className="py-2 text-muted whitespace-nowrap">{ev.timestamp ? new Date(ev.timestamp).toLocaleString() : '-'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
          )}
        </div>
      </div>
    </div>
  )
}

function TabButton({ active, onClick, children }: { active: boolean; onClick: () => void; children: React.ReactNode }) {
  return (
    <button
      className={`px-4 py-2.5 text-xs font-medium transition-colors ${
        active ? 'text-primary border-b-2 border-primary' : 'text-muted hover:text-gray-700'
      }`}
      onClick={onClick}
    >
      {children}
    </button>
  )
}
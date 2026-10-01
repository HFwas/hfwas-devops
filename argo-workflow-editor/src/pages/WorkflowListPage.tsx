import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { Plus, RefreshCw, Play, Trash2, Search } from 'lucide-react'
import { pageWorkflows, submitWorkflow, deleteWorkflow } from '../api/workflowApi'
import type { WorkflowSummary } from '../types/workflow'

export default function WorkflowListPage() {
  const navigate = useNavigate()
  const [workflows, setWorkflows] = useState<WorkflowSummary[]>([])
  const [loading, setLoading] = useState(false)
  const [keyword, setKeyword] = useState('')
  const [statusFilter, setStatusFilter] = useState<string>('all')
  const [total, setTotal] = useState(0)
  const [pageNo, setPageNo] = useState(1)
  const pageSize = 20

  const fetch = async () => {
    setLoading(true)
    try {
      const res = await pageWorkflows({
        pageNo, pageSize,
        keyword: keyword || undefined,
        status: statusFilter === 'all' ? undefined : statusFilter,
      })
      setWorkflows(res.records)
      setTotal(res.total)
    } catch (e) {
      alert(e instanceof Error ? e.message : '加载失败')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { fetch() }, [pageNo, statusFilter])

  const handleRun = async (name: string) => {
    try {
      await submitWorkflow(name)
      fetch()
    } catch (e: unknown) {
      alert(e instanceof Error ? e.message : '运行失败')
    }
  }

  const handleDelete = async (name: string) => {
    if (!confirm(`确定删除工作流 "${name}"？`)) return
    try {
      await deleteWorkflow(name)
      fetch()
    } catch (e: unknown) {
      alert(e instanceof Error ? e.message : '删除失败')
    }
  }

  const statusIcon = (s: string) => {
    if (s === 'Running') return '🔵'
    if (s === 'Succeeded') return '✅'
    if (s === 'Failed') return '❌'
    if (s === 'Pending') return '⏳'
    if (s === 'Error') return '💥'
    return '⚪'
  }

  const statusType = (s: string) => {
    if (s === 'Running') return 'bg-blue-100 text-blue-700'
    if (s === 'Succeeded') return 'bg-green-100 text-green-700'
    if (s === 'Failed' || s === 'Error') return 'bg-red-100 text-red-700'
    return 'bg-gray-100 text-gray-600'
  }

  return (
    <div className="p-6 max-w-6xl mx-auto">
      {/* Toolbar */}
      <div className="flex items-center gap-3 mb-6">
        <div className="relative flex-1 max-w-xs">
          <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-muted" />
          <input
            className="w-full pl-8 pr-3 py-2 text-sm border border-border rounded-lg bg-white focus:outline-none focus:border-primary"
            placeholder="搜索工作流名称..."
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && fetch()}
          />
        </div>
        <select
          className="px-3 py-2 text-sm border border-border rounded-lg bg-white focus:outline-none focus:border-primary"
          value={statusFilter}
          onChange={(e) => { setStatusFilter(e.target.value); setPageNo(1) }}
        >
          <option value="all">全部</option>
          <option value="Running">运行中</option>
          <option value="Succeeded">已完成</option>
          <option value="Failed">失败</option>
        </select>
        <div className="flex-1" />
        <button
          className="flex items-center gap-1.5 px-3 py-2 text-sm text-gray-600 rounded-lg hover:bg-chip-bg transition-colors"
          onClick={fetch}
        >
          <RefreshCw size={14} />
          刷新
        </button>
        <button
          className="flex items-center gap-1.5 px-4 py-2 text-sm font-medium bg-primary text-white rounded-lg hover:bg-primary-hover transition-colors"
          onClick={() => navigate('/workflows/new')}
        >
          <Plus size={14} />
          新建工作流
        </button>
      </div>

      {/* Grid */}
      {loading ? (
        <div className="text-center py-20 text-muted text-sm">加载中...</div>
      ) : workflows.length === 0 ? (
        <div className="text-center py-20 text-muted">
          <div className="text-4xl mb-3">📋</div>
          <p className="text-sm">暂无工作流</p>
        </div>
      ) : (
        <>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
            {workflows.map((wf) => (
              <div
                key={wf.name}
                className="bg-white rounded-xl border border-border hover:shadow-md transition-shadow cursor-pointer"
                onClick={() => navigate(`/workflows/${wf.name}`)}
              >
                <div className="p-4">
                  <div className="flex items-start justify-between mb-3">
                    <div className="flex items-center gap-2">
                      <span className="text-lg">{statusIcon(wf.status)}</span>
                      <span className={`px-2 py-0.5 text-[11px] font-medium rounded-full ${statusType(wf.status)}`}>
                        {wf.status}
                      </span>
                    </div>
                  </div>
                  <div className="text-sm font-medium text-gray-800 mb-2 truncate">{wf.name}</div>
                  <div className="flex flex-wrap gap-x-4 gap-y-1 text-[11px] text-muted">
                    <span>⏱ {wf.duration || '-'}</span>
                    <span>🎯 {wf.entrypoint || '-'}</span>
                    <span>📅 {wf.startedAt ? new Date(wf.startedAt).toLocaleString() : '-'}</span>
                  </div>
                </div>
                <div className="flex border-t border-border" onClick={(e) => e.stopPropagation()}>
                  <button
                    className="flex-1 flex items-center justify-center gap-1 py-2 text-xs text-gray-500 hover:bg-chip-bg rounded-bl-xl transition-colors"
                    onClick={() => handleRun(wf.name)}
                  >
                    <Play size={12} /> 运行
                  </button>
                  <button
                    className="flex-1 flex items-center justify-center gap-1 py-2 text-xs text-gray-500 hover:bg-red-50 hover:text-red-600 rounded-br-xl transition-colors"
                    onClick={() => handleDelete(wf.name)}
                  >
                    <Trash2 size={12} /> 删除
                  </button>
                </div>
              </div>
            ))}
          </div>

          {total > pageSize && (
            <div className="flex justify-center mt-6 gap-2">
              {Array.from({ length: Math.ceil(total / pageSize) }, (_, i) => (
                <button
                  key={i}
                  className={`px-3 py-1 text-sm rounded-md transition-colors ${
                    pageNo === i + 1
                      ? 'bg-primary text-white'
                      : 'bg-white border border-border text-gray-600 hover:bg-chip-bg'
                  }`}
                  onClick={() => setPageNo(i + 1)}
                >
                  {i + 1}
                </button>
              ))}
            </div>
          )}
        </>
      )}
    </div>
  )
}
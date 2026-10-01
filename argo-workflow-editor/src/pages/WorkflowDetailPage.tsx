import { useState, useEffect } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { ArrowLeft, Play, Trash2, Edit3 } from 'lucide-react'
import { getWorkflow, submitWorkflow, deleteWorkflow, getWorkflowRuns } from '../api/workflowApi'
import type { WorkflowDetail, WorkflowRun } from '../types/workflow'

export default function WorkflowDetailPage() {
  const { name } = useParams<{ name: string }>()
  const navigate = useNavigate()
  const [detail, setDetail] = useState<WorkflowDetail | null>(null)
  const [runs, setRuns] = useState<WorkflowRun[]>([])
  const [loading, setLoading] = useState(false)

  const load = async () => {
    if (!name) return
    setLoading(true)
    try {
      const d = await getWorkflow(name)
      setDetail(d)
      const res = await getWorkflowRuns(name, { pageNo: 1, pageSize: 10 })
      setRuns(res.records)
    } catch (e: unknown) {
      alert(e instanceof Error ? e.message : '加载失败')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { load() }, [name])

  const handleRun = async () => {
    if (!name) return
    try {
      await submitWorkflow(name)
      load()
    } catch (e: unknown) {
      alert(e instanceof Error ? e.message : '运行失败')
    }
  }

  const handleDelete = async () => {
    if (!name || !confirm(`确定删除工作流 "${name}"？`)) return
    try {
      await deleteWorkflow(name)
      navigate('/')
    } catch (e: unknown) {
      alert(e instanceof Error ? e.message : '删除失败')
    }
  }

  const statusType = (s: string) => {
    if (s === 'Running') return 'bg-blue-100 text-blue-700'
    if (s === 'Succeeded') return 'bg-green-100 text-green-700'
    if (s === 'Failed' || s === 'Error') return 'bg-red-100 text-red-700'
    return 'bg-gray-100 text-gray-600'
  }

  if (loading) return <div className="p-6 text-center text-muted text-sm">加载中...</div>
  if (!detail) return <div className="p-6 text-center text-muted text-sm">工作流不存在</div>

  return (
    <div className="p-6 max-w-4xl mx-auto">
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <div className="flex items-center gap-3">
          <button
            className="flex items-center justify-center p-1.5 rounded text-muted hover:text-gray-800 hover:bg-chip-bg transition-colors"
            onClick={() => navigate('/')}
          >
            <ArrowLeft size={18} />
          </button>
          <div>
            <h1 className="text-lg font-semibold text-gray-800">{detail.name}</h1>
            <span className={`inline-block mt-1 px-2 py-0.5 text-[11px] font-medium rounded-full ${statusType(detail.status)}`}>
              {detail.status}
            </span>
          </div>
        </div>
        <div className="flex gap-2">
          <button
            className="flex items-center gap-1.5 px-3 py-2 text-sm text-gray-600 rounded-lg hover:bg-chip-bg transition-colors"
            onClick={() => navigate(`/workflows/${name}/edit`)}
          >
            <Edit3 size={14} /> 编辑
          </button>
          <button
            className="flex items-center gap-1.5 px-3 py-2 text-sm font-medium bg-primary text-white rounded-lg hover:bg-primary-hover transition-colors"
            onClick={handleRun}
          >
            <Play size={14} /> 运行
          </button>
          <button
            className="flex items-center gap-1.5 px-3 py-2 text-sm text-red-600 rounded-lg hover:bg-red-50 transition-colors"
            onClick={handleDelete}
          >
            <Trash2 size={14} /> 删除
          </button>
        </div>
      </div>

      {/* Info */}
      <div className="bg-white rounded-xl border border-border p-5 mb-6">
        <h2 className="text-sm font-semibold text-gray-800 mb-3">基本信息</h2>
        <div className="grid grid-cols-2 gap-4 text-sm">
          <InfoItem label="入口点" value={detail.entrypoint || '-'} />
          <InfoItem label="触发方式" value={detail.trigger || '-'} />
          <InfoItem label="开始时间" value={detail.startedAt ? new Date(detail.startedAt).toLocaleString() : '-'} />
          <InfoItem label="结束时间" value={detail.finishedAt ? new Date(detail.finishedAt).toLocaleString() : '-'} />
          <InfoItem label="耗时" value={detail.duration || '-'} />
          <InfoItem label="命名空间" value={detail.namespace || '-'} />
        </div>
        {detail.parameters && detail.parameters.length > 0 && (
          <>
            <h3 className="text-xs font-semibold text-gray-700 mt-4 mb-2">参数</h3>
            <div className="space-y-1">
              {detail.parameters.map((p, i) => (
                <div key={i} className="flex gap-4 text-xs">
                  <span className="font-mono text-gray-600 w-32">{p.name}</span>
                  <span className="text-gray-800">{p.value || '-'}</span>
                </div>
              ))}
            </div>
          </>
        )}
      </div>

      {/* Runs */}
      <div className="bg-white rounded-xl border border-border p-5">
        <h2 className="text-sm font-semibold text-gray-800 mb-3">运行历史</h2>
        {runs.length === 0 ? (
          <p className="text-xs text-muted py-4 text-center">暂无运行记录</p>
        ) : (
          <div className="space-y-2">
            {runs.map((run) => (
              <div
                key={run.id}
                className="flex items-center justify-between p-3 rounded-lg hover:bg-chip-bg transition-colors cursor-pointer"
                onClick={() => navigate(`/workflows/${name}/runs/${run.id}`)}
              >
                <div className="flex items-center gap-3">
                  <span className={`px-2 py-0.5 text-[11px] font-medium rounded-full ${statusType(run.status)}`}>
                    {run.status}
                  </span>
                  <span className="text-xs text-gray-600 font-mono">{run.id}</span>
                </div>
                <div className="text-xs text-muted">
                  {run.startedAt ? new Date(run.startedAt).toLocaleString() : '-'}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}

function InfoItem({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <span className="text-muted">{label}</span>
      <div className="text-gray-800 mt-0.5">{value}</div>
    </div>
  )
}
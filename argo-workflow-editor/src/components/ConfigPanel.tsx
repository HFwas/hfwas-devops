import type { FlowNodeData } from '../types/workflow'

interface ConfigPanelProps {
  node: { id: string; data: FlowNodeData } | null
  onUpdate: (id: string, data: Partial<FlowNodeData['config']>) => void
}

export default function ConfigPanel({ node, onUpdate }: ConfigPanelProps) {
  if (!node) {
    return (
      <aside className="w-[280px] flex-shrink-0 flex flex-col bg-card-bg border-l border-border">
        <div className="px-3 py-2.5 text-xs font-semibold text-gray-800 border-b border-border">配置</div>
        <div className="flex-1 flex flex-col items-center justify-center text-muted text-xs gap-2">
          <div className="opacity-40 text-3xl">⚙</div>
          <p>点击画布中的节点<br />即可编辑配置</p>
        </div>
      </aside>
    )
  }

  const config = node.data.config || {}
  const meta = node.data.meta

  return (
    <aside className="w-[280px] flex-shrink-0 flex flex-col bg-card-bg border-l border-border">
      <div className="px-3 py-2.5 text-xs font-semibold text-gray-800 border-b border-border">
        {node.data.label || '配置'}
      </div>
      <div className="flex-1 overflow-y-auto p-3 space-y-3">
        {/* 节点名称 */}
        <Section label="节点名称">
          <input
            className="w-full px-2 py-1.5 text-xs border border-border rounded-md bg-white focus:outline-none focus:border-primary"
            value={node.data.label}
            onChange={(e) => onUpdate(node.id, {})}
            // For name change we need to update label through parent
          />
        </Section>

        {/* 镜像 */}
        {(config.image || meta?.defaultImage) && (
          <Section label="镜像">
            <input
              className="w-full px-2 py-1.5 text-xs border border-border rounded-md bg-white focus:outline-none focus:border-primary font-mono"
              value={config.image || ''}
              placeholder={meta?.defaultImage}
              onChange={(e) => onUpdate(node.id, { image: e.target.value })}
            />
          </Section>
        )}

        {/* 命令 */}
        {(config.command || meta?.defaultCommand) && (
          <Section label="命令">
            <textarea
              className="w-full px-2 py-1.5 text-xs border border-border rounded-md bg-white focus:outline-none focus:border-primary font-mono resize-y"
              rows={4}
              value={config.command || ''}
              placeholder={meta?.defaultCommand}
              onChange={(e) => onUpdate(node.id, { command: e.target.value })}
            />
          </Section>
        )}

        {/* 重试策略 */}
        <Section label="重试策略">
          <select
            className="w-full px-2 py-1.5 text-xs border border-border rounded-md bg-white focus:outline-none focus:border-primary"
            value={config.retry?.limit ?? 0}
            onChange={(e) => onUpdate(node.id, { retry: { limit: Number(e.target.value) } })}
          >
            <option value={0}>不重试</option>
            <option value={1}>重试 1 次</option>
            <option value={3}>重试 3 次</option>
            <option value={5}>重试 5 次</option>
          </select>
        </Section>

        {/* 超时 */}
        <Section label="超时 (秒)">
          <input
            type="number"
            className="w-full px-2 py-1.5 text-xs border border-border rounded-md bg-white focus:outline-none focus:border-primary"
            value={config.timeout ?? 300}
            min={1}
            onChange={(e) => onUpdate(node.id, { timeout: Number(e.target.value) })}
          />
        </Section>
      </div>
    </aside>
  )
}

function Section({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <div className="text-[11px] font-medium text-gray-700 mb-1">{label}</div>
      {children}
    </div>
  )
}
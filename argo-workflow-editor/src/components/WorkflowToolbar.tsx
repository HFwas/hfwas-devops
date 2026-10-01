import { ArrowLeft, Play, Save, Code2 } from 'lucide-react'
import { useNavigate } from 'react-router-dom'

interface ToolbarProps {
  name: string
  onNameChange: (name: string) => void
  onSave: () => void
  onRun: () => void
  onToggleMode: () => void
  isYamlMode: boolean
}

export default function WorkflowToolbar({
  name, onNameChange, onSave, onRun, onToggleMode, isYamlMode,
}: ToolbarProps) {
  const navigate = useNavigate()

  return (
    <header className="flex items-center justify-between px-4 py-2 bg-card-bg border-b border-border flex-shrink-0">
      <div className="flex items-center gap-2">
        <button
          className="flex items-center justify-center p-1 rounded text-muted hover:text-gray-800 hover:bg-chip-bg transition-colors"
          onClick={() => navigate('/')}
        >
          <ArrowLeft size={16} />
        </button>
        <input
          className="px-2 py-1 text-sm border border-border rounded-md bg-white focus:outline-none focus:border-primary w-[200px]"
          placeholder="工作流名称"
          value={name}
          onChange={(e) => onNameChange(e.target.value)}
        />
      </div>
      <div className="flex items-center gap-1.5">
        <ToolbarButton onClick={onToggleMode}>
          <Code2 size={13} />
          {isYamlMode ? '可视化' : 'YAML'}
        </ToolbarButton>
        <ToolbarButton onClick={onSave}>
          <Save size={13} />
          保存
        </ToolbarButton>
        <button
          className="flex items-center gap-1 px-3 py-1.5 text-xs font-medium bg-primary text-white rounded-md hover:bg-primary-hover transition-colors"
          onClick={onRun}
        >
          <Play size={13} />
          运行
        </button>
      </div>
    </header>
  )
}

function ToolbarButton({ children, onClick }: { children: React.ReactNode; onClick: () => void }) {
  return (
    <button
      className="flex items-center gap-1 px-2.5 py-1.5 text-xs text-gray-600 rounded-md hover:bg-chip-bg transition-colors"
      onClick={onClick}
    >
      {children}
    </button>
  )
}
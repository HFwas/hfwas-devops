import { useEffect, useRef, useState } from 'react'
import { Search, X } from 'lucide-react'
import { Input } from '@/components/ui/input'
import { cn } from '@/lib/utils'

/**
 * 列表搜索框：输入防抖后回调，Enter 立即触发，支持清空。
 * 受控组件：value 是当前生效值，输入草稿在组件内部维护。
 */
export function SearchInput({
  value,
  onChange,
  onSearch,
  placeholder = '搜索',
  debounceMs = 300,
  disabled = false,
  autoFocus = false,
  className,
}: {
  value: string
  onChange: (value: string) => void
  onSearch?: (value: string) => void
  placeholder?: string
  debounceMs?: number
  disabled?: boolean
  autoFocus?: boolean
  className?: string
}) {
  const [draft, setDraft] = useState(value)
  const onChangeRef = useRef(onChange)
  const emittedRef = useRef(value)

  useEffect(() => {
    onChangeRef.current = onChange
  }, [onChange])

  useEffect(() => {
    setDraft(value)
    emittedRef.current = value
  }, [value])

  useEffect(() => {
    if (draft === value) return
    const timer = setTimeout(() => {
      emittedRef.current = draft
      onChangeRef.current(draft)
    }, debounceMs)
    return () => clearTimeout(timer)
  }, [draft, value, debounceMs])

  function submit(next: string) {
    emittedRef.current = next
    onChange(next)
    onSearch?.(next)
  }

  return (
    <div data-slot="search-input" className={cn('relative w-56', className)}>
      <Search className="pointer-events-none absolute left-2.5 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
      <Input
        type="search"
        value={draft}
        disabled={disabled}
        autoFocus={autoFocus}
        aria-label={placeholder}
        placeholder={placeholder}
        className="h-8 pl-8 pr-8 [&::-webkit-search-cancel-button]:hidden"
        onChange={(event) => setDraft(event.target.value)}
        onKeyDown={(event) => {
          if (event.key === 'Enter') {
            event.preventDefault()
            submit(draft)
          }
          if (event.key === 'Escape') {
            setDraft('')
            submit('')
          }
        }}
      />
      {draft && !disabled ? (
        <button
          type="button"
          aria-label="清空搜索"
          className="absolute right-1.5 top-1/2 flex size-6 -translate-y-1/2 items-center justify-center rounded text-muted-foreground hover:bg-muted hover:text-foreground"
          onClick={() => {
            setDraft('')
            submit('')
          }}
        >
          <X className="size-3.5" />
        </button>
      ) : null}
    </div>
  )
}

export function YamlEditor({
  id,
  label,
  value,
  onChange,
  readOnly = false,
  minHeightClass = 'min-h-64',
}: {
  id: string
  label: string
  value: string
  onChange?: (value: string) => void
  readOnly?: boolean
  minHeightClass?: string
}) {
  return (
    <label htmlFor={id} className="flex min-h-0 flex-col gap-2">
      <span className="text-sm font-medium">{label}</span>
      <textarea
        id={id}
        value={value}
        readOnly={readOnly}
        spellCheck={false}
        onChange={readOnly ? undefined : (event) => onChange?.(event.target.value)}
        className={`${minHeightClass} w-full rounded-md border border-input bg-background px-3 py-2 font-mono text-xs leading-5 read-only:bg-muted/40`}
      />
    </label>
  )
}

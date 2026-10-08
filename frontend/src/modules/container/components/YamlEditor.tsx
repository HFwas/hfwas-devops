export function YamlEditor({
  id,
  label,
  description,
  value,
  onChange,
  readOnly = false,
  error,
  minHeightClass = 'min-h-64',
}: {
  id: string
  label: string
  description?: string
  value: string
  onChange?: (value: string) => void
  readOnly?: boolean
  error?: string | null
  minHeightClass?: string
}) {
  const errorId = error ? `${id}-error` : undefined
  return (
    <div className="flex min-h-0 flex-col gap-2">
      <label htmlFor={id} className="text-sm font-medium">
        {label}
      </label>
      {description ? <p className="text-xs text-muted-foreground">{description}</p> : null}
      <textarea
        id={id}
        value={value}
        readOnly={readOnly}
        spellCheck={false}
        aria-invalid={error ? true : undefined}
        aria-describedby={errorId}
        onChange={readOnly ? undefined : (event) => onChange?.(event.target.value)}
        className={`${minHeightClass} w-full rounded-md border bg-background px-3 py-2 font-mono text-xs leading-5 read-only:bg-muted/40 ${error ? 'border-destructive' : 'border-input'}`}
      />
      {error ? (
        <p id={errorId} role="status" className="text-sm text-destructive">
          {error}
        </p>
      ) : null}
    </div>
  )
}

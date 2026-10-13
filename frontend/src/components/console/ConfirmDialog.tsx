import type { ReactNode } from 'react'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from '@/components/ui/alert-dialog'

/**
 * 全站统一的二次确认弹窗，替代 window.confirm。
 * 用 AlertDialog 渲染，跟随主题 token（含深色模式），可禁用确认按钮等待异步。
 * `children` 作为触发元素，须是单个可点击元素（通常是 <Button>）。
 */
export function ConfirmDialog({
  title,
  description,
  confirmText = '确认',
  cancelText = '取消',
  destructive = false,
  confirmDisabled = false,
  onConfirm,
  children,
}: {
  title: ReactNode
  description?: ReactNode
  confirmText?: string
  cancelText?: string
  /** 危险操作：确认按钮用 destructive 配色。 */
  destructive?: boolean
  confirmDisabled?: boolean
  onConfirm: () => void
  children: ReactNode
}) {
  return (
    <AlertDialog>
      <AlertDialogTrigger asChild>{children}</AlertDialogTrigger>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>{title}</AlertDialogTitle>
          {description ? <AlertDialogDescription>{description}</AlertDialogDescription> : null}
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel>{cancelText}</AlertDialogCancel>
          <AlertDialogAction
            variant={destructive ? 'destructive' : 'default'}
            disabled={confirmDisabled}
            onClick={onConfirm}
          >
            {confirmText}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}
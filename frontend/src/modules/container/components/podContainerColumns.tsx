import { DataTable, type DataTableColumn } from '@/components/console/DataTable'
import { StatusIcon } from '@/components/console/StatusIcon'
import type { ContainerStatus } from '@/modules/container/types/resource'

/** Pod 容器表：镜像列吃掉剩余宽度并省略，就绪 / 重启保持窄列对齐。 */
export function podContainerColumns(): DataTableColumn<ContainerStatus>[] {
  return [
    {
      id: 'name',
      header: '容器',
      headClassName: 'w-[22%]',
      className: 'w-[22%] whitespace-nowrap',
      cell: (item) => item.name,
    },
    {
      id: 'state',
      header: '状态',
      headClassName: 'w-28',
      className: 'w-28 whitespace-nowrap',
      cell: (item) => <StatusIcon variant="dot" status={item.state} />,
    },
    {
      id: 'image',
      header: '镜像',
      className: 'overflow-hidden',
      cell: (item) => (
        <span className="block truncate font-mono text-xs" title={item.image}>
          {item.image}
        </span>
      ),
    },
    {
      id: 'ready',
      header: '就绪',
      headClassName: 'w-16',
      className: 'w-16 whitespace-nowrap',
      cell: (item) => (item.ready ? '是' : '否'),
    },
    {
      id: 'restart',
      header: '重启',
      headClassName: 'w-16',
      className: 'w-16 whitespace-nowrap tabular-nums',
      cell: (item) => String(item.restartCount),
    },
  ]
}

export function PodContainerTable({ rows }: { rows: ContainerStatus[] }) {
  return (
    <DataTable
      fixed
      columns={podContainerColumns()}
      data={rows}
      getRowId={(item) => item.name}
      empty="没有容器"
    />
  )
}

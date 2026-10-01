import { create } from 'zustand'
import type { ClusterVO } from '@/modules/container/types/cluster'

interface ContainerClusterState {
  currentId: string | null
  namespace: string
  setCurrentId: (id: string | null) => void
  setNamespace: (namespace: string) => void
  pickDefault: (clusters: ClusterVO[]) => void
}

export const useContainerCluster = create<ContainerClusterState>((set, get) => ({
  currentId: null,
  namespace: '',
  setCurrentId: (id) => set({ currentId: id, namespace: get().currentId === id ? get().namespace : '' }),
  setNamespace: (namespace) => set({ namespace }),
  pickDefault: (clusters) => {
    const current = get().currentId
    if (current && clusters.some((item) => item.id === current)) return
    set({ currentId: clusters[0]?.id ?? null, namespace: '' })
  },
}))

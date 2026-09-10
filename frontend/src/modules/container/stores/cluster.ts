import { defineStore } from 'pinia'
import { ref } from 'vue'
import { clusterApi } from '../api/cluster'
import { namespaceApi } from '../api/namespace'
import type { ClusterVO } from '../types/cluster'
import type { NamespaceInfo } from '../types/resource'

export const useClusterStore = defineStore('container-cluster', () => {
  const currentCluster = ref<ClusterVO | null>(null)
  const clusterList = ref<ClusterVO[]>([])
  const namespaceList = ref<NamespaceInfo[]>([])
  const currentNamespace = ref<string>('default')

  async function fetchClusters() {
    const page = await clusterApi.page({ pageNo: 1, pageSize: 100 })
    clusterList.value = page.records ?? []
    if (!currentCluster.value && clusterList.value.length > 0) {
      currentCluster.value = clusterList.value[0]
    }
  }

  function setCurrent(cluster: ClusterVO) {
    currentCluster.value = cluster
  }

  async function fetchNamespaces() {
    if (!currentCluster.value) return
    try {
      const list = await namespaceApi.list(currentCluster.value.id)
      namespaceList.value = list ?? []
      // Reset to 'default' if it exists in the list, otherwise use first namespace
      if (namespaceList.value.length > 0) {
        const hasDefault = namespaceList.value.some(ns => ns.name === 'default')
        currentNamespace.value = hasDefault ? 'default' : namespaceList.value[0].name
      }
    } catch {
      namespaceList.value = []
    }
  }

  function setNamespace(ns: string) {
    currentNamespace.value = ns
  }

  return {
    currentCluster,
    clusterList,
    namespaceList,
    currentNamespace,
    fetchClusters,
    setCurrent,
    fetchNamespaces,
    setNamespace,
  }
})
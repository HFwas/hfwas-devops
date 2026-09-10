import { defineStore } from 'pinia'
import { ref } from 'vue'
import { clusterApi } from '../api/cluster'
import type { ClusterVO } from '../types/cluster'

export const useClusterStore = defineStore('container-cluster', () => {
  const currentCluster = ref<ClusterVO | null>(null)
  const clusterList = ref<ClusterVO[]>([])

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

  return { currentCluster, clusterList, fetchClusters, setCurrent }
})
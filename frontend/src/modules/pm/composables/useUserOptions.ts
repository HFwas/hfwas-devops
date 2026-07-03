import { userAuthApi } from '@/modules/user/api'
import type { UserProfile } from '@/modules/user/types'
import { asId } from '@/modules/pm/utils/id'

const cache = { list: null as UserProfile[] | null, labelMap: {} as Record<string, string> }

export function useUserOptions() {
  const loading = ref(false)
  const users = ref<UserProfile[]>([])
  const labelMap = ref<Record<string, string>>({})

  async function load(force = false) {
    if (!force && cache.list) {
      users.value = cache.list
      labelMap.value = cache.labelMap
      return
    }
    loading.value = true
    try {
      const list = await userAuthApi.userOptions()
      const map: Record<string, string> = {}
      for (const user of list) {
        if (user.id != null) {
          map[asId(user.id)] = user.displayName || user.username
        }
      }
      users.value = list
      labelMap.value = map
      cache.list = list
      cache.labelMap = map
    } finally {
      loading.value = false
    }
  }

  const selectOptions = computed(() =>
    users.value
      .filter((u) => u.id != null)
      .map((u) => ({ label: u.displayName || u.username, value: asId(u.id!) })),
  )

  onMounted(() => load())

  return { loading, users, labelMap, selectOptions, load }
}

export function invalidateUserOptionsCache() {
  cache.list = null
  cache.labelMap = {}
}

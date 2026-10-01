import { StrictMode, useEffect, useState } from 'react'
import { createRoot } from 'react-dom/client'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { RouterProvider } from 'react-router'
import { Toaster } from '@/components/ui/sonner'
import { router } from '@/router'
import { initKeycloak } from '@/shared/keycloak'
import { useAuthStore } from '@/stores/auth'
import '@/index.css'
import '@/shared/console/useConsoleTheme'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: 1, refetchOnWindowFocus: false },
  },
})

function Boot() {
  const [ready, setReady] = useState(false)

  useEffect(() => {
    let cancelled = false
    void (async () => {
      await Promise.race([
        initKeycloak(),
        new Promise((resolve) => setTimeout(resolve, 8000)),
      ])
      if (useAuthStore.getState().isLoggedIn()) {
        await useAuthStore.getState().fetchMe()
      }
      if (!cancelled) setReady(true)
    })()
    return () => {
      cancelled = true
    }
  }, [])

  if (!ready) {
    return <p className="p-6 text-sm text-muted-foreground">正在连接登录…</p>
  }

  return (
    <>
      <RouterProvider router={router} />
      <Toaster />
    </>
  )
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <Boot />
    </QueryClientProvider>
  </StrictMode>,
)

import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AuthProvider } from './auth/AuthProvider'
import { App } from './app/App'
import './index.css'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // Operational data changes through other people's actions, so a short freshness window
      // is right; the server remains the authority on every read.
      staleTime: 30_000,
      retry: (failureCount, error) => {
        const status = (error as { status?: number })?.status
        // Never retry a refusal: a 403 will still be a 403 in thirty seconds.
        if (status === 401 || status === 403 || status === 404) return false
        return failureCount < 2
      },
      refetchOnWindowFocus: true,
    },
  },
})

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <AuthProvider>
          <App />
        </AuthProvider>
      </BrowserRouter>
    </QueryClientProvider>
  </StrictMode>,
)
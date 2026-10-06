import { useEffect } from 'react'
import { useQuery } from '@tanstack/react-query'
import { api } from '../../lib/api'
import type { InstitutionBranding } from './types'

/**
 * Branding is public and identical for everyone, so it is fetched once and shared. It is
 * deliberately not part of the auth context: the login screen needs it before anybody has
 * signed in, and every other screen can read the same cached entry.
 */
export function useInstitutionBranding() {
  const query = useQuery({
    queryKey: ['institution-branding'],
    queryFn: () => api<InstitutionBranding | null>('/api/v1/public/institution'),
    staleTime: 5 * 60_000,
    // A missing or not-yet-set-up institution must not leave the login screen blank.
    retry: false,
  })

  const branding = query.data ?? undefined

  // The browser tab and favicon follow the institution, so a rebrand is visible immediately.
  useEffect(() => {
    const title = branding?.portalTitle || branding?.name
    if (title) document.title = `${title} · Sign in`
    return () => {
      document.title = 'Education ERP'
    }
  }, [branding?.portalTitle, branding?.name])

  useEffect(() => {
    const favicon = branding?.faviconUrl
    if (!favicon) return
    let link = document.querySelector<HTMLLinkElement>('link[rel="icon"]')
    if (!link) {
      link = document.createElement('link')
      link.rel = 'icon'
      document.head.appendChild(link)
    }
    link.href = favicon
  }, [branding?.faviconUrl])

  return { branding, isLoading: query.isLoading }
}
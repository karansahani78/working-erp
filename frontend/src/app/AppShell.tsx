import { useEffect, useState } from 'react'
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { useAuth } from '../auth/AuthProvider'
import { NAV, NAV_GROUP_ORDER, type NavItem } from '../app/navigation'
import { api } from '../lib/api'
import { Button, Spinner, TextInput } from '../components/ui'
import { routeFor, type SearchResponse } from '../features/search/types'

export function AppShell() {
  const { user, signOut } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [mobileNavOpen, setMobileNavOpen] = useState(false)

  useEffect(() => {
    setMobileNavOpen(false)
  }, [location.pathname])

  const visible = NAV.filter((item) => !item.permissions || item.permissions.every((p) => user?.permissions.includes(p)))
  const groups = NAV_GROUP_ORDER.filter((group) => visible.some((item) => item.group === group))

  return (
    <div className="flex min-h-screen bg-canvas">
      {mobileNavOpen && (
        <div
          className="fixed inset-0 z-30 bg-ink/30 lg:hidden"
          onClick={() => setMobileNavOpen(false)}
          aria-hidden="true"
        />
      )}

      <aside
        className={[
          'fixed inset-y-0 left-0 z-40 flex w-64 flex-col border-r border-border bg-surface',
          'transition-transform lg:static lg:translate-x-0',
          mobileNavOpen ? 'translate-x-0' : '-translate-x-full',
        ].join(' ')}
      >
        <div className="flex h-16 items-center gap-2.5 border-b border-border px-5">
          <div className="flex h-8 w-8 items-center justify-center rounded-md bg-primary text-sm font-bold text-white">
            E
          </div>
          <div className="min-w-0">
            <p className="truncate text-sm font-semibold text-ink">Education ERP</p>
            <p className="truncate text-xs text-ink-subtle">Institution workspace</p>
          </div>
        </div>

        <nav className="flex-1 overflow-y-auto px-3 py-4">
          {groups.map((group) => (
            <div key={group} className="mb-5">
              <p className="px-3 pb-1.5 text-[11px] font-semibold tracking-wider text-ink-subtle uppercase">
                {group}
              </p>
              <ul className="flex flex-col gap-0.5">
                {visible
                  .filter((item) => item.group === group)
                  .map((item) => (
                    <li key={item.to}>
                      <SidebarLink item={item} />
                    </li>
                  ))}
              </ul>
            </div>
          ))}
        </nav>

        <div className="border-t border-border p-3">
          <div className="flex items-center gap-3 rounded-lg px-2 py-2">
            <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-primary-soft text-sm font-semibold text-primary">
              {initials(user?.displayName ?? user?.username ?? '?')}
            </div>
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-medium text-ink">{user?.displayName ?? user?.username}</p>
              <p className="truncate text-xs text-ink-subtle">{titleCase(user?.role)}</p>
            </div>
          </div>
          <Button variant="ghost" className="mt-1 w-full justify-start" onClick={() => void signOut()}>
            Sign out
          </Button>
        </div>
      </aside>

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="sticky top-0 z-20 flex h-16 items-center gap-3 border-b border-border bg-canvas/85 px-4 backdrop-blur lg:px-8">
          <button
            type="button"
            className="rounded-md p-2 text-ink-muted hover:bg-surface-soft lg:hidden"
            onClick={() => setMobileNavOpen((open) => !open)}
            aria-label="Toggle navigation"
          >
            <svg width="20" height="20" viewBox="0 0 24 24" aria-hidden="true">
              <path d="M4 7h16M4 12h16M4 17h16" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
            </svg>
          </button>

          <GlobalSearch />

          <div className="ml-auto flex items-center gap-2">
            {user?.mustChangePassword && (
              <Button variant="secondary" onClick={() => navigate('/change-password')}>
                Set a new password
              </Button>
            )}
          </div>
        </header>

        <main className="flex-1 px-4 py-6 lg:px-8 lg:py-8">
          <Outlet />
        </main>
      </div>
    </div>
  )
}

function SidebarLink({ item }: { item: NavItem }) {
  return (
    <NavLink
      to={item.to}
      end={item.to === '/'}
      className={({ isActive }) =>
        [
          'flex items-center gap-2.5 rounded-lg px-3 py-2 text-sm transition-colors',
          isActive
            ? 'bg-primary-soft font-medium text-primary'
            : 'text-ink-muted hover:bg-surface-soft hover:text-ink',
        ].join(' ')
      }
    >
      <NavIcon name={item.icon} />
      <span className="truncate">{item.label}</span>
    </NavLink>
  )
}

/** One search box for the whole system, since the backend already searches every source. */
function GlobalSearch() {
  const navigate = useNavigate()
  const [term, setTerm] = useState('')

  const results = useQuery({
    queryKey: ['search', term],
    queryFn: () => api<SearchResponse>('/api/v1/search', { query: { q: term } }),
    enabled: term.trim().length >= 2,
    staleTime: 15_000,
  })

  const open = term.trim().length >= 2

  return (
    <div className="relative w-full max-w-md">
      <form
        onSubmit={(event) => {
          event.preventDefault()
          if (open) navigate(`/search?q=${encodeURIComponent(term.trim())}`)
        }}
        role="search"
      >
        <TextInput
          value={term}
          onChange={(event) => setTerm(event.target.value)}
          placeholder="Search students, staff, invoices…"
          aria-label="Search the whole system"
          className="pl-9"
        />
        <svg
          className="pointer-events-none absolute top-1/2 left-3 h-4 w-4 -translate-y-1/2 text-ink-subtle"
          viewBox="0 0 24 24"
          aria-hidden="true"
        >
          <circle cx="11" cy="11" r="7" stroke="currentColor" strokeWidth="2" fill="none" />
          <path d="m20 20-3.5-3.5" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
        </svg>
      </form>

      {open && (
        <div className="absolute top-full left-0 z-30 mt-2 w-full overflow-hidden rounded-card border border-border bg-surface shadow-lg">
          {results.isLoading && (
            <div className="flex justify-center p-4">
              <Spinner className="h-5 w-5 text-primary" />
            </div>
          )}
          {results.isError && <p className="p-4 text-sm text-danger">Search is unavailable right now.</p>}
          {results.data?.results.length === 0 && (
            <p className="p-4 text-sm text-ink-subtle">No records match “{term.trim()}”.</p>
          )}
          <ul className="max-h-96 divide-y divide-border overflow-y-auto">
            {results.data?.results.slice(0, 8).map((hit) => (
              <li key={`${hit.source}-${hit.id}`}>
                <button
                  type="button"
                  onClick={() => navigate(routeFor(hit))}
                  className="flex w-full flex-col items-start gap-0.5 px-4 py-2.5 text-left hover:bg-surface-soft"
                >
                  <span className="text-sm font-medium text-ink">{hit.heading}</span>
                  <span className="text-xs text-ink-subtle">
                    {hit.sourceLabel}
                    {hit.reference ? ` · ${hit.reference}` : ''}
                    {hit.detail ? ` · ${hit.detail}` : ''}
                  </span>
                </button>
              </li>
            ))}
          </ul>
          {results.data && results.data.results.length > 0 && (
            <button
              type="button"
              onClick={() => navigate(`/search?q=${encodeURIComponent(term.trim())}`)}
              className="w-full border-t border-border px-4 py-2 text-center text-xs font-medium text-primary hover:bg-surface-soft"
            >
              See all results
            </button>
          )}
        </div>
      )}
    </div>
  )
}

const ICONS: Record<string, string> = {
  grid: 'M4 4h7v7H4zM13 4h7v7h-7zM4 13h7v7H4zM13 13h7v7h-7z',
  users: 'M16 19v-1a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v1M9 7a3 3 0 1 0 0-6 3 3 0 0 0 0 6M22 19v-1a4 4 0 0 0-3-3.9',
  user: 'M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2M12 3a4 4 0 1 0 0 8 4 4 0 0 0 0-8',
  badge: 'M3 7h18v13H3zM8 7V5a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2',
  wallet: 'M3 7h16a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7zm0 0V6a2 2 0 0 1 2-2h11',
  sun: 'M12 4V2m0 20v-2m8-8h2M2 12h2m13.7-5.7 1.4-1.4M4.9 19.1l1.4-1.4m0-11.4L4.9 4.9m14.2 14.2-1.4-1.4M16 12a4 4 0 1 1-8 0 4 4 0 0 1 8 0',
  school: 'M3 10 12 4l9 6-9 6-9-6zm4 3v5c0 1 3 2 5 2s5-1 5-2v-5',
  list: 'M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01',
  calendar: 'M4 6h16v14H4zM4 10h16M8 3v4M16 3v4',
  award: 'M12 3a5 5 0 1 0 0 10 5 5 0 0 0 0-10M8.5 12 7 21l5-2.5L17 21l-1.5-9',
  chart: 'M4 20V10M10 20V4M16 20v-7M22 20H2',
  file: 'M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8zM14 3v5h5M9 13h6M9 17h6',
  scroll: 'M6 3h11a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V5M9 7h7M9 11h7M9 15h4',
  check: 'M4 12l5 5L20 6',
  coins: 'M12 8c4.4 0 8-1.3 8-3s-3.6-3-8-3-8 1.3-8 3 3.6 3 8 3M4 5v6c0 1.7 3.6 3 8 3s8-1.3 8-3V5M4 11v6c0 1.7 3.6 3 8 3s8-1.3 8-3v-6',
  receipt: 'M5 3v18l2-1.5L9 21l2-1.5L13 21l2-1.5L17 21l2-1.5V3H5M9 8h6M9 12h6',
  card: 'M2 7h20v10H2zM2 11h20',
  undo: 'M3 10h11a5 5 0 0 1 0 10H8M3 10l4-4M3 10l4 4',
  ledger: 'M4 4h13a2 2 0 0 1 2 2v14H6a2 2 0 0 1-2-2V4zm4 4h7M8 12h7M8 16h5',
  library: 'M4 4h5v16H4zM11 4h4v16h-4zM17 5l3 15',
  box: 'M3 8l9-5 9 5v8l-9 5-9-5V8zm0 0l9 5 9-5M12 13v8',
  tag: 'M3 12V5a2 2 0 0 1 2-2h7l9 9-9 9-9-9zM7.5 7.5h.01',
  folder: 'M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7z',
  doc: 'M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8zM14 3v5h5',
  upload: 'M12 16V4m0 0L8 8m4-4 4 4M4 17v2a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-2',
  shield: 'M12 3l8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6l8-3z',
  key: 'M15 7a4 4 0 1 1-3.5 5.9L4 20.4V17h3v-3h3l1.5-1.5A4 4 0 0 1 15 7',
  lock: 'M6 11h12v10H6zM9 11V7a3 3 0 0 1 6 0v4',
  cog: 'M12 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-2.9 1.2V21a2 2 0 1 1-4 0v-.1A1.7 1.7 0 0 0 7 19.4a1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0-1.2-2.9H1a2 2 0 1 1 0-4h.1A1.7 1.7 0 0 0 2.6 7a1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H7a1.7 1.7 0 0 0 1-1.5V1a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V7a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z',
  inbox: 'M3 13h5l1.5 3h5L16 13h5M3 13l3-8h12l3 8v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-6z',
  layers: 'M12 3 3 8l9 5 9-5-9-5zM3 14l9 5 9-5M3 11l9 5 9-5',
  book: 'M4 4h11a3 3 0 0 1 3 3v13H7a3 3 0 0 1-3-3V4zm14 3h2v13H7',
}

function NavIcon({ name }: { name: string }) {
  return (
    <svg width="17" height="17" viewBox="0 0 24 24" fill="none" aria-hidden="true" className="shrink-0">
      <path
        d={ICONS[name] ?? ICONS.grid}
        stroke="currentColor"
        strokeWidth="1.7"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}

function initials(name: string): string {
  const parts = name.trim().split(/\s+/).slice(0, 2)
  return parts.map((part) => part.charAt(0).toUpperCase()).join('') || '?'
}

function titleCase(value?: string): string {
  if (!value) return ''
  return value
    .toLowerCase()
    .split('_')
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ')
}

export function ShellFallback() {
  return (
    <div className="flex min-h-screen items-center justify-center bg-canvas">
      <Spinner className="h-8 w-8 text-primary" />
    </div>
  )
}


import { useQuery } from '@tanstack/react-query'
import { api } from '../../lib/api'
import { useAuth } from '../../auth/AuthProvider'
import { Badge, Panel, QueryBoundary, type BadgeTone } from '../../components/ui'

interface Tile {
  label: string
  value: string | number
  unit: string | null
  detail: string | null
}

interface Section {
  name: string
  requires: string | null
  tiles: Tile[]
}

interface Alert {
  severity: string
  kind: string
  message: string
  count: number
}

interface Dashboard {
  asOf: string
  sections: Section[]
  alerts: Alert[]
}

/**
 * The backend offers three dashboards, and which one applies is decided by permission, not by
 * role name. Exactly one is requested so a user never sees a 403 on their own home page.
 */
function pickDashboard(permissions: string[]): { path: string; title: string } {
  if (permissions.includes('DASHBOARD_PRINCIPAL')) {
    return { path: '/api/v1/dashboards/principal', title: 'Principal overview' }
  }
  if (permissions.includes('DASHBOARD_ACCOUNTANT')) {
    return { path: '/api/v1/dashboards/accountant', title: 'Finance overview' }
  }
  return { path: '/api/v1/dashboards/principal', title: 'Overview' }
}

const SEVERITY_TONE: Record<string, BadgeTone> = {
  CRITICAL: 'danger',
  HIGH: 'danger',
  WARNING: 'warning',
  MEDIUM: 'warning',
  INFO: 'info',
  LOW: 'info',
}

export function DashboardPage() {
  const { user } = useAuth()
  const { path, title } = pickDashboard(user?.permissions ?? [])

  const dashboard = useQuery({
    queryKey: ['dashboard', path],
    queryFn: () => api<Dashboard>(path),
    staleTime: 60_000,
  })

  return (
    <div className="mx-auto flex max-w-7xl flex-col gap-6">
      <header>
        <h1 className="text-2xl font-semibold text-ink">{title}</h1>
        <p className="mt-1 text-sm text-ink-subtle">
          {greeting()}, {user?.displayName ?? user?.username}
          {dashboard.data && (
            <span className="ml-2">· as at {formatDateTime(dashboard.data.asOf)}</span>
          )}
        </p>
      </header>

      <QueryBoundary
        isLoading={dashboard.isLoading}
        error={dashboard.error}
        data={dashboard.data}
        onRetry={() => void dashboard.refetch()}
        loadingRows={6}
        empty={<p className="text-sm text-ink-subtle">This dashboard has nothing to show yet.</p>}
      >
        {(data) => (
          <>
            {data.alerts.length > 0 && (
              <section aria-label="Attention needed" className="flex flex-col gap-2">
                {[...data.alerts]
                  .sort((a, b) => severityRank(a.severity) - severityRank(b.severity))
                  .map((alert) => (
                    <div
                      key={`${alert.kind}-${alert.message}`}
                      className="flex flex-wrap items-center gap-3 rounded-card border border-border bg-surface px-4 py-3"
                    >
                      <Badge tone={SEVERITY_TONE[alert.severity.toUpperCase()] ?? 'neutral'}>
                        {alert.severity}
                      </Badge>
                      <p className="min-w-0 flex-1 text-sm text-ink">{alert.message}</p>
                      {alert.count > 0 && (
                        <span className="nums text-sm font-semibold text-ink-muted">{alert.count}</span>
                      )}
                    </div>
                  ))}
              </section>
            )}

            {data.sections.map((section) => (
              <Panel key={section.name} title={section.name}>
                <dl className="grid grid-cols-2 gap-x-6 gap-y-5 sm:grid-cols-3 lg:grid-cols-4">
                  {section.tiles.map((tile) => (
                    <div key={tile.label} className="min-w-0">
                      <dt className="truncate text-xs tracking-wide text-ink-subtle uppercase">
                        {tile.label}
                      </dt>
                      <dd className="mt-1">
                        <span className="nums text-2xl font-semibold text-ink">{formatValue(tile)}</span>
                        {tile.detail && (
                          <p className="mt-0.5 text-xs text-ink-subtle">{tile.detail}</p>
                        )}
                      </dd>
                    </div>
                  ))}
                </dl>
              </Panel>
            ))}
          </>
        )}
      </QueryBoundary>
    </div>
  )
}

function formatValue(tile: Tile): string {
  const raw = tile.value
  if (typeof raw === 'number') {
    return `${new Intl.NumberFormat(undefined, { maximumFractionDigits: 2 }).format(raw)}${
      tile.unit === 'percent' ? '%' : ''
    }`
  }
  return `${raw}${tile.unit === 'percent' ? '%' : ''}`
}

function severityRank(severity: string): number {
  const order: Record<string, number> = { CRITICAL: 0, HIGH: 1, WARNING: 2, MEDIUM: 3, INFO: 4, LOW: 5 }
  return order[severity.toUpperCase()] ?? 9
}

function greeting(): string {
  const hour = new Date().getHours()
  if (hour < 12) return 'Good morning'
  if (hour < 18) return 'Good afternoon'
  return 'Good evening'
}

export function formatDateTime(value: string): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString(undefined, {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

export function formatDate(value: string | null | undefined): string {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' })
}

export { Skeleton } from '../../components/ui'

/**
 * The table every list screen uses. Columns are declared as data so that adding a column
 * never means restyling markup, and the loading, empty, error and pagination states are
 * handled once here rather than in twenty screens.
 */
import type { ReactNode } from 'react'
import {
  Button,
  EmptyState,
  ErrorState,
  LoadingState,
  StatusBadge,
  Skeleton,
  Badge,
} from './ui'

export interface Column<T> {
  key: string
  header: string
  render?: (row: T) => ReactNode
  align?: 'left' | 'right'
  /** Hidden below the given breakpoint to keep narrow screens readable. */
  hideBelow?: 'sm' | 'md' | 'lg' | 'xl'
  width?: string
}

export interface PageInfo {
  page: number
  totalPages: number
  totalElements: number
  first: boolean
  last: boolean
}

const HIDE: Record<string, string> = {
  sm: 'hidden sm:table-cell',
  md: 'hidden md:table-cell',
  lg: 'hidden lg:table-cell',
  xl: 'hidden xl:table-cell',
}

export function DataTable<T>({
  columns,
  rows,
  getKey,
  isLoading,
  error,
  onRetry,
  page,
  onPageChange,
  empty,
  loadingRows = 8,
  toolbar,
}: {
  columns: Column<T>[]
  rows: T[] | undefined
  getKey: (row: T) => string
  isLoading: boolean
  error: unknown
  onRetry?: () => void
  page?: PageInfo
  onPageChange?: (page: number) => void
  empty?: ReactNode
  loadingRows?: number
  toolbar?: ReactNode
}) {
  return (
    <div>
      {toolbar && <div className="border-b border-border p-4">{toolbar}</div>}

      {isLoading ? (
        rows ? (
          // Keep the previous page on screen while the next one loads, rather than flashing.
          <div className="animate-pulse opacity-60">{renderBody()}</div>
        ) : (
          <LoadingState rows={loadingRows} />
        )
      ) : error ? (
        <div className="p-5">
          <ErrorState error={error} onRetry={onRetry} />
        </div>
      ) : !rows || rows.length === 0 ? (
        empty ?? <EmptyState title="Nothing to show" />
      ) : (
        <>
          <div className="overflow-x-auto">{renderBody()}</div>
          {page && onPageChange && <Pager page={page} onChange={onPageChange} />}
        </>
      )}
    </div>
  )

  function renderBody() {
    return (
      <table className="w-full border-collapse text-sm">
        <thead>
          <tr className="border-b border-border text-left">
            {columns.map((column) => (
              <th
                key={column.key}
                scope="col"
                style={column.width ? { width: column.width } : undefined}
                className={[
                  'px-4 py-2.5 text-xs font-semibold tracking-wide text-ink-subtle uppercase',
                  column.align === 'right' ? 'text-right' : '',
                  column.hideBelow ? HIDE[column.hideBelow] : '',
                ].join(' ')}
              >
                {column.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {(rows ?? []).map((row) => (
            <tr key={getKey(row)} className="border-b border-border last:border-0 hover:bg-surface-soft/60">
              {columns.map((column) => (
                <td
                  key={column.key}
                  className={[
                    'px-4 py-3 align-middle',
                    column.align === 'right' ? 'nums text-right' : '',
                    column.hideBelow ? HIDE[column.hideBelow] : '',
                  ].join(' ')}
                >
                  {column.render ? column.render(row) : String(valueOf(row, column.key) ?? '—')}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    )
  }
}

export function Pager({ page, onChange }: { page: PageInfo; onChange: (page: number) => void }) {
  if (page.totalPages <= 1) {
    return (
      <p className="border-t border-border px-4 py-3 text-xs text-ink-subtle">
        {page.totalElements} record{page.totalElements === 1 ? '' : 's'}
      </p>
    )
  }
  return (
    <div className="flex flex-wrap items-center justify-between gap-3 border-t border-border px-4 py-3">
      <p className="nums text-xs text-ink-subtle">
        Page {page.page + 1} of {page.totalPages} · {page.totalElements} records
      </p>
      <div className="flex gap-2">
        <Button variant="secondary" disabled={page.first} onClick={() => onChange(page.page - 1)}>
          Previous
        </Button>
        <Button variant="secondary" disabled={page.last} onClick={() => onChange(page.page + 1)}>
          Next
        </Button>
      </div>
    </div>
  )
}

function valueOf(row: unknown, key: string): unknown {
  return (row as Record<string, unknown>)?.[key]
}

/** Page title, optional explanation and the primary action, in one consistent arrangement. */
export function PageHeader({
  title,
  description,
  actions,
  count,
}: {
  title: string
  description?: string
  actions?: ReactNode
  count?: number
}) {
  return (
    <header className="flex flex-wrap items-end justify-between gap-4">
      <div>
        <h1 className="text-2xl font-semibold text-ink">{title}</h1>
        {description && <p className="mt-1 text-sm text-ink-subtle">{description}</p>}
        {typeof count === 'number' && (
          <p className="nums mt-1 text-sm text-ink-subtle">
            {count} record{count === 1 ? '' : 's'}
          </p>
        )}
      </div>
      {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
    </header>
  )
}

export { Badge, StatusBadge, Skeleton }
import { useRef, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useFocusTrap } from '../../lib/useFocusTrap'
import { api, type PageResponse } from '../../lib/api'
import { describeError } from '../../auth/AuthProvider'
import {
  Badge,
  Button,
  EmptyState,
  Field,
  Panel,
  Select,
  TextInput,
} from '../../components/ui'
import { PageHeader, Pager } from '../../components/DataTable'
import { dateTime, humanise, text } from '../../lib/format'
import type { AuditActionCount, AuditEvent } from './types'

/**
 * Actions that change or reveal data are what an auditor reads this log for, so they are
 * pinned to the top of the filter with their real counts. Everything else is in the
 * full vocabulary.
 */
const NOTABLE = ['CREATE', 'UPDATE', 'DELETE', 'APPROVE', 'REJECT', 'PUBLISH', 'POST', 'REFUND', 'PERMISSION_CHANGE', 'PASSWORD_CHANGE']

export function AuditPage() {
  const [term, setTerm] = useState('')
  const [action, setAction] = useState('')
  const [entityType, setEntityType] = useState('')
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [page, setPage] = useState(0)
  const [selected, setSelected] = useState<AuditEvent | null>(null)

  const events = useQuery({
    queryKey: ['audit-events', { term, action, entityType, from, to, page }],
    queryFn: () =>
      api<PageResponse<AuditEvent>>('/api/v1/audit/events', {
        query: {
          term: term || undefined,
          action: action || undefined,
          entityType: entityType || undefined,
          from: from ? new Date(`${from}T00:00:00`).toISOString() : undefined,
          to: to ? new Date(`${to}T23:59:59`).toISOString() : undefined,
          page,
          size: 25,
        },
      }),
  })

  const vocabulary = useQuery({
    queryKey: ['audit-actions'],
    queryFn: () => api<AuditActionCount[]>('/api/v1/audit/actions'),
  })

  const rows = events.data?.data ?? []
  const counts = vocabulary.data ?? []
  const total = events.data?.totalElements ?? 0

  function reset() {
    setTerm('')
    setAction('')
    setEntityType('')
    setFrom('')
    setTo('')
    setPage(0)
  }

  return (
    <div className="mx-auto flex max-w-7xl flex-col gap-5">
      <PageHeader
        title="Audit log"
        description="Every privileged change, in the order it happened. Entries are written once and never edited."
      />

      <Panel title="Filter">
        <form
          className="grid gap-4 md:grid-cols-6"
          onSubmit={(event) => {
            event.preventDefault()
            setPage(0)
          }}
        >
          <div className="md:col-span-2">
            <Field label="Search">
              <TextInput
                name="term"
                placeholder="Summary or who did it"
                defaultValue={term}
                onChange={(event) => setTerm(event.target.value)}
              />
            </Field>
          </div>
          <Field label="Action">
            <Select name="action" value={action} onChange={(event) => setAction(event.target.value)}>
              <option value="">Any action</option>
              {counts.map((entry) => (
                <option key={entry.action} value={entry.action}>
                  {humanise(entry.action)}
                  {entry.occurrences > 0 ? ` (${entry.occurrences})` : ''}
                </option>
              ))}
            </Select>
          </Field>
          <Field label="Record type">
            <TextInput
              name="entityType"
              placeholder="e.g. User"
              defaultValue={entityType}
              onChange={(event) => setEntityType(event.target.value)}
            />
          </Field>
          <Field label="From">
            <TextInput name="from" type="date" value={from} onChange={(event) => setFrom(event.target.value)} />
          </Field>
          <Field label="To">
            <TextInput name="to" type="date" value={to} onChange={(event) => setTo(event.target.value)} />
          </Field>

          <div className="flex items-end gap-2 md:col-span-6">
            <Button type="submit">Apply</Button>
            <Button type="button" variant="ghost" onClick={reset}>
              Clear
            </Button>
            <span className="ml-auto text-sm text-ink-subtle">
              {total.toLocaleString()} {total === 1 ? 'event' : 'events'}
            </span>
          </div>
        </form>

        {NOTABLE.some((name) => counts.some((entry) => entry.action === name && entry.occurrences > 0)) && (
          <div className="mt-4 flex flex-wrap gap-1.5 border-t border-border pt-4">
            {counts
              .filter((entry) => NOTABLE.includes(entry.action) && entry.occurrences > 0)
              .map((entry) => (
                <button
                  key={entry.action}
                  type="button"
                  onClick={() => {
                    setAction(entry.action)
                    setPage(0)
                  }}
                  className={[
                    'rounded-full px-2.5 py-1 text-xs font-medium transition-colors',
                    action === entry.action
                      ? 'bg-primary text-white'
                      : 'bg-surface-soft text-ink-muted hover:bg-border',
                  ].join(' ')}
                >
                  {humanise(entry.action)}
                  <span className="nums ml-1.5 opacity-70">{entry.occurrences}</span>
                </button>
              ))}
          </div>
        )}
      </Panel>

      <Panel padded={false} title="Events">
        {events.isLoading ? (
          <p className="p-5 text-sm text-ink-subtle">Loading the trail…</p>
        ) : events.isError ? (
          <EmptyState title="Could not load the audit log" description={describeError(events.error)} />
        ) : rows.length === 0 ? (
          <EmptyState title="Nothing matches" description="Try a wider date range or clear the filter." />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full border-collapse text-sm">
              <thead>
                <tr className="border-b border-border text-left">
                  <Th>When</Th>
                  <Th>Who</Th>
                  <Th>Action</Th>
                  <Th hideBelow="md">Record</Th>
                  <Th>What happened</Th>
                  <Th>Outcome</Th>
                </tr>
              </thead>
              <tbody>
                {rows.map((event) => (
                  <tr
                    key={event.id}
                    className="cursor-pointer border-b border-border last:border-0 hover:bg-surface-soft"
                    onClick={() => setSelected(event)}
                  >
                    <td className="nums whitespace-nowrap px-4 py-3 text-ink-subtle">
                      {dateTime(event.occurredAt)}
                    </td>
                    <td className="whitespace-nowrap px-4 py-3 text-ink">
                      {event.actorUsername ?? 'System'}
                    </td>
                    <td className="px-4 py-3">
                      <Badge tone={toneFor(event.action)}>{humanise(event.action)}</Badge>
                    </td>
                    <td className="hidden px-4 py-3 text-ink-muted md:table-cell">
                      {event.entityType}
                      {event.entityLabel ? (
                        <span className="block text-xs text-ink-subtle">{event.entityLabel}</span>
                      ) : null}
                    </td>
                    <td className="px-4 py-3 text-ink">{event.summary ?? text(event.entityId)}</td>
                    <td className="whitespace-nowrap px-4 py-3">
                      {event.succeeded ? (
                        <Badge tone="success">Done</Badge>
                      ) : (
                        <Badge tone="danger">Failed</Badge>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Panel>

      {events.data && (
        <Pager
          page={
            events.data ?? {
              page: 0,
              totalPages: 0,
              totalElements: 0,
              first: true,
              last: true,
            }
          }
          onChange={setPage}
        />
      )}

      {selected && <EventDetail event={selected} onClose={() => setSelected(null)} />}
    </div>
  )
}

/**
 * Before and after snapshots are stored as JSON text. Rendering them as formatted JSON
 * shows exactly what changed without the log having to interpret it.
 */
function EventDetail({ event, onClose }: { event: AuditEvent; onClose: () => void }) {
  const panel = useRef<HTMLDivElement>(null)
  useFocusTrap(panel, true, onClose)

  return (
    <div
      className="fixed inset-0 z-50 flex items-start justify-center overflow-y-auto bg-ink/30 p-4"
      role="dialog"
      aria-modal="true"
      aria-label="Audit event"
      onClick={onClose}
    >
      <div
        ref={panel}
        tabIndex={-1}
        className="my-8 w-full max-w-3xl rounded-xl border border-border bg-surface shadow-lg"
        onClick={(click) => click.stopPropagation()}
      >
        <header className="flex items-start justify-between gap-4 border-b border-border p-5">
          <div>
            <div className="flex items-center gap-2">
              <Badge tone={toneFor(event.action)}>{humanise(event.action)}</Badge>
              {event.module && <Badge>{humanise(event.module)}</Badge>}
              {!event.succeeded && <Badge tone="danger">Failed</Badge>}
            </div>
            <h2 className="mt-2 font-semibold text-ink">{event.summary ?? 'Audit event'}</h2>
            <p className="mt-1 text-sm text-ink-subtle">
              {dateTime(event.occurredAt)} · {event.actorUsername ?? 'System'}
              {event.ipAddress ? ` from ${event.ipAddress}` : ''}
            </p>
          </div>
          <Button variant="ghost" size="sm" onClick={onClose} aria-label="Close">
            Close
          </Button>
        </header>

        <div className="grid gap-4 p-5">
          {!event.succeeded && event.failureReason && (
            <div className="rounded-lg bg-danger-soft px-3 py-2 text-sm text-danger">
              {event.failureReason}
            </div>
          )}

          <div className="grid gap-3 sm:grid-cols-2">
            <Meta label="Record type" value={event.entityType} />
            <Meta label="Record id" value={event.entityId ?? '—'} mono />
            <Meta label="Label" value={event.entityLabel ?? '—'} />
            <Meta label="Request id" value={event.requestId ?? '—'} mono />
          </div>

          {(event.beforeState || event.afterState) && (
            <div className="grid gap-3 lg:grid-cols-2">
              <Snapshot title="Before" json={event.beforeState} tone="danger" />
              <Snapshot title="After" json={event.afterState} tone="success" />
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

function Snapshot({ title, json, tone }: { title: string; json: string | null; tone: 'danger' | 'success' }) {
  // Snapshots are stored as text, so parse defensively without setting state during render.
  let pretty = '—'
  let failed = false
  if (json) {
    try {
      pretty = JSON.stringify(JSON.parse(json), null, 2)
    } catch {
      failed = true
      pretty = json
    }
  }
  return (
    <div>
      <p className="mb-1.5 text-xs font-semibold tracking-wide text-ink-subtle uppercase">{title}</p>
      <pre
        className={[
          'max-h-72 overflow-auto rounded-lg border p-3 text-xs whitespace-pre-wrap',
          tone === 'danger' ? 'border-danger-soft bg-danger-soft/40' : 'border-border bg-surface-soft',
        ].join(' ')}
      >
        {pretty}
      </pre>
      {failed && <p className="mt-1 text-xs text-ink-subtle">Stored snapshot was not valid JSON.</p>}
    </div>
  )
}

function Meta({ label, value, mono }: { label: string; value: string; mono?: boolean }) {
  return (
    <div>
      <p className="text-xs font-semibold tracking-wide text-ink-subtle uppercase">{label}</p>
      <p className={`mt-0.5 break-all text-sm text-ink ${mono ? 'nums' : ''}`}>{value}</p>
    </div>
  )
}

function Th({ children, hideBelow }: { children: React.ReactNode; hideBelow?: 'md' }) {
  return (
    <th
      scope="col"
      className={[
        'px-4 py-2.5 text-xs font-semibold tracking-wide text-ink-subtle uppercase',
        hideBelow === 'md' ? 'hidden md:table-cell' : '',
      ].join(' ')}
    >
      {children}
    </th>
  )
}

function toneFor(action: string): 'neutral' | 'success' | 'danger' | 'warning' | 'info' {
  if (['DELETE', 'REJECT', 'REFUND'].includes(action)) return 'danger'
  if (['CREATE', 'APPROVE', 'PUBLISH', 'POST'].includes(action)) return 'success'
  if (['UPDATE', 'PAYMENT', 'RESULT_CHANGE', 'PERMISSION_CHANGE', 'CONFIG_CHANGE'].includes(action)) return 'warning'
  if (['IMPORT', 'EXPORT', 'SETUP'].includes(action)) return 'info'
  return 'neutral'
}
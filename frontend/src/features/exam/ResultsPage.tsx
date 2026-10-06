import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, post, put } from '../../lib/api'
import { describeError, useAuth } from '../../auth/AuthProvider'
import {
  Badge,
  Button,
  EmptyState,
  ErrorState,
  Field,
  Panel,
  QueryBoundary,
  Select,
  StatusBadge,
  TextArea,
  TextInput,
} from '../../components/ui'
import { PageHeader } from '../../components/DataTable'
import { dateTime, humanise, num } from '../../lib/format'
import type { Examination, Result } from './types'

export interface ResultCorrection {
  id: string
  resultId: string
  oldMarks: number | null
  newMarks: number
  oldGrade: string | null
  newGrade: string | null
  reason: string
  status: string
  requestedBy: string | null
  requestedAt: string | null
  approvedBy: string | null
  approvedAt: string | null
  appliedAt: string | null
  approvalNotes: string | null
}

/**
 * The allowed moves per the server's ResultService.isAllowed, so the screen offers
 * exactly the transitions the API will accept.
 */
const RESULT_MOVES: Record<string, Array<{ to: string; permission: string; label: string }>> = {
  DRAFT: [{ to: 'MARKS_ENTERED', permission: 'MARKS_ENTER', label: 'Mark entered' }],
  MARKS_ENTERED: [{ to: 'VERIFIED', permission: 'MARKS_VERIFY', label: 'Verify' }],
  VERIFIED: [
    { to: 'APPROVED', permission: 'MARKS_APPROVE', label: 'Approve' },
    { to: 'MARKS_ENTERED', permission: 'MARKS_ENTER', label: 'Reopen' },
  ],
  APPROVED: [
    { to: 'PUBLISHED', permission: 'RESULT_PUBLISH', label: 'Publish' },
    { to: 'VERIFIED', permission: 'MARKS_VERIFY', label: 'Reopen' },
  ],
}

/**
 * Results are a workflow rather than a list: marks move from entered to verified,
 * approved and published, and a published result is corrected rather than edited.
 */
export function ResultsPage() {
  const exams = useQuery({
    queryKey: ['examinations'],
    queryFn: () => api<Examination[]>('/api/v1/exams'),
  })

  const [examinationId, setExaminationId] = useState('')
  const [status, setStatus] = useState('')
  const { can } = useAuth()

  const results = useQuery({
    queryKey: ['exam-results', examinationId],
    queryFn: () => api<Result[]>(`/api/v1/exams/${examinationId}/results`),
    enabled: Boolean(examinationId),
  })

  const rows = results.data ?? []
  const shown = status ? rows.filter((row) => row.status === status) : rows

  return (
    <div className="mx-auto flex max-w-7xl flex-col gap-5">
      <PageHeader
        title="Results"
        description="Move each result through verification, approval and publication."
      />

      <Panel>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Examination" htmlFor="result-exam" required>
            <Select
              id="result-exam"
              value={examinationId}
              onChange={(event) => setExaminationId(event.target.value)}
            >
              <option value="">Choose an examination</option>
              {(exams.data ?? []).map((exam) => (
                <option key={exam.id} value={exam.id}>
                  {exam.name} ({exam.code})
                </option>
              ))}
            </Select>
          </Field>
          <Field label="Status" htmlFor="result-status">
            <Select id="result-status" value={status} onChange={(event) => setStatus(event.target.value)}>
              <option value="">All statuses</option>
              {['DRAFT', 'MARKS_ENTERED', 'VERIFIED', 'APPROVED', 'PUBLISHED'].map((value) => (
                <option key={value} value={value}>
                  {humanise(value)}
                </option>
              ))}
            </Select>
          </Field>
        </div>
      </Panel>

      {!examinationId ? (
        <Panel>
          <EmptyState
            title="Choose an examination"
            description="Results are handled per examination, because each one owns its papers and marks."
          />
        </Panel>
      ) : (
        <Panel
          title="Results"
          description={
            can('RESULT_PUBLISH')
              ? 'Publication is visible to students the moment a result reaches it.'
              : 'You can read results and move them through the stages you are permitted to.'
          }
          padded={false}
        >
          <QueryBoundary
            isLoading={results.isLoading}
            error={results.error}
            data={results.data}
            onRetry={() => void results.refetch()}
            loadingRows={5}
            empty={
              <EmptyState
                title="No results for this examination"
                description="Enter marks against a paper to create its results."
              />
            }
          >
            {() => (
              <>
                <div className="flex flex-wrap gap-2 border-b border-border px-4 py-3">
                  <Badge>{rows.length} results</Badge>
                  <Badge tone="info">
                    {rows.filter((row) => row.status === 'PUBLISHED').length} published
                  </Badge>
                  <Badge tone={rows.some((row) => row.pass === false) ? 'warning' : 'success'}>
                    {rows.filter((row) => row.pass === true).length} passing
                  </Badge>
                </div>
                <div className="overflow-x-auto">
                  <table className="w-full border-collapse text-sm">
                    <thead>
                      <tr className="border-b border-border text-left">
                        <Th>Marks</Th>
                        <Th>Grade</Th>
                        <Th>Outcome</Th>
                        <Th>Status</Th>
                        <Th className="text-right">Actions</Th>
                      </tr>
                    </thead>
                    <tbody>
                      {shown.map((result) => (
                        <ResultRow key={result.id} result={result} />
                      ))}
                    </tbody>
                  </table>
                </div>
                {shown.length === 0 && (
                  <p className="border-t border-border px-4 py-3 text-sm text-ink-subtle">
                    No result is at that stage of the workflow.
                  </p>
                )}
              </>
            )}
          </QueryBoundary>
        </Panel>
      )}

      {examinationId && <CorrectionsPanel examinationId={examinationId} />}
    </div>
  )
}

function Th({ children, className = '' }: { children?: React.ReactNode; className?: string }) {
  return (
    <th
      className={`px-4 py-2.5 text-xs font-semibold tracking-wide text-ink-subtle uppercase ${className}`}
    >
      {children}
    </th>
  )
}

function ResultRow({ result }: { result: Result }) {
  const { can } = useAuth()
  const queryClient = useQueryClient()
  const [error, setError] = useState<string | null>(null)

  const transition = useMutation({
    mutationFn: (status: string) => put<Result>(`/api/v1/exams/results/${result.id}/status`, { status }),
    onSuccess: () => {
      setError(null)
      void queryClient.invalidateQueries({ queryKey: ['exam-results'] })
    },
    onError: (mutationError) => setError(describeError(mutationError)),
  })

  const moves = (RESULT_MOVES[result.status] ?? []).filter((move) => can(move.permission))

  return (
    <tr className="border-b border-border last:border-0">
      <td className="nums px-4 py-3">
        <span className="font-medium">{num(result.marksObtained)}</span>
        <span className="text-ink-subtle"> / {num(result.maxMarks)}</span>
        {result.percentage != null && (
          <span className="nums ml-2 text-xs text-ink-subtle">{num(result.percentage)}%</span>
        )}
      </td>
      <td className="px-4 py-3">
        <span className="font-medium text-ink">{result.letterGrade ?? '—'}</span>
        {result.gradePoint != null && (
          <span className="nums ml-1 text-xs text-ink-subtle">({num(result.gradePoint)})</span>
        )}
      </td>
      <td className="px-4 py-3">
        {result.pass == null ? (
          <span className="text-ink-subtle">Not graded</span>
        ) : (
          <Badge tone={result.pass ? 'success' : 'danger'}>{result.pass ? 'Pass' : 'Fail'}</Badge>
        )}
      </td>
      <td className="px-4 py-3">
        <StatusBadge status={result.status} />
        {result.publishedAt && (
          <span className="mt-1 block text-xs text-ink-subtle">
            {dateTime(result.publishedAt)}
          </span>
        )}
      </td>
      <td className="px-4 py-3 text-right">
        <div className="flex flex-wrap justify-end gap-2">
          {moves.map((move) => (
            <Button
              key={move.to}
              size="sm"
              variant={move.to === 'PUBLISHED' ? 'primary' : 'secondary'}
              loading={transition.isPending}
              onClick={() => transition.mutate(move.to)}
            >
              {move.label}
            </Button>
          ))}
          {result.status === 'PUBLISHED' && can('RESULT_CORRECT') && (
            <CorrectionButton result={result} />
          )}
        </div>
        {error && (
          <p role="alert" className="mt-2 text-xs text-danger">
            {error}
          </p>
        )}
      </td>
    </tr>
  )
}

function CorrectionButton({ result }: { result: Result }) {
  const queryClient = useQueryClient()
  const [open, setOpen] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const request = useMutation({
    mutationFn: (values: { newMarks: string; reason: string }) =>
      post<ResultCorrection>(`/api/v1/exams/results/${result.id}/corrections`, {
        newMarks: Number(values.newMarks),
        reason: values.reason,
      }),
    onSuccess: () => {
      setOpen(false)
      setError(null)
      void queryClient.invalidateQueries({ queryKey: ['exam-results'] })
      void queryClient.invalidateQueries({ queryKey: ['result-corrections'] })
    },
    onError: (mutationError) => setError(describeError(mutationError)),
  })

  if (!open) {
    return (
      <Button size="sm" variant="ghost" onClick={() => setOpen(true)}>
        Request correction
      </Button>
    )
  }

  return (
    <div className="mt-2 rounded-lg border border-border bg-surface-soft/60 p-3 text-left">
      <form
        className="flex flex-col gap-3"
        onSubmit={(event) => {
          event.preventDefault()
          const data = new FormData(event.currentTarget)
          const newMarks = String(data.get('newMarks') ?? '')
          const reason = String(data.get('reason') ?? '').trim()
          if (!newMarks || !reason) {
            setError('Give the corrected marks and a reason.')
            return
          }
          request.mutate({ newMarks, reason })
        }}
      >
        <TextInput
          id={`correction-marks-${result.id}`}
          name="newMarks"
          type="number"
          step="0.01"
          min="0"
          defaultValue={num(result.marksObtained)}
          aria-label="Corrected marks"
          required
        />
        <TextArea
          id={`correction-reason-${result.id}`}
          name="reason"
          rows={2}
          placeholder="Why the published marks need changing"
          aria-label="Reason"
          required
        />
        <div className="flex justify-end gap-2">
          <Button type="button" size="sm" variant="ghost" onClick={() => setOpen(false)}>
            Cancel
          </Button>
          <Button type="submit" size="sm" loading={request.isPending}>
            Submit request
          </Button>
        </div>
      </form>
      {error && (
        <p role="alert" className="mt-2 text-xs text-danger">
          {error}
        </p>
      )}
    </div>
  )
}

/**
 * Corrections arrive per result, so the reviewer works through the results of one
 * examination and decides on each pending request.
 */
export function CorrectionsPanel({ examinationId }: { examinationId: string }) {
  const queryClient = useQueryClient()
  const { can } = useAuth()
  const [error, setError] = useState<string | null>(null)

  const results = useQuery({
    queryKey: ['exam-results', examinationId],
    queryFn: () => api<Result[]>(`/api/v1/exams/${examinationId}/results`),
    enabled: Boolean(examinationId),
  })

  const corrections = useQuery({
    queryKey: [
      'result-corrections',
      examinationId,
      (results.data ?? []).map((row) => row.id).join(','),
    ],
    enabled: (results.data ?? []).length > 0,
    queryFn: async () => {
      const all = await Promise.all(
        (results.data ?? []).map(async (result) => ({
          result,
          items: await api<ResultCorrection[]>(`/api/v1/exams/results/${result.id}/corrections`),
        })),
      )
      return all.flatMap((entry) => entry.items.map((item) => ({ ...item, result: entry.result })))
    },
  })

  const decide = useMutation({
    mutationFn: ({ id, approved }: { id: string; approved: boolean }) =>
      put<ResultCorrection>(`/api/v1/exams/corrections/${id}/decision`, {
        approved,
        notes: approved ? 'Approved from the results workspace' : 'Rejected from the results workspace',
      }),
    onSuccess: () => {
      setError(null)
      void queryClient.invalidateQueries({ queryKey: ['result-corrections'] })
      void queryClient.invalidateQueries({ queryKey: ['exam-results'] })
    },
    onError: (mutationError) => setError(describeError(mutationError)),
  })

  const rows = corrections.data ?? []
  const pending = rows.filter((row) => row.status === 'PENDING')

  return (
    <Panel
      title="Correction requests"
      description="A published result cannot be edited, so a correction is reviewed and applied separately."
      padded={false}
    >
      <QueryBoundary
        isLoading={corrections.isLoading}
        error={corrections.error}
        data={corrections.data}
        onRetry={() => void corrections.refetch()}
        loadingRows={3}
        empty={<EmptyState title="No correction requests" description="Nothing is waiting for review." />}
      >
        {(data) => (
          <>
            {pending.length > 0 && (
              <div className="border-b border-border px-4 py-3">
                <Badge tone="warning">{pending.length} awaiting a decision</Badge>
              </div>
            )}
            <div className="overflow-x-auto">
              <table className="w-full border-collapse text-sm">
                <thead>
                  <tr className="border-b border-border text-left">
                    <Th>Change</Th>
                    <Th>Reason</Th>
                    <Th>Status</Th>
                    <Th className="text-right">Decision</Th>
                  </tr>
                </thead>
                <tbody>
                  {data.map((correction) => (
                    <tr key={correction.id} className="border-b border-border last:border-0">
                      <td className="nums px-4 py-3">
                        {num(correction.oldMarks)} → <span className="font-medium">{num(correction.newMarks)}</span>
                        {correction.newGrade && (
                          <span className="ml-2 text-xs text-ink-subtle">{correction.newGrade}</span>
                        )}
                      </td>
                      <td className="px-4 py-3 text-ink-muted">{correction.reason}</td>
                      <td className="px-4 py-3">
                        <StatusBadge status={correction.status} />
                      </td>
                      <td className="px-4 py-3 text-right">
                        {correction.status === 'PENDING' && can('MARKS_APPROVE') ? (
                          <div className="flex justify-end gap-2">
                            <Button
                              size="sm"
                              variant="secondary"
                              loading={decide.isPending}
                              onClick={() => decide.mutate({ id: correction.id, approved: false })}
                            >
                              Reject
                            </Button>
                            <Button
                              size="sm"
                              loading={decide.isPending}
                              onClick={() => decide.mutate({ id: correction.id, approved: true })}
                            >
                              Approve
                            </Button>
                          </div>
                        ) : (
                          <span className="text-xs text-ink-subtle">—</span>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </>
        )}
      </QueryBoundary>
      {error && (
        <p role="alert" className="border-t border-border p-4 text-sm text-danger">
          {error}
        </p>
      )}
    </Panel>
  )
}

/** Results a single student holds, used on the student record. */
export function StudentResultsSummary({ studentId }: { studentId: string }) {
  const results = useQuery({
    queryKey: ['student-results', studentId],
    queryFn: () => api<Result[]>(`/api/v1/exams/results/students/${studentId}`),
  })

  if (results.isLoading) return <p className="text-sm text-ink-subtle">Loading results…</p>
  if (results.isError) return <ErrorState error={results.error} onRetry={() => void results.refetch()} />
  const rows = results.data ?? []
  if (rows.length === 0) return <p className="text-sm text-ink-subtle">No results recorded yet.</p>

  const published = rows.filter((row) => row.status === 'PUBLISHED')

  return (
    <ul className="flex flex-col gap-2 text-sm">
      {published.map((row) => (
        <li key={row.id} className="flex items-center justify-between gap-3">
          <span className="text-ink-muted">
            {num(row.marksObtained)} / {num(row.maxMarks)}
          </span>
          <Badge tone={row.pass ? 'success' : 'danger'}>{row.letterGrade ?? humanise(row.status)}</Badge>
        </li>
      ))}
    </ul>
  )
}

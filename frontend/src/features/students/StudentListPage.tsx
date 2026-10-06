import { useState, type ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { api } from '../../lib/api'
import { useAuth } from '../../auth/AuthProvider'
import {
  Button,
  EmptyState,
  Panel,
  QueryBoundary,
  Select,
  StatusBadge,
  TextInput,
} from '../../components/ui'
import { formatDate } from '../dashboard/DashboardPage'
import { STUDENT_STATUSES, type Page, type Student } from './types'

const PAGE_SIZE = 20

export function StudentListPage() {
  const { can } = useAuth()
  const [term, setTerm] = useState('')
  const [appliedTerm, setAppliedTerm] = useState('')
  const [status, setStatus] = useState('')
  const [page, setPage] = useState(0)

  // Typing should not fire a request per keystroke; the term is applied on submit or on pause.
  const applyTerm = () => {
    setAppliedTerm(term.trim())
    setPage(0)
  }

  const students = useQuery({
    queryKey: ['students', appliedTerm, status, page],
    queryFn: () =>
      api<Page<Student>>('/api/v1/students', {
        query: { term: appliedTerm, status, page, size: PAGE_SIZE },
      }),
    placeholderData: keepPreviousData,
  })

  return (
    <div className="mx-auto flex max-w-7xl flex-col gap-5">
      <header className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold text-ink">Students</h1>
          <p className="mt-1 text-sm text-ink-subtle">
            {students.data ? `${students.data.totalElements} on record` : 'Loading the register…'}
          </p>
        </div>
        {can('STUDENT_CREATE') && (
          <Link to="/students/new">
            <Button>Admit a student</Button>
          </Link>
        )}
      </header>

      <Panel padded={false}>
        <form
          className="flex flex-wrap items-end gap-3 border-b border-border p-4"
          onSubmit={(event) => {
            event.preventDefault()
            applyTerm()
          }}
          role="search"
        >
          <div className="min-w-56 flex-1">
            <label htmlFor="student-search" className="mb-1.5 block text-xs font-medium text-ink-muted">
              Search
            </label>
            <TextInput
              id="student-search"
              value={term}
              onChange={(event) => setTerm(event.target.value)}
              placeholder="Name, number, email or phone"
            />
          </div>

          <div className="w-44">
            <label htmlFor="student-status" className="mb-1.5 block text-xs font-medium text-ink-muted">
              Status
            </label>
            <Select
              id="student-status"
              value={status}
              onChange={(event) => {
                setStatus(event.target.value)
                setPage(0)
              }}
            >
              <option value="">All statuses</option>
              {STUDENT_STATUSES.map((value) => (
                <option key={value} value={value}>
                  {value.charAt(0) + value.slice(1).toLowerCase().replace('_', ' ')}
                </option>
              ))}
            </Select>
          </div>

          <Button type="submit" variant="secondary">
            Search
          </Button>
          {(appliedTerm || status) && (
            <Button
              type="button"
              variant="ghost"
              onClick={() => {
                setTerm('')
                setAppliedTerm('')
                setStatus('')
                setPage(0)
              }}
            >
              Clear
            </Button>
          )}
        </form>

        <QueryBoundary
          isLoading={students.isLoading}
          error={students.error}
          data={students.data}
          onRetry={() => void students.refetch()}
          loadingRows={8}
          empty={
            <EmptyState
              title={appliedTerm || status ? 'No students match those filters' : 'No students yet'}
              description={
                appliedTerm || status
                  ? 'Try a different search term, or clear the filters.'
                  : 'Students appear here once an admission is confirmed or one is added directly.'
              }
              action={
                appliedTerm || status ? (
                  <Button
                    variant="secondary"
                    onClick={() => {
                      setTerm('')
                      setAppliedTerm('')
                      setStatus('')
                    }}
                  >
                    Clear filters
                  </Button>
                ) : undefined
              }
            />
          }
        >
          {(data) => (
            <>
              <div className="overflow-x-auto">
                <table className="w-full border-collapse text-sm">
                  <thead>
                    <tr className="border-b border-border text-left">
                      <Th>Number</Th>
                      <Th>Name</Th>
                      <Th>Status</Th>
                      <Th>Enrolled</Th>
                      <Th>Contact</Th>
                      <Th className="text-right">Open</Th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.data.map((student) => (
                      <tr key={student.id} className="border-b border-border last:border-0 hover:bg-surface-soft/60">
                        <td className="nums px-4 py-3 whitespace-nowrap text-ink-muted">{student.studentNumber}</td>
                        <td className="px-4 py-3">
                          <Link
                            to={`/students/number/${student.studentNumber}`}
                            className="font-medium text-primary hover:underline"
                          >
                            {student.fullName}
                          </Link>
                        </td>
                        <td className="px-4 py-3">
                          <StatusBadge status={student.status} />
                        </td>
                        <td className="px-4 py-3 whitespace-nowrap text-ink-muted">
                          {formatDate(student.enrollmentDate)}
                        </td>
                        <td className="px-4 py-3 text-ink-muted">
                          <span className="block truncate">{student.email ?? '—'}</span>
                          <span className="block truncate text-xs text-ink-subtle">{student.phone ?? ''}</span>
                        </td>
                        <td className="px-4 py-3 text-right">
                          <Link
                            to={`/students/number/${student.studentNumber}`}
                            className="text-sm font-medium text-primary hover:underline"
                          >
                            View
                          </Link>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>

              <Pagination page={data} onChange={setPage} />
            </>
          )}
        </QueryBoundary>
      </Panel>
    </div>
  )
}

function Th({ children, className = '' }: { children: ReactNode; className?: string }) {
  return (
    <th
      scope="col"
      className={`px-4 py-2.5 text-xs font-semibold tracking-wide text-ink-subtle uppercase ${className}`}
    >
      {children}
    </th>
  )
}

export function Pagination({
  page,
  onChange,
}: {
  page: Page<unknown>
  onChange: (page: number) => void
}) {
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

export { Skeleton } from '../../components/ui'
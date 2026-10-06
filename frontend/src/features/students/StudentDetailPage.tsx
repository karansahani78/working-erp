import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import { api } from '../../lib/api'
import { useAuth } from '../../auth/AuthProvider'
import { Badge, Panel, QueryBoundary, StatusBadge } from '../../components/ui'
import { formatDate } from '../dashboard/DashboardPage'
import type { Student } from './types'

/**
 * A student is addressed by number wherever a person can see it, because that is the
 * identifier staff and families actually use. The id is accepted too, for internal links.
 */
export function StudentDetailPage() {
  const { studentNumber, id } = useParams()
  const { can } = useAuth()

  const byNumber = Boolean(studentNumber)

  const student = useQuery({
    queryKey: ['student', byNumber ? studentNumber : id],
    queryFn: () =>
      api<Student>(
        byNumber ? `/api/v1/students/number/${studentNumber}` : `/api/v1/students/${id}`,
      ),
    enabled: Boolean(studentNumber || id),
  })

  return (
    <div className="mx-auto flex max-w-5xl flex-col gap-5">
      <nav aria-label="Breadcrumb" className="text-sm text-ink-subtle">
        <Link to="/students" className="text-primary hover:underline">
          Students
        </Link>
        <span className="mx-2">/</span>
        <span>{student.data?.studentNumber ?? 'Loading…'}</span>
      </nav>

      <QueryBoundary
        isLoading={student.isLoading}
        error={student.error}
        data={student.data}
        onRetry={() => void student.refetch()}
        loadingRows={6}
      >
        {(data) => (
          <>
            <header className="flex flex-wrap items-start justify-between gap-4">
              <div className="flex items-center gap-4">
                <div className="flex h-14 w-14 items-center justify-center rounded-full bg-primary-soft text-lg font-semibold text-primary">
                  {initials(data.fullName)}
                </div>
                <div>
                  <h1 className="text-2xl font-semibold text-ink">{data.fullName}</h1>
                  <div className="mt-1 flex flex-wrap items-center gap-2">
                    <span className="nums text-sm text-ink-subtle">{data.studentNumber}</span>
                    <StatusBadge status={data.status} />
                    {data.email && <Badge tone="info">{data.email}</Badge>}
                  </div>
                </div>
              </div>
              <div className="flex gap-2">
                {can('STUDENT_UPDATE') && (
                  <Link to={`/students/${data.id}/edit`}>
                    <button className="rounded-lg border border-border bg-surface px-4 py-2 text-sm font-medium text-ink hover:bg-surface-soft">
                      Edit
                    </button>
                  </Link>
                )}
                {can('ENROLLMENT_READ') && (
                  <Link to={`/students/number/${data.studentNumber}/enrolment`}>
                    <button className="rounded-lg bg-primary px-4 py-2 text-sm font-medium text-white hover:bg-primary-hover">
                      Enrolment
                    </button>
                  </Link>
                )}
              </div>
            </header>

            <div className="grid gap-5 lg:grid-cols-2">
              <Panel title="Personal details">
                <dl className="grid grid-cols-2 gap-x-6 gap-y-4 text-sm">
                  <Field label="First name" value={data.firstName} />
                  <Field label="Last name" value={data.lastName} />
                  <Field label="Date of birth" value={formatDate(data.dateOfBirth)} />
                  <Field label="Gender" value={data.gender} />
                  <Field label="Nationality" value={data.nationality} />
                  <Field label="Phone" value={data.phone} />
                </dl>
              </Panel>

              <Panel title="Record">
                <dl className="grid grid-cols-2 gap-x-6 gap-y-4 text-sm">
                  <Field label="Status" value={<StatusBadge status={data.status} />} />
                  <Field label="Enrolled on" value={formatDate(data.enrollmentDate)} />
                  <Field label="Record created" value={formatDate(data.createdAt)} />
                  <Field
                    label="Portal access"
                    value={data.userId ? 'Linked to a user account' : 'No portal account'}
                  />
                </dl>
              </Panel>
            </div>

            <Panel title="Contact">
              <dl className="grid gap-4 text-sm sm:grid-cols-2">
                <Field label="Email" value={data.email} />
                <Field label="Address" value={data.address} />
              </dl>
            </Panel>
          </>
        )}
      </QueryBoundary>
    </div>
  )
}

function Field({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="min-w-0">
      <dt className="text-xs tracking-wide text-ink-subtle uppercase">{label}</dt>
      <dd className="mt-0.5 truncate text-ink">{value ?? '—'}</dd>
    </div>
  )
}

function initials(name: string): string {
  return (
    name
      .trim()
      .split(/\s+/)
      .slice(0, 2)
      .map((part) => part.charAt(0).toUpperCase())
      .join('') || '?'
  )
}
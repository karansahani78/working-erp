import { useState, type ReactNode } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, del, post, put, type PageResponse } from '../../lib/api'
import { describeError, useAuth } from '../../auth/AuthProvider'
import {
  Badge,
  Button,
  EmptyState,
  Field,
  Panel,
  QueryBoundary,
  StatusBadge,
  TextArea,
  TextInput,
} from '../../components/ui'
import { PageHeader, Pager } from '../../components/DataTable'
import type { Student } from '../students/types'

export interface Guardian {
  id: string
  firstName: string
  middleName: string | null
  lastName: string | null
  fullName: string
  phone: string | null
  email: string | null
  occupation: string | null
  address: string | null
  userId: string | null
  accountLinked: boolean
}

/** Mirrors GuardianRequest; the server requires a first name. */
export interface GuardianRequest {
  firstName: string
  middleName?: string
  lastName?: string
  phone?: string
  email?: string
  occupation?: string
  address?: string
}

export interface GuardianLink {
  id: string
  guardianId: string
  guardianName: string
  phone: string | null
  email: string | null
  relationship: string
  primaryContact: boolean
  canPickup: boolean
}

export const RELATIONSHIPS = [
  'MOTHER',
  'FATHER',
  'GRANDPARENT',
  'SIBLING',
  'UNCLE',
  'AUNT',
  'LEGAL_GUARDIAN',
  'OTHER',
]

export function GuardiansPage() {
  const { can } = useAuth()
  const queryClient = useQueryClient()
  const [term, setTerm] = useState('')
  const [appliedTerm, setAppliedTerm] = useState('')
  const [page, setPage] = useState(0)
  const [error, setError] = useState<string | null>(null)

  const guardians = useQuery({
    queryKey: ['guardians', appliedTerm, page],
    queryFn: () =>
      api<PageResponse<Guardian>>('/api/v1/guardians', {
        query: { term: appliedTerm || undefined, size: 20, page },
      }),
  })

  const remove = useMutation({
    mutationFn: (id: string) => del<void>(`/api/v1/guardians/${id}`),
    onSuccess: () => {
      setError(null)
      void queryClient.invalidateQueries({ queryKey: ['guardians'] })
    },
    onError: (err) => setError(describeError(err)),
  })

  return (
    <div className="mx-auto flex max-w-7xl flex-col gap-5">
      <PageHeader
        title="Guardians"
        description="Parents and carers, their portal access, and the students in their care."
      />

      {can('GUARDIAN_MANAGE') && <GuardianForm onCreated={() => void guardians.refetch()} />}

      <Panel padded={false}>
        <form
          className="flex flex-wrap items-end gap-3 border-b border-border p-4"
          role="search"
          onSubmit={(event) => {
            event.preventDefault()
            setAppliedTerm(term.trim())
            setPage(0)
          }}
        >
          <div className="min-w-56 flex-1">
            <Field label="Search" htmlFor="guardian-search">
              <TextInput
                id="guardian-search"
                value={term}
                onChange={(event) => setTerm(event.target.value)}
                placeholder="Name, phone or email"
              />
            </Field>
          </div>
          <Button type="submit" variant="secondary">
            Search
          </Button>
          {appliedTerm && (
            <Button
              type="button"
              variant="ghost"
              onClick={() => {
                setTerm('')
                setAppliedTerm('')
                setPage(0)
              }}
            >
              Clear
            </Button>
          )}
        </form>

        <QueryBoundary
          isLoading={guardians.isLoading}
          error={guardians.error}
          data={guardians.data}
          onRetry={() => void guardians.refetch()}
          loadingRows={6}
          empty={
            <EmptyState
              title={appliedTerm ? 'No guardians match' : 'No guardians yet'}
              description={
                appliedTerm
                  ? 'Try a different name, phone or email.'
                  : 'Guardians appear here once a parent or carer is recorded.'
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
                      <Th>Name</Th>
                      <Th hideBelow="md">Phone</Th>
                      <Th hideBelow="md">Email</Th>
                      <Th>Portal access</Th>
                      <Th className="text-right">Open</Th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.data.map((guardian) => (
                      <tr key={guardian.id} className="border-b border-border last:border-0 hover:bg-surface-soft/60">
                        <td className="px-4 py-3 font-medium text-ink">{guardian.fullName}</td>
                        <td className="px-4 py-3 whitespace-nowrap text-ink-muted">{guardian.phone ?? '—'}</td>
                        <td className="px-4 py-3 text-ink-muted">{guardian.email ?? '—'}</td>
                        <td className="px-4 py-3">
                          <StatusBadge status={guardian.accountLinked ? 'LINKED' : 'NOT_LINKED'} />
                        </td>
                        <td className="px-4 py-3 text-right">
                          <span className="inline-flex items-center gap-2">
                            <Link
                              to={`/guardians/${guardian.id}`}
                              className="text-sm font-medium text-primary hover:underline"
                            >
                              View
                            </Link>
                            {can('GUARDIAN_MANAGE') && (
                              <Button
                                size="sm"
                                variant="ghost"
                                loading={remove.isPending}
                                onClick={() => remove.mutate(guardian.id)}
                              >
                                Delete
                              </Button>
                            )}
                          </span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <Pager page={data} onChange={setPage} />
            </>
          )}
        </QueryBoundary>

        {error && (
          <p role="alert" className="border-t border-border p-4 text-sm text-danger">
            {error}
          </p>
        )}
      </Panel>
    </div>
  )
}

/** One guardian: their details, portal access, and the students in their care. */
export function GuardianDetailPage({ id }: { id: string }) {
  const { can } = useAuth()
  const queryClient = useQueryClient()
  const [accountEmail, setAccountEmail] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  const guardian = useQuery({
    queryKey: ['guardian', id],
    queryFn: () => api<Guardian>(`/api/v1/guardians/${id}`),
  })

  const children = useQuery({
    queryKey: ['guardian-children', id],
    queryFn: () => api<PageResponse<Student>>(`/api/v1/guardians/${id}/students`, { query: { size: 20 } }),
  })

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: ['guardian', id] })
    void queryClient.invalidateQueries({ queryKey: ['guardians'] })
  }

  const saveDetails = useMutation({
    mutationFn: (values: GuardianRequest) => put<Guardian>(`/api/v1/guardians/${id}`, values),
    onSuccess: () => {
      setError(null)
      setNotice('Guardian details saved.')
      invalidate()
    },
    onError: (err) => setError(describeError(err)),
  })

  const linkAccount = useMutation({
    // The server matches the portal account by email, so a registrar need not find a uuid.
    mutationFn: (email: string) => put<Guardian>(`/api/v1/guardians/${id}/account`, { email }),
    onSuccess: () => {
      setError(null)
      setAccountEmail('')
      setNotice('Portal access linked.')
      invalidate()
    },
    onError: (err) => setError(describeError(err)),
  })

  const unlinkAccount = useMutation({
    mutationFn: () => del<void>(`/api/v1/guardians/${id}/account`),
    onSuccess: () => {
      setError(null)
      setNotice('Portal access removed.')
      invalidate()
    },
    onError: (err) => setError(describeError(err)),
  })

  /**
   * Unlinking is keyed by student and link, not by guardian, so the link id is read from
   * the student's own list of guardians rather than guessed.
   */
  const unlinkStudent = useMutation({
    mutationFn: async (studentId: string) => {
      const links = await api<GuardianLink[]>(`/api/v1/guardians/student/${studentId}`)
      const link = links.find((candidate) => candidate.guardianId === id)
      if (!link) throw new Error('That student is no longer linked to this guardian.')
      return del<void>(`/api/v1/guardians/student/${studentId}/links/${link.id}`)
    },
    onSuccess: () => {
      setError(null)
      setNotice('Student unlinked.')
      void queryClient.invalidateQueries({ queryKey: ['guardian-children', id] })
    },
    onError: (err) => setError(describeError(err)),
  })

  if (guardian.isLoading) {
    return <p className="text-sm text-ink-subtle">Loading guardian…</p>
  }

  if (guardian.isError) {
    return (
      <div className="flex flex-col gap-3">
        <p className="text-sm text-danger">{describeError(guardian.error)}</p>
        <Link to="/guardians" className="text-sm text-primary hover:underline">
          Back to guardians
        </Link>
      </div>
    )
  }

  return (
    <div className="mx-auto flex max-w-5xl flex-col gap-5">
      <header className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <Link to="/guardians" className="text-sm text-primary hover:underline">
            Guardians
          </Link>
          <h1 className="mt-1 text-2xl font-semibold text-ink">{guardian.data?.fullName}</h1>
        </div>
        {guardian.data && (
          <StatusBadge status={guardian.data.accountLinked ? 'LINKED' : 'NOT_LINKED'} />
        )}
      </header>

      {guardian.data && (
        <Panel title="Details">
          <form
            onSubmit={(event) => {
              event.preventDefault()
              const form = new FormData(event.currentTarget)
              const value = (key: string) => (form.get(key) as string | null)?.trim() || undefined
              saveDetails.mutate({
                firstName: value('firstName') ?? '',
                middleName: value('middleName'),
                lastName: value('lastName'),
                phone: value('phone'),
                email: value('email'),
                occupation: value('occupation'),
                address: value('address'),
              })
            }}
            className="grid gap-4 sm:grid-cols-2"
          >
            <Field label="First name" htmlFor="guardian-first" required>
              <TextInput id="guardian-first" name="firstName" defaultValue={guardian.data?.firstName} required />
            </Field>
            <Field label="Middle name" htmlFor="guardian-middle">
              <TextInput id="guardian-middle" name="middleName" defaultValue={guardian.data?.middleName ?? ''} />
            </Field>
            <Field label="Last name" htmlFor="guardian-last">
              <TextInput id="guardian-last" name="lastName" defaultValue={guardian.data?.lastName ?? ''} />
            </Field>
            <Field label="Occupation" htmlFor="guardian-occupation">
              <TextInput
                id="guardian-occupation"
                name="occupation"
                defaultValue={guardian.data?.occupation ?? ''}
              />
            </Field>
            <Field label="Phone" htmlFor="guardian-phone">
              <TextInput id="guardian-phone" name="phone" defaultValue={guardian.data?.phone ?? ''} />
            </Field>
            <Field label="Email" htmlFor="guardian-email">
              <TextInput
                id="guardian-email"
                name="email"
                type="email"
                defaultValue={guardian.data?.email ?? ''}
              />
            </Field>
            <div className="sm:col-span-2">
              <Field label="Address" htmlFor="guardian-address">
                <TextArea id="guardian-address" name="address" defaultValue={guardian.data?.address ?? ''} />
              </Field>
            </div>
            {can('GUARDIAN_MANAGE') && (
              <div className="sm:col-span-2">
                <Button type="submit" loading={saveDetails.isPending}>
                  Save details
                </Button>
              </div>
            )}
          </form>
        </Panel>
      )}

      {can('GUARDIAN_MANAGE') && (
        <Panel title="Portal access" description="The parent portal signs in with the account named by email.">
          {guardian.data?.accountLinked ? (
            <div className="flex flex-wrap items-center gap-3">
              <Badge tone="success">Linked to {guardian.data.email}</Badge>
              <Button
                variant="ghost"
                loading={unlinkAccount.isPending}
                onClick={() => unlinkAccount.mutate()}
              >
                Remove access
              </Button>
            </div>
          ) : (
            <form
              className="flex flex-wrap items-end gap-3"
              onSubmit={(event) => {
                event.preventDefault()
                linkAccount.mutate(accountEmail.trim())
              }}
            >
              <div className="min-w-64 flex-1">
                <Field label="Portal account email" htmlFor="guardian-account" required>
                  <TextInput
                    id="guardian-account"
                    type="email"
                    value={accountEmail}
                    onChange={(event) => setAccountEmail(event.target.value)}
                    placeholder="parent@example.com"
                    required
                  />
                </Field>
              </div>
              <Button type="submit" loading={linkAccount.isPending}>
                Link account
              </Button>
            </form>
          )}
        </Panel>
      )}

      <Panel title="Students in their care" padded={false}>
        <QueryBoundary
          isLoading={children.isLoading}
          error={children.error}
          data={children.data}
          onRetry={() => void children.refetch()}
          loadingRows={3}
          empty={
            <EmptyState
              title="No students linked"
              description="Link this guardian from a student's record so they appear in the parent portal."
            />
          }
        >
          {(data) => (
            <table className="w-full border-collapse text-sm">
              <thead>
                <tr className="border-b border-border text-left">
                  <Th>Student</Th>
                  <Th hideBelow="md">Number</Th>
                  <Th hideBelow="lg">Status</Th>
                  {can('GUARDIAN_MANAGE') && <Th className="text-right">Unlink</Th>}
                </tr>
              </thead>
              <tbody>
                {data.data.map((student) => (
                  <tr key={student.id} className="border-b border-border last:border-0">
                    <td className="px-4 py-3 font-medium text-ink">
                      <Link to={`/students/${student.id}`} className="text-primary hover:underline">
                        {student.fullName}
                      </Link>
                    </td>
                    <td className="nums px-4 py-3 whitespace-nowrap text-ink-muted">{student.studentNumber}</td>
                    <td className="px-4 py-3">
                      <StatusBadge status={student.status} />
                    </td>
                    {can('GUARDIAN_MANAGE') && (
                      <td className="px-4 py-3 text-right">
                        <Button
                          size="sm"
                          variant="ghost"
                          loading={unlinkStudent.isPending}
                          onClick={() => unlinkStudent.mutate(student.id)}
                        >
                          Unlink
                        </Button>
                      </td>
                    )}
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </QueryBoundary>
      </Panel>

      {notice && <p className="text-sm text-success">{notice}</p>}
      {error && (
        <p role="alert" className="text-sm text-danger">
          {error}
        </p>
      )}
    </div>
  )
}

function GuardianForm({ onCreated }: { onCreated: () => void }) {
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const create = useMutation({
    mutationFn: (values: GuardianRequest) => post<Guardian>('/api/v1/guardians', values),
    onSuccess: (guardian) => {
      setError(null)
      setOpen(false)
      onCreated()
      navigate(`/guardians/${guardian.id}`)
    },
    onError: (err) => setError(describeError(err)),
  })

  if (!open) {
    return (
      <div className="flex justify-end">
        <Button onClick={() => setOpen(true)}>Add guardian</Button>
      </div>
    )
  }

  const submit = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const value = (key: string) => (form.get(key) as string | null)?.trim() || undefined
    create.mutate({
      firstName: value('firstName') ?? '',
      middleName: value('middleName'),
      lastName: value('lastName'),
      phone: value('phone'),
      email: value('email'),
      occupation: value('occupation'),
      address: value('address'),
    })
  }

  return (
    <Panel title="Add a guardian" description="A parent or carer. Portal access is linked separately once the account exists.">
      <form onSubmit={submit} className="grid gap-4 sm:grid-cols-2">
        <Field label="First name" htmlFor="new-guardian-first" required>
          <TextInput id="new-guardian-first" name="firstName" required />
        </Field>
        <Field label="Middle name" htmlFor="new-guardian-middle">
          <TextInput id="new-guardian-middle" name="middleName" />
        </Field>
        <Field label="Last name" htmlFor="new-guardian-last">
          <TextInput id="new-guardian-last" name="lastName" />
        </Field>
        <Field label="Occupation" htmlFor="new-guardian-occupation">
          <TextInput id="new-guardian-occupation" name="occupation" />
        </Field>
        <Field label="Phone" htmlFor="new-guardian-phone">
          <TextInput id="new-guardian-phone" name="phone" />
        </Field>
        <Field label="Email" htmlFor="new-guardian-email">
          <TextInput id="new-guardian-email" name="email" type="email" />
        </Field>
        <div className="sm:col-span-2">
          <Field label="Address" htmlFor="new-guardian-address">
            <TextArea id="new-guardian-address" name="address" />
          </Field>
        </div>
        {error && <p className="sm:col-span-2 text-sm text-danger">{error}</p>}
        <div className="flex gap-2 sm:col-span-2">
          <Button type="submit" loading={create.isPending}>
            Create guardian
          </Button>
          <Button type="button" variant="ghost" onClick={() => setOpen(false)}>
            Cancel
          </Button>
        </div>
      </form>
    </Panel>
  )
}

function Th({ children, className = '', hideBelow }: { children: ReactNode; className?: string; hideBelow?: 'md' | 'lg' }) {
  const responsive = hideBelow === 'md' ? 'hidden md:table-cell' : hideBelow === 'lg' ? 'hidden lg:table-cell' : ''
  return (
    <th scope="col" className={`px-4 py-2 font-medium text-ink-subtle ${responsive} ${className}`}>
      {children}
    </th>
  )
}

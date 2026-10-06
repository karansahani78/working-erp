import { useState, type ReactNode } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, del, post } from '../../lib/api'
import { describeError, useAuth } from '../../auth/AuthProvider'
import {
  Badge,
  Button,
  EmptyState,
  ErrorState,
  Field,
  LoadingState,
  Panel,
  QueryBoundary,
  Select,
  StatusBadge,
  TextArea,
  TextInput,
} from '../../components/ui'
import { PageHeader, Pager } from '../../components/DataTable'
import { date, num, text } from '../../lib/format'
import type { AcademicYear, SchoolClass } from '../academic/types'
import {
  ACTIVE_APPLICATION_STATUSES,
  APPLICATION_STATUSES,
  CAMPAIGN_STATUSES,
  DOCUMENT_STATUSES,
  type AdmissionApplication,
  type ApplicationDocument,
  type DocumentVerification,
  type AdmissionCampaign,
  type ApplicationRequest,
  type CampaignRequest,
  type Page,
} from './types'

/* ---------------------------------------------------------------- campaigns */

export function CampaignListPage() {
  const { can } = useAuth()
  const queryClient = useQueryClient()
  const [status, setStatus] = useState('')
  const [page, setPage] = useState(0)
  const [error, setError] = useState<string | null>(null)

  const campaigns = useQuery({
    queryKey: ['admission-campaigns', status, page],
    queryFn: () =>
      api<Page<AdmissionCampaign>>('/api/v1/admissions/campaigns', {
        query: { status: status || undefined, size: 20, page },
      }),
  })

  const setCampaignStatus = useMutation({
    mutationFn: ({ id, next }: { id: string; next: string }) =>
      // The server reads the new status from a query parameter, not the body.
      api<AdmissionCampaign>(`/api/v1/admissions/campaigns/${id}/status`, {
        method: 'PATCH',
        query: { status: next },
      }),
    onSuccess: () => {
      setError(null)
      void queryClient.invalidateQueries({ queryKey: ['admission-campaigns'] })
    },
    onError: (err) => setError(describeError(err)),
  })

  const remove = useMutation({
    mutationFn: (id: string) => del<void>(`/api/v1/admissions/campaigns/${id}`),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['admission-campaigns'] }),
    onError: (err) => setError(describeError(err)),
  })

  return (
    <div className="mx-auto flex max-w-7xl flex-col gap-5">
      <PageHeader
        title="Admission campaigns"
        description="An intake window: the applications it accepts, its fee, and its capacity."
      />

      {can('ADMISSION_CREATE') && <CampaignForm onCreated={() => void campaigns.refetch()} />}

      <Panel padded={false}>
        <div className="flex flex-wrap items-end gap-3 border-b border-border p-4">
          <div className="w-48">
            <Field label="Status" htmlFor="campaign-status">
              <Select
                id="campaign-status"
                value={status}
                onChange={(event) => {
                  setStatus(event.target.value)
                  setPage(0)
                }}
              >
                <option value="">All campaigns</option>
                {CAMPAIGN_STATUSES.map((value) => (
                  <option key={value} value={value}>
                    {text(value)}
                  </option>
                ))}
              </Select>
            </Field>
          </div>
        </div>

        <QueryBoundary
          isLoading={campaigns.isLoading}
          error={campaigns.error}
          data={campaigns.data}
          onRetry={() => void campaigns.refetch()}
          loadingRows={6}
          empty={
            <EmptyState
              title="No campaigns yet"
              description="Create an intake window to start accepting applications."
            />
          }
        >
          {(data) => (
            <>
              <div className="overflow-x-auto">
                <table className="w-full border-collapse text-sm">
                  <thead>
                    <tr className="border-b border-border text-left">
                      <Th>Code</Th>
                      <Th>Name</Th>
                      <Th hideBelow="md">Window</Th>
                      <Th hideBelow="lg">Fee</Th>
                      <Th>Capacity</Th>
                      <Th>Status</Th>
                      <Th className="text-right">Actions</Th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.data.map((campaign) => (
                      <tr key={campaign.id} className="border-b border-border last:border-0 hover:bg-surface-soft/60">
                        <td className="nums px-4 py-3 whitespace-nowrap text-ink-muted">{campaign.code}</td>
                        <td className="px-4 py-3 font-medium text-ink">{campaign.name}</td>
                        <td className="px-4 py-3 whitespace-nowrap text-ink-muted">
                          {date(campaign.openDate)} – {date(campaign.closeDate)}
                        </td>
                        <td className="nums px-4 py-3 text-ink-muted">{num(campaign.applicationFee ?? 0)}</td>
                        <td className="nums px-4 py-3 text-ink-muted">
                          {campaign.capacity == null ? '—' : num(campaign.capacity)}
                        </td>
                        <td className="px-4 py-3">
                          <StatusBadge status={campaign.status} />
                        </td>
                        <td className="px-4 py-3 text-right">
                          <span className="inline-flex items-center gap-1">
                            {CAMPAIGN_STATUSES.filter((next) => next !== campaign.status).map((next) =>
                              can('ADMISSION_CREATE') ? (
                                <Button
                                  key={next}
                                  size="sm"
                                  variant="ghost"
                                  loading={setCampaignStatus.isPending}
                                  onClick={() => setCampaignStatus.mutate({ id: campaign.id, next })}
                                >
                                  {text(next)}
                                </Button>
                              ) : null,
                            )}
                            <Link
                              to={`/admissions?campaignId=${campaign.id}`}
                              className="px-2 text-sm font-medium text-primary hover:underline"
                            >
                              Applications
                            </Link>
                            {can('ADMISSION_CREATE') && campaign.status === 'PLANNED' && (
                              <Button
                                size="sm"
                                variant="ghost"
                                loading={remove.isPending}
                                onClick={() => remove.mutate(campaign.id)}
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

function CampaignForm({ onCreated }: { onCreated: () => void }) {
  const [open, setOpen] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const years = useQuery({
    queryKey: ['academic-years'],
    queryFn: () => api<Page<AcademicYear>>('/api/v1/academic/academic-years', { query: { size: 100 } }),
    enabled: open,
  })

  const create = useMutation({
    mutationFn: (values: CampaignRequest) => post<AdmissionCampaign>('/api/v1/admissions/campaigns', values),
    onSuccess: () => {
      setError(null)
      setOpen(false)
      onCreated()
    },
    onError: (err) => setError(describeError(err)),
  })

  if (!open) {
    return (
      <div className="flex justify-end">
        <Button onClick={() => setOpen(true)}>New campaign</Button>
      </div>
    )
  }

  const submit = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const value = (key: string) => (form.get(key) as string | null)?.trim() || undefined
    create.mutate({
      code: value('code') ?? '',
      name: value('name') ?? '',
      academicYearId: value('academicYearId'),
      openDate: value('openDate'),
      closeDate: value('closeDate'),
      applicationFee: value('applicationFee') ? Number(value('applicationFee')) : undefined,
      capacity: value('capacity') ? Number(value('capacity')) : undefined,
      status: value('status') || 'PLANNED',
      documentRequirements: (form.get('documentRequirements') as string | null)
        ?.split('\n')
        .map((line) => line.trim())
        .filter(Boolean),
    })
  }

  return (
    <Panel title="New campaign" description="Applicants can only submit while the campaign is open.">
      <form onSubmit={submit} className="grid gap-4 sm:grid-cols-3">
        <Field label="Code" htmlFor="campaign-code" required>
          <TextInput id="campaign-code" name="code" placeholder="ADM-2026" required />
        </Field>
        <Field label="Name" htmlFor="campaign-name" required>
          <TextInput id="campaign-name" name="name" placeholder="Grade 6 intake 2026" required />
        </Field>
        <Field label="Status" htmlFor="campaign-form-status">
          <Select id="campaign-form-status" name="status" defaultValue="PLANNED">
            {CAMPAIGN_STATUSES.map((value) => (
              <option key={value} value={value}>
                {text(value)}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="Academic year" htmlFor="campaign-year">
          <Select id="campaign-year" name="academicYearId" defaultValue="">
            <option value="">Not linked</option>
            {(years.data?.data ?? []).map((year) => (
              <option key={year.id} value={year.id}>
                {year.name}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="Opens" htmlFor="campaign-open">
          <TextInput id="campaign-open" name="openDate" type="date" />
        </Field>
        <Field label="Closes" htmlFor="campaign-close">
          <TextInput id="campaign-close" name="closeDate" type="date" />
        </Field>
        <Field label="Application fee" htmlFor="campaign-fee">
          <TextInput id="campaign-fee" name="applicationFee" type="number" min={0} step="0.01" />
        </Field>
        <Field label="Capacity" htmlFor="campaign-capacity">
          <TextInput id="campaign-capacity" name="capacity" type="number" min={1} />
        </Field>
        <div className="sm:col-span-3">
          <Field
            label="Required documents"
            htmlFor="campaign-documents"
            hint="One per line. Applicants upload against these types."
          >
            <TextArea id="campaign-documents" name="documentRequirements" rows={3} />
          </Field>
        </div>

        {error && <p className="sm:col-span-3 text-sm text-danger">{error}</p>}

        <div className="flex gap-2 sm:col-span-3">
          <Button type="submit" loading={create.isPending}>
            Create campaign
          </Button>
          <Button type="button" variant="ghost" onClick={() => setOpen(false)}>
            Cancel
          </Button>
        </div>
      </form>
    </Panel>
  )
}

/* -------------------------------------------------------------- applications */

export function ApplicationListPage() {
  const { can } = useAuth()
  const queryClient = useQueryClient()
  const [status, setStatus] = useState('')
  const [campaignId, setCampaignId] = useState('')
  const [term, setTerm] = useState('')
  const [page, setPage] = useState(0)
  const [error, setError] = useState<string | null>(null)

  const campaigns = useQuery({
    queryKey: ['admission-campaigns', 'all'],
    queryFn: () => api<Page<AdmissionCampaign>>('/api/v1/admissions/campaigns', { query: { size: 100 } }),
  })

  const applications = useQuery({
    queryKey: ['admission-applications', status, campaignId, term, page],
    queryFn: () =>
      api<Page<AdmissionApplication>>('/api/v1/admissions/applications', {
        query: {
          status: status || undefined,
          campaignId: campaignId || undefined,
          term: term.trim() || undefined,
          size: 20,
          page,
        },
      }),
  })

  const submitReview = useMutation({
    mutationFn: (id: string) => post<void>(`/api/v1/admissions/applications/${id}/submit`),
    onSuccess: () => {
      setError(null)
      void queryClient.invalidateQueries({ queryKey: ['admission-applications'] })
    },
    onError: (err) => setError(describeError(err)),
  })

  return (
    <div className="mx-auto flex max-w-7xl flex-col gap-5">
      <PageHeader
        title="Admission applications"
        description="Every applicant, where they sit in the workflow, and what happens next."
      />

      {can('ADMISSION_CREATE') && (
        <ApplicationForm
          campaigns={campaigns.data?.data ?? []}
          onCreated={() => void applications.refetch()}
        />
      )}

      <Panel padded={false}>
        <form
          className="flex flex-wrap items-end gap-3 border-b border-border p-4"
          role="search"
          onSubmit={(event) => {
            event.preventDefault()
            setPage(0)
            void applications.refetch()
          }}
        >
          <div className="w-52">
            <Field label="Campaign" htmlFor="application-campaign">
              <Select
                id="application-campaign"
                value={campaignId}
                onChange={(event) => {
                  setCampaignId(event.target.value)
                  setPage(0)
                }}
              >
                <option value="">All campaigns</option>
                {(campaigns.data?.data ?? []).map((campaign) => (
                  <option key={campaign.id} value={campaign.id}>
                    {campaign.name}
                  </option>
                ))}
              </Select>
            </Field>
          </div>
          <div className="w-56">
            <Field label="Status" htmlFor="application-status">
              <Select
                id="application-status"
                value={status}
                onChange={(event) => {
                  setStatus(event.target.value)
                  setPage(0)
                }}
              >
                <option value="">All statuses</option>
                {APPLICATION_STATUSES.map((value) => (
                  <option key={value} value={value}>
                    {text(value)}
                  </option>
                ))}
              </Select>
            </Field>
          </div>
          <div className="min-w-56 flex-1">
            <Field label="Search" htmlFor="application-search">
              <TextInput
                id="application-search"
                value={term}
                onChange={(event) => setTerm(event.target.value)}
                placeholder="Reference, name or email"
              />
            </Field>
          </div>
          <Button type="submit" variant="secondary">
            Search
          </Button>
        </form>

        <QueryBoundary
          isLoading={applications.isLoading}
          error={applications.error}
          data={applications.data}
          onRetry={() => void applications.refetch()}
          loadingRows={8}
          empty={
            <EmptyState
              title="No applications match"
              description="Applications appear here once an applicant is recorded against a campaign."
            />
          }
        >
          {(data) => (
            <>
              <div className="overflow-x-auto">
                <table className="w-full border-collapse text-sm">
                  <thead>
                    <tr className="border-b border-border text-left">
                      <Th>Reference</Th>
                      <Th>Applicant</Th>
                      <Th hideBelow="md">Campaign</Th>
                      <Th hideBelow="lg">Submitted</Th>
                      <Th>Status</Th>
                      <Th className="text-right">Open</Th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.data.map((application) => (
                      <tr key={application.id} className="border-b border-border last:border-0 hover:bg-surface-soft/60">
                        <td className="nums px-4 py-3 whitespace-nowrap text-ink-muted">
                          {application.referenceCode}
                        </td>
                        <td className="px-4 py-3 font-medium text-ink">
                          {/* The name is always the way in, whatever state the application is in. */}
                          <Link
                            to={`/admissions/applications/${application.id}`}
                            className="text-primary hover:underline"
                          >
                            {[application.firstName, application.middleName, application.lastName]
                              .filter(Boolean)
                              .join(' ')}
                          </Link>
                        </td>
                        <td className="px-4 py-3 text-ink-muted">
                          {campaigns.data?.data.find((c) => c.id === application.campaignId)?.name ?? '—'}
                        </td>
                        <td className="px-4 py-3 whitespace-nowrap text-ink-muted">
                          {date(application.submittedAt?.slice(0, 10) ?? null)}
                        </td>
                        <td className="px-4 py-3">
                          <StatusBadge status={application.status} />
                        </td>
                        <td className="px-4 py-3 text-right">
                          {application.status === 'DRAFT' && can('ADMISSION_UPDATE') ? (
                            <Button
                              size="sm"
                              variant="secondary"
                              loading={submitReview.isPending}
                              onClick={() => submitReview.mutate(application.id)}
                            >
                              Submit
                            </Button>
                          ) : (
                            <Link
                              to={`/admissions/applications/${application.id}`}
                              className="text-sm font-medium text-primary hover:underline"
                            >
                              View
                            </Link>
                          )}
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

function ApplicationForm({
  campaigns,
  onCreated,
}: {
  campaigns: AdmissionCampaign[]
  onCreated: () => void
}) {
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const classes = useQuery({
    queryKey: ['academic-classes', 'admissions'],
    queryFn: () => api<Page<SchoolClass>>('/api/v1/academic/classes', { query: { size: 100 } }),
    enabled: open,
  })

  const create = useMutation({
    mutationFn: (values: ApplicationRequest) =>
      post<AdmissionApplication>('/api/v1/admissions/applications', values),
    onSuccess: (application) => {
      setError(null)
      setOpen(false)
      onCreated()
      navigate(`/admissions/applications/${application.id}`)
    },
    onError: (err) => setError(describeError(err)),
  })

  if (!open) {
    return (
      <div className="flex justify-end">
        <Button onClick={() => setOpen(true)} disabled={campaigns.length === 0}>
          New application
        </Button>
      </div>
    )
  }

  const submit = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const value = (key: string) => (form.get(key) as string | null)?.trim() || undefined
    create.mutate({
      campaignId: value('campaignId') ?? '',
      firstName: value('firstName') ?? '',
      middleName: value('middleName'),
      lastName: value('lastName'),
      dateOfBirth: value('dateOfBirth'),
      gender: value('gender'),
      nationality: value('nationality'),
      phone: value('phone'),
      email: value('email'),
      address: value('address'),
      previousSchool: value('previousSchool'),
      previousQualification: value('previousQualification'),
      previousPercentage: value('previousPercentage') ? Number(value('previousPercentage')) : undefined,
      entranceScore: value('entranceScore') ? Number(value('entranceScore')) : undefined,
      applyingClassId: value('applyingClassId'),
    })
  }

  return (
    <Panel
      title="New application"
      description="Creates a draft. Documents and the decision come after it is submitted."
    >
      <form onSubmit={submit} className="grid gap-4 sm:grid-cols-3">
        <Field label="Campaign" htmlFor="application-form-campaign" required>
          <Select id="application-form-campaign" name="campaignId" required defaultValue="">
            <option value="">Choose a campaign</option>
            {campaigns.map((campaign) => (
              <option key={campaign.id} value={campaign.id}>
                {campaign.name}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="First name" htmlFor="application-first" required>
          <TextInput id="application-first" name="firstName" required />
        </Field>
        <Field label="Last name" htmlFor="application-last">
          <TextInput id="application-last" name="lastName" />
        </Field>
        <Field label="Middle name" htmlFor="application-middle">
          <TextInput id="application-middle" name="middleName" />
        </Field>
        <Field label="Date of birth" htmlFor="application-dob">
          <TextInput id="application-dob" name="dateOfBirth" type="date" />
        </Field>
        <Field label="Gender" htmlFor="application-gender">
          <Select id="application-gender" name="gender" defaultValue="">
            <option value="">Not stated</option>
            {['MALE', 'FEMALE', 'OTHER'].map((value) => (
              <option key={value} value={value}>
                {text(value)}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="Applying for class" htmlFor="application-class">
          <Select id="application-class" name="applyingClassId" defaultValue="">
            <option value="">Not decided</option>
            {(classes.data?.data ?? []).map((item) => (
              <option key={item.id} value={item.id}>
                {item.name}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="Phone" htmlFor="application-phone">
          <TextInput id="application-phone" name="phone" />
        </Field>
        <Field label="Email" htmlFor="application-email">
          <TextInput id="application-email" name="email" type="email" />
        </Field>
        <Field label="Nationality" htmlFor="application-nationality">
          <TextInput id="application-nationality" name="nationality" />
        </Field>
        <Field label="Previous school" htmlFor="application-previous">
          <TextInput id="application-previous" name="previousSchool" />
        </Field>
        <Field label="Previous qualification" htmlFor="application-qualification">
          <TextInput id="application-qualification" name="previousQualification" />
        </Field>
        <Field label="Previous percentage" htmlFor="application-percentage">
          <TextInput
            id="application-percentage"
            name="previousPercentage"
            type="number"
            min={0}
            max={100}
            step="0.01"
          />
        </Field>
        <Field label="Entrance score" htmlFor="application-entrance">
          <TextInput id="application-entrance" name="entranceScore" type="number" min={0} step="0.01" />
        </Field>
        <div className="sm:col-span-3">
          <Field label="Address" htmlFor="application-address">
            <TextArea id="application-address" name="address" />
          </Field>
        </div>

        {error && <p className="sm:col-span-3 text-sm text-danger">{error}</p>}

        <div className="flex gap-2 sm:col-span-3">
          <Button type="submit" loading={create.isPending}>
            Create draft
          </Button>
          <Button type="button" variant="ghost" onClick={() => setOpen(false)}>
            Cancel
          </Button>
        </div>
      </form>
    </Panel>
  )
}

/* ------------------------------------------------------------- detail screen */

export function ApplicationDetailPage({ id }: { id: string }) {
  const { can } = useAuth()
  const queryClient = useQueryClient()
  const [notes, setNotes] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  const application = useQuery({
    queryKey: ['admission-application', id],
    queryFn: () => api<AdmissionApplication>(`/api/v1/admissions/applications/${id}`),
  })

  const documents = useQuery({
    queryKey: ['admission-application-documents', id],
    queryFn: () => api<import('./types').ApplicationDocument[]>(`/api/v1/admissions/applications/${id}/documents`),
  })

  const decisions = useQuery({
    queryKey: ['admission-application-decisions', id],
    queryFn: () => api<import('./types').AdmissionDecision[]>(`/api/v1/admissions/applications/${id}/decisions`),
  })

  /**
   * The workflow endpoints are not uniform: eligibility takes a decision body, the
   * document check takes a list of verdicts, and approve/enrol/reject read their notes
   * from query parameters. Each action states which shape it needs.
   */
  const action = useMutation({
    mutationFn: async (next: WorkflowAction) => {
      if (next.endpoint === 'approve' || next.endpoint === 'enrol' || next.endpoint === 'reject') {
        // enrol takes a roll number rather than a note; the others take the note only.
        const query =
          next.endpoint === 'enrol' ? { rollNumber: next.rollNumber } : { notes: next.notes }
        return api<void>(`/api/v1/admissions/applications/${id}/${next.endpoint}`, {
          method: 'POST',
          query,
        })
      }
      if (next.endpoint === 'documents/verify') {
        return post<void>(`/api/v1/admissions/applications/${id}/documents/verify`, next.verifications ?? [])
      }
      const body = (next as { body?: unknown }).body
      return post<void>(`/api/v1/admissions/applications/${id}/${next.endpoint}`, body ?? {})
    },
    onSuccess: (_result, next) => {
      setError(null)
      setNotice(noticeFor(next))
      void queryClient.invalidateQueries({ queryKey: ['admission-application', id] })
      void queryClient.invalidateQueries({ queryKey: ['admission-application-documents', id] })
      void queryClient.invalidateQueries({ queryKey: ['admission-application-decisions', id] })
    },
    onError: (err) => setError(describeError(err)),
  })

  const status = application.data?.status

  return (
    <div className="mx-auto flex max-w-7xl flex-col gap-5">
      <header className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <Link to="/admissions" className="text-sm text-primary hover:underline">
            Applications
          </Link>
          <h1 className="mt-1 text-2xl font-semibold text-ink">
            {application.data
              ? [application.data.firstName, application.data.middleName, application.data.lastName]
                  .filter(Boolean)
                  .join(' ')
              : 'Application'}
          </h1>
          <p className="nums mt-1 text-sm text-ink-subtle">{application.data?.referenceCode}</p>
        </div>
        {status && <StatusBadge status={status} />}
      </header>

      {application.isLoading && <LoadingState label="Loading application" rows={6} />}
      {application.isError && (
        <ErrorState error={application.error} onRetry={() => void application.refetch()} />
      )}

      {application.data && (
        <>
          <Panel title="Applicant">
            <dl className="grid gap-4 sm:grid-cols-3">
              <Fact label="Date of birth" value={date(application.data.dateOfBirth)} />
              <Fact label="Gender" value={text(application.data.gender)} />
              <Fact label="Nationality" value={text(application.data.nationality)} />
              <Fact label="Phone" value={text(application.data.phone)} />
              <Fact label="Email" value={text(application.data.email)} />
              <Fact label="Submitted" value={date(application.data.submittedAt?.slice(0, 10) ?? null)} />
              <Fact label="Previous school" value={text(application.data.previousSchool)} />
              <Fact
                label="Previous percentage"
                value={
                  application.data.previousPercentage == null
                    ? '—'
                    : num(application.data.previousPercentage)
                }
              />
              <Fact
                label="Entrance score"
                value={application.data.entranceScore == null ? '—' : num(application.data.entranceScore)}
              />
            </dl>
            {application.data.address && (
              <p className="mt-4 text-sm text-ink-muted">{application.data.address}</p>
            )}
            {application.data.studentId && (
              <p className="mt-4 text-sm text-ink">
                Enrolled as{' '}
                <Link to={`/students/${application.data.studentId}`} className="text-primary hover:underline">
                  a student record
                </Link>
              </p>
            )}
          </Panel>

          <Panel title="Workflow" description="Each step is enforced by the server; only the valid next step is offered.">
            <div className="flex flex-wrap gap-2">
              {status === 'DRAFT' && can('ADMISSION_UPDATE') && (
                <Button loading={action.isPending} onClick={() => action.mutate({ endpoint: 'submit' })}>
                  Submit application
                </Button>
              )}
              {status === 'SUBMITTED' && can('ADMISSION_REVIEW') && (
                <Button loading={action.isPending} onClick={() => action.mutate({ endpoint: 'review' })}>
                  Start review
                </Button>
              )}
              {can('ADMISSION_REVIEW') && active(status) && (
                <Button
                  loading={action.isPending}
                  onClick={() =>
                    action.mutate({
                      endpoint: 'documents/verify',
                      verifications: (documents.data ?? []).map((document) => ({
                        documentType: document.documentType,
                        status: 'ACCEPTED',
                      })),
                    })
                  }
                >
                  Accept all documents
                </Button>
              )}
              {can('ADMISSION_REVIEW') && active(status) && (
                <DocumentDecisions documents={documents.data ?? []} onSubmit={(verifications) =>
                  action.mutate({ endpoint: 'documents/verify', verifications })
                } />
              )}
              {can('ADMISSION_REVIEW') && (status === 'ELIGIBILITY' || status === 'ENTRANCE') && (
                <ScoreRecorder
                  entrance={application.data.entranceScore}
                  merit={application.data.meritScore}
                  onSubmit={(entrance, merit) => action.mutate({ endpoint: 'scores', body: { entrance, merit } })}
                />
              )}
              {can('ADMISSION_APPROVE') && (
                <>
                  <Button
                    variant="secondary"
                    loading={action.isPending}
                    onClick={() =>
                      action.mutate({ endpoint: 'eligibility', body: { decision: 'SELECTED', notes: notes || undefined } })
                    }
                  >
                    Mark eligible
                  </Button>
                  <Button
                    variant="secondary"
                    loading={action.isPending}
                    onClick={() =>
                      action.mutate({ endpoint: 'eligibility', body: { decision: 'WAITLISTED', notes: notes || undefined } })
                    }
                  >
                    Waitlist
                  </Button>
                </>
              )}
              {can('ADMISSION_APPROVE') && (
                <Button
                  loading={action.isPending}
                  onClick={() => action.mutate({ endpoint: 'approve', notes: notes || undefined })}
                >
                  Approve
                </Button>
              )}
              {can('ENROLLMENT_CREATE') && (
                <Button
                  loading={action.isPending}
                  onClick={() => action.mutate({ endpoint: 'enrol', rollNumber: notes || undefined })}
                >
                  Enrol
                </Button>
              )}
              {can('ADMISSION_REJECT') && (
                <Button
                  variant="ghost"
                  loading={action.isPending}
                  onClick={() =>
                    action.mutate({
                      endpoint: 'reject',
                      notes: notes || 'Not a fit at this time.',
                    })
                  }
                >
                  Reject
                </Button>
              )}
            </div>

            <div className="mt-4 max-w-xl">
              <Field label="Notes for the next action" htmlFor="application-notes">
                <TextArea
                  id="application-notes"
                  value={notes}
                  onChange={(event) => setNotes(event.target.value)}
                  rows={2}
                />
              </Field>
            </div>

            {notice && <p className="mt-3 text-sm text-success">{notice}</p>}
            {error && (
              <p role="alert" className="mt-3 text-sm text-danger">
                {error}
              </p>
            )}
          </Panel>

          <Panel title="Documents" padded={false}>
            {documents.isLoading ? (
              <div className="p-4">
                <LoadingState label="Loading documents" rows={2} />
              </div>
            ) : (documents.data ?? []).length === 0 ? (
              <EmptyState title="No documents" description="Nothing has been uploaded against this application." />
            ) : (
              <table className="w-full border-collapse text-sm">
                <thead>
                  <tr className="border-b border-border text-left">
                    <Th>Type</Th>
                    <Th>File</Th>
                    <Th>Size</Th>
                    <Th>Status</Th>
                    <Th>Reviewed</Th>
                  </tr>
                </thead>
                <tbody>
                  {(documents.data ?? []).map((document) => (
                    <tr key={document.id} className="border-b border-border last:border-0">
                      <td className="px-4 py-3 font-medium text-ink">{document.documentType}</td>
                      <td className="px-4 py-3 text-ink-muted">{document.fileName}</td>
                      <td className="nums px-4 py-3 text-ink-muted">
                        {document.sizeBytes == null ? '—' : `${Math.round(document.sizeBytes / 1024)} KB`}
                      </td>
                      <td className="px-4 py-3">
                        <StatusBadge status={document.status} />
                      </td>
                      <td className="px-4 py-3 text-ink-muted">{date(document.reviewedAt?.slice(0, 10) ?? null)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </Panel>

          <Panel title="Decisions" padded={false}>
            {(decisions.data ?? []).length === 0 ? (
              <EmptyState title="No decisions yet" description="Decisions appear here once a reviewer acts." />
            ) : (
              <ul className="divide-y divide-border">
                {(decisions.data ?? []).map((decision) => (
                  <li key={decision.id} className="flex flex-wrap items-center gap-3 px-4 py-3 text-sm">
                    <Badge tone="neutral">{text(decision.decision)}</Badge>
                    <span className="text-ink-muted">{date(decision.decidedAt?.slice(0, 10) ?? null)}</span>
                    {decision.notes && <span className="text-ink-subtle">{decision.notes}</span>}
                  </li>
                ))}
              </ul>
            )}
          </Panel>
        </>
      )}
    </div>
  )
}

function Fact({ label, value }: { label: string; value: ReactNode }) {
  return (
    <div>
      <dt className="text-xs font-medium tracking-wide text-ink-subtle uppercase">{label}</dt>
      <dd className="mt-0.5 text-sm text-ink">{value}</dd>
    </div>
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

/** One workflow call, with the body or query shape the endpoint actually accepts. */
type WorkflowAction =
  | { endpoint: 'submit' | 'review' }
  | { endpoint: 'eligibility'; body: { decision: string; notes?: string } }
  | { endpoint: 'scores'; body: { entrance: number | null; merit: number | null } }
  | { endpoint: 'documents/verify'; verifications: DocumentVerification[] }
  | { endpoint: 'approve' | 'reject'; notes?: string }
  | { endpoint: 'enrol'; notes?: string; rollNumber?: string }

function active(status: string | undefined) {
  return ACTIVE_APPLICATION_STATUSES.includes(status as (typeof ACTIVE_APPLICATION_STATUSES)[number])
}

function noticeFor(next: WorkflowAction): string {
  switch (next.endpoint) {
    case 'submit':
      return 'Application submitted.'
    case 'review':
      return 'Review started.'
    case 'eligibility': {
      const decision = (next as { body: { decision: string } }).body.decision
      return decision === 'WAITLISTED' ? 'Applicant waitlisted.' : 'Applicant marked eligible.'
    }
    case 'scores':
      return 'Scores recorded.'
    case 'documents/verify':
      return 'Documents verified.'
    case 'approve':
      return 'Application approved and the student record created.'
    case 'enrol':
      return 'Applicant enrolled.'
    case 'reject':
      return 'Application rejected.'
  }
}

/** Lets a reviewer accept or reject each uploaded document in one pass. */
function DocumentDecisions({
  documents,
  onSubmit,
}: {
  documents: ApplicationDocument[]
  onSubmit: (verifications: DocumentVerification[]) => void
}) {
  const [verdicts, setVerdicts] = useState<Record<string, { status: string; notes: string }>>({})
  if (documents.length === 0) return null

  const allAnswered = documents.every((document) => verdicts[document.id]?.status)

  return (
    <div className="w-full border-t border-border pt-4">
      <h3 className="text-sm font-medium text-ink">Document verdicts</h3>
      <ul className="mt-2 flex flex-col gap-2">
        {documents.map((document) => (
          <li key={document.id} className="flex flex-wrap items-center gap-2 text-sm">
            <span className="min-w-40 flex-1 text-ink">{document.documentType}</span>
            <TextInput
              aria-label={`Notes for ${document.documentType}`}
              placeholder="Optional note"
              value={verdicts[document.id]?.notes ?? ''}
              onChange={(event) =>
                setVerdicts((current) => ({
                  ...current,
                  [document.id]: {
                    status: current[document.id]?.status ?? '',
                    notes: event.target.value,
                  },
                }))
              }
              className="w-56"
            />
            {DOCUMENT_STATUSES.filter((value) => value !== 'PENDING').map((value) => (
              <Button
                key={value}
                size="sm"
                variant={verdicts[document.id]?.status === value ? 'primary' : 'ghost'}
                onClick={() =>
                  setVerdicts((current) => ({
                    ...current,
                    [document.id]: {
                      status: value,
                      notes: current[document.id]?.notes ?? '',
                    },
                  }))
                }
              >
                {text(value)}
              </Button>
            ))}
          </li>
        ))}
      </ul>
      <div className="mt-3">
        <Button
          disabled={!allAnswered}
          onClick={() =>
            onSubmit(
              documents.map((document) => ({
                documentType: document.documentType,
                status: verdicts[document.id]?.status ?? 'PENDING',
                notes: verdicts[document.id]?.notes || undefined,
              })),
            )
          }
        >
          Submit verdicts
        </Button>
      </div>
    </div>
  )
}

/** Records the entrance and merit figures that drive selection. */
function ScoreRecorder({
  entrance,
  merit,
  onSubmit,
}: {
  entrance: number | null
  merit: number | null
  onSubmit: (entrance: number | null, merit: number | null) => void
}) {
  const [entranceValue, setEntranceValue] = useState(entrance == null ? '' : String(entrance))
  const [meritValue, setMeritValue] = useState(merit == null ? '' : String(merit))

  return (
    <div className="w-full border-t border-border pt-4">
      <h3 className="text-sm font-medium text-ink">Scores</h3>
      <div className="mt-2 flex flex-wrap items-end gap-3">
        <div className="w-40">
          <Field label="Entrance" htmlFor="application-entrance-score">
            <TextInput
              id="application-entrance-score"
              type="number"
              step="0.01"
              min={0}
              value={entranceValue}
              onChange={(event) => setEntranceValue(event.target.value)}
            />
          </Field>
        </div>
        <div className="w-40">
          <Field label="Merit" htmlFor="application-merit-score">
            <TextInput
              id="application-merit-score"
              type="number"
              step="0.01"
              min={0}
              value={meritValue}
              onChange={(event) => setMeritValue(event.target.value)}
            />
          </Field>
        </div>
        <Button
          onClick={() =>
            onSubmit(
              entranceValue === '' ? null : Number(entranceValue),
              meritValue === '' ? null : Number(meritValue),
            )
          }
        >
          Save scores
        </Button>
      </div>
    </div>
  )
}

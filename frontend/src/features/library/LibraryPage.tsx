import { Fragment, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, post, type PageResponse } from '../../lib/api'
import { describeError, useAuth } from '../../auth/AuthProvider'
import {
  Badge,
  Button,
  EmptyState,
  ErrorState,
  Field,
  LoadingState,
  Panel,
  Select,
  StatusBadge,
  TextInput,
  TextInputAdorned,
} from '../../components/ui'
import { PageHeader, Pager } from '../../components/DataTable'
import { date, humanise, money, num, text } from '../../lib/format'
import type { Author, Book, Category, Copy, Fine, LibraryOverview, Loan, Member, Reservation } from './types'

/** Nothing out, for before a member has been chosen. */
const EMPTY_LOANS: PageResponse<Loan> = {
  data: [], page: 0, size: 50, totalElements: 0, totalPages: 0, first: true, last: true, empty: true,
}

type Tab = 'overview' | 'books' | 'copies' | 'members' | 'loans' | 'reservations' | 'fines' | 'reference'

const TABS: { id: Tab; label: string }[] = [
  { id: 'overview', label: 'Overview' },
  { id: 'books', label: 'Catalogue' },
  { id: 'copies', label: 'Copies' },
  { id: 'members', label: 'Members' },
  { id: 'loans', label: 'Circulation' },
  { id: 'reservations', label: 'Reservations' },
  { id: 'fines', label: 'Fines' },
  { id: 'reference', label: 'Categories' },
]

export function LibraryPage() {
  const [tab, setTab] = useState<Tab>('overview')

  return (
    <div className="mx-auto flex max-w-7xl flex-col gap-5">
      <PageHeader
        title="Library"
        description="Catalogue, copies, members, circulation, reservations and fines."
      />

      <div className="flex flex-wrap gap-1.5 border-b border-border" role="tablist">
        {TABS.map((item) => (
          <button
            key={item.id}
            role="tab"
            aria-selected={tab === item.id}
            onClick={() => setTab(item.id)}
            className={[
              '-mb-px border-b-2 px-3 py-2 text-sm font-medium transition-colors',
              tab === item.id
                ? 'border-primary text-primary'
                : 'border-transparent text-ink-subtle hover:text-ink',
            ].join(' ')}
          >
            {item.label}
          </button>
        ))}
      </div>

      {tab === 'overview' && <OverviewTab />}
      {tab === 'books' && <CatalogueTab />}
      {tab === 'copies' && <CopiesTab />}
      {tab === 'members' && <MembersTab />}
      {tab === 'loans' && <CirculationTab />}
      {tab === 'reservations' && <ReservationsTab />}
      {tab === 'fines' && <FinesTab />}
      {tab === 'reference' && <ReferenceTab />}
    </div>
  )
}

/* ------------------------------------------------------------------ overview */

function OverviewTab() {
  const overview = useQuery({
    queryKey: ['library-overview'],
    queryFn: () => api<LibraryOverview>('/api/v1/library/overview'),
  })

  const overdue = useQuery({
    queryKey: ['library-overdue'],
    queryFn: () => api<Loan[]>('/api/v1/library/loans-overdue'),
  })

  const fines = useQuery({
    queryKey: ['library-fines-outstanding'],
    queryFn: () => api<Fine[]>('/api/v1/library/fines', { query: { status: 'OUTSTANDING' } }),
  })

  if (overview.isLoading) return <LoadingState label="Loading library…" />
  if (overview.isError) return <ErrorState error={overview.error} onRetry={() => void overview.refetch()} />

  const data = overview.data!

  return (
    <>
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Metric label="Titles" value={num(data.titles)} />
        <Metric label="Copies" value={`${num(data.copiesAvailable)} of ${num(data.copies)} available`} />
        <Metric label="Members" value={num(data.members)} />
        <Metric label="Books out" value={num(data.loansOut)} />
        <Metric label="Overdue" value={num(data.overdue)} tone={data.overdue > 0 ? 'danger' : 'neutral'} />
        <Metric label="Reservations waiting" value={num(data.waitingReservations)} />
        <Metric label="Fines outstanding" value={money(data.finesOutstanding)} tone={Number(data.finesOutstanding) > 0 ? 'warning' : 'neutral'} />
      </div>

      <div className="grid gap-5 lg:grid-cols-2">
        <Panel title="Overdue" padded={false} description="Chase these first">
          {overdue.data && overdue.data.length > 0 ? (
            <div className="overflow-x-auto">
              <table className="w-full border-collapse text-sm">
                <thead>
                  <tr className="border-b border-border text-left">
                    <Th>Book</Th>
                    <Th hideBelow="md">Member</Th>
                    <Th align="right">Days late</Th>
                  </tr>
                </thead>
                <tbody>
                  {overdue.data.map((loan) => (
                    <tr key={loan.id} className="border-b border-border last:border-0">
                      <td className="px-4 py-2.5 text-ink">{loan.bookTitle}</td>
                      <td className="hidden px-4 py-2.5 text-ink-muted md:table-cell">{loan.memberName}</td>
                      <td className="nums px-4 py-2.5 text-right text-danger">{loan.daysOverdue}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <EmptyState title="Nothing overdue" description="Every book is back on time." />
          )}
        </Panel>

        <Panel title="Outstanding fines" padded={false}>
          {fines.data && fines.data.length > 0 ? (
            <div className="overflow-x-auto">
              <table className="w-full border-collapse text-sm">
                <thead>
                  <tr className="border-b border-border text-left">
                    <Th>Member</Th>
                    <Th hideBelow="md">Reason</Th>
                    <Th align="right">Amount</Th>
                  </tr>
                </thead>
                <tbody>
                  {fines.data.map((fine) => (
                    <tr key={fine.id} className="border-b border-border last:border-0">
                      <td className="px-4 py-2.5 text-ink">{fine.memberName}</td>
                      <td className="hidden px-4 py-2.5 text-ink-muted md:table-cell">{fine.reason}</td>
                      <td className="nums px-4 py-2.5 text-right text-ink">{money(fine.amount)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <EmptyState title="No fines outstanding" description="Nobody owes the library anything." />
          )}
        </Panel>
      </div>
    </>
  )
}

/* ---------------------------------------------------------------- catalogue */

function CatalogueTab() {
  const { can } = useAuth()
  const queryClient = useQueryClient()
  const [page, setPage] = useState(0)
  const [term, setTerm] = useState('')
  const [search, setSearch] = useState('')
  const [categoryId, setCategoryId] = useState('')
  const [referenceOnly, setReferenceOnly] = useState(false)
  const [openBook, setOpenBook] = useState<string | null>(null)

  const categories = useQuery({
    queryKey: ['library-categories'],
    queryFn: () => api<Category[]>('/api/v1/library/categories'),
  })

  const books = useQuery({
    queryKey: ['library-books', { page, search, categoryId, referenceOnly }],
    queryFn: () =>
      api<PageResponse<Book>>('/api/v1/library/books', {
        query: { page, size: 20, term: search || undefined, categoryId: categoryId || undefined, referenceOnly },
      }),
  })

  const addBook = useMutation({
    mutationFn: (body: Record<string, unknown>) => post<Book>('/api/v1/library/books', body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['library-books'] })
    },
  })

  return (
    <>
      <Panel
        title="Catalogue"
        padded={false}
        actions={
          <div className="flex flex-wrap items-center gap-2">
            <form
              onSubmit={(event) => { event.preventDefault(); setPage(0); setSearch(term) }}
              className="flex gap-2"
            >
              <TextInput
                value={term}
                onChange={(event) => setTerm(event.target.value)}
                placeholder="Title, author or ISBN"
                aria-label="Search catalogue"
                className="w-56"
              />
              <Button type="submit" variant="secondary">Search</Button>
            </form>
            <Select value={categoryId} onChange={(event) => { setCategoryId(event.target.value); setPage(0) }} aria-label="Category" className="w-44">
              <option value="">All categories</option>
              {(categories.data ?? []).map((category) => (
                <option key={category.id} value={category.id}>{category.name}</option>
              ))}
            </Select>
            <label className="flex items-center gap-1.5 text-sm text-ink-muted">
              <input
                type="checkbox"
                checked={referenceOnly}
                onChange={(event) => { setReferenceOnly(event.target.checked); setPage(0) }}
              />
              Reference only
            </label>
          </div>
        }
      >
        {books.isLoading ? (
          <LoadingState label="Loading catalogue…" />
        ) : books.isError ? (
          <ErrorState error={books.error} onRetry={() => void books.refetch()} />
        ) : (books.data?.data ?? []).length === 0 ? (
          <EmptyState
            title="No titles found"
            description={search ? `Nothing matched “${search}”.` : 'The catalogue is empty.'}
          />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full border-collapse text-sm">
              <thead>
                <tr className="border-b border-border text-left">
                  <Th>Title</Th>
                  <Th hideBelow="md">Author</Th>
                  <Th hideBelow="lg">Category</Th>
                  <Th align="right">Copies</Th>
                  <Th>Status</Th>
                  <Th align="right"> </Th>
                </tr>
              </thead>
              <tbody>
                {(books.data?.data ?? []).map((book) => (
                  <Fragment key={book.id}>
                    <tr className="border-b border-border last:border-0 hover:bg-surface-soft/60">
                      <td className="px-4 py-3">
                        <p className="font-medium text-ink">{book.title}</p>
                        <p className="text-xs text-ink-subtle">
                          {book.isbn ? `ISBN ${book.isbn}` : 'No ISBN'}
                          {book.edition ? ` · ${book.edition}` : ''}
                        </p>
                      </td>
                      <td className="hidden px-4 py-3 text-ink-muted md:table-cell">
                        {(book.authors ?? []).join(', ') || '—'}
                      </td>
                      <td className="hidden px-4 py-3 text-ink-muted lg:table-cell">
                        {book.category?.name ?? '—'}
                      </td>
                      <td className="nums px-4 py-3 text-right text-ink-muted">
                        {book.availableCopies}/{book.totalCopies}
                      </td>
                      <td className="px-4 py-3">
                        {book.reference
                          ? <Badge tone="info">Reference</Badge>
                          : book.availableCopies > 0
                            ? <Badge tone="success">Available</Badge>
                            : <Badge tone="warning">All out</Badge>}
                      </td>
                      <td className="px-4 py-3 text-right">
                        <Button size="sm" variant="ghost" onClick={() => setOpenBook(openBook === book.id ? null : book.id)}>
                          {openBook === book.id ? 'Close' : 'Copies'}
                        </Button>
                      </td>
                    </tr>
                    {openBook === book.id && (
                      <tr className="border-b border-border bg-surface-soft/40">
                        <td colSpan={6} className="px-4 py-4">
                          <BookCopies book={book} />
                        </td>
                      </tr>
                    )}
                  </Fragment>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Panel>

      {books.data && <Pager page={books.data} onChange={setPage} />}

      {can('LIBRARY_MANAGE') && (
        <Panel title="Catalogue a new book">
          <NewBookForm
            categories={categories.data ?? []}
            pending={addBook.isPending}
            error={addBook.isError ? describeError(addBook.error) : null}
            onSubmit={(body) => addBook.mutate(body)}
          />
        </Panel>
      )}
    </>
  )
}

function BookCopies({ book }: { book: Book }) {
  const { can } = useAuth()
  const queryClient = useQueryClient()

  const copies = useQuery({
    queryKey: ['library-book-copies', book.id],
    queryFn: () => api<PageResponse<Copy>>(`/api/v1/library/books/${book.id}/copies`),
  })

  const addCopy = useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      post<Copy>(`/api/v1/library/books/${book.id}/copies/generated`, body),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['library-book-copies', book.id] }),
  })

  // A book goes out to a named person, so the counter has to say who before the copy moves.
  const [lending, setLending] = useState<Copy | null>(null)
  const issue = useMutation({
    mutationFn: ({ copyId, memberId, loanDays }: { copyId: string; memberId: string; loanDays?: number }) =>
      post<Loan>('/api/v1/library/loans', { copyId, memberId, loanDays }),
    onSuccess: () => {
      setLending(null)
      for (const key of ['library-book-copies', 'library-copies', 'library-overview', 'library-loans']) {
        void queryClient.invalidateQueries({ queryKey: [key] })
      }
    },
  })

  const setStatus = useMutation({
    mutationFn: ({ copyId, status }: { copyId: string; status: string }) =>
      api<Copy>(`/api/v1/library/copies/${copyId}/status?status=${status}`, { method: 'PATCH' }),
    onSuccess: () => void queryClient.invalidateQueries(),
  })

  const rows = copies.data?.data ?? []

  return (
    <div className="grid gap-4 lg:grid-cols-[1fr_20rem]">
      <div>
        {copies.isLoading ? (
          <p className="text-sm text-ink-subtle">Loading copies…</p>
        ) : rows.length === 0 ? (
          <p className="text-sm text-ink-subtle">No copies registered for this title yet.</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full border-collapse text-sm">
              <thead>
                <tr className="border-b border-border text-left">
                  <Th>Barcode</Th>
                  <Th hideBelow="md">Condition</Th>
                  <Th>Status</Th>
                  <Th align="right"> </Th>
                </tr>
              </thead>
              <tbody>
                {rows.map((copy) => (
                  <tr key={copy.id} className="border-b border-border last:border-0">
                    <td className="nums px-3 py-2 text-ink">{copy.barcode}</td>
                    <td className="hidden px-3 py-2 text-ink-muted md:table-cell">
                      {humanise(copy.conditionStatus)}
                    </td>
                    <td className="px-3 py-2"><StatusBadge status={copy.status} /></td>
                    <td className="px-3 py-2 text-right">
                      {can('LIBRARY_CIRCULATE') && copy.status === 'AVAILABLE' && (
                        <Button
                          size="sm"
                          variant="secondary"
                          onClick={() => {
                            issue.reset()
                            setLending(copy)
                          }}
                        >
                          Lend
                        </Button>
                      )}
                      {can('LIBRARY_MANAGE') && copy.status !== 'AVAILABLE' && copy.status !== 'ISSUED' && (
                        <Button size="sm" variant="ghost" loading={setStatus.isPending} onClick={() => setStatus.mutate({ copyId: copy.id, status: 'AVAILABLE' })}>
                          Return to shelf
                        </Button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {(issue.isError || setStatus.isError) && (
          <p className="mt-2 text-sm text-danger">{describeError(issue.error ?? setStatus.error)}</p>
        )}
      </div>

      {lending && (
        <LendForm
          copy={lending}
          pending={issue.isPending}
          error={issue.isError ? describeError(issue.error) : null}
          onSubmit={({ memberId, loanDays }) => issue.mutate({ copyId: lending.id, memberId, loanDays })}
          onCancel={() => setLending(null)}
        />
      )}

      {can('LIBRARY_MANAGE') && (
        <form
          onSubmit={(event) => {
            event.preventDefault()
            addCopy.mutate({
              acquisitionType: (event.currentTarget.elements.namedItem('acquisitionType') as HTMLSelectElement).value,
              conditionStatus: (event.currentTarget.elements.namedItem('conditionStatus') as HTMLSelectElement).value,
            })
          }}
          className="grid gap-3 rounded-lg border border-border bg-surface p-3"
        >
          <p className="text-sm font-medium text-ink">Register a copy</p>
          <Field label="Acquisition">
            <Select name="acquisitionType" defaultValue="PURCHASE">
              <option value="PURCHASE">Purchase</option>
              <option value="DONATION">Donation</option>
              <option value="TRANSFER">Transfer</option>
            </Select>
          </Field>
          <Field label="Condition">
            <Select name="conditionStatus" defaultValue="GOOD">
              <option value="NEW">New</option>
              <option value="GOOD">Good</option>
              <option value="FAIR">Fair</option>
              <option value="POOR">Poor</option>
            </Select>
          </Field>
          <Button type="submit" loading={addCopy.isPending}>Add copy</Button>
          {addCopy.isError && <p className="text-sm text-danger">{describeError(addCopy.error)}</p>}
        </form>
      )}
    </div>
  )
}

/** Who is taking this copy, and for how long. */
function LendForm({
  copy,
  pending,
  error,
  onSubmit,
  onCancel,
}: {
  copy: Copy
  pending: boolean
  error: string | null
  onSubmit: (values: { memberId: string; loanDays?: number }) => void
  onCancel: () => void
}) {
  const [term, setTerm] = useState('')
  const [memberId, setMemberId] = useState('')
  const [loanDays, setLoanDays] = useState('14')

  const members = useQuery({
    queryKey: ['library-members', { term }],
    queryFn: () =>
      api<PageResponse<Member>>('/api/v1/library/members', {
        query: { status: 'ACTIVE', term, page: 0, size: 25 },
      }),
  })

  const chosen = (members.data?.data ?? []).find((m) => m.id === memberId)

  return (
    <form
      onSubmit={(event) => {
        event.preventDefault()
        const days = Number(loanDays)
        onSubmit({ memberId, loanDays: days > 0 ? days : undefined })
      }}
      className="grid gap-3 rounded-lg border border-primary-soft bg-surface p-3"
    >
      <p className="text-sm font-medium text-ink">Lend {copy.barcode}</p>

      <Field label="Search for the member" htmlFor="lend-member-search">
        <input
          id="lend-member-search"
          value={term}
          onChange={(event) => setTerm(event.target.value)}
          placeholder="Name or member code"
          className="w-full rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink"
        />
      </Field>

      <Field label="Member" htmlFor="lend-member" required>
        <select
          id="lend-member"
          value={memberId}
          onChange={(event) => setMemberId(event.target.value)}
          required
          className="w-full rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink"
        >
          <option value="">Choose a member</option>
          {(members.data?.data ?? []).map((member) => (
            <option key={member.id} value={member.id}>
              {member.name} · {member.memberCode} ({member.booksOut}/{member.maxBooks} out)
            </option>
          ))}
        </select>
      </Field>

      {members.isLoading && <p className="text-xs text-ink-subtle">Looking for members…</p>}
      {!members.isLoading && (members.data?.data ?? []).length === 0 && (
        <p className="text-xs text-ink-subtle">
          No active member matches. Register their card under Members first.
        </p>
      )}
      {chosen && chosen.booksOut >= chosen.maxBooks && (
        <p className="text-xs text-danger">
          {chosen.name} already has {chosen.booksOut} out, which is their limit.
        </p>
      )}

      <Field label="Days" htmlFor="lend-days" hint="Leave blank for the standard loan period.">
        <input
          id="lend-days"
          type="number"
          min={1}
          max={180}
          value={loanDays}
          onChange={(event) => setLoanDays(event.target.value)}
          className="w-full rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink"
        />
      </Field>

      {error && <p className="text-sm text-danger">{error}</p>}

      <div className="flex gap-2">
        <Button type="submit" loading={pending} disabled={!memberId}>Lend the book</Button>
        <Button type="button" variant="ghost" onClick={onCancel}>Cancel</Button>
      </div>
    </form>
  )
}

function NewBookForm({
  categories,
  pending,
  error,
  onSubmit,
}: {
  categories: Category[]
  pending: boolean
  error: string | null
  onSubmit: (body: Record<string, unknown>) => void
}) {
  const authors = useQuery({
    queryKey: ['library-authors'],
    queryFn: () => api<Author[]>('/api/v1/library/authors'),
  })
  const publishers = useQuery({
    queryKey: ['library-publishers'],
    queryFn: () => api<{ id: string; name: string }[]>('/api/v1/library/publishers'),
  })

  const [values, setValues] = useState<Record<string, string>>({})
  const [authorIds, setAuthorIds] = useState<string[]>([])

  return (
    <form
      onSubmit={(event) => {
        event.preventDefault()
        onSubmit({
          title: values.title,
          isbn: values.isbn || undefined,
          edition: values.edition || undefined,
          publicationYear: values.publicationYear ? Number(values.publicationYear) : undefined,
          categoryId: values.categoryId || undefined,
          publisherId: values.publisherId || undefined,
          authorIds: authorIds.length > 0 ? authorIds : undefined,
          reference: values.reference === 'true',
        })
      }}
      className="grid gap-4 sm:grid-cols-2"
    >
      <Field label="Title" required>
        <TextInput value={values.title ?? ''} onChange={(e) => setValues({ ...values, title: e.target.value })} required maxLength={300} />
      </Field>
      <Field label="ISBN">
        <TextInput value={values.isbn ?? ''} onChange={(e) => setValues({ ...values, isbn: e.target.value })} maxLength={20} />
      </Field>
      <Field label="Edition">
        <TextInput value={values.edition ?? ''} onChange={(e) => setValues({ ...values, edition: e.target.value })} maxLength={60} />
      </Field>
      <Field label="Publication year">
        <TextInput type="number" value={values.publicationYear ?? ''} onChange={(e) => setValues({ ...values, publicationYear: e.target.value })} />
      </Field>
      <Field label="Category">
        <Select value={values.categoryId ?? ''} onChange={(e) => setValues({ ...values, categoryId: e.target.value })}>
          <option value="">Uncategorised</option>
          {categories.map((category) => (
            <option key={category.id} value={category.id}>{category.name}</option>
          ))}
        </Select>
      </Field>
      <Field label="Publisher">
        <Select value={values.publisherId ?? ''} onChange={(e) => setValues({ ...values, publisherId: e.target.value })}>
          <option value="">None</option>
          {(publishers.data ?? []).map((publisher) => (
            <option key={publisher.id} value={publisher.id}>{publisher.name}</option>
          ))}
        </Select>
      </Field>
      <Field label="Authors" hint="Ctrl-click for more than one">
        <select
          multiple
          size={4}
          value={authorIds}
          onChange={(e) => setAuthorIds(Array.from(e.target.selectedOptions, (option) => option.value))}
          className="w-full rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink"
        >
          {(authors.data ?? []).map((author) => (
            <option key={author.id} value={author.id}>{author.name}</option>
          ))}
        </select>
      </Field>
      <Field label="Type">
        <Select value={values.reference ?? 'false'} onChange={(e) => setValues({ ...values, reference: e.target.value })}>
          <option value="false">Borrowable</option>
          <option value="true">Reference only</option>
        </Select>
      </Field>
      <div className="sm:col-span-2">
        <Button type="submit" loading={pending} disabled={!values.title}>Catalogue book</Button>
        {error && <p className="mt-2 text-sm text-danger">{error}</p>}
      </div>
    </form>
  )
}

/* ------------------------------------------------------------------- copies */

function CopiesTab() {
  const [status, setStatus] = useState('AVAILABLE')
  const [page, setPage] = useState(0)

  const copies = useQuery({
    queryKey: ['library-copies', { status, page }],
    queryFn: () => api<PageResponse<Copy>>('/api/v1/library/copies', { query: { status, page, size: 50 } }),
  })

  return (
    <>
      <Panel
        title="Copies"
        padded={false}
        description="Every physical item, wherever it is"
        actions={
          <Select value={status} onChange={(e) => { setStatus(e.target.value); setPage(0) }} aria-label="Copy status">
            {['AVAILABLE', 'ISSUED', 'RESERVED', 'IN_REPAIR', 'WITHDRAWN', 'LOST'].map((value) => (
              <option key={value} value={value}>{humanise(value)}</option>
            ))}
          </Select>
        }
      >
        {copies.isLoading ? (
          <LoadingState label="Loading copies…" />
        ) : copies.isError ? (
          <ErrorState error={copies.error} onRetry={() => void copies.refetch()} />
        ) : (copies.data?.data ?? []).length === 0 ? (
          <EmptyState title="No copies" description={`Nothing is ${humanise(status).toLowerCase()} right now.`} />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full border-collapse text-sm">
              <thead>
                <tr className="border-b border-border text-left">
                  <Th>Barcode</Th>
                  <Th>Book</Th>
                  <Th hideBelow="md">Condition</Th>
                  <Th hideBelow="lg">Acquired</Th>
                  <Th>Status</Th>
                </tr>
              </thead>
              <tbody>
                {(copies.data?.data ?? []).map((copy) => (
                  <tr key={copy.id} className="border-b border-border last:border-0">
                    <td className="nums px-4 py-2.5 text-ink">{copy.barcode}</td>
                    <td className="px-4 py-2.5 text-ink-muted">{copy.bookTitle}</td>
                    <td className="hidden px-4 py-2.5 text-ink-muted md:table-cell">{humanise(copy.conditionStatus)}</td>
                    <td className="nums hidden px-4 py-2.5 text-ink-subtle lg:table-cell">{date(copy.acquiredOn)}</td>
                    <td className="px-4 py-2.5"><StatusBadge status={copy.status} /></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Panel>
      {copies.data && <Pager page={copies.data} onChange={setPage} />}
    </>
  )
}

/* ------------------------------------------------------------------ members */

function MembersTab() {
  const { can } = useAuth()
  const queryClient = useQueryClient()
  const [status, setStatus] = useState('ACTIVE')
  const [page, setPage] = useState(0)

  const members = useQuery({
    queryKey: ['library-members', { status, page }],
    queryFn: () => api<PageResponse<Member>>('/api/v1/library/members', { query: { status, page, size: 20 } }),
  })

  const addMember = useMutation({
    mutationFn: (body: Record<string, unknown>) => post<Member>('/api/v1/library/members', body),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['library-members'] }),
  })

  return (
    <>
      <Panel
        title="Members"
        padded={false}
        actions={
          <Select value={status} onChange={(e) => { setStatus(e.target.value); setPage(0) }} aria-label="Member status">
            {['ACTIVE', 'SUSPENDED', 'EXPIRED'].map((value) => (
              <option key={value} value={value}>{humanise(value)}</option>
            ))}
          </Select>
        }
      >
        {members.isLoading ? (
          <LoadingState label="Loading members…" />
        ) : members.isError ? (
          <ErrorState error={members.error} onRetry={() => void members.refetch()} />
        ) : (members.data?.data ?? []).length === 0 ? (
          <EmptyState title="No members" description={`Nobody is ${humanise(status).toLowerCase()}.`} />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full border-collapse text-sm">
              <thead>
                <tr className="border-b border-border text-left">
                  <Th>Member</Th>
                  <Th hideBelow="md">Kind</Th>
                  <Th hideBelow="lg">Ends</Th>
                  <Th align="right">Books out</Th>
                  <Th align="right" hideBelow="md">Fines</Th>
                  <Th>Status</Th>
                </tr>
              </thead>
              <tbody>
                {(members.data?.data ?? []).map((member) => (
                  <tr key={member.id} className="border-b border-border last:border-0">
                    <td className="px-4 py-2.5">
                      <p className="font-medium text-ink">{member.name}</p>
                      <p className="nums text-xs text-ink-subtle">{member.memberCode}</p>
                    </td>
                    <td className="hidden px-4 py-2.5 text-ink-muted md:table-cell">
                      {member.studentId ? 'Student' : member.employeeId ? 'Staff' : member.userId ? 'Account' : 'External'}
                    </td>
                    <td className="nums hidden px-4 py-2.5 text-ink-subtle lg:table-cell">{date(member.membershipEnd)}</td>
                    <td className="nums px-4 py-2.5 text-right text-ink-muted">
                      {member.booksOut}/{member.maxBooks}
                    </td>
                    <td className="nums hidden px-4 py-2.5 text-right text-ink-muted md:table-cell">
                      {money(member.outstandingFines)}
                    </td>
                    <td className="px-4 py-2.5"><StatusBadge status={member.status} /></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Panel>

      {members.data && <Pager page={members.data} onChange={setPage} />}

      {can('LIBRARY_MANAGE') && (
        <Panel title="Register an external member" description="Students and staff are added from their own records">
          <ExternalMemberForm
            pending={addMember.isPending}
            error={addMember.isError ? describeError(addMember.error) : null}
            onSubmit={(body) => addMember.mutate(body)}
          />
        </Panel>
      )}
    </>
  )
}

function ExternalMemberForm({
  pending,
  error,
  onSubmit,
}: {
  pending: boolean
  error: string | null
  onSubmit: (body: Record<string, unknown>) => void
}) {
  const [values, setValues] = useState<Record<string, string>>({})

  return (
    <form
      onSubmit={(event) => {
        event.preventDefault()
        onSubmit({
          memberCode: values.memberCode || undefined,
          externalName: values.externalName,
          externalPhone: values.externalPhone || undefined,
          externalEmail: values.externalEmail || undefined,
          maxBooks: values.maxBooks ? Number(values.maxBooks) : undefined,
        })
      }}
      className="grid gap-4 sm:grid-cols-2"
    >
      <Field label="Full name" required>
        <TextInput value={values.externalName ?? ''} onChange={(e) => setValues({ ...values, externalName: e.target.value })} required maxLength={200} />
      </Field>
      <Field label="Member code" hint="Left blank, one is assigned">
        <TextInput value={values.memberCode ?? ''} onChange={(e) => setValues({ ...values, memberCode: e.target.value })} maxLength={40} />
      </Field>
      <Field label="Phone">
        <TextInput value={values.externalPhone ?? ''} onChange={(e) => setValues({ ...values, externalPhone: e.target.value })} maxLength={40} />
      </Field>
      <Field label="Email">
        <TextInput type="email" value={values.externalEmail ?? ''} onChange={(e) => setValues({ ...values, externalEmail: e.target.value })} maxLength={180} />
      </Field>
      <Field label="Borrow limit">
        <TextInput type="number" min={1} max={10} defaultValue={3} onChange={(e) => setValues({ ...values, maxBooks: e.target.value })} />
      </Field>
      <div className="sm:col-span-2">
        <Button type="submit" loading={pending} disabled={!values.externalName}>Add member</Button>
        {error && <p className="mt-2 text-sm text-danger">{error}</p>}
      </div>
    </form>
  )
}

/* -------------------------------------------------------------- circulation */

function CirculationTab() {
  const { can } = useAuth()
  const queryClient = useQueryClient()
  const [memberId, setMemberId] = useState('')
  const [search, setSearch] = useState('')
  const [memberQuery, setMemberQuery] = useState('')

  const members = useQuery({
    queryKey: ['library-member-lookup', { search: memberQuery }],
    queryFn: () => api<PageResponse<Member>>('/api/v1/library/members', { query: { page: 0, size: 20 } }),
    enabled: can('LIBRARY_CIRCULATE'),
  })

  const loans = useQuery({
    queryKey: ['library-loans', { memberId }],
    queryFn: () =>
      memberId
        ? api<PageResponse<Loan>>(`/api/v1/library/loans/${memberId}`, { query: { size: 50 } })
        : Promise.resolve(EMPTY_LOANS),
    enabled: Boolean(memberId),
  })

  // Closing a loan moves the copy and the counts as well, so the shelf and the overview are
  // refreshed too; otherwise the desk returns a book and the copy still reads as issued.
  const circulationChanged = () => {
    for (const key of ['library-loans', 'library-book-copies', 'library-copies', 'library-overview',
      'library-overdue', 'library-fines-outstanding']) {
      void queryClient.invalidateQueries({ queryKey: [key] })
    }
  }

  const returnLoan = useMutation({
    mutationFn: ({ loanId, lost }: { loanId: string; lost: boolean }) =>
      post<Loan>(`/api/v1/library/loans/${loanId}/return`, { lost }),
    onSuccess: circulationChanged,
  })

  const renewLoan = useMutation({
    mutationFn: (loanId: string) => post<Loan>(`/api/v1/library/loans/${loanId}/renew`, {}),
    onSuccess: circulationChanged,
  })

  return (
    <>
      <Panel title="Circulation">
        <div className="grid gap-3 sm:grid-cols-[1fr_auto]">
          <Field label="Member" hint={memberId ? '' : 'Choose a member to see what they have out'}>
            <Select value={memberId} onChange={(e) => setMemberId(e.target.value)}>
              <option value="">All members</option>
              {(members.data?.data ?? []).map((member) => (
                <option key={member.id} value={member.id}>
                  {member.name} ({member.memberCode})
                </option>
              ))}
            </Select>
          </Field>
          <div className="flex items-end">
            <form
              onSubmit={(event) => { event.preventDefault(); setMemberQuery(search) }}
              className="flex gap-2"
            >
              <TextInput
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                placeholder="Reload members"
                aria-label="Refresh members"
                className="w-40"
              />
              <Button type="submit" variant="secondary">Refresh</Button>
            </form>
          </div>
        </div>
      </Panel>

      <Panel title="Loans" padded={false}>
        {!memberId ? (
          <EmptyState
            title="Choose a member"
            description="Loans are shown per member so you can see what is out and when it is due back."
          />
        ) : loans.isLoading ? (
          <LoadingState label="Loading loans…" />
        ) : (loans.data?.data ?? []).length === 0 ? (
          <EmptyState title="Nothing on loan" description="This member has no books out." />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full border-collapse text-sm">
              <thead>
                <tr className="border-b border-border text-left">
                  <Th>Book</Th>
                  <Th hideBelow="md">Member</Th>
                  <Th hideBelow="lg">Due</Th>
                  <Th>Status</Th>
                  <Th align="right">Fine</Th>
                  <Th align="right"> </Th>
                </tr>
              </thead>
              <tbody>
                {(loans.data?.data ?? []).map((loan) => (
                  <tr key={loan.id} className="border-b border-border last:border-0">
                    <td className="px-4 py-2.5">
                      <p className="font-medium text-ink">{loan.bookTitle}</p>
                      <p className="nums text-xs text-ink-subtle">{loan.barcode}</p>
                    </td>
                    <td className="hidden px-4 py-2.5 text-ink-muted md:table-cell">{loan.memberName}</td>
                    <td className="nums hidden px-4 py-2.5 text-ink-subtle lg:table-cell">{date(loan.dueAt)}</td>
                    <td className="px-4 py-2.5">
                      <StatusBadge status={loan.status} />
                      {loan.overdue && (
                        <span className="ml-1.5 text-xs text-danger">{loan.daysOverdue}d late</span>
                      )}
                    </td>
                    <td className="nums px-4 py-2.5 text-right text-ink-muted">{money(loan.fineAmount)}</td>
                    <td className="px-4 py-2.5 text-right">
                      {can('LIBRARY_CIRCULATE') && loan.status === 'ISSUED' && (
                        <span className="flex justify-end gap-1.5">
                          <Button size="sm" variant="ghost" loading={renewLoan.isPending} onClick={() => renewLoan.mutate(loan.id)}>
                            Renew
                          </Button>
                          <Button size="sm" variant="secondary" loading={returnLoan.isPending} onClick={() => returnLoan.mutate({ loanId: loan.id, lost: false })}>
                            Return
                          </Button>
                        </span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {(returnLoan.isError || renewLoan.isError) && (
          <p className="p-4 text-sm text-danger">{describeError(returnLoan.error ?? renewLoan.error)}</p>
        )}
      </Panel>
    </>
  )
}

/* ------------------------------------------------------------- reservations */

function ReservationsTab() {
  const queryClient = useQueryClient()
  const [bookId, setBookId] = useState('')
  const [memberId, setMemberId] = useState('')
  const [searchTerm, setSearchTerm] = useState('')
  const [search, setSearch] = useState('')

  const books = useQuery({
    queryKey: ['library-reserve-books', { search }],
    queryFn: () => api<PageResponse<Book>>('/api/v1/library/books', { query: { page: 0, size: 20, term: search || undefined } }),
  })

  const members = useQuery({
    queryKey: ['library-reserve-members'],
    queryFn: () => api<PageResponse<Member>>('/api/v1/library/members', { query: { page: 0, size: 20 } }),
  })
  const reservations = useQuery({
    queryKey: ['library-reservations', { bookId }],
    queryFn: () =>
      bookId
        ? api<Reservation[]>(`/api/v1/library/reservations/book/${bookId}`)
        : Promise.resolve([] as Reservation[]),
  })

  const reserve = useMutation({
    mutationFn: () => post<Reservation>('/api/v1/library/reservations', { bookId, memberId }),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['library-reservations'] }),
  })

  const cancel = useMutation({
    mutationFn: (id: string) => api<void>(`/api/v1/library/reservations/${id}`, { method: 'DELETE' }),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['library-reservations'] }),
  })

  return (
    <>
      <Panel title="Reserve a title">
        <div className="grid gap-3 sm:grid-cols-3">
          <Field label="Find title">
            <TextInputAdorned
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); setSearch(searchTerm) } }}
              placeholder="Search the catalogue"
              trailing={
                <Button type="button" variant="secondary" size="sm" onClick={() => setSearch(searchTerm)}>
                  Find
                </Button>
              }
            />
          </Field>
          <Field label="Book" required>
            <Select value={bookId} onChange={(e) => setBookId(e.target.value)}>
              <option value="">Choose a title</option>
              {(books.data?.data ?? []).map((book) => (
                <option key={book.id} value={book.id}>{book.title}</option>
              ))}
            </Select>
          </Field>
          <Field label="Member" required>
            <Select value={memberId} onChange={(e) => setMemberId(e.target.value)}>
              <option value="">Choose a member</option>
              {(members.data?.data ?? []).map((member) => (
                <option key={member.id} value={member.id}>{member.name}</option>
              ))}
            </Select>
          </Field>
        </div>
        <div className="mt-3 flex items-center gap-3">
          <Button loading={reserve.isPending} disabled={!bookId || !memberId} onClick={() => reserve.mutate()}>
            Reserve
          </Button>
          {reserve.isError && <span className="text-sm text-danger">{describeError(reserve.error)}</span>}
        </div>
      </Panel>

      <Panel title="Reservations" padded={false}>
        {!bookId ? (
          <EmptyState title="Choose a title" description="Pick a book above to see who is waiting for it." />
        ) : reservations.isLoading ? (
          <LoadingState label="Loading reservations…" />
        ) : (reservations.data ?? []).length === 0 ? (
          <EmptyState title="Nobody waiting" description="This title has no reservations." />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full border-collapse text-sm">
              <thead>
                <tr className="border-b border-border text-left">
                  <Th align="right">#</Th>
                  <Th>Member</Th>
                  <Th hideBelow="md">Reserved</Th>
                  <Th>Status</Th>
                  <Th align="right"> </Th>
                </tr>
              </thead>
              <tbody>
                {(reservations.data ?? []).map((reservation) => (
                  <tr key={reservation.id} className="border-b border-border last:border-0">
                    <td className="nums px-4 py-2.5 text-right text-ink-subtle">{reservation.queuePosition}</td>
                    <td className="px-4 py-2.5 text-ink">{reservation.memberName}</td>
                    <td className="nums hidden px-4 py-2.5 text-ink-subtle md:table-cell">{date(reservation.reservedAt)}</td>
                    <td className="px-4 py-2.5"><StatusBadge status={reservation.status} /></td>
                    <td className="px-4 py-2.5 text-right">
                      <Button size="sm" variant="ghost" loading={cancel.isPending} onClick={() => cancel.mutate(reservation.id)}>
                        Cancel
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {cancel.isError && <p className="p-4 text-sm text-danger">{describeError(cancel.error)}</p>}
      </Panel>
    </>
  )
}

/* -------------------------------------------------------------------- fines */

function FinesTab() {
  const queryClient = useQueryClient()
  const [status, setStatus] = useState('OUTSTANDING')
  const [page, setPage] = useState(0)

  const fines = useQuery({
    queryKey: ['library-fines', { status, page }],
    queryFn: () => api<PageResponse<Fine>>('/api/v1/library/fines', { query: { status, page, size: 20 } }),
  })

  const pay = useMutation({
    mutationFn: (id: string) => post<Fine>(`/api/v1/library/fines/${id}/pay`),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['library-fines'] }),
  })

  return (
    <>
      <Panel
        title="Fines"
        padded={false}
        actions={
          <Select value={status} onChange={(e) => { setStatus(e.target.value); setPage(0) }} aria-label="Fine status">
            {['OUTSTANDING', 'PAID', 'WAIVED'].map((value) => (
              <option key={value} value={value}>{humanise(value)}</option>
            ))}
          </Select>
        }
      >
        {fines.isLoading ? (
          <LoadingState label="Loading fines…" />
        ) : fines.isError ? (
          <ErrorState error={fines.error} onRetry={() => void fines.refetch()} />
        ) : (fines.data?.data ?? []).length === 0 ? (
          <EmptyState title="No fines" description={`Nothing is ${humanise(status).toLowerCase()}.`} />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full border-collapse text-sm">
              <thead>
                <tr className="border-b border-border text-left">
                  <Th>Member</Th>
                  <Th hideBelow="md">Reason</Th>
                  <Th hideBelow="lg">Assessed</Th>
                  <Th align="right">Amount</Th>
                  <Th>Status</Th>
                  <Th align="right"> </Th>
                </tr>
              </thead>
              <tbody>
                {(fines.data?.data ?? []).map((fine) => (
                  <tr key={fine.id} className="border-b border-border last:border-0">
                    <td className="px-4 py-2.5 text-ink">{fine.memberName}</td>
                    <td className="hidden px-4 py-2.5 text-ink-muted md:table-cell">{fine.reason}</td>
                    <td className="nums hidden px-4 py-2.5 text-ink-subtle lg:table-cell">{date(fine.assessedOn)}</td>
                    <td className="nums px-4 py-2.5 text-right text-ink">{money(fine.amount)}</td>
                    <td className="px-4 py-2.5"><StatusBadge status={fine.status} /></td>
                    <td className="px-4 py-2.5 text-right">
                      {fine.status === 'OUTSTANDING' && (
                        <Button size="sm" variant="secondary" loading={pay.isPending} onClick={() => pay.mutate(fine.id)}>
                          Mark paid
                        </Button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {pay.isError && <p className="p-4 text-sm text-danger">{describeError(pay.error)}</p>}
      </Panel>
      {fines.data && <Pager page={fines.data} onChange={setPage} />}
    </>
  )
}

/* ------------------------------------------------------- categories, etc. */

function ReferenceTab() {
  const { can } = useAuth()
  const queryClient = useQueryClient()
  const [values, setValues] = useState<Record<string, string>>({})

  const categories = useQuery({
    queryKey: ['library-categories'],
    queryFn: () => api<Category[]>('/api/v1/library/categories'),
  })
  const publishers = useQuery({
    queryKey: ['library-publishers'],
    queryFn: () => api<{ id: string; name: string }[]>('/api/v1/library/publishers'),
  })
  const authors = useQuery({
    queryKey: ['library-authors'],
    queryFn: () => api<Author[]>('/api/v1/library/authors'),
  })

  const addCategory = useMutation({
    mutationFn: (body: Record<string, unknown>) => post<Category>('/api/v1/library/categories', body),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['library-categories'] }),
  })
  const addPublisher = useMutation({
    mutationFn: (body: Record<string, unknown>) => post<{ id: string }>('/api/v1/library/publishers', body),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['library-publishers'] }),
  })
  const addAuthor = useMutation({
    mutationFn: (body: Record<string, unknown>) => post<Author>('/api/v1/library/authors', body),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['library-authors'] }),
  })

  return (
    <>
      <div className="grid gap-5 lg:grid-cols-3">
        <Panel title="Categories" padded={false}>
          {(categories.data ?? []).length === 0 ? (
            <EmptyState title="No categories" description="Add one to group the catalogue." />
          ) : (
            <ul className="divide-y divide-border">
              {(categories.data ?? []).map((category) => (
                <li key={category.id} className="flex items-center justify-between gap-3 px-4 py-2.5">
                  <span className="text-sm text-ink">{category.name}</span>
                  <span className="nums text-xs text-ink-subtle">{category.code}</span>
                </li>
              ))}
            </ul>
          )}
          {can('LIBRARY_MANAGE') && (
            <form
              onSubmit={(e) => {
                e.preventDefault()
                addCategory.mutate({ code: values.categoryCode, name: values.categoryName, description: values.categoryDescription || undefined })
              }}
              className="grid gap-3 border-t border-border p-4"
            >
              <Field label="Name" required>
                <TextInput value={values.categoryName ?? ''} onChange={(e) => setValues({ ...values, categoryName: e.target.value })} required maxLength={120} />
              </Field>
              <Field label="Code" required>
                <TextInput value={values.categoryCode ?? ''} onChange={(e) => setValues({ ...values, categoryCode: e.target.value })} required maxLength={40} />
              </Field>
              <Button type="submit" loading={addCategory.isPending}>Add category</Button>
              {addCategory.isError && <p className="text-sm text-danger">{describeError(addCategory.error)}</p>}
            </form>
          )}
        </Panel>

        <Panel title="Publishers" padded={false}>
          {(publishers.data ?? []).length === 0 ? (
            <EmptyState title="No publishers" description="Add one when cataloguing." />
          ) : (
            <ul className="divide-y divide-border">
              {(publishers.data ?? []).map((publisher) => (
                <li key={publisher.id} className="px-4 py-2.5 text-sm text-ink">{publisher.name}</li>
              ))}
            </ul>
          )}
          {can('LIBRARY_MANAGE') && (
            <form
              onSubmit={(e) => {
                e.preventDefault()
                addPublisher.mutate({ name: values.publisherName, email: values.publisherEmail || undefined })
              }}
              className="grid gap-3 border-t border-border p-4"
            >
              <Field label="Name" required>
                <TextInput value={values.publisherName ?? ''} onChange={(e) => setValues({ ...values, publisherName: e.target.value })} required maxLength={200} />
              </Field>
              <Field label="Email">
                <TextInput type="email" value={values.publisherEmail ?? ''} onChange={(e) => setValues({ ...values, publisherEmail: e.target.value })} maxLength={180} />
              </Field>
              <Button type="submit" loading={addPublisher.isPending} disabled={!values.publisherName}>Add publisher</Button>
              {addPublisher.isError && <p className="text-sm text-danger">{describeError(addPublisher.error)}</p>}
            </form>
          )}
        </Panel>

        <Panel title="Authors" padded={false}>
          {(authors.data ?? []).length === 0 ? (
            <EmptyState title="No authors" description="Add one when cataloguing." />
          ) : (
            <ul className="divide-y divide-border">
              {(authors.data ?? []).map((author) => (
                <li key={author.id} className="px-4 py-2.5 text-sm text-ink">{author.name}</li>
              ))}
            </ul>
          )}
          {can('LIBRARY_MANAGE') && (
            <form
              onSubmit={(e) => {
                e.preventDefault()
                addAuthor.mutate({ name: values.authorName })
              }}
              className="grid gap-3 border-t border-border p-4"
            >
              <Field label="Name" required>
                <TextInput value={values.authorName ?? ''} onChange={(e) => setValues({ ...values, authorName: e.target.value })} required maxLength={200} />
              </Field>
              <Button type="submit" loading={addAuthor.isPending} disabled={!values.authorName}>Add author</Button>
              {addAuthor.isError && <p className="text-sm text-danger">{describeError(addAuthor.error)}</p>}
            </form>
          )}
        </Panel>
      </div>
    </>
  )
}

/* ------------------------------------------------------------------- pieces */

function Metric({ label, value, tone = 'neutral' }: { label: string; value: string; tone?: 'neutral' | 'danger' | 'warning' }) {
  const colour = { neutral: 'text-ink', danger: 'text-danger', warning: 'text-warning' }[tone]
  return (
    <div className="rounded-card border border-border bg-surface px-5 py-4 shadow-[0_1px_2px_rgba(31,29,25,0.04)]">
      <p className="text-xs tracking-wide text-ink-subtle uppercase">{label}</p>
      <p className={`nums mt-1 text-2xl font-semibold ${colour}`}>{text(value)}</p>
    </div>
  )
}

function Th({ children, align, hideBelow }: { children: React.ReactNode; align?: 'right'; hideBelow?: 'md' | 'lg' }) {
  return (
    <th
      scope="col"
      className={[
        'px-4 py-2.5 text-xs font-semibold tracking-wide text-ink-subtle uppercase',
        align === 'right' ? 'text-right' : '',
        hideBelow === 'md' ? 'hidden md:table-cell' : '',
        hideBelow === 'lg' ? 'hidden lg:table-cell' : '',
      ].join(' ')}
    >
      {children}
    </th>
  )
}
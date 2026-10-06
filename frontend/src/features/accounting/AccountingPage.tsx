import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, post } from '../../lib/api'
import { describeError, useAuth } from '../../auth/AuthProvider'
import {
  Badge,
  Button,
  EmptyState,
  Field,
  Panel,
  QueryBoundary,
  Select,
  StatusBadge,
  TextInput,
} from '../../components/ui'
import { PageHeader } from '../../components/DataTable'
import { date, money } from '../../lib/format'

export interface ChartAccount {
  id: string
  code: string
  name: string
  accountGroup: AccountGroup
  accountType: AccountType
  parentId: string | null
  postable: boolean
  active: boolean
}

export type AccountGroup = 'ASSET' | 'LIABILITY' | 'EQUITY' | 'INCOME' | 'EXPENSE'

export type AccountType =
  | 'CURRENT_ASSET'
  | 'FIXED_ASSET'
  | 'RECEIVABLE'
  | 'CASH'
  | 'BANK'
  | 'CURRENT_LIABILITY'
  | 'PAYABLE'
  | 'CAPITAL'
  | 'REVENUE'
  | 'OPERATING_EXPENSE'
  | 'ADMIN_EXPENSE'
  | 'FINANCIAL_EXPENSE'
  | 'INCOME'
  | 'OTHER'

export interface JournalLine {
  id: string
  accountId: string
  accountCode: string
  accountName: string
  description: string | null
  debit: number
  credit: number
  partyType: string | null
  partyId: string | null
}

export interface JournalEntry {
  id: string
  entryNumber: string
  entryDate: string
  fiscalYearId: string
  accountingPeriodId: string
  sourceType: string
  sourceId: string | null
  description: string | null
  status: string
  totalDebit: number
  totalCredit: number
  balanced: boolean
  reversedById: string | null
  postedAt: string | null
  lines: JournalLine[]
}

export interface FiscalYear {
  id: string
  name: string
  code: string
  startDate: string
  endDate: string
  status: string
}

export interface AccountingPeriod {
  id: string
  fiscalYearId: string
  name: string
  startDate: string
  endDate: string
  status: string
}

export interface BankAccount {
  id: string
  name: string
  bankName: string
  accountNumber: string
  chartAccountId: string | null
  currency: string
  active: boolean
}

export interface Reconciliation {
  id: string
  bankAccountId: string
  statementDate: string
  statementEndingBalance: number
  status: string
  reconciledBy: string | null
  reconciledAt: string | null
}

export interface TrialBalance {
  asOf: string
  totalDebit: number
  totalCredit: number
  balanced: boolean
  rows: Array<{
    accountId: string
    code: string
    name: string
    accountType: AccountType
    debit: number
    credit: number
  }>
}

const ACCOUNT_GROUPS: AccountGroup[] = ['ASSET', 'LIABILITY', 'EQUITY', 'INCOME', 'EXPENSE']

const ACCOUNT_TYPES: AccountType[] = [
  'CURRENT_ASSET',
  'FIXED_ASSET',
  'RECEIVABLE',
  'CASH',
  'BANK',
  'CURRENT_LIABILITY',
  'PAYABLE',
  'CAPITAL',
  'REVENUE',
  'OPERATING_EXPENSE',
  'ADMIN_EXPENSE',
  'FINANCIAL_EXPENSE',
  'INCOME',
  'OTHER',
]

const SOURCE_TYPES = [
  'MANUAL',
  'PAYMENT',
  'REFUND',
  'EXPENSE',
  'INCOME',
  'PAYROLL',
  'OPENING',
] as const

/** A journal entry only posts when its debits and credits agree, which the server enforces. */
function balanced(debits: number[], credits: number[]): boolean {
  const debit = debits.reduce((sum, value) => sum + value, 0)
  const credit = credits.reduce((sum, value) => sum + value, 0)
  return debit > 0 && Math.abs(debit - credit) < 0.005
}

/**
 * Accounting is a ledger rather than a set of independent forms, so the screen is arranged
 * as the order money moves: the chart of accounts, the periods it may be booked into, the
 * journal itself, and the trial balance that proves the two sides agree.
 */
export function AccountingPage() {
  const [tab, setTab] = useState<'journal' | 'chart' | 'periods' | 'trial'>('journal')

  return (
    <div className="mx-auto flex max-w-7xl flex-col gap-5">
      <PageHeader
        title="Accounting"
        description="The chart of accounts, the periods it may be booked into, and the journal behind every payment."
      />

      <div role="tablist" aria-label="Accounting sections" className="flex flex-wrap gap-2">
        {(
          [
            ['journal', 'Journal'],
            ['chart', 'Chart of accounts'],
            ['periods', 'Fiscal years'],
            ['trial', 'Trial balance'],
          ] as const
        ).map(([value, label]) => (
          <Button
            key={value}
            role="tab"
            aria-selected={tab === value}
            variant={tab === value ? 'primary' : 'secondary'}
            size="sm"
            onClick={() => setTab(value)}
          >
            {label}
          </Button>
        ))}
      </div>

      {tab === 'journal' && <JournalTab />}
      {tab === 'chart' && <ChartTab />}
      {tab === 'periods' && <PeriodsTab />}
      {tab === 'trial' && <TrialBalanceTab />}
    </div>
  )
}

function JournalTab() {
  const { can } = useAuth()
  const queryClient = useQueryClient()
  const [from, setFrom] = useState(firstOfMonth())
  const [to, setTo] = useState(today())
  const [selected, setSelected] = useState<JournalEntry | null>(null)
  const [error, setError] = useState<string | null>(null)

  const entries = useQuery({
    queryKey: ['journal-entries', from, to],
    queryFn: () =>
      api<JournalEntry[]>(`/api/v1/accounting/journal-entries?from=${from}&to=${to}`),
  })

  // A period only accepts postings while its fiscal year is open, so offer nothing else.
  const periods = useQuery({
    queryKey: ['postable-accounting-periods'],
    queryFn: async () => {
      const years = await api<FiscalYear[]>('/api/v1/accounting/fiscal-years')
      const openYears = years.filter((year) => year.status === 'OPEN')
      const nested = await Promise.all(
        openYears.map((year) =>
          api<AccountingPeriod[]>(`/api/v1/accounting/fiscal-years/${year.id}/periods`),
        ),
      )
      return nested.flat().filter((period) => period.status === 'OPEN')
    },
  })

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: ['journal-entries'] })
    void queryClient.invalidateQueries({ queryKey: ['trial-balance'] })
  }

  const postEntry = useMutation({
    mutationFn: (id: string) => post<JournalEntry>(`/api/v1/accounting/journal-entries/${id}/post`),
    onSuccess: (entry) => {
      setError(null)
      setSelected(entry)
      invalidate()
    },
    onError: (mutationError) => setError(describeError(mutationError)),
  })

  const reverse = useMutation({
    mutationFn: ({ id, reason }: { id: string; reason: string }) =>
      post<JournalEntry>(
        `/api/v1/accounting/journal-entries/${id}/reverse${reason ? `?reason=${encodeURIComponent(reason)}` : ''}`,
      ),
    onSuccess: () => {
      setError(null)
      invalidate()
    },
    onError: (mutationError) => setError(describeError(mutationError)),
  })

  return (
    <>
      <Panel>
        <div className="grid gap-4 sm:grid-cols-3">
          <Field label="From" htmlFor="journal-from">
            <TextInput id="journal-from" type="date" value={from} onChange={(event) => setFrom(event.target.value)} />
          </Field>
          <Field label="To" htmlFor="journal-to">
            <TextInput id="journal-to" type="date" value={to} onChange={(event) => setTo(event.target.value)} />
          </Field>
        </div>
      </Panel>

      {error && (
        <p role="alert" className="rounded-lg border border-danger/30 bg-danger-soft px-4 py-3 text-sm text-danger">
          {error}
        </p>
      )}

      <Panel title="Journal" description="Every movement, and where it was booked." padded={false}>
        <QueryBoundary
          isLoading={entries.isLoading}
          error={entries.error}
          data={entries.data}
          onRetry={() => void entries.refetch()}
          loadingRows={4}
          empty={
            <EmptyState
              title="No entries in this period"
              description="Choose a wider date range, or record one below."
            />
          }
        >
          {(data) => (
            <div className="overflow-x-auto">
              <table className="w-full border-collapse text-sm">
                <thead>
                  <tr className="border-b border-border text-left">
                    <Th>Entry</Th>
                    <Th>Date</Th>
                    <Th>Description</Th>
                    <Th>Source</Th>
                    <Th align="right">Debit</Th>
                    <Th align="right">Credit</Th>
                    <Th>Status</Th>
                    <Th align="right">Actions</Th>
                  </tr>
                </thead>
                <tbody>
                  {data.map((entry) => (
                    <tr key={entry.id} className="border-b border-border last:border-0">
                      <td className="nums px-4 py-3 whitespace-nowrap font-medium text-ink">
                        {entry.entryNumber}
                      </td>
                      <td className="px-4 py-3 whitespace-nowrap text-ink-muted">{date(entry.entryDate)}</td>
                      <td className="px-4 py-3 text-ink-muted">{entry.description ?? '—'}</td>
                      <td className="px-4 py-3 text-ink-muted">{entry.sourceType}</td>
                      <td className="nums px-4 py-3 text-right">{money(entry.totalDebit)}</td>
                      <td className="nums px-4 py-3 text-right">{money(entry.totalCredit)}</td>
                      <td className="px-4 py-3">
                        <StatusBadge status={entry.status} />
                        {!entry.balanced && (
                          <span className="ml-2 text-xs text-danger">unbalanced</span>
                        )}
                      </td>
                      <td className="px-4 py-3 text-right">
                        <div className="flex justify-end gap-2">
                          <Button
                            size="sm"
                            variant="ghost"
                            onClick={() => setSelected(entry)}
                          >
                            View
                          </Button>
                          {can('ACCOUNTING_POST') && entry.status === 'DRAFT' && entry.balanced && (
                            <Button
                              size="sm"
                              loading={postEntry.isPending}
                              onClick={() => postEntry.mutate(entry.id)}
                            >
                              Post
                            </Button>
                          )}
                          {can('ACCOUNTING_POST') && entry.status === 'POSTED' && (
                            <Button
                              size="sm"
                              variant="ghost"
                              loading={reverse.isPending}
                              onClick={() =>
                                reverse.mutate({ id: entry.id, reason: 'Reversed from the journal' })
                              }
                            >
                              Reverse
                            </Button>
                          )}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </QueryBoundary>
      </Panel>

      {can('ACCOUNTING_MANAGE') && (
        <NewEntry periods={periods.data ?? []} onDone={invalidate} />
      )}

      {selected && <EntryDetail entry={selected} />}
    </>
  )
}

function EntryDetail({ entry }: { entry: JournalEntry }) {
  return (
    <Panel title={`Entry ${entry.entryNumber}`} description={entry.description ?? undefined} padded={false}>
      <div className="grid gap-4 border-b border-border px-4 py-4 sm:grid-cols-4">
        <Stat label="Date" value={date(entry.entryDate)} />
        <Stat label="Source" value={entry.sourceType} />
        <Stat label="Status" value={entry.status} />
        <Stat
          label="Balanced"
          value={entry.balanced ? 'Yes' : 'No'}
        />
      </div>
      <div className="overflow-x-auto">
        <table className="w-full border-collapse text-sm">
          <thead>
            <tr className="border-b border-border text-left">
              <Th>Account</Th>
              <Th>Description</Th>
              <Th align="right">Debit</Th>
              <Th align="right">Credit</Th>
            </tr>
          </thead>
          <tbody>
            {entry.lines.map((line) => (
              <tr key={line.id} className="border-b border-border last:border-0">
                <td className="px-4 py-3">
                  <span className="nums block text-xs text-ink-subtle">{line.accountCode}</span>
                  <span className="text-ink">{line.accountName}</span>
                </td>
                <td className="px-4 py-3 text-ink-muted">{line.description ?? '—'}</td>
                <td className="nums px-4 py-3 text-right">
                  {Number(line.debit) > 0 ? money(line.debit) : '—'}
                </td>
                <td className="nums px-4 py-3 text-right">
                  {Number(line.credit) > 0 ? money(line.credit) : '—'}
                </td>
              </tr>
            ))}
          </tbody>
          <tfoot>
            <tr className="border-t border-border bg-surface-soft/50">
              <td className="px-4 py-3 font-medium" colSpan={2}>
                Totals
              </td>
              <td className="nums px-4 py-3 text-right font-medium">{money(entry.totalDebit)}</td>
              <td className="nums px-4 py-3 text-right font-medium">{money(entry.totalCredit)}</td>
            </tr>
          </tfoot>
        </table>
      </div>
    </Panel>
  )
}

function NewEntry({ periods, onDone }: { periods: AccountingPeriod[]; onDone: () => void }) {
  const [open, setOpen] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [lines, setLines] = useState([
    { accountId: '', description: '', debit: '', credit: '' },
    { accountId: '', description: '', debit: '', credit: '' },
  ])

  const accounts = useQuery({
    queryKey: ['chart-accounts', 'postable'],
    queryFn: () => api<ChartAccount[]>('/api/v1/accounting/accounts'),
  })

  const postable = (accounts.data ?? []).filter((account) => account.postable && account.active)

  const create = useMutation({
    mutationFn: (payload: {
      entryDate: string
      accountingPeriodId: string
      description: string
      sourceType: string
      lines: Array<{
        accountId: string
        description?: string
        debit: number
        credit: number
      }>
    }) => post<JournalEntry>('/api/v1/accounting/journal-entries', payload),
    onSuccess: () => {
      setError(null)
      setOpen(false)
      setLines([
        { accountId: '', description: '', debit: '', credit: '' },
        { accountId: '', description: '', debit: '', credit: '' },
      ])
      onDone()
    },
    onError: (mutationError) => setError(describeError(mutationError)),
  })

  if (!open) {
    return (
      <Button variant="secondary" onClick={() => setOpen(true)} disabled={periods.length === 0}>
        Record a journal entry
      </Button>
    )
  }

  const debits = lines.map((line) => Number(line.debit || 0))
  const credits = lines.map((line) => Number(line.credit || 0))
  const isBalanced = balanced(debits, credits)

  return (
    <Panel
      title="Record a journal entry"
      description="An entry only posts when the debits and credits agree."
    >
      {periods.length === 0 ? (
        <EmptyState
          title="No open period"
          description="Open an accounting period before booking into the ledger."
        />
      ) : (
        <form
          className="flex flex-col gap-4"
          onSubmit={(event) => {
            event.preventDefault()
            const data = new FormData(event.currentTarget)
            const accountingPeriodId = String(data.get('accountingPeriodId') ?? '')
            const entryDate = String(data.get('entryDate') ?? '')
            if (!accountingPeriodId || !entryDate) {
              setError('A period and a date are required.')
              return
            }
            const filled = lines.filter((line) => line.accountId)
            if (filled.length < 2) {
              setError('An entry needs at least two lines.')
              return
            }
            for (const line of filled) {
              const debit = Number(line.debit || 0)
              const credit = Number(line.credit || 0)
              if (debit < 0 || credit < 0 || (debit > 0 && credit > 0)) {
                setError('Each line carries a debit or a credit, not both.')
                return
              }
              if (debit === 0 && credit === 0) {
                setError('Every line needs a debit or a credit amount.')
                return
              }
            }
            if (!isBalanced) {
              setError('Debits and credits must agree before the entry is saved.')
              return
            }
            create.mutate({
              entryDate,
              accountingPeriodId,
              description: String(data.get('description') ?? ''),
              sourceType: String(data.get('sourceType') ?? 'MANUAL'),
              lines: filled.map((line) => ({
                accountId: line.accountId,
                description: line.description || undefined,
                debit: Number(line.debit || 0),
                credit: Number(line.credit || 0),
              })),
            })
          }}
        >
          <div className="grid gap-4 sm:grid-cols-4">
            <Field label="Period" required>
              <Select name="accountingPeriodId" defaultValue="">
                <option value="">Choose a period</option>
                {periods.map((period) => (
                  <option key={period.id} value={period.id}>
                    {period.name} ({date(period.startDate)} → {date(period.endDate)})
                  </option>
                ))}
              </Select>
            </Field>
            <Field label="Date" required>
              <TextInput name="entryDate" type="date" defaultValue={today()} required />
            </Field>
            <Field label="Description">
              <TextInput name="description" placeholder="What this entry records" />
            </Field>
            <Field label="Source" required>
              <Select name="sourceType" defaultValue="MANUAL">
                {SOURCE_TYPES.map((value) => (
                  <option key={value} value={value}>
                    {value.replace('_', ' ')}
                  </option>
                ))}
              </Select>
            </Field>
          </div>

          <div className="overflow-x-auto">
            <table className="w-full border-collapse text-sm">
              <thead>
                <tr className="border-b border-border text-left">
                  <th className="px-3 py-2 text-xs font-semibold tracking-wide text-ink-subtle uppercase">
                    Account
                  </th>
                  <th className="px-3 py-2 text-xs font-semibold tracking-wide text-ink-subtle uppercase">
                    Note
                  </th>
                  <th className="px-3 py-2 text-right text-xs font-semibold tracking-wide text-ink-subtle uppercase">
                    Debit
                  </th>
                  <th className="px-3 py-2 text-right text-xs font-semibold tracking-wide text-ink-subtle uppercase">
                    Credit
                  </th>
                  <th className="px-3 py-2" />
                </tr>
              </thead>
              <tbody>
                {lines.map((line, index) => (
                  <tr key={index}>
                    <td className="px-3 py-2">
                      <Select
                        aria-label={`Account for line ${index + 1}`}
                        value={line.accountId}
                        onChange={(event) =>
                          setLines((current) =>
                            current.map((item, position) =>
                              position === index ? { ...item, accountId: event.target.value } : item,
                            ),
                          )
                        }
                      >
                        <option value="">Choose an account</option>
                        {postable.map((account) => (
                          <option key={account.id} value={account.id}>
                            {account.code} · {account.name}
                          </option>
                        ))}
                      </Select>
                    </td>
                    <td className="px-3 py-2">
                      <TextInput
                        aria-label={`Note for line ${index + 1}`}
                        value={line.description}
                        onChange={(event) =>
                          setLines((current) =>
                            current.map((item, position) =>
                              position === index ? { ...item, description: event.target.value } : item,
                            ),
                          )
                        }
                      />
                    </td>
                    <td className="px-3 py-2">
                      <TextInput
                        aria-label={`Debit for line ${index + 1}`}
                        type="number"
                        step="0.01"
                        min="0"
                        className="text-right"
                        value={line.debit}
                        onChange={(event) =>
                          setLines((current) =>
                            current.map((item, position) =>
                              position === index ? { ...item, debit: event.target.value } : item,
                            ),
                          )
                        }
                      />
                    </td>
                    <td className="px-3 py-2">
                      <TextInput
                        aria-label={`Credit for line ${index + 1}`}
                        type="number"
                        step="0.01"
                        min="0"
                        className="text-right"
                        value={line.credit}
                        onChange={(event) =>
                          setLines((current) =>
                            current.map((item, position) =>
                              position === index ? { ...item, credit: event.target.value } : item,
                            ),
                          )
                        }
                      />
                    </td>
                    <td className="px-3 py-2 text-right">
                      {lines.length > 2 && (
                        <Button
                          size="sm"
                          variant="ghost"
                          onClick={() =>
                            setLines((current) => current.filter((_, position) => position !== index))
                          }
                        >
                          Remove
                        </Button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
              <tfoot>
                <tr className="border-t border-border">
                  <td className="px-3 py-2 text-sm font-medium" colSpan={2}>
                    Totals
                  </td>
                  <td className="nums px-3 py-2 text-right text-sm font-medium">
                    {money(debits.reduce((sum, value) => sum + value, 0))}
                  </td>
                  <td className="nums px-3 py-2 text-right text-sm font-medium">
                    {money(credits.reduce((sum, value) => sum + value, 0))}
                  </td>
                  <td className="px-3 py-2">
                    <Badge tone={isBalanced ? 'success' : 'warning'}>
                      {isBalanced ? 'Balanced' : 'Out by '}
                    </Badge>
                  </td>
                </tr>
              </tfoot>
            </table>
          </div>

          <div className="flex flex-wrap items-center gap-3">
            <Button
              type="button"
              variant="secondary"
              onClick={() =>
                setLines((current) => [
                  ...current,
                  { accountId: '', description: '', debit: '', credit: '' },
                ])
              }
            >
              Add a line
            </Button>
            <Button type="submit" loading={create.isPending} disabled={!isBalanced}>
              Save entry
            </Button>
            <Button type="button" variant="ghost" onClick={() => setOpen(false)}>
              Cancel
            </Button>
            {error && (
              <p role="alert" className="text-sm text-danger">
                {error}
              </p>
            )}
          </div>
        </form>
      )}
    </Panel>
  )
}

function ChartTab() {
  const { can } = useAuth()
  const queryClient = useQueryClient()
  const [error, setError] = useState<string | null>(null)
  const [showForm, setShowForm] = useState(false)

  const accounts = useQuery({
    queryKey: ['chart-accounts'],
    queryFn: () => api<ChartAccount[]>('/api/v1/accounting/accounts'),
  })

  const create = useMutation({
    mutationFn: (values: { code: string; name: string; accountGroup: string; accountType: string }) =>
      post<ChartAccount>('/api/v1/accounting/accounts', {
        code: values.code,
        name: values.name,
        accountGroup: values.accountGroup,
        accountType: values.accountType,
        postable: true,
      }),
    onSuccess: () => {
      setError(null)
      setShowForm(false)
      void queryClient.invalidateQueries({ queryKey: ['chart-accounts'] })
    },
    onError: (mutationError) => setError(describeError(mutationError)),
  })

  return (
    <>
      {error && (
        <p role="alert" className="rounded-lg border border-danger/30 bg-danger-soft px-4 py-3 text-sm text-danger">
          {error}
        </p>
      )}

      <Panel
        title="Chart of accounts"
        description="What money can be booked against. Only postable accounts accept a journal line."
        actions={
          can('ACCOUNTING_MANAGE') ? (
            <Button
              size="sm"
              variant={showForm ? 'ghost' : 'secondary'}
              onClick={() => setShowForm((open) => !open)}
            >
              {showForm ? 'Close' : 'Add account'}
            </Button>
          ) : null
        }
        padded={false}
      >
        {showForm && can('ACCOUNTING_MANAGE') && (
          <form
            className="grid gap-4 border-b border-border px-4 py-4 sm:grid-cols-4"
            onSubmit={(event) => {
              event.preventDefault()
              const data = new FormData(event.currentTarget)
              const code = String(data.get('code') ?? '').trim()
              const name = String(data.get('name') ?? '').trim()
              if (!code || !name) {
                setError('An account needs a code and a name.')
                return
              }
              create.mutate({
                code,
                name,
                accountGroup: String(data.get('accountGroup') ?? 'ASSET'),
                accountType: String(data.get('accountType') ?? 'OTHER'),
              })
            }}
          >
            <Field label="Code" required>
              <TextInput name="code" placeholder="1200" required />
            </Field>
            <Field label="Name" required>
              <TextInput name="name" placeholder="Accounts receivable" required />
            </Field>
            <Field label="Group" required>
              <Select name="accountGroup" defaultValue="ASSET">
                {ACCOUNT_GROUPS.map((value) => (
                  <option key={value} value={value}>
                    {value.replace('_', ' ')}
                  </option>
                ))}
              </Select>
            </Field>
            <Field label="Type" required>
              <Select name="accountType" defaultValue="CURRENT_ASSET">
                {ACCOUNT_TYPES.map((value) => (
                  <option key={value} value={value}>
                    {value.replace('_', ' ')}
                  </option>
                ))}
              </Select>
            </Field>
            <div className="sm:col-span-4">
              <Button type="submit" loading={create.isPending}>
                Add account
              </Button>
            </div>
          </form>
        )}

        <QueryBoundary
          isLoading={accounts.isLoading}
          error={accounts.error}
          data={accounts.data}
          onRetry={() => void accounts.refetch()}
          loadingRows={6}
          empty={
            <EmptyState
              title="No accounts yet"
              description="Add the accounts this institution books against."
            />
          }
        >
          {(data) => (
            <div className="overflow-x-auto">
              <table className="w-full border-collapse text-sm">
                <thead>
                  <tr className="border-b border-border text-left">
                    <Th>Code</Th>
                    <Th>Account</Th>
                    <Th>Group</Th>
                    <Th>Type</Th>
                    <Th>Posting</Th>
                  </tr>
                </thead>
                <tbody>
                  {data.map((account) => (
                    <tr key={account.id} className="border-b border-border last:border-0">
                      <td className="nums px-4 py-3 font-medium text-ink">{account.code}</td>
                      <td className="px-4 py-3 text-ink">{account.name}</td>
                      <td className="px-4 py-3 text-ink-muted">
                        {account.accountGroup.replace('_', ' ')}
                      </td>
                      <td className="px-4 py-3 text-ink-muted">
                        {account.accountType.replace(/_/g, ' ').toLowerCase()}
                      </td>
                      <td className="px-4 py-3">
                        {account.postable ? (
                          <Badge tone="info">Postable</Badge>
                        ) : (
                          <span className="text-ink-subtle">Header</span>
                        )}
                        {!account.active && (
                          <span className="ml-2 text-xs text-ink-subtle">inactive</span>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </QueryBoundary>
      </Panel>
    </>
  )
}

function PeriodsTab() {
  const { can } = useAuth()
  const queryClient = useQueryClient()
  const [error, setError] = useState<string | null>(null)
  const [openYearId, setOpenYearId] = useState('')
  const [periodForm, setPeriodForm] = useState({ name: '', startDate: '', endDate: '' })

  const years = useQuery({
    queryKey: ['fiscal-years'],
    queryFn: () => api<FiscalYear[]>('/api/v1/accounting/fiscal-years'),
  })

  const periods = useQuery({
    queryKey: ['accounting-periods', openYearId],
    queryFn: () => api<AccountingPeriod[]>(`/api/v1/accounting/fiscal-years/${openYearId}/periods`),
    enabled: Boolean(openYearId),
  })

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: ['fiscal-years'] })
    void queryClient.invalidateQueries({ queryKey: ['accounting-periods'] })
  }

  const createYear = useMutation({
    mutationFn: (values: { name: string; code: string; startDate: string; endDate: string }) =>
      post<FiscalYear>('/api/v1/accounting/fiscal-years', values),
    onSuccess: () => {
      setError(null)
      invalidate()
    },
    onError: (mutationError) => setError(describeError(mutationError)),
  })

  const transition = useMutation({
    mutationFn: ({ id, action }: { id: string; action: 'open' | 'close' }) =>
      post<FiscalYear>(`/api/v1/accounting/fiscal-years/${id}/${action}`),
    onSuccess: () => {
      setError(null)
      invalidate()
    },
    onError: (mutationError) => setError(describeError(mutationError)),
  })

  const createPeriod = useMutation({
    mutationFn: () =>
      post<AccountingPeriod>('/api/v1/accounting/periods', {
        fiscalYearId: openYearId,
        name: periodForm.name,
        startDate: periodForm.startDate,
        endDate: periodForm.endDate,
      }),
    onSuccess: () => {
      setError(null)
      setPeriodForm({ name: '', startDate: '', endDate: '' })
      invalidate()
    },
    onError: (mutationError) => setError(describeError(mutationError)),
  })

  const closePeriod = useMutation({
    mutationFn: (id: string) => post<AccountingPeriod>(`/api/v1/accounting/periods/${id}/close`),
    onSuccess: () => {
      setError(null)
      invalidate()
    },
    onError: (mutationError) => setError(describeError(mutationError)),
  })

  return (
    <>
      {error && (
        <p role="alert" className="rounded-lg border border-danger/30 bg-danger-soft px-4 py-3 text-sm text-danger">
          {error}
        </p>
      )}

      <Panel title="Fiscal years" padded={false}>
        {can('ACCOUNTING_MANAGE') && (
          <form
            className="grid gap-4 border-b border-border px-4 py-4 sm:grid-cols-4"
            onSubmit={(event) => {
              event.preventDefault()
              const data = new FormData(event.currentTarget)
              const name = String(data.get('name') ?? '').trim()
              const code = String(data.get('code') ?? '').trim()
              const startDate = String(data.get('startDate') ?? '')
              const endDate = String(data.get('endDate') ?? '')
              if (!name || !code || !startDate || !endDate) {
                setError('A fiscal year needs a name, a code and both dates.')
                return
              }
              if (endDate <= startDate) {
                setError('The end date has to fall after the start date.')
                return
              }
              createYear.mutate({ name, code, startDate, endDate })
            }}
          >
            <Field label="Name" required>
              <TextInput name="name" placeholder="2026" required />
            </Field>
            <Field label="Code" required>
              <TextInput name="code" placeholder="FY26" required />
            </Field>
            <Field label="Starts" required>
              <TextInput name="startDate" type="date" required />
            </Field>
            <Field label="Ends" required>
              <TextInput name="endDate" type="date" required />
            </Field>
            <div className="sm:col-span-4">
              <Button type="submit" loading={createYear.isPending}>
                Add fiscal year
              </Button>
            </div>
          </form>
        )}

        <QueryBoundary
          isLoading={years.isLoading}
          error={years.error}
          data={years.data}
          onRetry={() => void years.refetch()}
          loadingRows={3}
          empty={
            <EmptyState
              title="No fiscal year"
              description="The ledger needs a fiscal year before anything can be booked."
            />
          }
        >
          {(data) => (
            <ul className="divide-y divide-border">
              {data.map((year) => (
                <li key={year.id} className="flex flex-wrap items-center justify-between gap-4 px-4 py-3">
                  <div>
                    <span className="font-medium text-ink">{year.name}</span>
                    <span className="nums ml-2 text-xs text-ink-subtle">{year.code}</span>
                    <span className="ml-3 text-sm text-ink-muted">
                      {date(year.startDate)} → {date(year.endDate)}
                    </span>
                    <span className="ml-3">
                      <StatusBadge status={year.status} />
                    </span>
                  </div>
                  <div className="flex gap-2">
                    <Button
                      size="sm"
                      variant="secondary"
                      onClick={() => setOpenYearId(year.id)}
                    >
                      Periods
                    </Button>
                    {can('ACCOUNTING_MANAGE') && year.status === 'PLANNED' && (
                      <Button
                        size="sm"
                        loading={transition.isPending}
                        onClick={() => transition.mutate({ id: year.id, action: 'open' })}
                      >
                        Open
                      </Button>
                    )}
                    {can('ACCOUNTING_MANAGE') && year.status === 'OPEN' && (
                      <Button
                        size="sm"
                        variant="ghost"
                        loading={transition.isPending}
                        onClick={() => transition.mutate({ id: year.id, action: 'close' })}
                      >
                        Close year
                      </Button>
                    )}
                  </div>
                </li>
              ))}
            </ul>
          )}
        </QueryBoundary>
      </Panel>

      {openYearId && (
        <Panel
          title="Accounting periods"
          description="The ledger may only be booked into a period that is open."
          padded={false}
        >
          <div className="overflow-x-auto">
            <table className="w-full border-collapse text-sm">
              <thead>
                <tr className="border-b border-border text-left">
                  <Th>Period</Th>
                  <Th>From</Th>
                  <Th>To</Th>
                  <Th>Status</Th>
                  <Th align="right">Actions</Th>
                </tr>
              </thead>
              <tbody>
                {(periods.data ?? []).map((period) => (
                  <tr key={period.id} className="border-b border-border last:border-0">
                    <td className="px-4 py-3 font-medium text-ink">{period.name}</td>
                    <td className="px-4 py-3 whitespace-nowrap text-ink-muted">{date(period.startDate)}</td>
                    <td className="px-4 py-3 whitespace-nowrap text-ink-muted">{date(period.endDate)}</td>
                    <td className="px-4 py-3">
                      <StatusBadge status={period.status} />
                    </td>
                    <td className="px-4 py-3 text-right">
                      {can('ACCOUNTING_MANAGE') && period.status === 'OPEN' && (
                        <Button
                          size="sm"
                          variant="ghost"
                          loading={closePeriod.isPending}
                          onClick={() => closePeriod.mutate(period.id)}
                        >
                          Close period
                        </Button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {(periods.data ?? []).length === 0 && (
            <EmptyState title="No periods" description="Add a period to start booking." />
          )}

          {can('ACCOUNTING_MANAGE') && (
            <form
              className="grid gap-4 border-t border-border px-4 py-4 sm:grid-cols-4"
              onSubmit={(event) => {
                event.preventDefault()
                if (!periodForm.name || !periodForm.startDate || !periodForm.endDate) {
                  setError('A period needs a name and both dates.')
                  return
                }
                if (periodForm.endDate <= periodForm.startDate) {
                  setError('The end date has to fall after the start date.')
                  return
                }
                createPeriod.mutate()
              }}
            >
              <Field label="Name" required>
                <TextInput
                  value={periodForm.name}
                  onChange={(event) =>
                    setPeriodForm((current) => ({ ...current, name: event.target.value }))
                  }
                  placeholder="Term one"
                  required
                />
              </Field>
              <Field label="Starts" required>
                <TextInput
                  type="date"
                  value={periodForm.startDate}
                  onChange={(event) =>
                    setPeriodForm((current) => ({ ...current, startDate: event.target.value }))
                  }
                  required
                />
              </Field>
              <Field label="Ends" required>
                <TextInput
                  type="date"
                  value={periodForm.endDate}
                  onChange={(event) =>
                    setPeriodForm((current) => ({ ...current, endDate: event.target.value }))
                  }
                  required
                />
              </Field>
              <div className="flex items-end">
                <Button type="submit" loading={createPeriod.isPending}>
                  Add period
                </Button>
              </div>
            </form>
          )}
        </Panel>
      )}
    </>
  )
}

function TrialBalanceTab() {
  const [asOf, setAsOf] = useState(today())

  const trial = useQuery({
    queryKey: ['trial-balance', asOf],
    queryFn: () => api<TrialBalance>(`/api/v1/accounting/trial-balance?asOf=${asOf}`),
  })

  const rows = trial.data?.rows ?? []

  return (
    <>
      <Panel>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="As at" htmlFor="trial-asof" hint="Posted entries up to this date are included.">
            <TextInput
              id="trial-asof"
              type="date"
              value={asOf}
              onChange={(event) => setAsOf(event.target.value)}
            />
          </Field>
        </div>
      </Panel>

      <Panel title="Trial balance" padded={false}>
        <QueryBoundary
          isLoading={trial.isLoading}
          error={trial.error}
          data={trial.data}
          onRetry={() => void trial.refetch()}
          loadingRows={5}
          empty={
            <EmptyState
              title="Nothing posted yet"
              description="Post a journal entry and it will appear here."
            />
          }
        >
          {(data) => (
            <>
              <div className="flex flex-wrap items-center gap-2 border-b border-border px-4 py-3">
                <Badge>{rows.length} accounts</Badge>
                <Badge tone={data.balanced ? 'success' : 'danger'}>
                  {data.balanced ? 'In balance' : 'Out of balance'}
                </Badge>
                <span className="nums text-sm text-ink-muted">
                  {money(data.totalDebit)} debited · {money(data.totalCredit)} credited
                </span>
              </div>
              <div className="overflow-x-auto">
                <table className="w-full border-collapse text-sm">
                  <thead>
                    <tr className="border-b border-border text-left">
                      <Th>Code</Th>
                      <Th>Account</Th>
                      <Th>Type</Th>
                      <Th align="right">Debit</Th>
                      <Th align="right">Credit</Th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.rows.map((row) => (
                      <tr key={row.accountId} className="border-b border-border last:border-0">
                        <td className="nums px-4 py-3 text-ink-muted">{row.code}</td>
                        <td className="px-4 py-3 text-ink">{row.name}</td>
                        <td className="px-4 py-3 text-ink-muted">
                          {row.accountType.replace(/_/g, ' ').toLowerCase()}
                        </td>
                        <td className="nums px-4 py-3 text-right">
                          {Number(row.debit) > 0 ? money(row.debit) : '—'}
                        </td>
                        <td className="nums px-4 py-3 text-right">
                          {Number(row.credit) > 0 ? money(row.credit) : '—'}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                  <tfoot>
                    <tr className="border-t border-border bg-surface-soft/50">
                      <td className="px-4 py-3 font-medium" colSpan={3}>
                        Totals
                      </td>
                      <td className="nums px-4 py-3 text-right font-medium">{money(data.totalDebit)}</td>
                      <td className="nums px-4 py-3 text-right font-medium">{money(data.totalCredit)}</td>
                    </tr>
                  </tfoot>
                </table>
              </div>
            </>
          )}
        </QueryBoundary>
      </Panel>

      <BankPanel />
    </>
  )
}

function BankPanel() {
  const { can } = useAuth()
  const queryClient = useQueryClient()
  const [selectedBankId, setSelectedBankId] = useState('')
  const [error, setError] = useState<string | null>(null)

  const banks = useQuery({
    queryKey: ['bank-accounts'],
    queryFn: () => api<BankAccount[]>('/api/v1/accounting/bank-accounts'),
  })

  const reconciliations = useQuery({
    queryKey: ['reconciliations', selectedBankId],
    queryFn: () => api<Reconciliation[]>(`/api/v1/accounting/bank-accounts/${selectedBankId}/reconciliations`),
    enabled: Boolean(selectedBankId),
  })

  const createBank = useMutation({
    mutationFn: (values: { name: string; bankName: string; accountNumber: string; currency: string }) =>
      post<BankAccount>('/api/v1/accounting/bank-accounts', values),
    onSuccess: () => {
      setError(null)
      void queryClient.invalidateQueries({ queryKey: ['bank-accounts'] })
    },
    onError: (mutationError) => setError(describeError(mutationError)),
  })

  const startReconciliation = useMutation({
    mutationFn: (values: { statementDate: string; statementEndingBalance: string }) =>
      post<Reconciliation>(`/api/v1/accounting/bank-accounts/${selectedBankId}/reconciliations`, {
        statementDate: values.statementDate,
        statementEndingBalance: Number(values.statementEndingBalance),
      }),
    onSuccess: () => {
      setError(null)
      void queryClient.invalidateQueries({ queryKey: ['reconciliations'] })
    },
    onError: (mutationError) => setError(describeError(mutationError)),
  })

  const complete = useMutation({
    mutationFn: (id: string) => post<Reconciliation>(`/api/v1/accounting/reconciliations/${id}/complete`),
    onSuccess: () => {
      setError(null)
      void queryClient.invalidateQueries({ queryKey: ['reconciliations'] })
    },
    onError: (mutationError) => setError(describeError(mutationError)),
  })

  const bankRows = banks.data ?? []

  return (
    <Panel title="Bank accounts" description="Statements are reconciled against the ledger." padded={false}>
      {error && (
        <p role="alert" className="border-b border-border bg-danger-soft px-4 py-3 text-sm text-danger">
          {error}
        </p>
      )}

      {bankRows.length === 0 ? (
        <EmptyState title="No bank accounts" description="Add one to reconcile statements." />
      ) : (
        <>
          <div className="overflow-x-auto">
            <table className="w-full border-collapse text-sm">
              <thead>
                <tr className="border-b border-border text-left">
                  <Th>Account</Th>
                  <Th>Bank</Th>
                  <Th>Number</Th>
                  <Th>Currency</Th>
                  <Th align="right">Actions</Th>
                </tr>
              </thead>
              <tbody>
                {bankRows.map((bank) => (
                  <tr key={bank.id} className="border-b border-border last:border-0">
                    <td className="px-4 py-3 font-medium text-ink">{bank.name}</td>
                    <td className="px-4 py-3 text-ink-muted">{bank.bankName}</td>
                    <td className="nums px-4 py-3 text-ink-muted">{bank.accountNumber}</td>
                    <td className="px-4 py-3 text-ink-muted">{bank.currency}</td>
                    <td className="px-4 py-3 text-right">
                      <Button size="sm" variant="secondary" onClick={() => setSelectedBankId(bank.id)}>
                        Reconcile
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {selectedBankId && (
            <div className="border-t border-border px-4 py-4">
              <h3 className="mb-3 text-sm font-semibold text-ink">Reconciliations</h3>
              <div className="overflow-x-auto">
                <table className="w-full border-collapse text-sm">
                  <thead>
                    <tr className="border-b border-border text-left">
                      <Th>Statement date</Th>
                      <Th align="right">Statement balance</Th>
                      <Th>Status</Th>
                      <Th align="right">Actions</Th>
                    </tr>
                  </thead>
                  <tbody>
                    {(reconciliations.data ?? []).map((reconciliation) => (
                      <tr key={reconciliation.id} className="border-b border-border last:border-0">
                        <td className="px-4 py-3 whitespace-nowrap text-ink-muted">
                          {date(reconciliation.statementDate)}
                        </td>
                        <td className="nums px-4 py-3 text-right">
                          {money(reconciliation.statementEndingBalance)}
                        </td>
                        <td className="px-4 py-3">
                          <StatusBadge status={reconciliation.status} />
                        </td>
                        <td className="px-4 py-3 text-right">
                          {can('ACCOUNTING_MANAGE') &&
                          reconciliation.status !== 'COMPLETED' &&
                          reconciliation.status !== 'COMPLETE' ? (
                            <Button
                              size="sm"
                              loading={complete.isPending}
                              onClick={() => complete.mutate(reconciliation.id)}
                            >
                              Complete
                            </Button>
                          ) : (
                            <span className="text-xs text-ink-subtle">—</span>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              {(reconciliations.data ?? []).length === 0 && (
                <EmptyState title="No reconciliations" description="Start one against a statement." />
              )}

              {can('ACCOUNTING_MANAGE') && (
                <form
                  className="mt-4 flex flex-wrap items-end gap-3"
                  onSubmit={(event) => {
                    event.preventDefault()
                    const data = new FormData(event.currentTarget)
                    const statementDate = String(data.get('statementDate') ?? '')
                    const statementEndingBalance = String(data.get('statementEndingBalance') ?? '')
                    if (!statementDate || statementEndingBalance === '') {
                      setError('A reconciliation needs a statement date and balance.')
                      return
                    }
                    if (Number(statementEndingBalance) < 0) {
                      setError('A statement balance cannot be negative.')
                      return
                    }
                    startReconciliation.mutate({ statementDate, statementEndingBalance })
                  }}
                >
                  <div className="w-48">
                    <Field label="Statement date" required>
                      <TextInput name="statementDate" type="date" required />
                    </Field>
                  </div>
                  <div className="w-40">
                    <Field label="Ending balance" required>
                      <TextInput name="statementEndingBalance" type="number" step="0.01" min="0" required />
                    </Field>
                  </div>
                  <Button type="submit" loading={startReconciliation.isPending}>
                    Start reconciliation
                  </Button>
                </form>
              )}
            </div>
          )}
        </>
      )}

      {can('ACCOUNTING_MANAGE') && (
        <form
          className="grid gap-4 border-t border-border px-4 py-4 sm:grid-cols-4"
          onSubmit={(event) => {
            event.preventDefault()
            const data = new FormData(event.currentTarget)
            const name = String(data.get('bankNameAccount') ?? '').trim()
            const bankName = String(data.get('bankName') ?? '').trim()
            const accountNumber = String(data.get('accountNumber') ?? '').trim()
            if (!name || !bankName || !accountNumber) {
              setError('A bank account needs a name, a bank and an account number.')
              return
            }
            createBank.mutate({
              name,
              bankName,
              accountNumber,
              currency: String(data.get('currency') ?? 'NPR'),
            })
          }}
        >
          <Field label="Account name" required>
            <TextInput name="bankNameAccount" placeholder="Collection account" required />
          </Field>
          <Field label="Bank" required>
            <TextInput name="bankName" placeholder="Nabil Bank" required />
          </Field>
          <Field label="Account number" required>
            <TextInput name="accountNumber" required />
          </Field>
          <Field label="Currency">
            <TextInput name="currency" defaultValue="NPR" maxLength={3} />
          </Field>
          <div className="sm:col-span-4">
            <Button type="submit" loading={createBank.isPending}>
              Add bank account
            </Button>
          </div>
        </form>
      )}
    </Panel>
  )
}

function Th({ children, align }: { children: React.ReactNode; align?: 'right' }) {
  return (
    <th
      scope="col"
      className={`px-4 py-2.5 text-xs font-semibold tracking-wide text-ink-subtle uppercase ${
        align === 'right' ? 'text-right' : ''
      }`}
    >
      {children}
    </th>
  )
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <p className="text-xs font-semibold tracking-wide text-ink-subtle uppercase">{label}</p>
      <p className="nums mt-1 text-sm font-medium text-ink">{value}</p>
    </div>
  )
}

function today(): string {
  return new Date().toISOString().slice(0, 10)
}

function firstOfMonth(): string {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-01`
}
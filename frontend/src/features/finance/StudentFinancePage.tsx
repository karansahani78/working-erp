import { useState, type ReactNode } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, post } from '../../lib/api'
import { describeError, useAuth } from '../../auth/AuthProvider'
import { Button, EmptyState, Field, Panel, Select, StatusBadge, TextInput } from '../../components/ui'
import { PageHeader } from '../../components/DataTable'
import { date, dateTime, money } from '../../lib/format'
import {
  PAYMENT_METHODS,
  type FeeAssessment,
  type Invoice,
  type Payment,
  type Refund,
} from './types'

/**
 * Money is held against a student, not in a global ledger: the API exposes a student's
 * invoices, payments and refunds. This page is therefore reached from a student, and shows
 * their whole financial position in one place.
 */
export function StudentFinancePage() {
  const { studentNumber, id } = useParams()
  const byNumber = Boolean(studentNumber)
  const { can } = useAuth()
  const queryClient = useQueryClient()
  const [formError, setFormError] = useState<string | null>(null)

  const student = useQuery({
    queryKey: ['student', byNumber ? studentNumber : id],
    queryFn: () =>
      api<{ id: string; studentNumber: string; fullName: string }>(
        byNumber ? `/api/v1/students/number/${studentNumber}` : `/api/v1/students/${id}`,
      ),
    enabled: Boolean(studentNumber || id),
  })

  const studentId = student.data?.id

  const assessments = useQuery({
    queryKey: ['fee-assessments', studentId],
    queryFn: () => api<FeeAssessment[]>(`/api/v1/finance/students/${studentId}/fee-assessments`),
    enabled: Boolean(studentId),
  })
  const invoices = useQuery({
    queryKey: ['invoices', studentId],
    queryFn: () => api<Invoice[]>(`/api/v1/finance/students/${studentId}/invoices`),
    enabled: Boolean(studentId),
  })
  const payments = useQuery({
    queryKey: ['payments', studentId],
    queryFn: () => api<Payment[]>(`/api/v1/finance/students/${studentId}/payments`),
    enabled: Boolean(studentId),
  })
  const refunds = useQuery({
    queryKey: ['refunds', studentId],
    queryFn: () => api<Refund[]>(`/api/v1/finance/students/${studentId}/refunds`),
    enabled: Boolean(studentId),
  })

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: ['invoices', studentId] })
    void queryClient.invalidateQueries({ queryKey: ['payments', studentId] })
    void queryClient.invalidateQueries({ queryKey: ['fee-assessments', studentId] })
  }

  const issueInvoice = useMutation({
    mutationFn: (assessmentId: string) =>
      post<Invoice>(`/api/v1/finance/students/${studentId}/invoices`, {
        assessmentId,
        issueDate: new Date().toISOString().slice(0, 10),
      }),
    onSuccess: () => {
      invalidate()
      setFormError(null)
    },
    onError: (error) => setFormError(describeError(error)),
  })

  const assessmentRows = assessments.data ?? []
  const net = assessmentRows.reduce((sum, a) => sum + Number(a.netAmount ?? 0), 0)
  const paid = (payments.data ?? [])
    .filter((payment) => payment.status === 'CONFIRMED')
    .reduce((sum, payment) => sum + Number(payment.amount ?? 0), 0)
  const outstanding = (invoices.data ?? []).reduce((sum, invoice) => sum + Number(invoice.balanceDue ?? 0), 0)

  if (student.isError) {
    return (
      <div className="mx-auto max-w-3xl">
        <Panel>
          <p className="text-sm text-danger">{describeError(student.error)}</p>
        </Panel>
      </div>
    )
  }

  return (
    <div className="mx-auto flex max-w-6xl flex-col gap-5">
      <nav aria-label="Breadcrumb" className="text-sm text-ink-subtle">
        <Link to="/students" className="text-primary hover:underline">
          Students
        </Link>
        <span className="mx-2">/</span>
        {student.data && (
          <>
            <Link
              to={`/students/number/${student.data.studentNumber}`}
              className="text-primary hover:underline"
            >
              {student.data.fullName}
            </Link>
            <span className="mx-2">/</span>
          </>
        )}
        <span>Fees</span>
      </nav>

      <PageHeader
        title="Fees and payments"
        description={student.data ? `${student.data.fullName} · ${student.data.studentNumber}` : undefined}
      />

      <div className="grid gap-4 sm:grid-cols-3">
        <Panel title="Assessed">
          <p className="nums text-2xl font-semibold text-ink">{money(net)}</p>
        </Panel>
        <Panel title="Paid">
          <p className="nums text-2xl font-semibold text-primary">{money(paid)}</p>
        </Panel>
        <Panel title="Outstanding">
          <p className="nums text-2xl font-semibold text-ink">{money(outstanding)}</p>
        </Panel>
      </div>

      {formError && (
        <div role="alert" className="rounded-lg border border-danger/30 bg-danger-soft px-4 py-3 text-sm text-danger">
          {formError}
        </div>
      )}

      <Panel title="Assessments" description="What this student owes for the year." padded={false}>
        {assessments.isLoading ? (
          <p className="p-5 text-sm text-ink-subtle">Loading…</p>
        ) : assessmentRows.length === 0 ? (
          <EmptyState title="No assessment yet" description="Assess a fee structure against this student to begin." />
        ) : (
          <ul className="divide-y divide-border">
            {assessmentRows.map((assessment) => (
              <li key={assessment.id} className="flex flex-wrap items-center justify-between gap-4 p-4">
                <div>
                  <div className="flex items-center gap-2">
                    <span className="nums font-medium text-ink">{money(assessment.netAmount)}</span>
                    <StatusBadge status={assessment.status} />
                  </div>
                  <p className="nums mt-1 text-xs text-ink-subtle">
                    Gross {money(assessment.grossAmount)}
                    {Number(assessment.discountAmount) > 0 && ` · discount ${money(assessment.discountAmount)}`}
                    {Number(assessment.scholarshipAmount) > 0 && ` · scholarship ${money(assessment.scholarshipAmount)}`}
                    {assessment.dueDate && ` · due ${date(assessment.dueDate)}`}
                  </p>
                </div>
                {can('INVOICE_CREATE') && (
                  <Button
                    variant="secondary"
                    loading={issueInvoice.isPending}
                    onClick={() => issueInvoice.mutate(assessment.id)}
                  >
                    Raise invoice
                  </Button>
                )}
              </li>
            ))}
          </ul>
        )}
      </Panel>

      <Invoices invoices={invoices} onRecord={can('PAYMENT_CREATE')} studentId={studentId} />
      <Payments payments={payments} />
      <Refunds refunds={refunds} />

      {can('PAYMENT_CREATE') && studentId && (
        <RecordPayment studentId={studentId} invoices={invoices.data ?? []} onDone={invalidate} />
      )}
    </div>
  )
}

function Invoices({
  invoices,
  onRecord,
  studentId,
}: {
  invoices: ReturnType<typeof useQuery<Invoice[]>>
  onRecord: boolean
  studentId?: string
}) {
  if (invoices.isLoading) return <Panel title="Invoices"><p className="text-sm text-ink-subtle">Loading…</p></Panel>
  const rows = invoices.data ?? []
  return (
    <Panel title="Invoices" padded={false}>
      {rows.length === 0 ? (
        <EmptyState title="No invoices" description="Raise one from an assessment above." />
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full border-collapse text-sm">
            <thead>
              <tr className="border-b border-border text-left">
                <Th>Number</Th>
                <Th>Issued</Th>
                <Th>Due</Th>
                <Th align="right">Total</Th>
                <Th align="right">Paid</Th>
                <Th align="right">Balance</Th>
                <Th>Status</Th>
              </tr>
            </thead>
            <tbody>
              {rows.map((invoice) => (
                <tr key={invoice.id} className="border-b border-border last:border-0">
                  <td className="nums px-4 py-3 whitespace-nowrap text-ink">{invoice.invoiceNumber}</td>
                  <td className="px-4 py-3 whitespace-nowrap text-ink-muted">{date(invoice.issueDate)}</td>
                  <td className="px-4 py-3 whitespace-nowrap text-ink-muted">{date(invoice.dueDate)}</td>
                  <td className="nums px-4 py-3 text-right">{money(invoice.totalAmount)}</td>
                  <td className="nums px-4 py-3 text-right text-ink-muted">{money(invoice.paidAmount)}</td>
                  <td className="nums px-4 py-3 text-right font-medium">
                    {money(invoice.balanceDue)}
                  </td>
                  <td className="px-4 py-3">
                    <StatusBadge status={invoice.status} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {onRecord && rows.length === 0 && studentId && (
        <p className="border-t border-border px-4 py-3 text-xs text-ink-subtle">
          No invoice can be paid until one is raised.
        </p>
      )}
    </Panel>
  )
}

function Payments({ payments }: { payments: ReturnType<typeof useQuery<Payment[]>> }) {
  if (payments.isLoading) return <Panel title="Payments"><p className="text-sm text-ink-subtle">Loading…</p></Panel>
  const rows = payments.data ?? []
  return (
    <Panel title="Payments" padded={false}>
      {rows.length === 0 ? (
        <EmptyState title="No payments recorded" />
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full border-collapse text-sm">
            <thead>
              <tr className="border-b border-border text-left">
                <Th>Receipt</Th>
                <Th>Amount</Th>
                <Th>Method</Th>
                <Th>Received</Th>
                <Th>Status</Th>
              </tr>
            </thead>
            <tbody>
              {rows.map((payment) => (
                <tr key={payment.id} className="border-b border-border last:border-0">
                  <td className="nums px-4 py-3 whitespace-nowrap text-ink">{payment.receiptNumber ?? '—'}</td>
                  <td className="nums px-4 py-3 font-medium">{money(payment.amount)}</td>
                  <td className="px-4 py-3 text-ink-muted">{payment.method}</td>
                  <td className="px-4 py-3 whitespace-nowrap text-ink-muted">{dateTime(payment.receivedAt)}</td>
                  <td className="px-4 py-3">
                    <StatusBadge status={payment.status} />
                    {payment.failureReason && (
                      <p className="mt-0.5 text-xs text-danger">{payment.failureReason}</p>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Panel>
  )
}

function Refunds({ refunds }: { refunds: ReturnType<typeof useQuery<Refund[]>> }) {
  if (refunds.isLoading) return <Panel title="Refunds"><p className="text-sm text-ink-subtle">Loading…</p></Panel>
  const rows = refunds.data ?? []
  return (
    <Panel title="Refunds" padded={false}>
      {rows.length === 0 ? (
        <EmptyState title="No refunds" />
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full border-collapse text-sm">
            <thead>
              <tr className="border-b border-border text-left">
                <Th align="right">Amount</Th>
                <Th>Reason</Th>
                <Th>Requested</Th>
                <Th>Status</Th>
              </tr>
            </thead>
            <tbody>
              {rows.map((refund) => (
                <tr key={refund.id} className="border-b border-border last:border-0">
                  <td className="nums px-4 py-3 text-right font-medium">{money(refund.amount)}</td>
                  <td className="px-4 py-3 text-ink-muted">{refund.reason}</td>
                  <td className="px-4 py-3 whitespace-nowrap text-ink-muted">{dateTime(refund.createdAt)}</td>
                  <td className="px-4 py-3">
                    <StatusBadge status={refund.status} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Panel>
  )
}

function RecordPayment({
  studentId,
  invoices,
  onDone,
}: {
  studentId: string
  invoices: Invoice[]
  onDone: () => void
}) {
  const [open, setOpen] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const outstanding = invoices.filter((invoice) => Number(invoice.balanceDue) > 0)

  const record = useMutation({
    mutationFn: (payload: { invoiceId?: string; amount: string; method: string; reference?: string }) =>
      post<Payment>('/api/v1/finance/payments', {
        studentId,
        invoiceId: payload.invoiceId || undefined,
        amount: Number(payload.amount),
        method: payload.method,
        reference: payload.reference || undefined,
      }),
    onSuccess: () => {
      setError(null)
      setOpen(false)
      onDone()
    },
    onError: (mutationError) => setError(describeError(mutationError)),
  })

  if (!open) {
    return (
      <Button onClick={() => setOpen(true)} disabled={outstanding.length === 0}>
        Record a payment
      </Button>
    )
  }

  return (
    <Panel title="Record a payment">
      <form
        className="grid gap-4 sm:grid-cols-2"
        onSubmit={(event) => {
          event.preventDefault()
          const data = new FormData(event.currentTarget)
          record.mutate({
            invoiceId: String(data.get('invoiceId') ?? ''),
            amount: String(data.get('amount') ?? ''),
            method: String(data.get('method') ?? 'CASH'),
            reference: String(data.get('reference') ?? ''),
          })
        }}
      >
        <Field label="Against invoice" hint="Leave empty to hold the payment on account.">
          <Select name="invoiceId" defaultValue="">
            <option value="">On account</option>
            {outstanding.map((invoice) => (
              <option key={invoice.id} value={invoice.id}>
                {invoice.invoiceNumber} · {money(invoice.balanceDue)} due
              </option>
            ))}
          </Select>
        </Field>
        <Field label="Amount" required>
          <TextInput name="amount" type="number" step="0.01" min="0.01" required />
        </Field>
        <Field label="Method" required>
          <Select name="method" defaultValue="CASH">
            {PAYMENT_METHODS.map((value) => (
              <option key={value} value={value}>
                {value.replace('_', ' ')}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="Reference" hint="Cheque or transaction number, if any.">
          <TextInput name="reference" />
        </Field>

        {error && <p className="text-sm text-danger sm:col-span-2">{error}</p>}

        <div className="flex gap-2 sm:col-span-2">
          <Button type="submit" loading={record.isPending}>
            Record payment
          </Button>
          <Button type="button" variant="secondary" onClick={() => setOpen(false)}>
            Cancel
          </Button>
        </div>
      </form>
    </Panel>
  )
}

function Th({ children, align }: { children: ReactNode; align?: 'right' }) {
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
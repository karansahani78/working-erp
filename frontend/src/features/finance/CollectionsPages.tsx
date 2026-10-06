import { useState, type ReactNode } from 'react'
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
  Select,
  StatusBadge,
  TextInput,
} from '../../components/ui'
import { PageHeader } from '../../components/DataTable'
import { date, dateTime, money } from '../../lib/format'
import type { Student } from '../students/types'
import {
  PAYMENT_METHODS,
  type Award,
  type Concession,
  type FeeAssessment,
  type Invoice,
  type Payment,
  type Refund,
} from './types'

/**
 * Money is recorded against a student, so each of these modules opens by choosing one and
 * then works on that student's invoices, payments or refunds.
 */
function useStudentPicker() {
  const students = useQuery({
    queryKey: ['students', 'finance'],
    queryFn: () =>
      api<PageResponse<Student>>('/api/v1/students', {
        query: { size: 200, sort: 'studentNumber,asc' },
      }),
  })

  const [studentId, setStudentId] = useState('')

  const picker = (
    <Panel>
      <div className="grid gap-4 sm:grid-cols-[minmax(0,20rem)_1fr] sm:items-end">
        <Field label="Student" htmlFor="finance-student" required>
          <Select
            id="finance-student"
            value={studentId}
            onChange={(event) => setStudentId(event.target.value)}
          >
            <option value="">Choose a student</option>
            {(students.data?.data ?? []).map((student) => (
              <option key={student.id} value={student.id}>
                {student.fullName} ({student.studentNumber})
              </option>
            ))}
          </Select>
        </Field>
        {studentId && (
          <p className="text-sm text-ink-muted">
            {(students.data?.data ?? []).find((student) => student.id === studentId)?.fullName} — every
            figure below belongs to this student.
          </p>
        )}
      </div>
    </Panel>
  )

  return { studentId, setStudentId, picker, students: students.data?.data ?? [] }
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

// ---------------------------------------------------------------------- invoices

/** Assessments become invoices, and a draft invoice can be issued or cancelled. */
export function InvoicesPage() {
  const { studentId, picker } = useStudentPicker()
  const { can } = useAuth()
  const queryClient = useQueryClient()
  const [error, setError] = useState<string | null>(null)

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

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: ['invoices', studentId] })
    void queryClient.invalidateQueries({ queryKey: ['fee-assessments', studentId] })
  }

  const raise = useMutation({
    mutationFn: (assessmentId: string) =>
      post<Invoice>(`/api/v1/finance/students/${studentId}/invoices`, {
        assessmentId,
        issueDate: new Date().toISOString().slice(0, 10),
      }),
    onSuccess: () => {
      setError(null)
      invalidate()
    },
    onError: (mutationError) => setError(describeError(mutationError)),
  })

  const issue = useMutation({
    mutationFn: (invoiceId: string) => post<Invoice>(`/api/v1/finance/invoices/${invoiceId}/issue`),
    onSuccess: () => {
      setError(null)
      invalidate()
    },
    onError: (mutationError) => setError(describeError(mutationError)),
  })

  const cancel = useMutation({
    mutationFn: (invoiceId: string) =>
      del<Invoice>(`/api/v1/finance/invoices/${invoiceId}?reason=Cancelled+from+the+invoice+list`),
    onSuccess: () => {
      setError(null)
      invalidate()
    },
    onError: (mutationError) => setError(describeError(mutationError)),
  })

  const billed = invoices.data ?? []
  const billedAssessmentIds = new Set(billed.map((invoice) => invoice.assessmentId))
  const unbilled = (assessments.data ?? []).filter(
    (assessment) => !billedAssessmentIds.has(assessment.id) && assessment.status !== 'CANCELLED',
  )

  return (
    <div className="mx-auto flex max-w-7xl flex-col gap-5">
      <PageHeader
        title="Invoices"
        description="An invoice is raised from an assessment and then issued to the student."
      />

      {picker}
      {error && (
        <p role="alert" className="rounded-lg border border-danger/30 bg-danger-soft px-4 py-3 text-sm text-danger">
          {error}
        </p>
      )}

      {!studentId ? (
        <Panel>
          <EmptyState
            title="Choose a student"
            description="Invoices belong to a student, so start by choosing who this is for."
          />
        </Panel>
      ) : (
        <>
          <Panel
            title="Assessments ready to invoice"
            description="An assessment with no invoice against it yet."
            padded={false}
          >
            <QueryBoundary
              isLoading={assessments.isLoading}
              error={assessments.error}
              data={assessments.data}
              onRetry={() => void assessments.refetch()}
              loadingRows={3}
              empty={<EmptyState title="Nothing to invoice" description="Every assessment has an invoice." />}
            >
              {(data) => (
                <ul className="divide-y divide-border">
                  {data
                    .filter(
                      (assessment) =>
                        !billedAssessmentIds.has(assessment.id) && assessment.status !== 'CANCELLED',
                    )
                    .map((assessment) => (
                      <li key={assessment.id} className="flex flex-wrap items-center justify-between gap-4 px-4 py-3">
                        <div>
                          <span className="nums font-medium text-ink">{money(assessment.netAmount)}</span>
                          <span className="ml-2">
                            <StatusBadge status={assessment.status} />
                          </span>
                          <p className="mt-0.5 text-xs text-ink-subtle">
                            {assessment.dueDate ? `Due ${date(assessment.dueDate)}` : 'No due date set'}
                          </p>
                        </div>
                        {can('INVOICE_CREATE') && (
                          <Button
                            size="sm"
                            variant="secondary"
                            loading={raise.isPending}
                            onClick={() => raise.mutate(assessment.id)}
                          >
                            Raise invoice
                          </Button>
                        )}
                      </li>
                    ))}
                </ul>
              )}
            </QueryBoundary>
            {unbilled.length === 0 && !assessments.isLoading && (
              <p className="border-t border-border px-4 py-3 text-sm text-ink-subtle">
                Nothing is waiting to be invoiced.
              </p>
            )}
          </Panel>

          <Panel title="Invoices" padded={false}>
            <QueryBoundary
              isLoading={invoices.isLoading}
              error={invoices.error}
              data={invoices.data}
              onRetry={() => void invoices.refetch()}
              loadingRows={4}
              empty={
                <EmptyState
                  title="No invoices"
                  description="Raise one from an assessment above."
                />
              }
            >
              {(data) => (
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
                        {can('INVOICE_CREATE') && <Th align="right">Actions</Th>}
                      </tr>
                    </thead>
                    <tbody>
                      {data.map((invoice) => (
                        <tr key={invoice.id} className="border-b border-border last:border-0">
                          <td className="nums px-4 py-3 whitespace-nowrap font-medium text-ink">
                            {invoice.invoiceNumber}
                          </td>
                          <td className="px-4 py-3 whitespace-nowrap text-ink-muted">
                            {date(invoice.issueDate)}
                          </td>
                          <td className="px-4 py-3 whitespace-nowrap text-ink-muted">
                            {date(invoice.dueDate)}
                          </td>
                          <td className="nums px-4 py-3 text-right">{money(invoice.totalAmount)}</td>
                          <td className="nums px-4 py-3 text-right text-ink-muted">
                            {money(invoice.paidAmount)}
                          </td>
                          <td className="nums px-4 py-3 text-right font-medium">
                            {money(invoice.balanceDue)}
                          </td>
                          <td className="px-4 py-3">
                            <StatusBadge status={invoice.status} />
                          </td>
                          {can('INVOICE_CREATE') && (
                            <td className="px-4 py-3 text-right">
                              <div className="flex justify-end gap-2">
                                {invoice.status === 'DRAFT' && (
                                  <Button
                                    size="sm"
                                    variant="secondary"
                                    loading={issue.isPending}
                                    onClick={() => issue.mutate(invoice.id)}
                                  >
                                    Issue
                                  </Button>
                                )}
                                {invoice.status !== 'CANCELLED' && Number(invoice.paidAmount) === 0 && (
                                  <Button
                                    size="sm"
                                    variant="ghost"
                                    loading={cancel.isPending}
                                    onClick={() => cancel.mutate(invoice.id)}
                                  >
                                    Cancel
                                  </Button>
                                )}
                              </div>
                            </td>
                          )}
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </QueryBoundary>
          </Panel>
        </>
      )}
    </div>
  )
}

// ---------------------------------------------------------------------- payments

/** Payments are recorded against an invoice, and gateway payments settle separately. */
export function PaymentsPage() {
  const { studentId, picker, students } = useStudentPicker()
  const { can } = useAuth()
  const queryClient = useQueryClient()
  const [error, setError] = useState<string | null>(null)

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

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: ['payments', studentId] })
    void queryClient.invalidateQueries({ queryKey: ['invoices', studentId] })
  }

  const record = useMutation({
    mutationFn: (values: { invoiceId: string; amount: string; method: string; reference: string }) =>
      post<Payment>('/api/v1/finance/payments', {
        studentId,
        invoiceId: values.invoiceId || undefined,
        amount: Number(values.amount),
        method: values.method,
        reference: values.reference || undefined,
      }),
    onSuccess: () => {
      setError(null)
      invalidate()
    },
    onError: (mutationError) => setError(describeError(mutationError)),
  })

  const settle = useMutation({
    mutationFn: ({ id, action }: { id: string; action: 'confirm' | 'fail' }) =>
      post<Payment>(`/api/v1/finance/payments/${id}/${action}`),
    onSuccess: () => {
      setError(null)
      invalidate()
    },
    onError: (mutationError) => setError(describeError(mutationError)),
  })

  const outstanding = (invoices.data ?? []).filter((invoice) => Number(invoice.balanceDue) > 0)
  const rows = payments.data ?? []
  // Money that actually reached the school. A pending or failed receipt never counts,
  // and a receipt that was later refunded still left the account in.
  const SETTLED = ['CONFIRMED', 'PARTIALLY_REFUNDED', 'REFUNDED']
  const collected = rows
    .filter((payment) => SETTLED.includes(payment.status))
    .reduce((sum, payment) => sum + Number(payment.amount ?? 0), 0)

  return (
    <div className="mx-auto flex max-w-7xl flex-col gap-5">
      <PageHeader title="Payments" description="Every receipt raised against a student's account." />

      {picker}
      {error && (
        <p role="alert" className="rounded-lg border border-danger/30 bg-danger-soft px-4 py-3 text-sm text-danger">
          {error}
        </p>
      )}

      {!studentId ? (
        <Panel>
          <EmptyState
            title="Choose a student"
            description="Payments are recorded against a student, so begin with the account holder."
          />
        </Panel>
      ) : (
        <>
          <div className="grid gap-4 sm:grid-cols-3">
            <Panel title="Collected" description="Confirmed receipts held for this student.">
              <p className="nums text-2xl font-semibold text-primary">{money(collected)}</p>
            </Panel>
            <Panel title="Payments">
              <p className="nums text-2xl font-semibold text-ink">{rows.length}</p>
            </Panel>
            <Panel title="Still outstanding">
              <p className="nums text-2xl font-semibold text-ink">
                {money(outstanding.reduce((sum, invoice) => sum + Number(invoice.balanceDue), 0))}
              </p>
            </Panel>
          </div>

          {can('PAYMENT_CREATE') && (
            <Panel title="Record a payment">
              <form
                className="grid gap-4 sm:grid-cols-2"
                onSubmit={(event) => {
                  event.preventDefault()
                  const data = new FormData(event.currentTarget)
                  const amount = String(data.get('amount') ?? '')
                  const invoiceId = String(data.get('invoiceId') ?? '')
                  if (!amount || Number(amount) <= 0) {
                    setError('Enter an amount greater than zero.')
                    return
                  }
                  if (!invoiceId && outstanding.length === 0) {
                    setError('This student has no invoice left to pay.')
                    return
                  }
                  record.mutate({
                    invoiceId,
                    amount,
                    method: String(data.get('method') ?? 'CASH'),
                    reference: String(data.get('reference') ?? ''),
                  })
                }}
              >
                <Field label="Against invoice" hint="Leave on account only when nothing is outstanding.">
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
                <div className="sm:col-span-2">
                  <Button type="submit" loading={record.isPending}>
                    Record payment
                  </Button>
                </div>
              </form>
            </Panel>
          )}

          <Panel title="Payments" padded={false}>
            <QueryBoundary
              isLoading={payments.isLoading}
              error={payments.error}
              data={payments.data}
              onRetry={() => void payments.refetch()}
              loadingRows={4}
              empty={<EmptyState title="No payments yet" description="Record one above." />}
            >
              {(data) => (
                <div className="overflow-x-auto">
                  <table className="w-full border-collapse text-sm">
                    <thead>
                      <tr className="border-b border-border text-left">
                        <Th>Receipt</Th>
                        <Th align="right">Amount</Th>
                        <Th>Method</Th>
                        <Th>Invoice</Th>
                        <Th>Received</Th>
                        <Th>Status</Th>
                        {can('PAYMENT_CREATE') && <Th align="right">Actions</Th>}
                      </tr>
                    </thead>
                    <tbody>
                      {data.map((payment) => {
                        const invoice = (invoices.data ?? []).find(
                          (item) => item.id === payment.invoiceId,
                        )
                        return (
                          <tr key={payment.id} className="border-b border-border last:border-0">
                            <td className="nums px-4 py-3 whitespace-nowrap font-medium text-ink">
                              {payment.receiptNumber ?? '—'}
                            </td>
                            <td className="nums px-4 py-3 text-right">{money(payment.amount)}</td>
                            <td className="px-4 py-3 text-ink-muted">
                              {payment.method.replace('_', ' ')}
                            </td>
                            <td className="nums px-4 py-3 whitespace-nowrap text-ink-muted">
                              {invoice?.invoiceNumber ?? 'On account'}
                            </td>
                            <td className="px-4 py-3 whitespace-nowrap text-ink-muted">
                              {dateTime(payment.receivedAt)}
                            </td>
                            <td className="px-4 py-3">
                              <StatusBadge status={payment.status} />
                              {payment.failureReason && (
                                <p className="mt-0.5 text-xs text-danger">{payment.failureReason}</p>
                              )}
                            </td>
                            {can('PAYMENT_CREATE') && (
                              <td className="px-4 py-3 text-right">
                                {payment.status === 'PENDING' ? (
                                  <div className="flex justify-end gap-2">
                                    <Button
                                      size="sm"
                                      variant="ghost"
                                      loading={settle.isPending}
                                      onClick={() => settle.mutate({ id: payment.id, action: 'fail' })}
                                    >
                                      Fail
                                    </Button>
                                    <Button
                                      size="sm"
                                      loading={settle.isPending}
                                      onClick={() => settle.mutate({ id: payment.id, action: 'confirm' })}
                                    >
                                      Confirm
                                    </Button>
                                  </div>
                                ) : (
                                  <span className="text-xs text-ink-subtle">—</span>
                                )}
                              </td>
                            )}
                          </tr>
                        )
                      })}
                    </tbody>
                  </table>
                </div>
              )}
            </QueryBoundary>
          </Panel>
        </>
      )}

      <p className="text-xs text-ink-subtle">
        {students.length} student{students.length === 1 ? '' : 's'} available.
      </p>
    </div>
  )
}

// ----------------------------------------------------------------------- refunds

/** A refund is requested against a settled payment and then approved or rejected. */
export function RefundsPage() {
  const { studentId, picker } = useStudentPicker()
  const { can } = useAuth()
  const queryClient = useQueryClient()
  const [error, setError] = useState<string | null>(null)

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
    void queryClient.invalidateQueries({ queryKey: ['refunds', studentId] })
    void queryClient.invalidateQueries({ queryKey: ['payments', studentId] })
  }

  const request = useMutation({
    mutationFn: (values: { paymentId: string; amount: string; reason: string; method: string }) =>
      post<Refund>(`/api/v1/finance/students/${studentId}/refunds`, {
        paymentId: values.paymentId,
        amount: Number(values.amount),
        reason: values.reason,
        method: values.method,
      }),
    onSuccess: () => {
      setError(null)
      invalidate()
    },
    onError: (mutationError) => setError(describeError(mutationError)),
  })

  const decide = useMutation({
    mutationFn: ({ id, status }: { id: string; status: 'APPROVED' | 'REJECTED' }) =>
      put<Refund>(`/api/v1/finance/refunds/${id}/decision`, {
        status,
        reason: status === 'APPROVED' ? 'Approved from the refunds list' : 'Rejected from the refunds list',
      }),
    onSuccess: () => {
      setError(null)
      invalidate()
    },
    onError: (mutationError) => setError(describeError(mutationError)),
  })

  // Only a confirmed payment can carry a refund, and never more than it settled.
  const settled = (payments.data ?? []).filter((payment) => payment.status === 'CONFIRMED')
  const alreadyRefunded = (refunds.data ?? [])
    .filter((refund) => refund.status === 'APPROVED' || refund.status === 'PROCESSED')
    .reduce((sum, refund) => sum + Number(refund.amount ?? 0), 0)
  const refundable = settled.reduce((sum, payment) => sum + Number(payment.amount), 0) - alreadyRefunded

  const rows = refunds.data ?? []
  const pending = rows.filter((refund) => refund.status === 'PENDING')

  return (
    <div className="mx-auto flex max-w-7xl flex-col gap-5">
      <PageHeader
        title="Refunds"
        description="A refund is requested against a settled payment and needs a decision before it is paid."
      />

      {picker}
      {error && (
        <p role="alert" className="rounded-lg border border-danger/30 bg-danger-soft px-4 py-3 text-sm text-danger">
          {error}
        </p>
      )}

      {!studentId ? (
        <Panel>
          <EmptyState
            title="Choose a student"
            description="Refunds follow the payment they came from, so start with the student."
          />
        </Panel>
      ) : (
        <>
          {pending.length > 0 && (
            <div className="flex items-center gap-2">
              <Badge tone="warning">
                {pending.length} refund{pending.length === 1 ? '' : 's'} awaiting a decision
              </Badge>
            </div>
          )}

          {can('PAYMENT_REFUND') && (
            <Panel
              title="Request a refund"
              description={
                refundable > 0
                  ? `${money(refundable)} of settled payments can still be refunded.`
                  : 'Nothing is available to refund for this student.'
              }
            >
              {refundable > 0 && settled.length > 0 ? (
                <form
                  className="grid gap-4 sm:grid-cols-2"
                  onSubmit={(event) => {
                    event.preventDefault()
                    const data = new FormData(event.currentTarget)
                    const paymentId = String(data.get('paymentId') ?? '')
                    const amount = String(data.get('amount') ?? '')
                    const reason = String(data.get('reason') ?? '').trim()
                    if (!paymentId || !amount || !reason) {
                      setError('Choose a payment, give an amount and explain why.')
                      return
                    }
                    if (Number(amount) > refundable) {
                      setError(`The most that can be refunded is ${money(refundable)}.`)
                      return
                    }
                    request.mutate({
                      paymentId,
                      amount,
                      reason,
                      method: String(data.get('method') ?? 'CASH'),
                    })
                  }}
                >
                  <Field label="Against payment" required>
                    <Select name="paymentId" defaultValue="">
                      <option value="">Choose a receipt</option>
                      {settled.map((payment) => (
                        <option key={payment.id} value={payment.id}>
                          {payment.receiptNumber ?? payment.id.slice(0, 8)} · {money(payment.amount)}
                        </option>
                      ))}
                    </Select>
                  </Field>
                  <Field label="Amount" required>
                    <TextInput name="amount" type="number" step="0.01" min="0.01" max={refundable} required />
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
                  <Field label="Reason" required>
                    <TextInput name="reason" placeholder="Fee withdrawn, course dropped" required />
                  </Field>
                  <div className="sm:col-span-2">
                    <Button type="submit" loading={request.isPending}>
                      Request refund
                    </Button>
                  </div>
                </form>
              ) : (
                <EmptyState
                  title="No settled payment to refund"
                  description="A refund needs a confirmed payment behind it."
                />
              )}
            </Panel>
          )}

          <Panel title="Refund requests" padded={false}>
            <QueryBoundary
              isLoading={refunds.isLoading}
              error={refunds.error}
              data={refunds.data}
              onRetry={() => void refunds.refetch()}
              loadingRows={3}
              empty={<EmptyState title="No refunds" description="Nothing has been refunded." />}
            >
              {(data) => (
                <div className="overflow-x-auto">
                  <table className="w-full border-collapse text-sm">
                    <thead>
                      <tr className="border-b border-border text-left">
                        <Th align="right">Amount</Th>
                        <Th>Reason</Th>
                        <Th>Method</Th>
                        <Th>Requested</Th>
                        <Th>Status</Th>
                        {can('PAYMENT_REFUND') && <Th align="right">Decision</Th>}
                      </tr>
                    </thead>
                    <tbody>
                      {data.map((refund) => (
                        <tr key={refund.id} className="border-b border-border last:border-0">
                          <td className="nums px-4 py-3 text-right font-medium">{money(refund.amount)}</td>
                          <td className="px-4 py-3 text-ink-muted">{refund.reason}</td>
                          <td className="px-4 py-3 text-ink-muted">
                            {refund.method.replace('_', ' ')}
                          </td>
                          <td className="px-4 py-3 whitespace-nowrap text-ink-muted">
                            {dateTime(refund.createdAt)}
                          </td>
                          <td className="px-4 py-3">
                            <StatusBadge status={refund.status} />
                            {refund.processedAt && (
                              <p className="mt-0.5 text-xs text-ink-subtle">
                                {dateTime(refund.processedAt)}
                              </p>
                            )}
                          </td>
                          {can('PAYMENT_REFUND') && (
                            <td className="px-4 py-3 text-right">
                              {refund.status === 'PENDING' ? (
                                <div className="flex justify-end gap-2">
                                  <Button
                                    size="sm"
                                    variant="ghost"
                                    loading={decide.isPending}
                                    onClick={() => decide.mutate({ id: refund.id, status: 'REJECTED' })}
                                  >
                                    Reject
                                  </Button>
                                  <Button
                                    size="sm"
                                    loading={decide.isPending}
                                    onClick={() => decide.mutate({ id: refund.id, status: 'APPROVED' })}
                                  >
                                    Approve
                                  </Button>
                                </div>
                              ) : (
                                <span className="text-xs text-ink-subtle">—</span>
                              )}
                            </td>
                          )}
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </QueryBoundary>
          </Panel>
        </>
      )}
    </div>
  )
}

// ------------------------------------------------------------------ scholarships

/**
 * Scholarships are defined once and then granted to a student as a concession, which
 * reduces what that student is assessed rather than being paid out as cash.
 */
export function ScholarshipsPage() {
  const { can } = useAuth()
  const queryClient = useQueryClient()
  const [error, setError] = useState<string | null>(null)
  const [showForm, setShowForm] = useState(false)
  const [grantFor, setGrantFor] = useState('')
  const [academicYearId, setAcademicYearId] = useState('')

  const scholarships = useQuery({
    queryKey: ['scholarships'],
    queryFn: () => api<Award[]>('/api/v1/finance/scholarships'),
  })

  const students = useQuery({
    queryKey: ['students', 'finance'],
    queryFn: () =>
      api<PageResponse<Student>>('/api/v1/students', {
        query: { size: 200, sort: 'studentNumber,asc' },
      }),
  })

  const years = useQuery({
    queryKey: ['academic-years', 'scholarships'],
    queryFn: () =>
      api<PageResponse<{ id: string; name: string }>>('/api/v1/academic/academic-years', {
        query: { size: 100 },
      }),
  })

  const concessions = useQuery({
    queryKey: ['concessions', grantFor, academicYearId],
    queryFn: () =>
      api<Concession[]>(`/api/v1/finance/students/${grantFor}/concessions?academicYearId=${academicYearId}`),
    enabled: Boolean(grantFor && academicYearId),
  })

  const create = useMutation({
    mutationFn: (values: { code: string; name: string; valueType: string; value: string; description: string }) =>
      post<Award>('/api/v1/finance/scholarships', {
        code: values.code,
        name: values.name,
        valueType: values.valueType,
        value: Number(values.value),
        description: values.description || undefined,
      }),
    onSuccess: () => {
      setError(null)
      setShowForm(false)
      void queryClient.invalidateQueries({ queryKey: ['scholarships'] })
    },
    onError: (mutationError) => setError(describeError(mutationError)),
  })

  const grant = useMutation({
    mutationFn: (values: { scholarshipId: string; amount: string; reason: string }) =>
      post<Concession>(`/api/v1/finance/students/${grantFor}/concessions?academicYearId=${academicYearId}`, {
        scholarshipId: values.scholarshipId,
        amount: Number(values.amount),
        reason: values.reason || undefined,
      }),
    onSuccess: () => {
      setError(null)
      void queryClient.invalidateQueries({ queryKey: ['concessions'] })
    },
    onError: (mutationError) => setError(describeError(mutationError)),
  })

  const revoke = useMutation({
    mutationFn: (id: string) => del<Concession>(`/api/v1/finance/concessions/${id}`),
    onSuccess: () => {
      setError(null)
      void queryClient.invalidateQueries({ queryKey: ['concessions'] })
    },
    onError: (mutationError) => setError(describeError(mutationError)),
  })

  const rows = scholarships.data ?? []

  return (
    <div className="mx-auto flex max-w-7xl flex-col gap-5">
      <PageHeader
        title="Scholarships"
        description="Defined once, then granted to a student as a concession against their fees."
      />

      {error && (
        <p role="alert" className="rounded-lg border border-danger/30 bg-danger-soft px-4 py-3 text-sm text-danger">
          {error}
        </p>
      )}

      <Panel
        title="Scholarships"
        actions={
          can('FEE_CREATE') ? (
            <Button size="sm" variant={showForm ? 'ghost' : 'secondary'} onClick={() => setShowForm((open) => !open)}>
              {showForm ? 'Close' : 'Add scholarship'}
            </Button>
          ) : null
        }
        padded={false}
      >
        {showForm && can('FEE_CREATE') && (
          <form
            className="grid gap-4 border-b border-border px-4 py-4 sm:grid-cols-2"
            onSubmit={(event) => {
              event.preventDefault()
              const data = new FormData(event.currentTarget)
              const code = String(data.get('code') ?? '').trim()
              const name = String(data.get('name') ?? '').trim()
              const value = String(data.get('value') ?? '')
              if (!code || !name || !value || Number(value) <= 0) {
                setError('A scholarship needs a code, a name and a value above zero.')
                return
              }
              create.mutate({
                code,
                name,
                valueType: String(data.get('valueType') ?? 'FIXED'),
                value,
                description: String(data.get('description') ?? ''),
              })
            }}
          >
            <Field label="Code" required>
              <TextInput name="code" placeholder="MERIT-25" required />
            </Field>
            <Field label="Name" required>
              <TextInput name="name" placeholder="Merit scholarship" required />
            </Field>
            <Field label="Value type" required>
              <Select name="valueType" defaultValue="FIXED">
                <option value="FIXED">Fixed amount</option>
                <option value="PERCENTAGE">Percentage of fees</option>
              </Select>
            </Field>
            <Field label="Value" required>
              <TextInput name="value" type="number" step="0.01" min="0.01" required />
            </Field>
            <Field label="Description">
              <TextInput name="description" />
            </Field>
            <div className="sm:col-span-2">
              <Button type="submit" loading={create.isPending}>
                Save scholarship
              </Button>
            </div>
          </form>
        )}

        <QueryBoundary
          isLoading={scholarships.isLoading}
          error={scholarships.error}
          data={scholarships.data}
          onRetry={() => void scholarships.refetch()}
          loadingRows={3}
          empty={
            <EmptyState
              title="No scholarships defined"
              description="Define one so it can be granted against a student's fees."
            />
          }
        >
          {(data) => (
            <div className="overflow-x-auto">
              <table className="w-full border-collapse text-sm">
                <thead>
                  <tr className="border-b border-border text-left">
                    <Th>Code</Th>
                    <Th>Name</Th>
                    <Th>Value type</Th>
                    <Th align="right">Value</Th>
                    <Th>Status</Th>
                  </tr>
                </thead>
                <tbody>
                  {data.map((award) => (
                    <tr key={award.id} className="border-b border-border last:border-0">
                      <td className="nums px-4 py-3 font-medium text-ink">{award.code}</td>
                      <td className="px-4 py-3 text-ink">{award.name}</td>
                      <td className="px-4 py-3 text-ink-muted">{award.valueType}</td>
                      <td className="nums px-4 py-3 text-right">
                        {award.valueType === 'PERCENTAGE' ? `${award.value}%` : money(award.value)}
                      </td>
                      <td className="px-4 py-3">
                        <StatusBadge status={award.status} />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </QueryBoundary>
      </Panel>

      <Panel title="Grant a scholarship" description="A concession reduces what the student is assessed.">
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Student" htmlFor="grant-student" required>
            <Select
              id="grant-student"
              value={grantFor}
              onChange={(event) => setGrantFor(event.target.value)}
            >
              <option value="">Choose a student</option>
              {(students.data?.data ?? []).map((student) => (
                <option key={student.id} value={student.id}>
                  {student.fullName} ({student.studentNumber})
                </option>
              ))}
            </Select>
          </Field>
          <Field label="Academic year" htmlFor="grant-year" required>
            <Select
              id="grant-year"
              value={academicYearId}
              onChange={(event) => setAcademicYearId(event.target.value)}
            >
              <option value="">Choose a year</option>
              {(years.data?.data ?? []).map((year) => (
                <option key={year.id} value={year.id}>
                  {year.name}
                </option>
              ))}
            </Select>
          </Field>
        </div>

        {can('FEE_APPROVE') && grantFor && academicYearId && rows.length > 0 && (
          <form
            className="mt-4 grid gap-4 border-t border-border pt-4 sm:grid-cols-2"
            onSubmit={(event) => {
              event.preventDefault()
              const data = new FormData(event.currentTarget)
              const scholarshipId = String(data.get('scholarshipId') ?? '')
              const amount = String(data.get('amount') ?? '')
              if (!scholarshipId || !amount || Number(amount) <= 0) {
                setError('Choose a scholarship and an amount above zero.')
                return
              }
              grant.mutate({
                scholarshipId,
                amount,
                reason: String(data.get('reason') ?? ''),
              })
            }}
          >
            <Field label="Scholarship" required>
              <Select name="scholarshipId" defaultValue="">
                <option value="">Choose a scholarship</option>
                {rows
                  .filter((award) => award.status === 'ACTIVE')
                  .map((award) => (
                    <option key={award.id} value={award.id}>
                      {award.name} ({award.code})
                    </option>
                  ))}
              </Select>
            </Field>
            <Field label="Amount" hint="What this grant is worth against the fees." required>
              <TextInput name="amount" type="number" step="0.01" min="0.01" required />
            </Field>
            <Field label="Reason">
              <TextInput name="reason" placeholder="Top of the year" />
            </Field>
            <div className="flex items-end">
              <Button type="submit" loading={grant.isPending}>
                Grant
              </Button>
            </div>
          </form>
        )}

        {grantFor && academicYearId && (
          <div className="mt-5">
            <p className="mb-2 text-xs font-semibold tracking-wide text-ink-subtle uppercase">
              Concessions granted
            </p>
            <QueryBoundary
              isLoading={concessions.isLoading}
              error={concessions.error}
              data={concessions.data}
              onRetry={() => void concessions.refetch()}
              loadingRows={2}
              empty={<EmptyState title="No concessions" description="Nothing has been granted to this student." />}
            >
              {(data) => (
                <ul className="divide-y divide-border">
                  {data.map((concession) => {
                    const award = rows.find((item) => item.id === concession.scholarshipId)
                    return (
                      <li key={concession.id} className="flex flex-wrap items-center justify-between gap-3 py-3">
                        <div>
                          <span className="nums font-medium text-ink">{money(concession.amount)}</span>
                          <span className="ml-2 text-sm text-ink-muted">
                            {award?.name ?? 'Concession'}
                          </span>
                          {concession.reason && (
                            <p className="mt-0.5 text-xs text-ink-subtle">{concession.reason}</p>
                          )}
                        </div>
                        <div className="flex items-center gap-2">
                          <StatusBadge status={concession.status} />
                          {can('FEE_APPROVE') && (
                            <Button
                              size="sm"
                              variant="ghost"
                              loading={revoke.isPending}
                              onClick={() => revoke.mutate(concession.id)}
                            >
                              Revoke
                            </Button>
                          )}
                        </div>
                      </li>
                    )
                  })}
                </ul>
              )}
            </QueryBoundary>
          </div>
        )}
      </Panel>
    </div>
  )
}
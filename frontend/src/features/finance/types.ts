export interface FeeComponent {
  id: string
  componentType: string
  name: string
  amount: number
  mandatory: boolean
  description: string | null
}

export interface FeeStructure {
  id: string
  name: string
  code: string
  programId: string | null
  schoolClassId: string | null
  academicYearId: string
  totalAmount: number
  componentTotal: number
  componentsMatchTotal: boolean
  currency: string
  status: 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'
  description: string | null
  components: FeeComponent[]
  createdAt: string
}

export interface InvoiceItem {
  id: string
  description: string
  componentType: string
  amount: number
  concession: boolean
}

export interface Invoice {
  id: string
  studentId: string
  assessmentId: string
  invoiceNumber: string
  issueDate: string
  dueDate: string | null
  totalAmount: number
  paidAmount: number
  balanceDue: number
  status: string
  notes: string | null
  items: InvoiceItem[]
  createdAt: string
}

export interface Payment {
  id: string
  studentId: string
  invoiceId: string | null
  receiptNumber: string | null
  amount: number
  currency: string
  method: string
  provider: string | null
  reference: string | null
  status: string
  failureReason: string | null
  receivedAt: string
  confirmedAt: string | null
}

export interface Refund {
  id: string
  paymentId: string
  studentId: string
  amount: number
  reason: string
  method: string
  status: string
  approvedAt: string | null
  processedAt: string | null
  createdAt: string
}

export interface FeeAssessment {
  id: string
  studentId: string
  feeStructureId: string
  academicYearId: string
  grossAmount: number
  discountAmount: number
  scholarshipAmount: number
  netAmount: number
  paidAmount: number
  refundedAmount: number
  outstandingAmount: number
  currency: string
  dueDate: string | null
  status: string
  notes: string | null
}

export interface ReceivablesRow {
  assessmentId: string
  studentId: string
  feeStructureId: string
  netAmount: number
  paidAmount: number
  outstandingAmount: number
  dueDate: string | null
  daysOverdue: number
  status: string
}

export interface Award {
  id: string
  code: string
  name: string
  valueType: 'PERCENTAGE' | 'FIXED'
  value: number
  description: string | null
  status: 'ACTIVE' | 'INACTIVE' | 'EXPIRED'
}

export interface Concession {
  id: string
  studentId: string
  discountId: string | null
  scholarshipId: string | null
  academicYearId: string
  amount: number
  reason: string | null
  status: string
  createdAt: string
}

export interface ReceivablesReport {
  totalOutstanding: number
  totalOverdue: number
  assessmentCount: number
  rows: ReceivablesRow[]
}

export const PAYMENT_METHODS = ['CASH', 'BANK_TRANSFER', 'CHEQUE', 'ESEWA', 'KHALTI', 'FONEPAY'] as const

export const REFUND_STATUSES = ['PENDING', 'APPROVED', 'REJECTED', 'PROCESSED'] as const

export const FEE_STRUCTURE_STATUSES = ['DRAFT', 'PUBLISHED', 'ARCHIVED'] as const

export const SCHOLARSHIP_VALUE_TYPES = ['PERCENTAGE', 'FIXED'] as const
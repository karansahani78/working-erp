export interface Category {
  id: string
  code: string
  name: string
  description: string | null
}

export interface Publisher {
  id: string
  name: string
  address: string | null
  email: string | null
  phone: string | null
}

export interface Author {
  id: string
  name: string
  biography: string | null
}

export interface Book {
  id: string
  isbn: string | null
  title: string
  edition: string | null
  publicationYear: number | null
  language: string | null
  publisher: Publisher | null
  category: Category | null
  authors: string[] | null
  callNumber: string | null
  shelfLocation: string | null
  coverUrl: string | null
  description: string | null
  reference: boolean
  totalCopies: number
  availableCopies: number
}

export interface Copy {
  id: string
  bookId: string
  bookTitle: string
  barcode: string
  acquisitionType: string | null
  acquiredOn: string | null
  price: string | null
  conditionStatus: string | null
  status: string
  notes: string | null
}

export interface Loan {
  id: string
  copyId: string
  barcode: string
  bookId: string
  bookTitle: string
  author: string | null
  memberCode: string | null
  memberName: string
  issuedAt: string
  dueAt: string
  returnedAt: string | null
  renewalCount: number
  status: string
  overdue: boolean
  daysOverdue: number
  daysLoaned: number
  fineAmount: string
}

export interface Member {
  id: string
  memberCode: string
  name: string
  userId: string | null
  studentId: string | null
  employeeId: string | null
  externalName: string | null
  externalPhone: string | null
  externalEmail: string | null
  maxBooks: number
  booksOut: number
  membershipStart: string | null
  membershipEnd: string | null
  status: string
  currentLoans: Loan[] | null
  outstandingFines: string
}

export interface Reservation {
  id: string
  bookId: string
  bookTitle: string
  memberId: string
  memberName: string
  reservedAt: string
  expiresAt: string | null
  queuePosition: number
  status: string
}

export interface Fine {
  id: string
  memberId: string
  memberName: string
  issueId: string | null
  reason: string
  amount: string
  currency: string
  assessedOn: string
  paidAt: string | null
  waivedAt: string | null
  waiverReason: string | null
  status: string
}

export interface LibraryOverview {
  titles: number
  copies: number
  copiesAvailable: number
  members: number
  loansOut: number
  overdue: number
  waitingReservations: number
  finesOutstanding: string
}
export const SOURCES = [
  'STUDENT',
  'EMPLOYEE',
  'APPLICANT',
  'COURSE',
  'INVOICE',
  'PAYMENT',
  'BOOK',
  'ASSET',
] as const

export type Source = (typeof SOURCES)[number]
import type { Source } from './sources'

export interface SearchHit {
  source: Source
  sourceLabel: string
  id: string
  heading: string
  detail: string
  reference: string | null
}

export interface SearchResponse {
  term: string
  count: number
  results: SearchHit[]
}

/**
 * The API deliberately returns identifiers, not links: a URL is a presentation decision and
 * belongs to the client. This maps a hit onto the screen that shows it, preferring the human
 * reference over the opaque id because that is what a person recognises.
 */
const ROUTES: Record<Source, (hit: SearchHit) => string> = {
  STUDENT: (hit) => (hit.reference ? `/students/number/${hit.reference}` : `/students/${hit.id}`),
  EMPLOYEE: (hit) => (hit.reference ? `/employees/${hit.reference}` : '/employees'),
  APPLICANT: (hit) => `/admissions/applications/${hit.id}`,
  COURSE: (hit) => (hit.reference ? `/courses/${hit.reference}` : '/courses'),
  INVOICE: (hit) => (hit.reference ? `/invoices/${hit.reference}` : '/invoices'),
  PAYMENT: (hit) => (hit.reference ? `/payments/${hit.reference}` : '/payments'),
  BOOK: (hit) => (hit.reference ? `/library/books/${hit.reference}` : `/library/books/${hit.id}`),
  ASSET: (hit) => (hit.reference ? `/assets/${hit.reference}` : `/assets/${hit.id}`),
}

export function routeFor(hit: SearchHit): string {
  return ROUTES[hit.source](hit)
}

export type { Source }
export { SOURCES } from './sources'
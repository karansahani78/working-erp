/** Formatting helpers, so money and dates look the same on every screen. */

const MONEY = new Intl.NumberFormat(undefined, {
  style: 'currency',
  currency: 'USD',
  maximumFractionDigits: 2,
})

const NUMBER = new Intl.NumberFormat(undefined, { maximumFractionDigits: 2 })

export function money(value: unknown): string {
  const amount = toNumber(value)
  if (amount === null) return '—'
  return MONEY.format(amount)
}

export function num(value: unknown): string {
  const amount = toNumber(value)
  if (amount === null) return '—'
  return NUMBER.format(amount)
}

export function toNumber(value: unknown): number | null {
  if (typeof value === 'number') return Number.isFinite(value) ? value : null
  if (typeof value === 'string' && value.trim() !== '') {
    const parsed = Number(value)
    return Number.isFinite(parsed) ? parsed : null
  }
  return null
}

export function date(value: string | null | undefined): string {
  if (!value) return '—'
  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) return value
  return parsed.toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' })
}

export function dateTime(value: string | null | undefined): string {
  if (!value) return '—'
  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) return value
  return parsed.toLocaleString(undefined, {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

/** A label for a stored SCREAMING_SNAKE_CASE value. */
export function humanise(value: string | null | undefined): string {
  if (!value) return '—'
  return value
    .toLowerCase()
    .split('_')
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(' ')
}

export function text(value: unknown): string {
  if (value === null || value === undefined || value === '') return '—'
  return String(value)
}

/** A common toolbar: a search box that only fires when the form is submitted. */
export function initials(name: string): string {
  return (
    name
      .trim()
      .split(/\s+/)
      .slice(0, 2)
      .map((part) => part.charAt(0).toUpperCase())
      .join('') || '?'
  )
}
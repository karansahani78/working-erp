/**
 * The shared vocabulary of the interface: buttons, fields, tables, panels and the three
 * states every data view needs. Screens compose these rather than restyling markup, so the
 * whole application stays visually consistent.
 */
import {
  cloneElement,
  forwardRef,
  isValidElement,
  useId,
  type ButtonHTMLAttributes,
  type InputHTMLAttributes,
  type ReactElement,
  type ReactNode,
  type SelectHTMLAttributes,
} from 'react'
import { describeError } from '../auth/AuthProvider'

const cx = (...parts: Array<string | false | null | undefined>) => parts.filter(Boolean).join(' ')

/* ------------------------------------------------------------------ buttons */

type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger'

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant
  loading?: boolean
  size?: 'sm' | 'md'
}

const BUTTON_STYLES: Record<ButtonVariant, string> = {
  primary: 'bg-primary text-white hover:bg-primary-hover disabled:bg-border-strong',
  secondary: 'bg-surface text-ink border border-border hover:bg-surface-soft',
  ghost: 'bg-transparent text-ink-muted hover:bg-surface-soft hover:text-ink',
  danger: 'bg-danger text-white hover:brightness-110',
}

export function Button({ variant = 'primary', loading, size = 'md', className, children, ...rest }: ButtonProps) {
  return (
    <button
      {...rest}
      disabled={rest.disabled || loading}
      className={cx(
        'inline-flex items-center justify-center gap-2 rounded-lg text-sm font-medium',
        size === 'sm' ? 'px-2.5 py-1.5 text-xs' : 'px-4 py-2',
        'transition-colors disabled:cursor-not-allowed disabled:opacity-60',
        BUTTON_STYLES[variant],
        className,
      )}
    >
      {loading && <Spinner className="h-4 w-4" />}
      {children}
    </button>
  )
}

export function Spinner({ className }: { className?: string }) {
  return (
    <svg className={cx('animate-spin', className ?? 'h-5 w-5')} viewBox="0 0 24 24" aria-hidden="true">
      <circle cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="3" fill="none" opacity="0.25" />
      <path d="M12 2a10 10 0 0 1 10 10" stroke="currentColor" strokeWidth="3" fill="none" strokeLinecap="round" />
    </svg>
  )
}

/* ------------------------------------------------------------------- fields */

interface FieldProps {
  label: string
  hint?: string
  error?: string
  required?: boolean
  children: ReactNode
  htmlFor?: string
}

export function Field({ label, hint, error, required, children, htmlFor }: FieldProps) {
  // A label that points at nothing is worse than no label: a screen reader announces the
  // text but focus lands nowhere. When the caller does not name the control, one is
  // generated here and passed down to the input.
  const generated = useId()
  const id = htmlFor ?? generated

  const control = isValidElement(children)
    ? cloneElement(children as ReactElement<Record<string, unknown>>, {
        id,
        'aria-invalid': (children.props as Record<string, unknown>)['aria-invalid'] ?? (error ? true : undefined),
        'aria-describedby': (children.props as Record<string, unknown>)['aria-describedby'] ?? (hint || error ? `${id}-help` : undefined),
      })
    : children

  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="text-sm font-medium text-ink">
        {label}
        {required && <span className="ml-0.5 text-danger">*</span>}
      </label>
      {control}
      {error ? (
        <p id={`${id}-help`} className="text-xs text-danger">{error}</p>
      ) : hint ? (
        <p id={`${id}-help`} className="text-xs text-ink-subtle">{hint}</p>
      ) : null}
    </div>
  )
}

const CONTROL =
  'w-full rounded-lg border bg-surface px-3 py-2 text-sm text-ink placeholder:text-ink-subtle ' +
  'transition-colors focus:border-primary disabled:bg-surface-soft disabled:text-ink-subtle'

export const TextInput = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement>>(
  function TextInput({ className, ...rest }, ref) {
    return <input ref={ref} {...rest} className={cx(CONTROL, 'border-border', className)} />
  },
)

export const Select = forwardRef<HTMLSelectElement, SelectHTMLAttributes<HTMLSelectElement>>(
  function Select({ className, children, ...rest }, ref) {
    return (
      <select ref={ref} {...rest} className={cx(CONTROL, 'border-border', className)}>
        {children}
      </select>
    )
  },
)

export const TextArea = forwardRef<
  HTMLTextAreaElement,
  InputHTMLAttributes<HTMLTextAreaElement> & { rows?: number }
>(function TextArea({ className, rows = 3, ...rest }, ref) {
  return <textarea ref={ref} rows={rows} {...rest} className={cx(CONTROL, 'border-border', className)} />
})

/**
 * A text input with a control pinned inside its right edge, such as a show/hide toggle for a
 * password.
 *
 * Callers should use this rather than wrapping a `TextInput` in their own positioned
 * `<div>`: `Field` puts its generated id on whatever element it is given, so a plain wrapper
 * would take the id, leaving the label pointing at a `div` and colliding with the input's
 * own id. Here the id and ARIA attributes reach the real input.
 */
export const TextInputAdorned = forwardRef<
  HTMLInputElement,
  Omit<InputHTMLAttributes<HTMLInputElement>, 'children'> & {
    /** Rendered at the right edge and given the full height of the field as a target. */
    trailing: ReactNode
  }
>(function TextInputAdorned({ trailing, className, ...rest }, ref) {
  return (
    <div className="relative">
      <TextInput ref={ref} {...rest} className={cx('pr-16', className)} />
      <div className="absolute inset-y-0 right-0 flex items-center pr-1.5">{trailing}</div>
    </div>
  )
})

/* ------------------------------------------------------------------- panels */

export function Panel({
  title,
  description,
  actions,
  children,
  className,
  padded = true,
}: {
  title?: ReactNode
  description?: string
  actions?: ReactNode
  children: ReactNode
  className?: string
  padded?: boolean
}) {
  return (
    <section
      className={cx(
        'rounded-card border border-border bg-surface',
        'shadow-[0_1px_2px_rgba(31,29,25,0.04)]',
        className,
      )}
    >
      {title && (
        <header className="flex flex-wrap items-start justify-between gap-3 border-b border-border px-5 py-4">
          <div>
            <h2 className="text-sm font-semibold tracking-wide text-ink uppercase">{title}</h2>
            {description && <p className="mt-1 text-sm text-ink-subtle">{description}</p>}
          </div>
          {actions && <div className="flex items-center gap-2">{actions}</div>}
        </header>
      )}
      <div className={padded ? 'p-5' : ''}>{children}</div>
    </section>
  )
}

/* ------------------------------------------------------------------- badges */

const BADGE_TONES = {
  neutral: 'bg-surface-soft text-ink-muted',
  success: 'bg-primary-soft text-primary',
  warning: 'bg-warning-soft text-warning',
  danger: 'bg-danger-soft text-danger',
  info: 'bg-info-soft text-info',
} as const

export type BadgeTone = keyof typeof BADGE_TONES

export function Badge({ tone = 'neutral', children }: { tone?: BadgeTone; children: ReactNode }) {
  return (
    <span
      className={cx(
        'inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium whitespace-nowrap',
        BADGE_TONES[tone],
      )}
    >
      {children}
    </span>
  )
}

/**
 * Statuses are stored as SCREAMING_SNAKE_CASE. This turns one into a sentence so no screen
 * has to hand-map the same twenty values.
 */
const STATUS_TONES: Record<string, BadgeTone> = {
  ACTIVE: 'success',
  ENROLLED: 'success',
  APPROVED: 'success',
  COMPLETED: 'success',
  PAID: 'success',
  POSTED: 'success',
  ISSUED: 'info',
  PENDING: 'warning',
  IN_PROGRESS: 'warning',
  SUBMITTED: 'warning',
  DRAFT: 'neutral',
  APPLICANT: 'info',
  INACTIVE: 'neutral',
  GRADUATED: 'info',
  WITHDRAWN: 'neutral',
  SUSPENDED: 'danger',
  REJECTED: 'danger',
  CANCELLED: 'danger',
  OVERDUE: 'danger',
  FAILED: 'danger',
  PARTIALLY_PAID: 'warning',
  AVAILABLE: 'success',
  ASSIGNED: 'info',
  IN_MAINTENANCE: 'warning',
  LOST: 'danger',
  DISPOSED: 'neutral',
  SCHEDULED: 'info',
  EXPIRED: 'danger',
}

export function StatusBadge({ status }: { status?: string | null }) {
  if (!status) return <Badge>Unknown</Badge>
  const tone = STATUS_TONES[status] ?? 'neutral'
  return <Badge tone={tone}>{humanise(status)}</Badge>
}

export function humanise(value: string): string {
  return value
    .toLowerCase()
    .split('_')
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(' ')
}

/* -------------------------------------------------------------------- states */

/** A skeleton that matches the shape of the content it replaces, to avoid a layout jump. */
export function Skeleton({ className }: { className?: string }) {
  return <div className={cx('animate-pulse rounded bg-surface-soft', className ?? 'h-4 w-full')} />
}

export function LoadingState({ label = 'Loading…', rows = 3 }: { label?: string; rows?: number }) {
  return (
    <div role="status" aria-live="polite" className="flex flex-col gap-3 p-5">
      <span className="sr-only">{label}</span>
      {Array.from({ length: rows }).map((_, index) => (
        <Skeleton key={index} className={cx('h-4', index === rows - 1 ? 'w-2/3' : 'w-full')} />
      ))}
    </div>
  )
}

export function EmptyState({
  title,
  description,
  action,
  icon = '—',
}: {
  title: string
  description?: string
  action?: ReactNode
  icon?: ReactNode
}) {
  return (
    <div className="flex flex-col items-center gap-3 px-6 py-14 text-center">
      <div className="flex h-12 w-12 items-center justify-center rounded-full bg-surface-soft text-lg text-ink-subtle">
        {icon}
      </div>
      <div>
        <p className="font-medium text-ink">{title}</p>
        {description && <p className="mx-auto mt-1 max-w-sm text-sm text-ink-subtle">{description}</p>}
      </div>
      {action}
    </div>
  )
}

export function ErrorState({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const forbidden = (error as { status?: number })?.status === 403
  return (
    <div
      role="alert"
      className="flex flex-col items-center gap-3 rounded-card border border-danger/30 bg-danger-soft px-6 py-10 text-center"
    >
      <p className="font-medium text-danger">
        {forbidden ? 'You do not have access to this' : 'This could not be loaded'}
      </p>
      <p className="max-w-md text-sm text-ink-muted">{describeError(error)}</p>
      {onRetry && (
        <Button variant="secondary" onClick={onRetry}>
          Try again
        </Button>
      )}
    </div>
  )
}

/** One component decides what a query shows, so no screen forgets a state. */
export function QueryBoundary<T>({
  isLoading,
  error,
  data,
  isEmpty,
  onRetry,
  loadingRows,
  empty,
  children,
}: {
  isLoading: boolean
  error: unknown
  data: T | undefined
  isEmpty?: (data: T) => boolean
  onRetry?: () => void
  loadingRows?: number
  empty?: ReactNode
  children: (data: T) => ReactNode
}) {
  if (isLoading) return <LoadingState rows={loadingRows} />
  if (error) return <ErrorState error={error} onRetry={onRetry} />
  if (data === undefined || data === null) return <LoadingState rows={loadingRows} />
  // An empty list is empty even when no predicate was supplied, so list screens
  // show their empty state rather than a bare table header.
  const nothing = isEmpty ? isEmpty(data) : Array.isArray(data) && data.length === 0
  if (nothing) {
    return <>{empty ?? <EmptyState title="Nothing to show yet" />}</>
  }
  return <>{children(data)}</>
}
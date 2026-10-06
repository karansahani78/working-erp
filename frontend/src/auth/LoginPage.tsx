import { useEffect, useRef, useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { Navigate } from 'react-router-dom'
import { describeError, useAuth } from './AuthProvider'
import { ForgotPasswordDialog } from './ForgotPasswordDialog'
import { Button, Field, TextInput, TextInputAdorned } from '../components/ui'
import {
  brandingStyle,
  initialsFor,
  onBrandColor,
  postalAddress,
  supportContact,
  type InstitutionBranding,
} from '../features/branding/types'
import { useInstitutionBranding } from '../features/branding/useInstitutionBranding'

const schema = z.object({
  loginId: z.string().min(1, 'Enter your username or email'),
  password: z.string().min(1, 'Enter your password'),
})

type Form = z.infer<typeof schema>

/**
 * Who is signing in. The server resolves the account from the login id either way, so this
 * only sets expectations and the wording of the field; it is deliberately not a second
 * authentication route.
 */
type Audience = 'staff' | 'learner'

const AUDIENCES: {
  id: Audience
  label: string
  fieldLabel: string
  hint: string
  placeholder: string
}[] = [
  {
    id: 'staff',
    label: 'Admin / Staff',
    fieldLabel: 'Username or email',
    hint: 'Use the staff username or email issued to you.',
    placeholder: 'admin',
  },
  {
    id: 'learner',
    label: 'Student / Parent',
    fieldLabel: 'Student number or email',
    hint: 'Use the student number or registered email address.',
    placeholder: 'SUNDAR-2026-00001',
  },
]

export function LoginPage() {
  const { signIn, user, initialising, passwordChanged } = useAuth()
  // Set by the change-password screen, which cannot show its own confirmation because
  // signing out is part of finishing the job.
  const justChangedPassword = passwordChanged
  const { branding, isLoading: brandingLoading } = useInstitutionBranding()
  const [audience, setAudience] = useState<Audience>('staff')
  const [formError, setFormError] = useState<string | null>(null)
  const [showPassword, setShowPassword] = useState(false)
  const [askingForHelp, setAskingForHelp] = useState(false)
  const audienceRef = useRef<HTMLDivElement>(null)

  const context = AUDIENCES.find((item) => item.id === audience) ?? AUDIENCES[0]

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<Form>({ resolver: zodResolver(schema) })

  // The reset dialog is a modal; Escape closes it and Tab stays inside it while it is open.
  useEffect(() => {
    if (!askingForHelp) return
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setAskingForHelp(false)
        return
      }
      if (event.key !== 'Tab') return
      const focusable = audienceRef.current?.querySelectorAll<HTMLElement>(
        'button:not([disabled]), input:not([disabled]), a[href]',
      )
      if (!focusable || focusable.length === 0) return
      const first = focusable[0]
      const last = focusable[focusable.length - 1]
      if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault()
        first.focus()
      } else if (event.shiftKey && document.activeElement === first) {
        event.preventDefault()
        last.focus()
      }
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [askingForHelp])

  // An account told to set its own password goes there first; everywhere else would be a
  // session the person has not chosen yet, and the flag was previously carried and ignored.
  if (!initialising && user) {
    return <Navigate to={user.mustChangePassword ? '/change-password' : '/'} replace />
  }

  const submit = handleSubmit(async (values) => {
    setFormError(null)
    try {
      await signIn(values.loginId.trim(), values.password)
    } catch (error) {
      setFormError(describeError(error))
    }
  })

  const institutionName = branding?.name?.trim() || 'Education ERP'
  const portalTitle = branding?.portalTitle?.trim()
  const contact = supportContact(branding)
  const address = postalAddress(branding)

  return (
    <div
      className="flex min-h-screen flex-col items-center justify-center bg-canvas px-4 py-8 sm:py-12"
      style={brandingStyle(branding)}
    >
      <main className="w-full max-w-[27rem]">
        <div
          className={[
            'rounded-2xl border border-border bg-surface',
            'shadow-[0_1px_2px_rgba(31,29,25,0.04),0_16px_40px_rgba(31,29,25,0.06)]',
          ].join(' ')}
        >
          {/* Brand: logo, institution and portal name, all read from configuration. */}
          <header className="px-6 pt-8 pb-7 text-center sm:px-9">
            <InstitutionMark branding={branding} />
            <h1 className="mt-5 text-xl leading-tight font-semibold tracking-tight text-primary">
              {institutionName}
            </h1>
            {portalTitle && <p className="mt-1.5 text-sm text-ink-subtle">{portalTitle}</p>}
            <div className="mx-auto mt-5 h-px w-10 bg-primary/25" aria-hidden="true" />
          </header>

          <div className="px-6 pb-7 sm:px-9">
            <div
              ref={audienceRef}
              role="radiogroup"
              aria-label="Sign in as"
              className="grid grid-cols-2 gap-1 rounded-lg bg-surface-soft p-1"
            >
              {AUDIENCES.map((item) => (
                <button
                  key={item.id}
                  type="button"
                  role="radio"
                  aria-checked={audience === item.id}
                  onClick={() => setAudience(item.id)}
                  className={[
                    'rounded-md px-3 py-2 text-sm font-medium transition-colors',
                    audience === item.id
                      ? 'bg-surface text-primary shadow-[0_1px_2px_rgba(31,29,25,0.06)]'
                      : 'text-ink-subtle hover:text-ink',
                  ].join(' ')}
                >
                  {item.label}
                </button>
              ))}
            </div>

            {/* The institution name is the h1, so the purpose of the page needs its own
                heading rather than being folded into the branding. */}
            <div className="mt-6 text-center">
              <h2 className="text-base font-semibold text-ink">Sign in</h2>
              <p className="mt-1 text-sm text-ink-subtle">
                Continue to {portalTitle || 'the portal'}
              </p>
            </div>

            <form onSubmit={submit} noValidate className="mt-6 flex flex-col gap-4">
              <Field
                label={context.fieldLabel}
                htmlFor="loginId"
                error={errors.loginId?.message}
                required
              >
                <TextInput
                  id="loginId"
                  autoComplete="username"
                  autoFocus
                  spellCheck={false}
                  placeholder={context.placeholder}
                  {...register('loginId')}
                  aria-invalid={Boolean(errors.loginId)}
                />
              </Field>

              <Field label="Password" htmlFor="password" error={errors.password?.message} required>
                <TextInputAdorned
                  id="password"
                  type={showPassword ? 'text' : 'password'}
                  autoComplete="current-password"
                  {...register('password')}
                  aria-invalid={Boolean(errors.password)}
                  trailing={
                    <button
                      type="button"
                      onClick={() => setShowPassword((shown) => !shown)}
                      className="rounded px-2.5 py-1 text-xs font-medium text-ink-subtle transition-colors hover:text-primary"
                    >
                      {showPassword ? 'Hide' : 'Show'}
                    </button>
                  }
                />
              </Field>

              <div className="-mt-1 flex justify-end">
                <button
                  type="button"
                  onClick={() => setAskingForHelp(true)}
                  className="rounded text-sm font-medium text-primary underline underline-offset-2 hover:text-primary-hover"
                >
                  Forgot password?
                </button>
              </div>

              {justChangedPassword && (
                <div
                  role="status"
                  className="rounded-lg border border-success/30 bg-success-soft px-4 py-3 text-sm text-success"
                >
                  Password changed. Sign in with your new password.
                </div>
              )}

              {formError && (
                <div
                  role="alert"
                  className="rounded-lg border border-danger/30 bg-danger-soft px-4 py-3 text-sm text-danger"
                >
                  {formError}
                </div>
              )}

              <Button type="submit" loading={isSubmitting} className="mt-1 w-full py-2.5">
                Sign in
              </Button>
            </form>

            <p className="mt-5 text-center text-xs text-ink-subtle">{context.hint}</p>
          </div>

          {(contact.email || contact.phone || contact.website || address) && (
            <footer className="border-t border-border bg-surface-soft/50 px-6 py-5 text-center sm:px-9">
              {address && <p className="text-xs text-ink-subtle">{address}</p>}
              <div className="mt-2 flex flex-wrap items-center justify-center gap-x-4 gap-y-1 text-xs">
                {contact.email && (
                  <a
                    href={`mailto:${contact.email}`}
                    className="text-ink-muted underline underline-offset-2 hover:text-primary"
                  >
                    {contact.email}
                  </a>
                )}
                {contact.phone && (
                  <a
                    href={`tel:${contact.phone.replace(/\s+/g, '')}`}
                    className="text-ink-muted underline underline-offset-2 hover:text-primary"
                  >
                    {contact.phone}
                  </a>
                )}
                {contact.website && (
                  <a
                    href={contact.website}
                    target="_blank"
                    rel="noreferrer"
                    className="text-ink-muted underline underline-offset-2 hover:text-primary"
                  >
                    Website
                  </a>
                )}
              </div>
            </footer>
          )}
        </div>

        <p className="mt-6 text-center text-xs text-ink-subtle">
          {brandingLoading ? '' : 'Authorised access only. All activity is logged.'}
        </p>
      </main>

      {askingForHelp && <ForgotPasswordDialog onClose={() => setAskingForHelp(false)} />}
    </div>
  )
}

/**
 * The uploaded logo when the institution has one, otherwise a monogram drawn from the
 * configured name so the card is never left with a broken image or a bare letter.
 */
function InstitutionMark({ branding }: { branding?: InstitutionBranding }) {
  const logoUrl = branding?.logoUrl?.trim()

  if (logoUrl) {
    return (
      <img
        src={logoUrl}
        alt={`${branding?.name?.trim() || 'Institution'} logo`}
        className="mx-auto h-16 w-auto max-w-[13rem] object-contain"
      />
    )
  }

  return (
    <div
      aria-hidden="true"
      className="mx-auto flex h-16 w-16 items-center justify-center rounded-xl bg-primary text-xl font-semibold tracking-wide"
      style={{ color: onBrandColor(branding?.primaryColor) }}
    >
      {initialsFor(branding)}
    </div>
  )
}
import { useCallback, useRef, useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { post } from '../lib/api'
import { useFocusTrap } from '../lib/useFocusTrap'
import { describeError } from './AuthProvider'
import { Button, Field, TextInput } from '../components/ui'

const schema = z.object({
  identifier: z.string().min(1, 'Enter your username or email'),
  email: z.string().email('Enter a valid email address').or(z.literal('')),
})

type Form = z.infer<typeof schema>

/**
 * Password reset request.
 *
 * The server answers with the same message whether or not the account exists, so this dialog
 * reports that single answer verbatim and never reveals whether the account was found.
 */
export function ForgotPasswordDialog({ onClose }: { onClose: () => void }) {
  const panel = useRef<HTMLDivElement>(null)
  const [sent, setSent] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  const [sentMessage, setSentMessage] = useState<string | null>(null)

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<Form>({ resolver: zodResolver(schema) })

  const close = useCallback(() => onClose(), [onClose])
  // The dialog claimed to be modal but let the keyboard walk out behind it, and Escape did
  // nothing at all.
  useFocusTrap(panel, true, close)

  const submit = handleSubmit(async (values) => {
    setFormError(null)
    try {
      const response = await post<{ message: string }>('/api/v1/auth/forgot-password', {
        identifier: values.identifier.trim(),
        email: values.email.trim() || undefined,
      })
      setSentMessage(response.message)
      setSent(true)
    } catch (error) {
      setFormError(describeError(error))
    }
  })

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-ink/40 px-4 py-8"
      role="dialog"
      aria-modal="true"
      aria-labelledby="forgot-title"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) onClose()
      }}
    >
      <div
        ref={panel}
        tabIndex={-1}
        className="w-full max-w-md rounded-card border border-border bg-surface shadow-[0_12px_40px_rgba(31,29,25,0.14)]"
      >
        <header className="border-b border-border px-6 py-5">
          <h2 id="forgot-title" className="text-base font-semibold text-ink">
            Reset your password
          </h2>
          <p className="mt-1 text-sm text-ink-subtle">
            We will send a reset link to the email address on your account.
          </p>
        </header>

        {sent ? (
          <div className="px-6 py-6">
            <div
              role="status"
              className="rounded-lg border border-primary/25 bg-primary-soft px-4 py-3 text-sm text-primary"
            >
              {sentMessage}
            </div>
            <p className="mt-4 text-sm text-ink-muted">
              If the email does not arrive within a few minutes, check your spam folder or{' '}
              <button
                type="button"
                onClick={() => {
                  setSent(false)
                  setSentMessage(null)
                }}
                className="font-medium text-primary underline underline-offset-2 hover:text-primary-hover"
              >
                try a different account
              </button>
              .
            </p>
            <div className="mt-6 flex justify-end">
              <Button onClick={onClose}>Back to sign in</Button>
            </div>
          </div>
        ) : (
          <form onSubmit={submit} noValidate className="flex flex-col gap-5 px-6 py-6">
            <Field
              label="Username or email"
              htmlFor="forgot-identifier"
              error={errors.identifier?.message}
              required
            >
              <TextInput
                id="forgot-identifier"
                autoComplete="username"
                autoFocus
                {...register('identifier')}
                aria-invalid={Boolean(errors.identifier)}
              />
            </Field>

            <Field
              label="Email on the account"
              htmlFor="forgot-email"
              hint="Only needed if the username does not match the account email."
              error={errors.email?.message}
            >
              <TextInput
                id="forgot-email"
                type="email"
                autoComplete="email"
                {...register('email')}
                aria-invalid={Boolean(errors.email)}
              />
            </Field>

            {formError && (
              <div
                role="alert"
                className="rounded-lg border border-danger/30 bg-danger-soft px-4 py-3 text-sm text-danger"
              >
                {formError}
              </div>
            )}

            <div className="flex justify-end gap-2">
              <Button type="button" variant="ghost" onClick={onClose}>
                Cancel
              </Button>
              <Button type="submit" loading={isSubmitting}>
                Send reset link
              </Button>
            </div>
          </form>
        )}
      </div>
    </div>
  )
}
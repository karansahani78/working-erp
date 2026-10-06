import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useState } from 'react'
import { post } from '../lib/api'
import { describeError, useAuth } from './AuthProvider'
import { Button, Field, Panel, TextInput } from '../components/ui'
import { PageHeader } from '../components/DataTable'

const schema = z
  .object({
    currentPassword: z.string().min(1, 'Enter your current password'),
    newPassword: z
      .string()
      .min(10, 'Use at least 10 characters')
      .regex(/[a-z]/, 'Include a lowercase letter')
      .regex(/[A-Z]/, 'Include an uppercase letter')
      .regex(/[0-9]/, 'Include a digit'),
    confirmPassword: z.string().min(1, 'Repeat the new password'),
  })
  .refine((values) => values.newPassword === values.confirmPassword, {
    path: ['confirmPassword'],
    message: 'The two passwords do not match',
  })
  .refine((values) => values.newPassword !== values.currentPassword, {
    path: ['newPassword'],
    message: 'The new password must differ from the current one',
  })

type FormValues = z.infer<typeof schema>

/**
 * Changing a password invalidates the session server-side, so on success the local tokens are
 * cleared and the user signs in again rather than carrying a stale token forward.
 */
export function ChangePasswordPage() {
  const { signOut } = useAuth()
  const [serverError, setServerError] = useState<string | null>(null)

  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { currentPassword: '', newPassword: '', confirmPassword: '' },
  })

  return (
    <div className="mx-auto flex max-w-2xl flex-col gap-5">
      <PageHeader
        title="Change password"
        description="You will be signed out afterwards so the old session cannot be reused."
      />

      <Panel>
        <form
          className="flex flex-col gap-4"
          onSubmit={form.handleSubmit(async (values) => {
            setServerError(null)
            try {
              await post<void>('/api/v1/auth/change-password', {
                currentPassword: values.currentPassword,
                newPassword: values.newPassword,
              })
              form.reset()
              // The server has already invalidated every session for this account, so drop the
              // local tokens too rather than leaving a session the server will reject. The
              // sign-in screen reads the confirmation from the auth provider: routing here is
              // already happening (the guard sends the signed-out user to /login) and competing
              // navigations dropped the notice on the floor.
              await signOut({ passwordChanged: true })
            } catch (error) {
              setServerError(describeError(error))
            }
          })}
        >
          <Field
            label="Current password"
            required
            error={form.formState.errors.currentPassword?.message}
          >
            <TextInput
              type="password"
              autoComplete="current-password"
              aria-invalid={Boolean(form.formState.errors.currentPassword)}
              {...form.register('currentPassword')}
            />
          </Field>

          <Field
            label="New password"
            required
            hint="At least 10 characters, with upper case, lower case and a digit."
            error={form.formState.errors.newPassword?.message}
          >
            <TextInput
              type="password"
              autoComplete="new-password"
              aria-invalid={Boolean(form.formState.errors.newPassword)}
              {...form.register('newPassword')}
            />
          </Field>

          <Field label="Repeat new password" required error={form.formState.errors.confirmPassword?.message}>
            <TextInput
              type="password"
              autoComplete="new-password"
              aria-invalid={Boolean(form.formState.errors.confirmPassword)}
              {...form.register('confirmPassword')}
            />
          </Field>

          {serverError && <p className="text-sm text-danger">{serverError}</p>}

          <div className="flex gap-2">
            <Button type="submit" loading={form.formState.isSubmitting}>
              Change password
            </Button>
          </div>
        </form>
      </Panel>
    </div>
  )
}
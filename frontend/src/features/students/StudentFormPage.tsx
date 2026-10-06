import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { post } from '../../lib/api'
import { describeError } from '../../auth/AuthProvider'
import { Button, Field, Panel, Select, TextArea, TextInput } from '../../components/ui'
import { GENDERS, STUDENT_STATUSES, type Student, type StudentRequest } from './types'

// The browser knows the format; the server still validates everything.
const schema = z.object({
  firstName: z.string().min(1, 'A first name is required').max(100),
  middleName: z.string().max(100).optional(),
  lastName: z.string().max(100).optional(),
  dateOfBirth: z.string().optional(),
  gender: z.string().optional(),
  nationality: z.string().max(80).optional(),
  phone: z.string().max(60).optional(),
  email: z.string().email('Enter a valid email address').max(180).or(z.literal('')).optional(),
  address: z.string().max(400).optional(),
  photoUrl: z.string().max(400).optional(),
  status: z.string().optional(),
})

type Form = z.infer<typeof schema>

export function StudentFormPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [formError, setFormError] = useState<string | null>(null)

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<Form>({
    resolver: zodResolver(schema),
    defaultValues: { status: 'ACTIVE' },
  })

  const create = useMutation({
    mutationFn: (values: StudentRequest) => post<Student>('/api/v1/students', values),
    onSuccess: (created) => {
      void queryClient.invalidateQueries({ queryKey: ['students'] })
      navigate(`/students/number/${created.studentNumber}`, { replace: true })
    },
    onError: (error) => setFormError(describeError(error)),
  })

  const submit = handleSubmit((values) => {
    setFormError(null)
    create.mutate(prune(values))
  })

  return (
    <div className="mx-auto flex max-w-3xl flex-col gap-5">
      <header>
        <Link to="/students" className="text-sm text-primary hover:underline">
          Students
        </Link>
        <h1 className="mt-1 text-2xl font-semibold text-ink">Admit a student</h1>
        <p className="mt-1 text-sm text-ink-subtle">
          A student number is issued automatically once the record is saved.
        </p>
      </header>

      <form onSubmit={submit} noValidate className="flex flex-col gap-5">
        <Panel title="Name">
          <div className="grid gap-4 sm:grid-cols-3">
            <Field label="First name" required error={errors.firstName?.message}>
              <TextInput autoFocus {...register('firstName')} />
            </Field>
            <Field label="Middle name" error={errors.middleName?.message}>
              <TextInput {...register('middleName')} />
            </Field>
            <Field label="Last name" error={errors.lastName?.message}>
              <TextInput {...register('lastName')} />
            </Field>
          </div>
        </Panel>

        <Panel title="Personal">
          <div className="grid gap-4 sm:grid-cols-3">
            <Field label="Date of birth" error={errors.dateOfBirth?.message}>
              <TextInput type="date" {...register('dateOfBirth')} />
            </Field>
            <Field label="Gender" error={errors.gender?.message}>
              <Select {...register('gender')}>
                <option value="">Not stated</option>
                {GENDERS.map((value) => (
                  <option key={value} value={value}>
                    {value.charAt(0) + value.slice(1).toLowerCase()}
                  </option>
                ))}
              </Select>
            </Field>
            <Field label="Nationality" error={errors.nationality?.message}>
              <TextInput {...register('nationality')} />
            </Field>
          </div>
        </Panel>

        <Panel title="Contact">
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Email" error={errors.email?.message}>
              <TextInput type="email" {...register('email')} />
            </Field>
            <Field label="Phone" error={errors.phone?.message}>
              <TextInput {...register('phone')} />
            </Field>
          </div>
          <div className="mt-4">
            <Field label="Address" error={errors.address?.message}>
              <TextArea {...register('address')} />
            </Field>
          </div>
        </Panel>

        <Panel title="Status">
          <div className="max-w-xs">
            <Field
              label="Status"
              hint="Use Applicant until the admission is confirmed."
              error={errors.status?.message}
            >
              <Select {...register('status')}>
                {STUDENT_STATUSES.map((value) => (
                  <option key={value} value={value}>
                    {value.charAt(0) + value.slice(1).toLowerCase().replace('_', ' ')}
                  </option>
                ))}
              </Select>
            </Field>
          </div>
        </Panel>

        {formError && (
          <div role="alert" className="rounded-lg border border-danger/30 bg-danger-soft px-4 py-3 text-sm text-danger">
            {formError}
          </div>
        )}

        <div className="flex gap-3">
          <Button type="submit" loading={create.isPending}>
            Save student
          </Button>
          <Button type="button" variant="secondary" onClick={() => navigate(-1)}>
            Cancel
          </Button>
        </div>
      </form>
    </div>
  )
}

/** Empty strings mean "not supplied" to the API, not "set to empty". */
function prune(values: Form): StudentRequest {
  const out: Record<string, string> = {}
  for (const [key, value] of Object.entries(values)) {
    if (typeof value === 'string' && value.trim() !== '') out[key] = value.trim()
  }
  return out as unknown as StudentRequest
}

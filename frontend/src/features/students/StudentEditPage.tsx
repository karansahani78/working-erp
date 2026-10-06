import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, put } from '../../lib/api'
import { describeError } from '../../auth/AuthProvider'
import { Button, Field, Panel, Select, TextArea, TextInput } from '../../components/ui'
import { GENDERS, STUDENT_STATUSES, type Student, type StudentRequest } from './types'

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

/** Edits an existing student. The student number is issued on admission and never changes here. */
export function StudentEditPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [formError, setFormError] = useState<string | null>(null)

  const student = useQuery({
    queryKey: ['student-detail', id],
    queryFn: () => api<Student>(`/api/v1/students/${id}`),
    enabled: Boolean(id),
  })

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<Form>({ resolver: zodResolver(schema) })

  // The form is seeded once the record arrives, rather than rendering empty and then filling.
  useEffect(() => {
    if (!student.data) return
    reset({
      firstName: student.data.firstName,
      middleName: student.data.middleName ?? '',
      lastName: student.data.lastName ?? '',
      dateOfBirth: student.data.dateOfBirth ?? '',
      gender: student.data.gender ?? '',
      nationality: student.data.nationality ?? '',
      phone: student.data.phone ?? '',
      email: student.data.email ?? '',
      address: student.data.address ?? '',
      photoUrl: student.data.photoUrl ?? '',
      status: student.data.status,
    })
  }, [student.data, reset])

  const update = useMutation({
    mutationFn: (values: Form) => put<Student>(`/api/v1/students/${id}`, prune(values)),
    onSuccess: (updated) => {
      void queryClient.invalidateQueries({ queryKey: ['student-detail', id] })
      void queryClient.invalidateQueries({ queryKey: ['students'] })
      navigate(`/students/${updated.id}`)
    },
    onError: (error) => setFormError(describeError(error)),
  })

  const submit = handleSubmit((values) => {
    setFormError(null)
    update.mutate(values)
  })

  if (student.isLoading) {
    return (
      <div className="mx-auto flex max-w-3xl flex-col gap-5">
        <p className="text-sm text-ink-subtle">Loading student…</p>
      </div>
    )
  }

  if (student.isError) {
    return (
      <div className="mx-auto flex max-w-3xl flex-col gap-4">
        <p className="text-sm text-danger">{describeError(student.error)}</p>
        <Link to="/students" className="text-sm text-primary hover:underline">
          Back to students
        </Link>
      </div>
    )
  }

  return (
    <div className="mx-auto flex max-w-3xl flex-col gap-5">
      <header>
        <Link to={`/students/${id}`} className="text-sm text-primary hover:underline">
          {student.data?.fullName ?? 'Student'}
        </Link>
        <h1 className="mt-1 text-2xl font-semibold text-ink">Edit student</h1>
        <p className="nums mt-1 text-sm text-ink-subtle">{student.data?.studentNumber}</p>
      </header>

      <form onSubmit={submit} noValidate className="flex flex-col gap-5">
        <Panel title="Name">
          <div className="grid gap-4 sm:grid-cols-3">
            <Field label="First name" error={errors.firstName?.message} required>
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
            <Field label="Phone" error={errors.phone?.message}>
              <TextInput {...register('phone')} />
            </Field>
            <Field label="Email" error={errors.email?.message}>
              <TextInput type="email" {...register('email')} />
            </Field>
          </div>
          <div className="mt-4">
            <Field label="Address" error={errors.address?.message}>
              <TextArea {...register('address')} />
            </Field>
          </div>
        </Panel>

        <Panel title="Record">
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Status" error={errors.status?.message}>
              <Select {...register('status')}>
                {STUDENT_STATUSES.map((value) => (
                  <option key={value} value={value}>
                    {value.charAt(0) + value.slice(1).toLowerCase().replace('_', ' ')}
                  </option>
                ))}
              </Select>
            </Field>
            <Field label="Photo URL" error={errors.photoUrl?.message}>
              <TextInput {...register('photoUrl')} />
            </Field>
          </div>
        </Panel>

        {formError && (
          <div
            role="alert"
            className="rounded-lg border border-danger/30 bg-danger-soft px-4 py-3 text-sm text-danger"
          >
            {formError}
          </div>
        )}

        <div className="flex gap-2">
          <Button type="submit" loading={update.isPending}>
            Save changes
          </Button>
          <Button type="button" variant="ghost" onClick={() => navigate(-1)}>
            Cancel
          </Button>
        </div>
      </form>
    </div>
  )
}

/** Empty inputs are sent as absent fields so the server keeps the stored value. */
function prune(values: Form): StudentRequest {
  const body: Record<string, unknown> = {}
  for (const [key, value] of Object.entries(values)) {
    if (typeof value === 'string' && value.trim() !== '') body[key] = value.trim()
  }
  return body as unknown as StudentRequest
}
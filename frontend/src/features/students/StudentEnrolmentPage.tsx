import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, post } from '../../lib/api'
import { describeError } from '../../auth/AuthProvider'
import { Button, Field, Panel, Select, TextInput } from '../../components/ui'
import { PageHeader } from '../../components/DataTable'
import type { AcademicYear, SchoolClass, Section, Semester } from '../academic/types'

const schema = z.object({
  academicYearId: z.string().min(1, 'Choose an academic year'),
  semesterId: z.string().optional(),
  schoolClassId: z.string().min(1, 'Choose a class'),
  sectionId: z.string().optional(),
  rollNumber: z.string().max(20).optional(),
})

type Form = z.infer<typeof schema>

/**
 * Enrols a student into a class for a year.
 *
 * The server requires an academic year and a class, and takes the semester and section when
 * the institution uses them, so both are offered but neither is demanded.
 */
export function StudentEnrolmentPage({ studentId }: { studentId: string }) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [formError, setFormError] = useState<string | null>(null)
  const [schoolClassId, setSchoolClassId] = useState('')
  const [academicYearId, setAcademicYearId] = useState('')

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<Form>({
    resolver: zodResolver(schema),
    defaultValues: { academicYearId: '', semesterId: '', schoolClassId: '', sectionId: '', rollNumber: '' },
  })

  const years = useQuery({
    queryKey: ['academic-years'],
    queryFn: () => api<{ data: AcademicYear[] }>('/api/v1/academic/academic-years', { query: { size: 100 } }),
  })

  const effectiveYear = academicYearId || years.data?.data.find((year) => year.current)?.id || ''

  const classes = useQuery({
    queryKey: ['academic-classes', effectiveYear],
    queryFn: () =>
      api<{ data: SchoolClass[] }>('/api/v1/academic/classes', {
        query: { yearId: effectiveYear, size: 100 },
      }),
    enabled: Boolean(effectiveYear),
  })

  const semesters = useQuery({
    queryKey: ['semesters', effectiveYear],
    queryFn: () => api<Semester[]>('/api/v1/academic/semesters', { query: { academicYearId: effectiveYear } }),
    enabled: Boolean(effectiveYear),
  })

  const sections = useQuery({
    queryKey: ['sections', schoolClassId],
    queryFn: () => api<Section[]>('/api/v1/academic/sections', { query: { schoolClassId } }),
    enabled: Boolean(schoolClassId),
  })

  const enrol = useMutation({
    mutationFn: (values: Form) =>
      post<{ id: string }>('/api/v1/enrollments', {
        studentId,
        academicYearId: values.academicYearId,
        semesterId: values.semesterId || undefined,
        schoolClassId: values.schoolClassId,
        sectionId: values.sectionId || undefined,
        rollNumber: values.rollNumber || undefined,
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['student-enrollments', studentId] })
      void queryClient.invalidateQueries({ queryKey: ['student-detail', studentId] })
      navigate(`/students/${studentId}`)
    },
    onError: (error) => setFormError(describeError(error)),
  })

  const submit = handleSubmit((values) => {
    setFormError(null)
    enrol.mutate(values)
  })

  return (
    <div className="mx-auto flex max-w-3xl flex-col gap-5">
      <PageHeader title="Enrol a student" description="Places a student in a class for an academic year." />

      <form onSubmit={submit} noValidate className="flex flex-col gap-5">
        <Panel title="Placement">
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Academic year" error={errors.academicYearId?.message} required>
              <Select
                {...register('academicYearId')}
                onChange={(event) => {
                  setAcademicYearId(event.target.value)
                  register('academicYearId').onChange(event)
                }}
              >
                <option value="">Choose a year</option>
                {(years.data?.data ?? []).map((year) => (
                  <option key={year.id} value={year.id}>
                    {year.name}
                    {year.current ? ' (current)' : ''}
                  </option>
                ))}
              </Select>
            </Field>

            <Field label="Semester" hint="Optional, when the year is split into semesters.">
              <Select {...register('semesterId')}>
                <option value="">Not applicable</option>
                {(semesters.data ?? []).map((semester) => (
                  <option key={semester.id} value={semester.id}>
                    {semester.name}
                  </option>
                ))}
              </Select>
            </Field>

            <Field label="Class" error={errors.schoolClassId?.message} required>
              <Select
                {...register('schoolClassId')}
                onChange={(event) => {
                  setSchoolClassId(event.target.value)
                  register('schoolClassId').onChange(event)
                }}
              >
                <option value="">Choose a class</option>
                {(classes.data?.data ?? []).map((item) => (
                  <option key={item.id} value={item.id}>
                    {item.name}
                  </option>
                ))}
              </Select>
            </Field>

            <Field label="Section" hint="Optional, when the class is split into sections.">
              <Select {...register('sectionId')}>
                <option value="">Not applicable</option>
                {(sections.data ?? []).map((section) => (
                  <option key={section.id} value={section.id}>
                    {section.name}
                  </option>
                ))}
              </Select>
            </Field>

            <Field label="Roll number" error={errors.rollNumber?.message}>
              <TextInput {...register('rollNumber')} placeholder="Optional" />
            </Field>
          </div>

          {!effectiveYear && (
            <p className="mt-4 text-sm text-ink-subtle">
              Choose an academic year to see its classes.
            </p>
          )}
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
          <Button type="submit" loading={enrol.isPending}>
            Enrol student
          </Button>
          <Button type="button" variant="ghost" onClick={() => navigate(-1)}>
            Cancel
          </Button>
        </div>
      </form>
    </div>
  )
}
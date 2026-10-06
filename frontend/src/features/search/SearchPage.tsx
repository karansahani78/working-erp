import { useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { api } from '../../lib/api'
import { Badge, Button, EmptyState, Panel, QueryBoundary, Select, TextInput } from '../../components/ui'
import { SOURCES, routeFor, type SearchResponse } from './types'

export function SearchPage() {
  const [params, setParams] = useSearchParams()
  const navigate = useNavigate()
  const [term, setTerm] = useState(params.get('q') ?? '')
  const [source, setSource] = useState('')
  const query = params.get('q') ?? ''

  const results = useQuery({
    queryKey: ['search', query],
    queryFn: () => api<SearchResponse>('/api/v1/search', { query: { q: query } }),
    enabled: query.trim().length >= 2,
  })

  const submit = (event: React.FormEvent) => {
    event.preventDefault()
    setParams(term.trim() ? { q: term.trim() } : {})
  }

  const all = results.data?.results ?? []
  const shown = source ? all.filter((hit) => hit.source === source) : all

  const grouped = shown.reduce<Record<string, typeof shown>>((accumulator, hit) => {
    ;(accumulator[hit.sourceLabel] ??= []).push(hit)
    return accumulator
  }, {})

  return (
    <div className="mx-auto flex max-w-5xl flex-col gap-5">
      <header>
        <h1 className="text-2xl font-semibold text-ink">Search</h1>
        <p className="mt-1 text-sm text-ink-subtle">
          Every record you are permitted to see, matched on name, number and reference.
        </p>
      </header>

      <Panel padded={false}>
        <form onSubmit={submit} role="search" className="flex flex-wrap items-end gap-3 border-b border-border p-4">
          <div className="min-w-56 flex-1">
            <label htmlFor="search-page-term" className="mb-1.5 block text-xs font-medium text-ink-muted">
              Search term
            </label>
            <TextInput
              id="search-page-term"
              value={term}
              onChange={(event) => setTerm(event.target.value)}
              placeholder="At least two characters"
              autoFocus
            />
          </div>
          <div className="w-48">
            <label htmlFor="search-page-source" className="mb-1.5 block text-xs font-medium text-ink-muted">
              Limit to
            </label>
            <Select id="search-page-source" value={source} onChange={(event) => setSource(event.target.value)}>
              <option value="">Everything</option>
              {SOURCES.map((value) => (
                <option key={value} value={value}>
                  {value.charAt(0) + value.slice(1).toLowerCase()}
                </option>
              ))}
            </Select>
          </div>
          <Button type="submit">Search</Button>
        </form>

        {query.trim().length < 2 ? (
          <EmptyState
            title="Enter at least two characters"
            description="A one-letter search would match most of the database, so it is not offered."
          />
        ) : (
          <QueryBoundary
            isLoading={results.isLoading}
            error={results.error}
            data={results.data}
            onRetry={() => void results.refetch()}
            loadingRows={6}
          >
            {(data) => (
              <>
                <p className="border-b border-border px-4 py-3 text-sm text-ink-subtle">
                  {data.count} result{data.count === 1 ? '' : 's'} for{' '}
                  <span className="font-medium text-ink">{data.term}</span>
                </p>

                {shown.length === 0 ? (
                  <EmptyState
                    title="Nothing matched"
                    description="No permitted record contains that text. Check the spelling or search for a shorter fragment."
                  />
                ) : (
                  <div className="divide-y divide-border">
                    {Object.entries(grouped).map(([label, hits]) => (
                      <section key={label}>
                        <h2 className="bg-surface-soft/60 px-4 py-2 text-xs font-semibold tracking-wide text-ink-subtle uppercase">
                          {label}
                        </h2>
                        <ul className="divide-y divide-border">
                          {hits.map((hit) => (
                            <li key={`${hit.source}-${hit.id}`}>
                              <button
                                type="button"
                                onClick={() => navigate(routeFor(hit))}
                                className="flex w-full items-center gap-3 px-4 py-3 text-left hover:bg-surface-soft/60"
                              >
                                <div className="min-w-0 flex-1">
                                  <p className="truncate font-medium text-ink">{hit.heading}</p>
                                  {hit.detail && <p className="truncate text-xs text-ink-subtle">{hit.detail}</p>}
                                </div>
                                {hit.reference && (
                                  <span className="nums shrink-0 text-xs text-ink-subtle">{hit.reference}</span>
                                )}
                                <Badge>Open</Badge>
                              </button>
                            </li>
                          ))}
                        </ul>
                      </section>
                    ))}
                  </div>
                )}
              </>
            )}
          </QueryBoundary>
        )}
      </Panel>
    </div>
  )
}
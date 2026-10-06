import { chromium } from 'playwright'

const BASE = 'http://localhost:5173'
let failures = 0
const check = (name, ok, extra = '') => {
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${extra ? ` — ${extra}` : ''}`)
  if (!ok) failures += 1
}

const browser = await chromium.launch()
const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } })
const errors = []
page.on('console', (m) => {
  if (m.type() === 'error') errors.push(m.text())
})
page.on('pageerror', (e) => errors.push(String(e)))

await page.goto(BASE, { waitUntil: 'networkidle' })
await page.getByLabel(/username or email/i).fill('admin')
await page.getByLabel(/password/i).fill('Institution123!')
await page.getByRole('button', { name: 'Sign in' }).click()
await page.getByRole('button', { name: 'Sign out' }).waitFor({ timeout: 20000 })

// ---------------------------------------------------------------------- results
await page.goto(`${BASE}/results`, { waitUntil: 'networkidle' })
check(
  'results heading',
  (await page.getByRole('heading', { name: 'Results', level: 1 }).count()) === 1,
)
check('no placeholder copy left', !(await page.locator('main').innerText()).includes('Come soon'))
check('examination picker present', (await page.locator('#result-exam').count()) === 1)
check('empty state before choosing', (await page.getByText('Choose an examination').count()) >= 1)

await page.locator('#result-exam').selectOption({ label: 'Term One Exam (T1-2026)' })
await page.waitForTimeout(1000)
const resultsText = await page.locator('main').innerText()
check('seeded result shown', resultsText.includes('82'), resultsText.replace(/\n+/g, ' ').slice(0, 120))
check('grade shown', resultsText.includes('A'))
check('published badge shown', /published/i.test(resultsText))
// A published result cannot be reopened, so no workflow buttons should be offered.
check('no reopen action on a published result', !resultsText.includes('Reopen'))
check('correction requests panel rendered', /correction requests/i.test(resultsText))
check('applied correction listed', /applied/i.test(resultsText))

// ----------------------------------------------------------------- report cards
await page.goto(`${BASE}/report-cards`, { waitUntil: 'networkidle' })
check(
  'report cards heading',
  (await page.getByRole('heading', { name: 'Report cards', level: 1 }).count()) === 1,
)
check('student picker present', (await page.locator('#card-student').count()) === 1)
check('generate action present', (await page.getByRole('button', { name: 'Generate report card' }).count()) === 1)

await page.locator('#card-student').selectOption({ label: 'Ananya Sharma (SUNDAR-2026-00001)' })
await page.waitForTimeout(1000)
const cardsText = await page.locator('main').innerText()
check('existing card listed', cardsText.includes('RC-2026-'))
check('card shows the outcome', cardsText.includes('PASS'))
check('card is published', /published/i.test(cardsText))

await page.getByRole('button', { name: 'View' }).first().click()
await page.waitForTimeout(800)
const cardText = await page.locator('main').innerText()
check('card detail lists the subject', cardText.includes('Physics'))
check('card detail shows the grade', cardText.includes('82') || cardText.includes('88'))
// A published card is final, so no further transition buttons should be offered.
check(
  'no transition buttons on a published card',
  (await page.getByRole('button', { name: 'Publish' }).count()) === 0,
)

// ------------------------------------------------------------------ transcripts
await page.goto(`${BASE}/transcripts`, { waitUntil: 'networkidle' })
check(
  'transcripts heading',
  (await page.getByRole('heading', { name: 'Transcripts', level: 1 }).count()) === 1,
)
check('transcript student picker', (await page.locator('#transcript-student').count()) === 1)
check('transcript empty state', (await page.getByText('No transcript open').count()) === 1)

await page.locator('#transcript-student').selectOption({ label: 'Ananya Sharma (SUNDAR-2026-00001)' })
await page.getByRole('button', { name: 'Generate transcript' }).click()
await page.waitForTimeout(1500)
const transcriptText = await page.locator('main').innerText()
check('transcript generated', transcriptText.includes('TR-2026-'))
check('transcript carries the report card', transcriptText.includes('RC-2026-'))
check('transcript is finalised', /finalised/i.test(transcriptText))

check(
  'no runtime errors',
  errors.filter((e) => !e.includes('401')).length === 0,
  errors.slice(0, 3).join(' | '),
)

await browser.close()
console.log(failures === 0 ? '\nAll results checks passed' : `\n${failures} check(s) failed`)
process.exit(failures === 0 ? 0 : 1)
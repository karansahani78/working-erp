/**
 * Drives the real interface in a browser: sign in, land on the dashboard, walk to the
 * register, open a student and search. It fails on any console error or failed request,
 * because a screen that merely renders while quietly erroring is not working.
 */
import { chromium } from 'playwright'

const BASE = process.env.BASE_URL ?? 'http://localhost:5173'
const LOGIN_ID = process.env.LOGIN_ID ?? 'admin'
const PASSWORD = process.env.PASSWORD ?? 'Institution123!'

const problems = []

async function main() {
  const browser = await chromium.launch()
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })

  // The wrong-password check deliberately provokes a 401, so that one is not a defect.
  let expectingRejection = false
  page.on('console', (message) => {
    if (message.type() !== 'error') return
    if (expectingRejection && /401/.test(message.text())) return
    problems.push(`console: ${message.text()}`)
  })
  page.on('pageerror', (error) => problems.push(`pageerror: ${error.message}`))
  page.on('requestfailed', (request) => {
    problems.push(`requestfailed: ${request.url()} ${request.failure()?.errorText ?? ''}`)
  })
  page.on('response', (response) => {
    if (response.status() >= 500) problems.push(`http ${response.status()}: ${response.url()}`)
  })

  const step = async (name, action) => {
    process.stdout.write(`  ${name} … `)
    try {
      await action()
      console.log('ok')
    } catch (error) {
      console.log('FAILED')
      problems.push(`${name}: ${error.message}`)
    }
  }

  await step('login screen renders', async () => {
    await page.goto(BASE, { waitUntil: 'networkidle' })
    await page.getByRole('heading', { name: 'Sign in' }).waitFor({ timeout: 10_000 })
  })

  await step('rejects a wrong password with a readable message', async () => {
    await page.getByLabel(/username or email/i).fill(LOGIN_ID)
    await page.getByLabel(/password/i).fill('wrong-password-entirely')
    expectingRejection = true
    await page.getByRole('button', { name: 'Sign in' }).click()
    await page.getByRole('alert').waitFor({ timeout: 10_000 })
    const message = await page.getByRole('alert').innerText()
    if (!message.trim()) throw new Error('the error alert was empty')
    expectingRejection = false
  })

  await step('signs in and lands on the dashboard', async () => {
    await page.getByLabel(/password/i).fill(PASSWORD)
    await page.getByRole('button', { name: 'Sign in' }).click()
    await page.getByRole('button', { name: 'Sign out' }).waitFor({ timeout: 15_000 })
    if (await page.getByRole('button', { name: 'Sign in' }).count()) {
      throw new Error('still on the login screen')
    }
  })

  await step('sidebar shows the permitted sections', async () => {
    for (const label of ['Students', 'Attendance', 'Fee structures', 'Reports', 'Data imports']) {
      const link = page.getByRole('link', { name: label, exact: true })
      if ((await link.count()) === 0) throw new Error(`no sidebar link for ${label}`)
    }
  })

  await step('dashboard renders sections and tiles', async () => {
    await page.getByRole('link', { name: 'Students', exact: true }).click()
    await page.getByRole('link', { name: 'Dashboard', exact: true }).click()
    await page.getByRole('heading', { name: /overview/i }).waitFor({ timeout: 10_000 })
    const tiles = await page.locator('dt').count()
    if (tiles === 0) throw new Error('the dashboard rendered no tiles')
  })

  await step('student register lists the created student', async () => {
    await page.getByRole('link', { name: 'Students', exact: true }).click()
    await page.getByRole('heading', { name: 'Students' }).waitFor({ timeout: 10_000 })
    await page.getByLabel(/^search$/i).fill('Sharma')
    await page.getByRole('button', { name: 'Search' }).click()
    await page.getByRole('link', { name: 'Ananya Sharma' }).waitFor({ timeout: 10_000 })
  })

  await step('student detail opens by number', async () => {
    await page.getByRole('link', { name: 'Ananya Sharma' }).click()
    await page.getByRole('heading', { name: 'Ananya Sharma' }).waitFor({ timeout: 10_000 })
    const body = await page.innerText('body')
    if (!body.includes('SUNDAR-')) throw new Error('the student number is missing from the page')
  })

  await step('global search finds the student', async () => {
    await page.getByLabel('Search the whole system').fill('sharma')
    await page.getByText('Ananya Sharma').first().waitFor({ timeout: 10_000 })
  })

  await step('search page groups results by source', async () => {
    await page.goto(`${BASE}/search?q=sharma`, { waitUntil: 'networkidle' })
    await page.getByRole('heading', { name: 'Search' }).waitFor({ timeout: 10_000 })
    await page.getByText(/result/i).first().waitFor({ timeout: 10_000 })
  })

  // Everything below was built after the first ten checks, so each page is opened
  // directly and required to render without a console error or a failed request.
  const pages = [
    ['academics', 'Academic structure'],
    ['attendance', 'Attendance'],
    ['examinations', 'Examinations'],
    ['fees', 'Fee structures'],
    ['receivables', 'Receivables'],
    ['library', 'Library'],
    ['imports', 'Data imports'],
    ['reports', 'Reports'],
    ['audit', 'Audit'],
    ['users', 'Users'],
    ['roles', 'Roles'],
    ['settings', 'Modules'],
  ]

  for (const [path, heading] of pages) {
    await step(`${heading} page renders`, async () => {
      await page.goto(`${BASE}/${path}`, { waitUntil: 'networkidle' })
      await page.getByRole('heading', { name: new RegExp(heading, 'i') }).first()
        .waitFor({ timeout: 10_000 })
    })
  }

  await step('library catalogue filters by category', async () => {
    await page.goto(`${BASE}/library`, { waitUntil: 'networkidle' })
    await page.getByRole('tab', { name: 'Catalogue' }).click()
    await page.locator('header').getByLabel('Category').selectOption({ index: 1 })
    await page.getByRole('heading', { name: 'Catalogue', exact: true }).waitFor({ timeout: 10_000 })
  })

  await step('library circulation tab loads', async () => {
    await page.getByRole('tab', { name: 'Circulation' }).click()
    await page.getByText('Choose a member', { exact: true }).waitFor({ timeout: 10_000 })
  })

  await step('import wizard explains the file it wants', async () => {
    await page.goto(`${BASE}/imports/new`, { waitUntil: 'networkidle' })
    await page.getByRole('heading', { name: 'Start an import' }).waitFor({ timeout: 10_000 })
    await page.getByLabel('Record type').selectOption('STUDENTS')
    await page.getByText('Columns this import expects').waitFor({ timeout: 10_000 })
    // The confirm button must stay disabled until a file is chosen, rather than failing.
    const disabled = await page.getByRole('button', { name: 'Upload and continue' }).isDisabled()
    if (!disabled) throw new Error('upload is offered before a file has been chosen')
  })

  await step('module settings lists core and optional modules', async () => {
    await page.goto(`${BASE}/settings`, { waitUntil: 'networkidle' })
    await page.getByText('Core modules').waitFor({ timeout: 10_000 })
    await page.getByText('Optional modules').waitFor({ timeout: 10_000 })
    const body = await page.innerText('body')
    if (!body.includes('LIBRARY')) throw new Error('the library module row is missing')
  })

  await step('users screen offers a role when creating an account', async () => {
    await page.goto(`${BASE}/users`, { waitUntil: 'networkidle' })
    await page.getByText('Create a user').waitFor({ timeout: 10_000 })
    // The accounts table filters by role and the create form picks one, so the form's
    // control is the one that matters here.
    await page.getByRole('heading', { name: 'Create a user' }).waitFor({ timeout: 10_000 })
    const form = page.locator('form').filter({ has: page.getByRole('button', { name: 'Create user' }) })
    await form.getByLabel(/^Role/).selectOption({ index: 1 })
  })

  await step('reports run without a manual parameter', async () => {
    await page.goto(`${BASE}/reports`, { waitUntil: 'networkidle' })
    // Reports are chosen from a group list, so the group is picked before the report.
    await page.getByRole('button', { name: /^Students/ }).click()
    await page.getByRole('button', { name: 'Student list' }).click()
    await page.getByRole('button', { name: 'Run report' }).click()
    // The catalogue defaults to the current month, so a dated report fills in on its own.
    await page.getByRole('button', { name: 'CSV' }).waitFor({ timeout: 10_000 })
    // The table must show real captions and values, not empty cells.
    // Column captions are styled in capitals, so the comparison ignores case.
    const table = (await page.innerText('body')).toLowerCase()
    if (!table.includes('student number')) throw new Error('report column captions are missing')
    if (!table.includes('sundar-')) throw new Error('report rows are missing')
    await page.screenshot({ path: 'target/e2e-report.png', fullPage: true })
  })

  await step('a signed-in user is returned to the login screen on sign out', async () => {
    await page.goto(BASE, { waitUntil: 'networkidle' })
    await page.getByRole('button', { name: 'Sign out' }).click()
    await page.getByRole('heading', { name: 'Sign in' }).waitFor({ timeout: 15_000 })
  })

  await page.screenshot({ path: 'target/e2e-login.png', fullPage: true })
  await browser.close()

  if (problems.length > 0) {
    console.error(`\n${problems.length} problem(s):`)
    for (const problem of [...new Set(problems)]) console.error(`  - ${problem}`)
    process.exit(1)
  }
  console.log('\nAll interface checks passed with no console or network errors.')
}

main().catch((error) => {
  console.error(error)
  process.exit(1)
})
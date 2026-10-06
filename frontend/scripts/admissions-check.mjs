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
page.on('console', (m) => { if (m.type() === 'error') errors.push(m.text()) })
page.on('pageerror', (e) => errors.push(String(e)))

await page.goto(BASE, { waitUntil: 'networkidle' })
await page.getByLabel(/username or email/i).fill('admin')
await page.getByLabel(/password/i).fill('Institution123!')
await page.getByRole('button', { name: 'Sign in' }).click()
await page.getByRole('button', { name: 'Sign out' }).waitFor({ timeout: 20000 })

await page.goto(`${BASE}/admissions/campaigns`, { waitUntil: 'networkidle' })
check('campaigns heading', (await page.getByRole('heading', { name: 'Admission campaigns', level: 1 }).count()) === 1)
check('campaign row is listed', (await page.getByRole('cell', { name: 'Grade 6 intake 2026' }).count()) === 1)
await page.getByRole('button', { name: 'New campaign' }).click()
check('campaign form opens', (await page.locator('#campaign-code').count()) === 1)
check('campaign name field present', (await page.locator('#campaign-name').count()) === 1)
check('required document hint present', (await page.getByText('One per line').count()) === 1)

await page.goto(`${BASE}/admissions`, { waitUntil: 'networkidle' })
check('applications heading', (await page.getByRole('heading', { name: 'Admission applications', level: 1 }).count()) === 1)
check('draft shows a Submit action', (await page.getByRole('button', { name: 'Submit' }).count()) >= 1)

await page.getByRole('link', { name: 'Nima Tamang' }).first().click()
await page.waitForURL(/\/admissions\/applications\//, { timeout: 10000 })
// The detail screen fills in as three separate queries land, so wait for the last one.
await page.getByRole('heading', { name: 'Workflow' }).waitFor({ timeout: 10000 })
await page.waitForTimeout(500)
const detail = await page.locator('main').innerText()
check('detail shows the reference', detail.includes('ADM--6132'))
check('detail shows previous school', detail.includes('Sundar Primary'), detail.replace(/\n+/g, ' ').slice(0, 200))
// StatusBadge renders the readable form of the code, not the raw enum.
check('detail shows the draft status', detail.includes('Draft'))
check('submit action offered for a draft', (await page.getByRole('button', { name: 'Submit application' }).count()) === 1)
check('workflow panel present', (await page.getByText('Each step is enforced').count()) === 1)
check('notes field offered', (await page.locator('#application-notes').count()) === 1)

await page.screenshot({ path: '/tmp/admissions.png', fullPage: true })
check('no runtime errors', errors.filter((e) => !e.includes('401')).length === 0, errors.slice(0, 3).join(' | '))

console.log(failures === 0 ? '\nAll admissions checks passed' : `\n${failures} failed`)
await browser.close()
process.exit(failures === 0 ? 0 : 1)

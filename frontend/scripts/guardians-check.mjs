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

await page.goto(`${BASE}/guardians`, { waitUntil: 'networkidle' })
check('guardians heading', (await page.getByRole('heading', { name: 'Guardians', level: 1 }).count()) === 1)
check('seeded guardian listed', (await page.getByRole('cell', { name: 'Bimala Shrestha' }).count()) === 1)
check('portal access shown as unlinked', (await page.getByText('Not linked').count()) >= 1)
await page.getByRole('button', { name: 'Add guardian' }).click()
check('guardian form opens', (await page.locator('#new-guardian-first').count()) === 1)

await page.getByRole('link', { name: 'View' }).first().click()
await page.waitForURL(/\/guardians\//, { timeout: 10000 })
await page.getByRole('heading', { name: 'Details' }).waitFor({ timeout: 10000 })
await page.waitForTimeout(400)
check('detail heading shows the name', (await page.getByRole('heading', { name: 'Bimala Shrestha' }).count()) === 1)
check('portal access panel offered', (await page.getByLabel('Portal account email').count()) === 1)
// Panel titles are uppercased by CSS, which innerText reflects, so compare loosely.
const childrenText = (await page.locator('main').innerText()).toLowerCase()
check('linked student panel present', childrenText.includes('students in their care'))
check('the linked child is named', childrenText.includes('ananya sharma'), childrenText.replace(/\n+/g, ' ').slice(0, 160))
check('no runtime errors', errors.filter((e) => !e.includes('401')).length === 0, errors.slice(0, 3).join(' | '))

await page.screenshot({ path: '/tmp/guardians.png', fullPage: true })
console.log(failures === 0 ? '\nAll guardian checks passed' : `\n${failures} failed`)
await browser.close()
process.exit(failures === 0 ? 0 : 1)

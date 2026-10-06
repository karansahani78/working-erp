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

// ------------------------------------------------------------------ curriculum
await page.goto(`${BASE}/curriculum`, { waitUntil: 'networkidle' })
check(
  'curriculum heading',
  (await page.getByRole('heading', { name: 'Curriculum', level: 1 }).count()) === 1,
)
check('programme list loads', (await page.getByText('Science').count()) >= 1)
check('new programme form available', (await page.locator('#program-code').count()) === 1)
await page.locator('#curriculum-program').selectOption({ label: 'Science' })
await page.waitForTimeout(500)
await page.locator('#curriculum-version').selectOption({ index: 1 })
await page.waitForTimeout(500)
await page.locator('#curriculum-pick').selectOption({ index: 1 })
await page.waitForTimeout(800)
const curriculumText = await page.locator('main').innerText()
check('seeded programme selected', curriculumText.includes('Science'))
check('seeded curriculum selected', curriculumText.includes('PHY Physics'))
check('server requirement type shown', curriculumText.includes('MANDATORY'))
check(
  'core is not offered as a requirement',
  !curriculumText.includes('CORE'),
  'CORE is not a server enum value',
)

// ------------------------------------------------------------------- timetable
await page.goto(`${BASE}/timetable`, { waitUntil: 'networkidle' })
check(
  'timetable heading',
  (await page.getByRole('heading', { name: 'Timetable', level: 1 }).count()) === 1,
)
await page.waitForTimeout(600)
const timetableText = await page.locator('main').innerText()
check('week days rendered', timetableText.includes('MONDAY'))
check('section selector names the class', timetableText.includes('Grade 6 · A'))
check('scheduled period appears', timetableText.includes('Physics'))
check('entry form available', (await page.locator('#timetable-day').count()) === 1)

// -------------------------------------------------------------------- calendar
await page.goto(`${BASE}/schedules`, { waitUntil: 'networkidle' })
check(
  'calendar heading',
  (await page.getByRole('heading', { name: 'Calendar', level: 1 }).count()) === 1,
)
check('seeded event listed', (await page.getByText('First term').count()) >= 1)
check('event form available', (await page.locator('#calendar-title').count()) === 1)
// The event type must come from the fixed server enum rather than free text.
const typeTag = await page.locator('#calendar-type').evaluate((node) => node.tagName)
check('event type is a constrained select', typeTag === 'SELECT', typeTag)
check(
  'legacy TERM type is not offered',
  !(await page.locator('#calendar-type').locator('option[value="TERM"]').count()),
)

check(
  'no runtime errors',
  errors.filter((e) => !e.includes('401')).length === 0,
  errors.slice(0, 3).join(' | '),
)

await browser.close()
console.log(failures === 0 ? '\nAll academic checks passed' : `\n${failures} check(s) failed`)
process.exit(failures === 0 ? 0 : 1)
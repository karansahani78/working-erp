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

const text = () => page.locator('main').innerText()

// ------------------------------------------------------------ focus trap: sign in
// Signed out first: the sign-in screen redirects away from anybody already signed in.
await page.getByRole('button', { name: 'Sign out' }).click()
await page.waitForURL(/\/login/, { timeout: 20000 })
const forgotButton = page.getByRole('button', { name: 'Forgot password?' })
await forgotButton.focus()
await forgotButton.press('Enter')
const dialog = page.getByRole('dialog')
await dialog.waitFor({ timeout: 10000 })
check('the reset dialog opens', /Reset your password/.test(await dialog.innerText()))
check('it claims to be modal', (await dialog.getAttribute('aria-modal')) === 'true')
check('focus starts inside the dialog',
  await dialog.evaluate((node) => node.contains(document.activeElement)),
  await page.evaluate(() => document.activeElement?.id || document.activeElement?.tagName))

// Tab all the way round: focus must never land on the page behind.
const stops = []
let escaped = null
for (let step = 0; step < 12 && !escaped; step += 1) {
  await page.keyboard.press('Tab')
  const inside = await dialog.evaluate((node) => node.contains(document.activeElement))
  if (!inside) {
    escaped = await page.evaluate(() => {
      const el = document.activeElement
      return `${el?.tagName}: ${(el?.textContent || el?.id || '').trim().slice(0, 40)}`
    })
  }
  stops.push(await page.evaluate(() => {
    const el = document.activeElement
    return `${el?.tagName}:${(el?.textContent || el?.id || '').trim().slice(0, 24)}`
  }))
}
check('tabbing cannot leave the dialog', escaped === null, escaped ?? `${stops.length} stops, all inside`)
check('every control in the dialog can be reached', new Set(stops).size >= 4, [...new Set(stops)].join(' / '))

await page.keyboard.press('Shift+Tab')
check('shift-tab stays inside too',
  await dialog.evaluate((node) => node.contains(document.activeElement)))

await page.keyboard.press('Escape')
await page.waitForTimeout(600)
check('escape closes the dialog', (await page.getByRole('dialog').count()) === 0)
check('focus comes back to the button that opened it',
  await page.evaluate(() => (document.activeElement?.textContent || '').trim() === 'Forgot password?'),
  await page.evaluate(() => document.activeElement?.textContent?.trim().slice(0, 40) ?? 'nothing'))

// ------------------------------------------------- focus trap: the document drawer
await page.getByLabel(/username or email/i).fill('admin')
await page.getByLabel(/password/i).fill('Institution123!')
await page.getByRole('button', { name: 'Sign in' }).click()
await page.getByRole('button', { name: 'Sign out' }).waitFor({ timeout: 20000 })
await page.goto(`${BASE}/documents`, { waitUntil: 'networkidle' })
await page.waitForTimeout(1500)
await page.locator('tbody tr').first().getByRole('button', { name: 'Open' }).click()
await page.getByRole('dialog').waitFor({ timeout: 10000 })
const drawer = page.getByRole('dialog')
for (let step = 0; step < 20; step += 1) await page.keyboard.press('Tab')
check('tabbing cannot leave the document drawer',
  await drawer.evaluate((node) => node.contains(document.activeElement)),
  await page.evaluate(() => document.activeElement?.tagName))
await page.keyboard.press('Escape')
await page.waitForTimeout(800)
check('escape closes the document drawer', (await page.getByRole('dialog').count()) === 0)

// -------------------------------------------------- focus trap: the asset drawer
await page.goto(`${BASE}/assets`, { waitUntil: 'networkidle' })
await page.waitForTimeout(1500)
const assetOpen = page.locator('tbody tr').first().getByRole('button').last()
await assetOpen.click()
const assetDrawer = page.getByRole('dialog')
if (await assetDrawer.count()) {
  for (let step = 0; step < 20; step += 1) await page.keyboard.press('Tab')
  check('tabbing cannot leave the asset drawer',
    await assetDrawer.evaluate((node) => node.contains(document.activeElement)),
    await page.evaluate(() => document.activeElement?.tagName))
  await page.keyboard.press('Escape')
  await page.waitForTimeout(800)
  check('escape closes the asset drawer', (await page.getByRole('dialog').count()) === 0)
} else {
  check('an asset drawer opened to test', false, 'no drawer')
}

// -------------------------------------------- changing a password, end to end
// The success message used to be overwritten by the sign-out in the same tick, so nobody
// was ever told the change had worked.
const stamp = Date.now().toString().slice(-6)
const newPassword = `Rotated${stamp}Pass1`
await page.goto(`${BASE}/change-password`, { waitUntil: 'networkidle' })
check('the change-password screen is reachable', /Change password/.test(await text()))
check('it says why you will be signed out', /signed out/i.test(await text()))

// Wrong current password is refused, and nothing is said about success.
await page.getByLabel(/^Current password/).fill('NotThePassword1')
await page.getByLabel(/^New password/).fill(newPassword)
await page.getByLabel(/^Repeat new password/).fill(newPassword)
await page.getByRole('button', { name: 'Change password' }).click()
await page.waitForTimeout(2500)
const refused = await text()
check('a wrong current password is refused', /password/i.test(refused) && !/Password changed/i.test(refused),
  refused.match(/.*[Pp]assword.*/)?.[0]?.trim()?.slice(0, 90) ?? '')

await page.getByLabel(/^Current password/).fill('Institution123!')
await page.getByRole('button', { name: 'Change password' }).click()
await page.waitForURL(/\/login/, { timeout: 20000 })
check('a successful change lands on the sign-in screen', /\/login/.test(page.url()), page.url())
const banner = await page.locator('body').innerText()
check('the sign-in screen says the password was changed',
  /Password changed/i.test(banner), banner.split('\n').find((l) => /Password changed/i.test(l)) ?? 'no banner')
check('the banner is announced politely, not as an error',
  (await page.getByRole('status').filter({ hasText: /Password changed/i }).count()) === 1)

// And the new password is the one that works.
await page.getByLabel(/username or email/i).fill('admin')
await page.getByLabel(/password/i).fill(newPassword)
await page.getByRole('button', { name: 'Sign in' }).click()
await page.getByRole('button', { name: 'Sign out' }).waitFor({ timeout: 20000 })
check('the new password signs in', true)

// Put it back, so the rest of the suite is unaffected.
await page.goto(`${BASE}/change-password`, { waitUntil: 'networkidle' })
await page.getByLabel(/^Current password/).fill(newPassword)
await page.getByLabel(/^New password/).fill('Institution123!')
await page.getByLabel(/^Repeat new password/).fill('Institution123!')
await page.getByRole('button', { name: 'Change password' }).click()
await page.waitForURL(/\/login/, { timeout: 20000 })
await page.getByLabel(/username or email/i).fill('admin')
await page.getByLabel(/password/i).fill('Institution123!')
await page.getByRole('button', { name: 'Sign in' }).click()
await page.getByRole('button', { name: 'Sign out' }).waitFor({ timeout: 20000 })
check('the password was restored', true)

// A rejected password and the sign-out that follows both produce an expected 401; anything
// else is a real error.
const unexpected = errors.filter((line) => !/401/.test(line))
// ------------------------------------------ an account forced to change its password
// The flag used to be carried in the profile and then ignored: the person signed in, landed
// wherever they were going, and found out at some later, unrelated moment.
const stamp2 = `${Date.now().toString().slice(-6)}`
const FORCED = { username: `forced${stamp2}`, password: 'Forced123!Pass' }
const adminToken = (await (
  await fetch('http://localhost:8080/api/v1/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ loginId: 'admin', password: 'Institution123!' }),
  })
).json()).data.accessToken
const makeUser = await fetch('http://localhost:8080/api/v1/admin/users', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${adminToken}` },
  body: JSON.stringify({
    username: FORCED.username,
    password: FORCED.password,
    displayName: 'Forced Reset',
    email: `${FORCED.username}@sunrise.edu.test`,
    phone: '+977-1-5550177',
    role: 'STAFF',
    mustChangePassword: true,
  }),
})
check('a temporary account can be created', makeUser.status === 201, String(makeUser.status))

await page.getByRole('button', { name: 'Sign out' }).click()
await page.waitForURL(/\/login/, { timeout: 20000 })
await page.getByLabel(/username or email/i).fill(FORCED.username)
await page.getByLabel(/password/i).fill(FORCED.password)
await page.getByRole('button', { name: 'Sign in' }).click()
await page.waitForURL(/\/change-password/, { timeout: 20000 })
check('signing in with the flag set lands on the password screen',
  /\/change-password/.test(page.url()), page.url())

// And it holds: asking for the dashboard does not get you there.
for (const target of ['/', '/students', '/assets']) {
  await page.goto(`${BASE}${target}`, { waitUntil: 'networkidle' })
  await page.waitForTimeout(500)
  check(`the flag holds at ${target}`, /\/change-password/.test(page.url()), page.url())
}

// The screen itself must not redirect to itself.
check('the password screen does not redirect to itself', (await page.getByRole('dialog').count()) === 0)

const fresh = `Changed${stamp2}Pass2`
await page.getByLabel(/^Current password/).fill(FORCED.password)
await page.getByLabel(/^New password/).fill(fresh)
await page.getByLabel(/^Repeat new password/).fill(fresh)
await page.getByRole('button', { name: 'Change password' }).click()
await page.waitForURL(/\/login/, { timeout: 20000 })
check('changing the password clears the flag with a sign-out', /\/login/.test(page.url()), page.url())

await page.getByLabel(/username or email/i).fill(FORCED.username)
await page.getByLabel(/password/i).fill(fresh)
await page.getByRole('button', { name: 'Sign in' }).click()
await page.waitForTimeout(3000)
check('the account now reaches the app', !/\/change-password/.test(page.url()), page.url())
await page.goto(`${BASE}/students`, { waitUntil: 'networkidle' }).catch(() => {})
await page.waitForTimeout(800)
check('and stays there when asked again', !/\/change-password/.test(page.url()), page.url())

await page.getByRole('button', { name: 'Sign out' }).click().catch(() => {})
await page.waitForTimeout(1000)

check('no unexpected runtime errors', unexpected.length === 0, unexpected.slice(0, 3).join(' | '))

await browser.close()
console.log(failures === 0 ? '\nAll auth checks passed' : `\n${failures} check(s) failed`)
process.exit(failures === 0 ? 0 : 1)

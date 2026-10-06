# Education ERP — Master Blueprint & Implementation Specification

## 0. Purpose

This document is the authoritative product, architecture, domain, workflow, UX, security, deployment, and implementation specification for a commercial Education ERP.

The system must be built as a **production-ready modular monolith** that can be installed independently for a single educational institution.

The same codebase must be reusable for:

- Schools
- +2 / higher-secondary colleges
- Private colleges
- Universities
- Faculties
- Departments
- Engineering colleges
- IT colleges
- Nursing and health institutions
- Management colleges
- Technical institutions
- Other configurable educational organizations

Each deployment represents exactly **one institution**.

The product must be configurable and white-labelable so that the same software can be deployed for different institutions with:

- Institution name
- Logo
- Favicon
- Colors
- Contact details
- Academic model
- Academic year
- Programs/classes
- Faculties/departments
- Fee structures
- Grading policies
- Enabled modules
- Document templates
- Notification templates
- User roles and permissions

The implementation must avoid hardcoded institution-specific behavior.

---

# 1. Non-Negotiable Product Principles

## 1.1 Single institution per deployment

One deployment = one institution.

Do NOT implement:

- Multi-tenancy
- Tenant resolver
- Tenant ID on every entity
- Tenant middleware
- Tenant database switching
- Schema-per-tenant
- Tenant subdomains
- Tenant switching UI

Institution configuration belongs to the deployment itself.

## 1.2 Modular monolith

Use one backend application.

Preferred structure:

```text
backend
 ├── auth
 ├── institution
 ├── academic
 ├── admission
 ├── student
 ├── attendance
 ├── examination
 ├── finance
 ├── accounting
 ├── hr
 ├── communication
 ├── library
 ├── inventory
 ├── assets
 ├── documents
 ├── reporting
 ├── imports
 ├── audit
 └── common
```

Modules must be internally decoupled.

Do not split into microservices unless there is a demonstrated technical requirement.

Do not introduce Kafka simply because it is popular.

Use:

- application services
- domain services
- transactional boundaries
- internal events
- background jobs

where appropriate.

---

# 2. Product Goal

The ERP must manage the complete educational lifecycle:

```text
Institution Setup
      ↓
Academic Configuration
      ↓
Admission
      ↓
Student Creation
      ↓
Enrollment
      ↓
Course/Class Assignment
      ↓
Attendance
      ↓
Examination
      ↓
Results
      ↓
Fees
      ↓
Payments
      ↓
Accounting
      ↓
Graduation
      ↓
Alumni
```

Supporting systems:

```text
HR
Payroll
Library
Inventory
Assets
Documents
Communication
Reports
Audit
Portals
Notifications
```

The system must behave as one integrated ERP rather than a collection of unrelated CRUD screens.

---

# 3. Technology Stack

## Backend

Recommended:

- Java 21 LTS
- Spring Boot
- Spring MVC
- Spring Security
- Spring Data JPA
- Hibernate
- Bean Validation
- PostgreSQL
- Flyway
- Redis
- MapStruct
- OpenAPI / Swagger
- Actuator
- Micrometer
- Prometheus
- Grafana
- Maven or Gradle

Use DTOs between API and persistence.

Do not expose JPA entities directly.

## Frontend

Recommended:

- React
- TypeScript
- Vite
- Tailwind CSS
- TanStack Query
- React Hook Form
- Zod
- Zustand only where necessary

Use feature-based organization.

## Infrastructure

- Docker
- Docker Compose
- PostgreSQL
- Redis
- Nginx
- S3-compatible object storage
- Prometheus
- Grafana

Possible storage providers:

- AWS S3
- MinIO
- Cloudflare R2
- other S3-compatible providers

---

# 4. Environment Architecture

Support:

```text
development
staging
production
```

Never commit production secrets.

Use environment variables or a secret manager.

Examples:

```env
DATABASE_URL=
DATABASE_USERNAME=
DATABASE_PASSWORD=

JWT_SECRET=

REDIS_URL=

S3_ENDPOINT=
S3_ACCESS_KEY=
S3_SECRET_KEY=
S3_BUCKET=

SMTP_HOST=
SMTP_USERNAME=
SMTP_PASSWORD=

PAYMENT_ESEWA_ENABLED=
PAYMENT_KHALTI_ENABLED=
PAYMENT_FONEPAY_ENABLED=
```

Never hardcode:

- passwords
- JWT secrets
- API keys
- encryption keys
- payment credentials
- SMTP credentials

---

# 5. Database Principles

PostgreSQL is the primary relational database.

Flyway owns schema evolution.

Use:

```yaml
spring.jpa.hibernate.ddl-auto: validate
```

Never use:

```text
ddl-auto=create
ddl-auto=create-drop
ddl-auto=update
```

in production.

Every schema change requires a migration.

Example:

```text
V1__initial_schema.sql
V2__institution.sql
V3__users.sql
V4__academic_structure.sql
...
```

Use UUIDs where appropriate.

Use timestamps consistently.

Prefer:

```text
created_at
updated_at
created_by
updated_by
```

for auditable entities.

Use optimistic locking where concurrent editing is possible.

---

# 6. Institution Model

The institution is the root configuration object.

## Institution fields

Recommended:

```text
id
name
short_name
institution_code
institution_type
logo_url
favicon_url
primary_color
secondary_color
address
municipality
district
province
country
phone
email
website
timezone
currency
fiscal_year
academic_model
portal_title
portal_description
support_email
support_phone
setup_completed
created_at
updated_at
```

Institution types:

```text
SCHOOL
PLUS_TWO
COLLEGE
UNIVERSITY
FACULTY
TRAINING_INSTITUTE
TECHNICAL_INSTITUTE
HEALTH_INSTITUTE
OTHER
```

Academic models:

```text
SCHOOL
COLLEGE
UNIVERSITY
HYBRID
```

---

# 7. First-Run Setup Wizard

A fresh installation must NOT display a fake/demo institution.

If no institution exists:

```text
/login
```

must redirect to:

```text
/setup
```

The setup wizard should contain:

1. Institution Information
2. Academic Model
3. Contact and Address
4. Branding
5. Academic Year
6. Academic Structure
7. Initial Administrator
8. Optional Modules
9. Review
10. Complete

After completion:

```text
setup_completed = true
```

Normal users can then log in.

Do not allow ordinary users to bypass setup.

Setup must be transactional where practical.

---

# 8. White Labeling

The UI must dynamically use:

- institution name
- short name
- logo
- favicon
- colors
- portal title
- support email
- support phone
- address

Browser title example:

```text
Student Portal | Example College
```

Documents must dynamically use institution information.

Generated documents include:

- invoices
- receipts
- admission letters
- report cards
- marksheets
- transcripts
- certificates
- payslips
- notices

No document may contain a hardcoded institution name.

---

# 9. Public Institution API

Provide a safe public endpoint such as:

```http
GET /api/v1/public/institution
```

Expose only non-sensitive information.

Example:

```json
{
  "name": "Example College",
  "shortName": "EC",
  "institutionType": "COLLEGE",
  "logoUrl": "/storage/public/logo.png",
  "faviconUrl": "/storage/public/favicon.ico",
  "primaryColor": "#123456",
  "secondaryColor": "#abcdef",
  "portalTitle": "Student Portal",
  "supportEmail": "support@example.edu.np",
  "supportPhone": "+977..."
}
```

Never expose:

- secrets
- database information
- JWT settings
- internal configuration
- admin users
- private documents

---

# 10. Authentication

Authentication must support:

- username/email/login ID
- password
- password change
- forgot password
- reset password
- refresh tokens/session management
- logout
- account lockout
- failed-login tracking
- optional MFA readiness

Password hashing:

```text
BCrypt
```

or:

```text
Argon2
```

Never store plaintext passwords.

---

# 11. Roles

Initial roles:

```text
SUPER_ADMIN
INSTITUTION_ADMIN
PRINCIPAL
VICE_PRINCIPAL
HOD
TEACHER
ACCOUNTANT
HR
LIBRARIAN
RECEPTIONIST
EXAM_CONTROLLER
STUDENT
PARENT
STAFF
```

The system must support custom roles later.

Permissions should be granular.

Example:

```text
STUDENT_READ
STUDENT_CREATE
STUDENT_UPDATE
STUDENT_DELETE

ADMISSION_READ
ADMISSION_APPROVE
ADMISSION_REJECT

FEE_READ
FEE_CREATE
PAYMENT_CREATE
REFUND_APPROVE

RESULT_ENTER
RESULT_VERIFY
RESULT_APPROVE
RESULT_PUBLISH
RESULT_CORRECT
```

Backend authorization is authoritative.

---

# 12. Login UX

The login page should show institution branding.

Structure:

```text
[Institution Logo]

Institution Name

Portal Title

[Admin / Staff]
[Student / Parent]

Login ID
Password

[Remember Me]

Forgot Password?

Login

Support contact
```

The selector is UX only.

It must not grant permissions.

Backend roles determine actual access.

Do not create fake:

- signup
- online examination
- student registration
- parent registration

unless those workflows actually exist.

---

# 13. Academic Structure

The academic model must support both school and higher education.

## School mode

```text
Institution
 └── Academic Year
      └── Class
           └── Section
                └── Subject
                     └── Teacher
```

Example:

```text
Grade 10
 ├── Section A
 ├── Section B
 └── Section C
```

## College/University mode

```text
Institution
 └── Faculty
      └── Department
           └── Program
                └── Program Version
                     └── Curriculum
                          └── Semester/Term
                               └── Course
                                    └── Course Offering
```

---

# 14. Academic Entities

Required entities include:

```text
Campus
Faculty
Department
Program
ProgramVersion
Curriculum
AcademicYear
Semester
Term
Class
Section
Subject
Course
CourseOffering
Room
AcademicCalendar
Holiday
WorkingDay
```

Additional entities may include:

```text
Batch
Intake
CoursePrerequisite
CourseEquivalence
ElectiveGroup
ProgramCourse
```

---

# 15. Program

Program fields:

```text
id
code
name
level
duration
department_id
status
```

Examples:

```text
BCA
BBA
BIT
B.Tech
MBA
MCA
+2 Science
+2 Management
```

Do not hardcode programs.

---

# 16. Program Version

Programs change over time.

Therefore:

```text
Program
 └── ProgramVersion
```

Example:

```text
BCA
 ├── 2024 Curriculum
 ├── 2025 Curriculum
 └── 2026 Curriculum
```

Students should remain associated with the curriculum applicable to their batch.

---

# 17. Curriculum

Curriculum must support:

- mandatory courses
- electives
- credits
- prerequisites
- semester/term
- internal/external evaluation
- grading configuration

Example:

```text
Semester 1
 ├── Programming
 ├── Mathematics
 ├── Digital Logic
 └── Communication
```

---

# 18. Course Offering

A course offering represents a real teaching instance.

Example:

```text
Course:
CS101 Programming

Offering:
CS101
BCA
Semester 1
2026
Section A
Teacher: John
Room: Lab 1
```

The UI must not expose irrelevant fields.

Do not show:

```text
Semester
School Class
```

simultaneously when only one applies.

The frontend should know the configured academic model.

---

# 19. Student Master Record

The Student entity is the central source of truth.

Student fields:

```text
id
student_number
first_name
middle_name
last_name
date_of_birth
gender
nationality
phone
email
address
photo_url
status
admission_id
created_at
updated_at
```

Student number must be generated by the server.

Example:

```text
BCA-2026-00124
```

Format should be configurable.

Statuses:

```text
APPLICANT
ACTIVE
SUSPENDED
TRANSFERRED
WITHDRAWN
GRADUATED
ALUMNI
```

---

# 20. Student 360

Every student should have a centralized profile.

Tabs:

```text
Overview
Academic
Attendance
Courses
Exams
Results
Fees
Payments
Documents
Guardians
Communication
Activity
```

The Student 360 page should be the main operational interface.

---

# 21. Guardians

Support:

```text
Student
 ├── Father
 ├── Mother
 ├── Guardian
 └── Other
```

One parent may have multiple children.

Do not assume:

```text
one parent = one student
```

Parent-child relationships must be explicit.

---

# 22. Admissions

Admissions must support:

```text
Admission Campaign
Application
Applicant
Application Document
Eligibility
Entrance Exam
Merit
Selection
Waitlist
Offer
Admission Approval
Fee Assessment
Student Creation
Enrollment
```

Application lifecycle:

```text
DRAFT
SUBMITTED
UNDER_REVIEW
DOCUMENT_VERIFICATION
ELIGIBILITY
ENTRANCE
SELECTED
WAITLISTED
REJECTED
ADMITTED
ENROLLED
```

---

# 23. Admission Workflow

Expected flow:

```text
Create Campaign
      ↓
Application Submitted
      ↓
Document Verification
      ↓
Eligibility Check
      ↓
Entrance / Merit
      ↓
Selection
      ↓
Offer
      ↓
Approval
      ↓
Fee Assessment
      ↓
Payment
      ↓
Student Creation
      ↓
Enrollment
```

The system must not require manual duplicate data entry.

Approved applicants should become students through a controlled workflow.

---

# 24. Documents

Admissions may require:

- citizenship/passport
- previous certificates
- transcript
- photograph
- character certificate
- migration certificate
- birth certificate
- other configurable documents

Document requirements must be configurable.

---

# 25. Attendance

Support:

```text
Daily attendance
Period attendance
Course attendance
Bulk attendance
Attendance correction
Attendance approval
Attendance reports
```

Statuses:

```text
PRESENT
ABSENT
LATE
EXCUSED
```

Attendance correction should be audited.

---

# 26. Attendance Notifications

Examples:

```text
Student absent
Repeated absence
Attendance below threshold
```

Parent notifications may be sent through:

- in-app
- email
- SMS
- push
- WhatsApp if configured

---

# 27. Examination

Core entities:

```text
Examination
ExamType
ExamSchedule
ExamSubject
ExamEligibility
ExamAttendance
MarkEntry
Grade
GradeBoundary
Result
ReportCard
Marksheet
Transcript
Certificate
```

Result workflow:

```text
DRAFT
MARKS_ENTERED
VERIFIED
APPROVED
PUBLISHED
```

Published results must not be silently modified.

---

# 28. Result Correction

If a published result needs correction:

```text
Correction Request
      ↓
Authorization
      ↓
Change
      ↓
Audit
      ↓
Recalculation
      ↓
Approval
      ↓
Republish
```

Record:

```text
old value
new value
reason
actor
timestamp
approval
```

---

# 29. Grading

Do not hardcode grading systems.

Support:

- percentage
- letter grade
- GPA
- CGPA
- credit-based grading
- pass/fail
- configurable grade boundaries

Example:

```text
A+
A
B+
B
C+
C
D
F
```

Boundaries must be institution-configurable.

---

# 30. Finance

Core entities:

```text
FeeStructure
FeeComponent
StudentFeeAssessment
Invoice
InvoiceItem
Payment
Refund
Discount
Scholarship
Fine
```

Support:

- fixed fees
- installments
- due dates
- partial payments
- discounts
- scholarships
- fines
- waivers
- refunds

---

# 31. Payment Methods

Nepal-focused payment methods:

```text
CASH
BANK_TRANSFER
CHEQUE
ESEWA
KHALTI
FONEPAY
```

Architecture must use a provider abstraction.

Example:

```text
PaymentProvider
 ├── EsewaPaymentProvider
 ├── KhaltiPaymentProvider
 ├── FonepayPaymentProvider
 └── ManualPaymentProvider
```

Payment callbacks must be:

- server verified
- authenticated where supported
- idempotent
- audited

Never trust frontend payment status.

---

# 32. Accounting

Accounting module should include:

```text
ChartOfAccounts
AccountGroup
Account
JournalEntry
JournalLine
FiscalYear
AccountingPeriod
BankAccount
Expense
Income
Receivable
Payable
Ledger
TrialBalance
BankReconciliation
```

Confirmed financial events should create accounting records.

Example:

```text
Student Payment
      ↓
Payment Confirmation
      ↓
Accounting Journal Entry
```

---

# 33. HR

Core entities:

```text
Employee
Department
Designation
Employment
Qualification
EmployeeDocument
Leave
EmployeeAttendance
SalaryStructure
Payroll
Payslip
```

Employee profile should include:

- personal data
- employment data
- qualifications
- documents
- department
- designation
- salary
- attendance
- leave

---

# 34. Payroll

Support:

```text
Basic Salary
Allowances
Deductions
Overtime
Bonus
Loan
Advance
Tax
Net Salary
```

Payroll must be configurable.

Do not hardcode tax rates.

Tax rules should be versioned/configurable.

---

# 35. Communication

Channels:

```text
IN_APP
EMAIL
SMS
PUSH
WHATSAPP
```

Events:

```text
AttendanceMarkedAbsent
FeeDue
PaymentCompleted
ResultPublished
AdmissionApproved
LeaveApproved
NoticePublished
```

Use templates.

Example:

```text
Hello {{studentName}},

Your fee payment of {{amount}} has been received.

Receipt: {{receiptNumber}}
```

---

# 36. Parent Portal

Parent can access authorized children.

Features:

```text
Children
Attendance
Fees
Payments
Receipts
Results
Timetable
Notices
Documents
Notifications
```

Parent must never access another student's data.

Object-level authorization is mandatory.

---

# 37. Student Portal

Features:

```text
Profile
Academic Information
Courses
Timetable
Attendance
Exams
Results
Fees
Payments
Documents
Notices
Notifications
```

Students may only access their own information.

---

# 38. Teacher Portal

Features:

```text
Today's Classes
Assigned Courses
Students
Attendance
Marks
Timetable
Notices
Leave
Notifications
Course Materials
```

Teachers can only access assigned classes/courses unless granted broader permissions.

---

# 39. Library

Entities:

```text
Book
BookCopy
Author
Publisher
Category
LibraryMember
Issue
Return
Renewal
Reservation
LibraryFine
```

Workflow:

```text
Book Added
      ↓
Copy Registered
      ↓
Member Borrows
      ↓
Issue
      ↓
Return / Renew
      ↓
Fine if applicable
```

Barcode support should be practical.

---

# 40. Inventory

Entities:

```text
Item
ItemCategory
Store
Stock
StockMovement
StockIssue
StockTransfer
Purchase
```

Support:

- purchase
- receiving
- issue
- return
- transfer
- adjustment
- low-stock alerts

Inventory movements must be auditable.

---

# 41. Assets

Asset fields:

```text
asset_number
name
category
serial_number
purchase_date
purchase_cost
location
department
assigned_person
warranty_expiry
status
```

Statuses:

```text
AVAILABLE
ASSIGNED
IN_MAINTENANCE
LOST
DISPOSED
```

---

# 42. Document Management

Documents must support:

- upload
- download
- preview
- metadata
- versioning
- access control
- verification
- expiry
- deletion policy

Store files in object storage.

Do not store large binary files directly in PostgreSQL unless there is a specific reason.

Documents are private by default.

Validate:

- MIME type
- extension
- size
- filename
- content where appropriate

---

# 43. Reporting

Reports should include:

## Students

- student list
- active students
- demographic reports
- enrollment reports
- graduation reports

## Attendance

- daily attendance
- monthly attendance
- student attendance
- class attendance
- attendance shortage

## Examination

- marksheets
- report cards
- grade sheets
- transcripts
- GPA/CGPA
- result summaries

## Finance

- collections
- outstanding
- invoices
- payments
- refunds
- discounts
- scholarships
- receivables

## HR

- employee list
- attendance
- leave
- payroll
- payslips

## Inventory

- stock
- movement
- low stock
- purchase
- issue

## Library

- issued books
- overdue books
- fines
- circulation

Exports:

```text
CSV
XLSX
PDF
```

---

# 44. Excel Import System

Import workflow:

```text
Upload
   ↓
Map Columns
   ↓
Validate
   ↓
Duplicate Detection
   ↓
Preview
   ↓
Confirm
   ↓
Import
   ↓
Import Report
```

Supported imports:

```text
Students
Guardians
Employees
Courses
Fees
Inventory
Books
```

Never immediately import unvalidated spreadsheets.

Show row-level errors.

Example:

```text
Row 42:
Invalid date of birth

Row 58:
Duplicate student email
```

---

# 45. Global Search

Search across authorized data:

```text
Students
Employees
Applicants
Courses
Invoices
Payments
Books
Assets
```

Search must respect authorization.

Example:

A teacher searching for a student must not see unrelated financial information.

---

# 46. Audit Logging

Audit:

```text
LOGIN
LOGOUT
CREATE
UPDATE
DELETE
APPROVE
REJECT
PUBLISH
PAYMENT
REFUND
RESULT_CHANGE
PERMISSION_CHANGE
PASSWORD_CHANGE
SECURITY_EVENT
```

Audit fields:

```text
actor
action
entity
entityId
timestamp
IP
userAgent
before
after
```

Never store:

- passwords
- tokens
- secret keys

---

# 47. Dashboards

Dashboards must provide operational information.

## Principal

- total students
- attendance
- admissions
- fee collection
- outstanding
- results
- staff
- alerts

## Accountant

- today's collection
- outstanding fees
- payments
- refunds
- expenses
- receivables

## Teacher

- today's classes
- attendance
- pending marks
- timetable

## Student

- courses
- attendance
- fees
- examinations
- results
- notices

## Parent

- children
- attendance
- fees
- results
- notices

Do not use decorative charts without business meaning.

---

# 48. Module Configuration

Feature flags/configuration:

```text
SCHOOL_MODE
COLLEGE_MODE
UNIVERSITY_MODE

LIBRARY_ENABLED
PAYROLL_ENABLED
HOSTEL_ENABLED
TRANSPORT_ENABLED
ONLINE_EXAM_ENABLED
PLACEMENT_ENABLED
RESEARCH_ENABLED
ALUMNI_ENABLED
```

Only expose modules that are actually implemented and enabled.

Do not display fake "Coming Soon" features as if they were operational.

---

# 49. Nepal Localization

Default currency:

```text
NPR
```

Support:

- Nepal phone numbers
- Nepal provinces
- districts
- municipalities
- Nepal-focused payments
- AD calendar
- architecture capable of BS/Bikram Sambat
- configurable fiscal year

Do not hardcode a foreign academic or financial system.

---

# 50. Academic Policy Configuration

Institutions may differ in:

- semester/year system
- internal/external marks
- grading
- attendance thresholds
- fee deadlines
- refund rules
- no-dues requirements
- examination eligibility
- promotion rules

These must be configuration/policy driven.

Do not hardcode one institution's rules globally.

---

# 51. Transaction Boundaries

Use database transactions for critical workflows:

```text
Admission Approval
Student Creation
Enrollment
Fee Assessment
Payment
Refund
Accounting Entry
Result Publishing
Result Correction
Payroll
Inventory Movement
```

Example:

```text
Payment
 ├── payment record
 ├── invoice update
 ├── accounting entry
 └── audit event
```

These operations should not partially succeed.

---

# 52. Background Jobs

Use background processing for:

```text
Email
SMS
Notifications
Reports
Fee reminders
Payment reconciliation
Document processing
Imports
Cleanup
Scheduled alerts
```

Jobs must be:

- idempotent
- retryable
- observable
- failure-safe

---

# 53. Redis

Use Redis only where it provides real value.

Possible uses:

```text
Refresh sessions
Rate limiting
Temporary state
Short-lived configuration cache
Reference-data cache
Job coordination
```

Do not cache everything blindly.

---

# 54. API Standards

Base path:

```text
/api/v1
```

Examples:

```http
GET    /api/v1/students
POST   /api/v1/students
GET    /api/v1/students/{id}
PUT    /api/v1/students/{id}
DELETE /api/v1/students/{id}
```

Use pagination.

Example:

```text
?page=0&size=20&sort=createdAt,desc
```

Use filters where useful.

---

# 55. API Response Standards

Successful response:

```json
{
  "data": {},
  "message": "Success"
}
```

Pagination:

```json
{
  "data": [],
  "page": 0,
  "size": 20,
  "totalElements": 120,
  "totalPages": 6
}
```

Error:

```json
{
  "timestamp": "...",
  "status": 400,
  "code": "VALIDATION_ERROR",
  "message": "Please correct the highlighted fields.",
  "fieldErrors": {
    "email": "Invalid email address."
  },
  "path": "/api/v1/students"
}
```

Do not leak internal exceptions.

---

# 56. Frontend Error Handling

Never display raw backend validation messages when they are intended for developers.

Bad:

```text
Provide exactly one of programId (college/university mode) or schoolClassId (school mode)
```

Good:

```text
Program
[ Select program ]

Class
[ Select class ]
```

based on the institution's academic mode.

User-facing messages should be human-readable.

---

# 57. Frontend Form Principles

Every form must include:

- labels
- required indicators
- validation
- loading state
- disabled submit while saving
- server validation
- success state
- error state
- cancel behavior
- unsaved-change handling where needed

Do not build forms that simply dump database fields onto the screen.

---

# 58. Empty States

Every list must have an intentional empty state.

Example:

```text
No students found.

Try changing your filters or add your first student.
```

Provide appropriate action:

```text
Add Student
```

---

# 59. Loading States

Use:

- skeletons
- spinners for actions
- disabled buttons
- progress indicators

Never freeze the interface.

---

# 60. Permission-Aware UI

Buttons must respect permissions.

Example:

If user lacks:

```text
STUDENT_DELETE
```

do not show Delete.

However, backend authorization must still enforce it.

Frontend hiding is not security.

---

# 61. Security

Implement:

- Spring Security
- BCrypt/Argon2
- JWT/session security
- refresh token rotation where applicable
- CSRF strategy appropriate to authentication model
- CORS restrictions
- rate limiting
- login throttling
- secure cookies where used
- security headers
- input validation
- output encoding
- object-level authorization
- audit logging

Prevent:

```text
IDOR
Privilege Escalation
SQL Injection
XSS
CSRF
Broken Access Control
Mass Assignment
Sensitive Data Exposure
```

---

# 62. Object-Level Authorization

Never assume:

```text
authenticated = authorized
```

Example:

```http
GET /students/{id}
```

must verify that the user can access that student.

Parent:

```text
Parent → Child A
```

must not access:

```text
Child B
```

Teacher:

```text
Teacher → Assigned Course
```

must not automatically access all courses.

---

# 63. File Security

Uploaded files should have:

- randomized storage names
- controlled access
- MIME validation
- size validation
- virus scanning where practical
- private buckets by default
- signed URLs for temporary access

Never trust a filename extension.

---

# 64. Observability

Expose:

```text
/actuator/health
```

and appropriate metrics.

Use:

```text
Micrometer
Prometheus
Grafana
```

Monitor:

- request latency
- error rate
- DB pool
- JVM
- memory
- CPU
- background jobs
- Redis
- storage
- payment failures

Do not expose sensitive actuator details publicly.

---

# 65. Logging

Use structured logs.

Include:

```text
timestamp
level
service
requestId
userId when appropriate
endpoint
duration
status
```

Never log:

```text
password
JWT
refresh token
API key
payment secret
private document contents
```

---

# 66. Backups

Production database must have automated backups.

At minimum:

```text
Daily backup
Retention policy
Off-site backup
Restore testing
```

A backup that has never been restored should not be considered verified.

Object storage backups should also be considered.

---

# 67. Deployment

Production architecture:

```text
Internet
   ↓
Nginx / Load Balancer
   ↓
Frontend
   ↓
Spring Boot Backend
   ↓
PostgreSQL
   ↓
Redis
   ↓
Object Storage
```

Monitoring:

```text
Prometheus
Grafana
```

---

# 68. Docker

Provide production-oriented Dockerfiles.

Use multi-stage builds.

Do not ship development tooling in production containers.

Example:

```text
build stage
   ↓
runtime stage
```

Keep images small.

---

# 69. Configuration Management

Configuration categories:

```text
System
Institution
Academic
Finance
Security
Notifications
Payments
Documents
Modules
```

Institution-specific settings must be stored/configured, not compiled into source code.

---

# 70. Document Templates

Templates should support variables:

```text
{{institution.name}}
{{institution.address}}
{{student.name}}
{{student.studentNumber}}
{{program.name}}
{{academicYear.name}}
{{amount}}
{{date}}
```

Use templates for:

- receipts
- invoices
- certificates
- report cards
- marksheets
- transcripts
- admission letters
- payslips

---

# 71. Notification Templates

Templates must be editable.

Example:

```text
Payment Received

Dear {{studentName}},

Payment of NPR {{amount}} has been successfully received.

Receipt No: {{receiptNumber}}

{{institution.name}}
```

---

# 72. Setup for a New Customer

Commercial onboarding should be:

```text
Deploy
 ↓
Open URL
 ↓
Setup Wizard
 ↓
Institution Identity
 ↓
Branding
 ↓
Academic Model
 ↓
Academic Year
 ↓
Academic Structure
 ↓
Admin
 ↓
Users
 ↓
Fees
 ↓
Grading
 ↓
Notifications
 ↓
Import Existing Data
 ↓
Go Live
```

No source-code modification should be necessary for normal branding/configuration.

---

# 73. Existing Student Import

A new institution should be able to import existing records.

Example:

```text
students.xlsx
```

Map:

```text
Full Name → firstName/lastName
DOB → dateOfBirth
Email → email
Phone → phone
Program → program
Batch → batch
```

Validate all rows before committing.

---

# 74. Data Integrity

Use database constraints where appropriate.

Examples:

- unique institution code
- unique student number
- unique email where required
- unique course code within relevant scope
- no duplicate enrollment
- no duplicate payment reference
- no negative stock without explicit adjustment policy

Use foreign keys.

Avoid unnecessary nullable relationships.

---

# 75. Soft Delete

Do not blindly soft-delete everything.

Use soft delete only where business requirements justify retention.

Financial records generally must remain immutable/retained.

Use statuses instead of deletion where appropriate.

---

# 76. Financial Immutability

Do not delete confirmed payments.

Do not overwrite accounting history.

Use:

```text
refund
reversal
adjustment
correction
```

instead of destructive mutation.

---

# 77. Result Immutability

Published results must be protected.

Corrections require:

```text
reason
authorization
audit
```

---

# 78. Payment Idempotency

Payment operations must use idempotency.

Example:

```text
providerTransactionId
paymentReference
idempotencyKey
```

The same callback must not create two payments.

---

# 79. Admission Idempotency

Approval operations must be safe against repeated requests.

Do not create duplicate students if an approval request is retried.

---

# 80. Inventory Integrity

Stock should be derived from movements or controlled transaction logic.

Example:

```text
Opening Stock
+ Purchase
+ Return
- Issue
- Damage
± Adjustment
= Current Stock
```

---

# 81. Academic Enrollment Integrity

Prevent:

- duplicate enrollment
- enrollment in inactive program
- enrollment into invalid semester
- missing prerequisite where enforced
- course over-capacity
- conflicting course selection

---

# 82. Timetable

Support:

```text
Day
Time Slot
Room
Teacher
Course/Class
Section
```

Prevent:

```text
Teacher conflict
Room conflict
Section conflict
```

---

# 83. Academic Calendar

Support:

- academic year
- semester/term dates
- holidays
- examination periods
- admission periods
- registration periods
- working days

These dates should drive workflows.

---

# 84. No-Dues

Institutions may require no-dues before:

- examination
- certificate issuance
- graduation
- transcript
- clearance

This should be configurable.

---

# 85. Graduation

Graduation workflow may include:

```text
Eligibility
 ↓
Credit Completion
 ↓
Fee Clearance
 ↓
Library Clearance
 ↓
Department Clearance
 ↓
Exam/Result Completion
 ↓
Approval
 ↓
Certificate
 ↓
Alumni
```

---

# 86. Alumni

Optional module.

Support:

- alumni profile
- graduation year
- program
- employment
- contact
- communication
- events

---

# 87. Hostel

Optional module.

Potential entities:

```text
Hostel
Building
Room
Bed
Allocation
Fee
CheckIn
CheckOut
```

Only enable if implemented.

---

# 88. Transport

Optional module.

Potential entities:

```text
Vehicle
Driver
Route
Stop
StudentTransportAssignment
Trip
```

---

# 89. Placement

Optional module.

Potential entities:

```text
Company
Job
PlacementDrive
Application
Interview
Offer
Placement
```

---

# 90. Research

Optional university module.

Potential entities:

```text
ResearchProject
Researcher
Publication
Conference
Grant
Patent
ResearchDocument
```

---

# 91. Online Examination

Optional.

If implemented:

- question bank
- examination
- attempt
- timer
- answer
- evaluation
- result

Never display this module unless actually implemented.

---

# 92. AI Features

AI must remain assistive.

Possible future features:

- report summarization
- document classification
- admission FAQ assistant
- analytics explanation
- natural-language report queries

AI must never bypass:

- permissions
- payment controls
- result approval
- accounting controls
- authorization
- audit

---

# 93. API Documentation

Use OpenAPI.

Document:

- authentication
- request models
- response models
- errors
- pagination
- permissions
- examples

Keep Swagger available in development/staging and protect appropriately in production.

---

# 94. Testing Strategy

Tests must exist at multiple levels.

## Unit

Test:

- services
- validators
- calculations
- policy rules

## Integration

Test:

- database
- repositories
- security
- API
- Flyway migrations

## End-to-End

Test actual workflows.

---

# 95. Critical E2E Workflows

At minimum:

### Institution setup

```text
Fresh DB
→ Setup Wizard
→ Institution Created
→ Admin Created
→ Login
```

### Admission

```text
Applicant
→ Application
→ Verification
→ Approval
→ Fee Assessment
→ Payment
→ Student
→ Enrollment
```

### Attendance

```text
Teacher
→ Course
→ Attendance
→ Save
→ Parent sees absence
```

### Examination

```text
Exam
→ Marks
→ Verification
→ Approval
→ Publish
→ Student sees result
```

### Finance

```text
Fee
→ Invoice
→ Payment
→ Receipt
→ Accounting Entry
```

### Parent

```text
Parent
→ Child
→ Attendance
→ Fee
→ Result
```

---

# 96. Test Environment

If tests fail because of environment limitations such as Mockito/Byte Buddy self-attach on macOS, distinguish:

```text
application failure
```

from:

```text
test infrastructure failure
```

Do not falsely report the product as passing.

---

# 97. Definition of Done

A feature is NOT complete merely because:

```text
Entity exists
Repository exists
Controller exists
Frontend page exists
```

A complete feature requires:

```text
Database
↓
Domain model
↓
Business rules
↓
Service
↓
Repository
↓
DTO
↓
Validation
↓
Authorization
↓
API
↓
Frontend
↓
Loading
↓
Empty state
↓
Error state
↓
Audit
↓
Tests
↓
E2E workflow
```

---

# 98. Development Phases

## Phase 0 — Full Audit

Inspect:

- backend
- frontend
- database
- migrations
- security
- configuration
- routes
- UI
- existing modules
- tests
- deployment

Produce:

```text
Working
Broken
Incomplete
Fake
Duplicated
Unsafe
Missing
```

Do not rewrite blindly.

---

## Phase 1 — Foundation

Implement:

- common response
- errors
- validation
- auditing foundation
- configuration
- DB conventions
- security foundation
- API conventions

---

## Phase 2 — Institution Setup

Implement:

- Institution
- first-run wizard
- branding
- public branding API
- module configuration
- academic model
- setup completion

---

## Phase 3 — Authentication

Implement:

- users
- roles
- permissions
- login
- logout
- refresh
- password reset
- account lockout
- audit

---

## Phase 4 — Academic

Implement:

- campus
- faculty
- department
- program
- program version
- curriculum
- academic year
- semester
- class
- section
- subject/course
- course offering
- timetable
- calendar

---

## Phase 5 — Admissions and Students

Implement:

- campaigns
- applications
- documents
- verification
- selection
- admission
- fee assessment
- student creation
- enrollment
- student 360
- guardian relationships

---

## Phase 6 — Attendance and Examination

Implement:

- attendance
- corrections
- exams
- marks
- grading
- approval
- publishing
- report cards
- transcripts

---

## Phase 7 — Finance and Accounting

Implement:

- fees
- invoices
- payments
- refunds
- discounts
- scholarships
- accounting
- ledger
- reconciliation

---

## Phase 8 — HR and Payroll

Implement:

- employee
- attendance
- leave
- salary
- payroll
- payslip

---

## Phase 9 — Communication and Portals

Implement:

- notifications
- templates
- student portal
- parent portal
- teacher portal

---

## Phase 10 — Library, Inventory, Assets, Documents

Implement:

- library
- inventory
- assets
- document management

---

## Phase 11 — Reports and Imports

Implement:

- reporting
- exports
- Excel imports
- dashboards
- global search

---

## Phase 12 — Hardening

Perform:

- security audit
- performance testing
- authorization audit
- backup testing
- migration testing
- E2E tests
- deployment validation
- observability validation
- production readiness review

---

# 99. Agent Execution Rules

AI coding agents must follow these rules.

## Rule 1

Read the existing project before changing it.

## Rule 2

Do not assume a missing feature does not exist.

Search first.

## Rule 3

Do not duplicate entities.

Reuse existing domain models if correct.

## Rule 4

Do not break existing working functionality without a reason.

## Rule 5

Do not create fake implementations.

## Rule 6

Do not leave TODO placeholders for critical ERP functionality.

## Rule 7

Do not expose raw backend errors to users.

## Rule 8

Do not skip authorization.

## Rule 9

Do not skip database migrations.

## Rule 10

Do not use `ddl-auto=update`.

## Rule 11

Do not seed fake demo data into production.

## Rule 12

Do not hardcode institution-specific information.

## Rule 13

Do not introduce microservices.

## Rule 14

Do not introduce multi-tenancy.

## Rule 15

Do not add technologies without a concrete reason.

---

# 100. Required Agent Workflow

For every feature:

```text
1. Inspect existing implementation
2. Identify gaps
3. Design domain changes
4. Design DB migration
5. Implement backend
6. Implement security
7. Implement API
8. Implement frontend
9. Add loading/error/empty states
10. Add audit
11. Add tests
12. Run build
13. Run tests
14. Inspect UI
15. Fix errors
16. Verify E2E workflow
17. Only then continue
```

---

# 101. Phase Gates

Do not move to the next phase while the current phase contains:

- compilation errors
- migration failures
- broken routes
- broken authentication
- obvious authorization vulnerabilities
- unfinished critical workflows
- fake functionality
- raw backend errors
- unusable UI

---

# 102. UI Quality Standard

The frontend must look like a serious enterprise application.

Avoid:

- generic CRUD tables everywhere
- excessive cards
- meaningless charts
- giant empty dashboards
- placeholder buttons
- fake statistics
- fake status labels
- hardcoded institution names
- developer-oriented messages

Prefer:

- operational dashboards
- clear navigation
- searchable tables
- filters
- pagination
- detail pages
- contextual actions
- bulk actions
- confirmation dialogs
- responsive layouts
- consistent typography
- accessible controls

---

# 103. Navigation

Navigation should be module-aware.

Example:

```text
Dashboard

Admissions
  Applications
  Campaigns
  Documents

Students
  All Students
  Enrollment
  Guardians

Academics
  Programs
  Courses
  Classes
  Timetable
  Calendar

Attendance

Examinations
  Exams
  Marks
  Results

Finance
  Fees
  Invoices
  Payments
  Refunds

Accounting

HR
  Employees
  Attendance
  Leave
  Payroll

Library

Inventory

Assets

Documents

Reports

Communication

Settings
```

Only display modules enabled for the institution.

---

# 104. Search and Filters

Tables should support:

- search
- filter
- sort
- pagination
- column visibility where useful
- export
- bulk operations where appropriate

Avoid fetching thousands of records unnecessarily.

---

# 105. Performance

Optimize:

- N+1 queries
- unnecessary joins
- pagination
- indexes
- expensive reports
- repeated configuration lookup

Use database indexes on:

- foreign keys
- unique business identifiers
- frequently searched fields

Do not add indexes blindly.

---

# 106. Caching

Cache only stable/read-heavy data.

Possible:

```text
Institution branding
Reference data
Configuration
```

Invalidate cache when configuration changes.

Never cache authorization decisions indefinitely.

---

# 107. Concurrency

Use optimistic locking where needed.

Examples:

- fee assessment
- payment processing
- inventory
- result publishing
- configuration editing

Prevent double submission.

---

# 108. Security Audit Checklist

Verify:

```text
Authentication
Authorization
IDOR
Privilege escalation
Password security
Session security
JWT security
CSRF
CORS
XSS
SQL injection
File upload
Rate limiting
Sensitive logging
Secret exposure
API enumeration
```

---

# 109. Production Checklist

Before release:

```text
[ ] Production secrets externalized
[ ] Database backups enabled
[ ] Restore tested
[ ] Flyway migrations tested
[ ] ddl-auto=validate
[ ] HTTPS enabled
[ ] Secure cookies configured where applicable
[ ] CORS restricted
[ ] Rate limiting configured
[ ] Logs sanitized
[ ] Actuator protected
[ ] Monitoring configured
[ ] Error tracking configured
[ ] Object storage configured
[ ] Email configured
[ ] Payment callbacks verified
[ ] E2E tests pass
[ ] Frontend build passes
[ ] Backend build passes
[ ] No demo data
[ ] No fake features
[ ] Institution setup tested
```

---

# 110. Commercial Rebranding

For a new customer, configuration should be sufficient for:

```text
Institution Name
Logo
Favicon
Colors
Contact Information
Academic Model
Programs
Departments
Classes
Fees
Grading
Academic Calendar
Modules
Templates
```

The source code should not need institution-specific modifications.

---

# 111. Example Customer Profiles

## School

```text
Academic Model = SCHOOL
Modules:
Students
Admissions
Classes
Sections
Subjects
Attendance
Exams
Fees
Parent Portal
Library
```

## College

```text
Academic Model = COLLEGE
Modules:
Admissions
Faculties
Departments
Programs
Semesters
Courses
Enrollment
Attendance
Exams
Fees
Accounting
Student Portal
Library
```

## University

```text
Academic Model = UNIVERSITY
Modules:
Faculties
Departments
Programs
Program Versions
Curriculum
Credits
Electives
Semester
Enrollment
Exams
Results
Finance
Accounting
HR
Research
Library
Student Portal
Parent Portal where applicable
```

---

# 112. Example Student Lifecycle

```text
Applicant
   ↓
Application
   ↓
Document Verification
   ↓
Eligible
   ↓
Selected
   ↓
Admission Approved
   ↓
Fee Assessment
   ↓
Payment
   ↓
Student Created
   ↓
Enrollment
   ↓
Course Registration
   ↓
Attendance
   ↓
Examination
   ↓
Result
   ↓
Promotion
   ↓
Next Semester
   ↓
Graduation
   ↓
Alumni
```

The system should preserve the lifecycle history.

---

# 113. Source of Truth Principle

Avoid duplicating the same fact in multiple places.

Examples:

Student identity:

```text
Student
```

Academic enrollment:

```text
Enrollment
```

Payment:

```text
Payment
```

Accounting:

```text
JournalEntry
```

Do not maintain separate contradictory copies.

---

# 114. Business Rules Must Live in Services

Do not put important business rules only in:

- controllers
- React components
- database triggers

Business rules should primarily exist in domain/application services with database constraints supporting integrity.

---

# 115. DTO Design

Example:

```java
public record CreateStudentRequest(
    String firstName,
    String lastName,
    LocalDate dateOfBirth,
    String email,
    String phone
) {}
```

Response:

```java
public record StudentResponse(
    UUID id,
    String studentNumber,
    String firstName,
    String lastName,
    String status
) {}
```

Never return:

```java
StudentEntity
```

directly.

---

# 116. Validation

Validate:

- required fields
- formats
- lengths
- ranges
- relationships
- business constraints

Example:

```text
email format
phone format
date not future
fee amount >= 0
credit > 0
```

Business validation belongs in services.

---

# 117. Database Migration Rules

Every schema change:

```text
migration
→ application
→ validation
→ tests
```

Never manually alter production schema outside controlled migration procedures.

---

# 118. Demo Data

Development may have fixtures.

Production must not depend on demo seed data.

Use explicit:

```text
development profile
test fixtures
```

Do not insert:

```text
Demo International College
```

or fake students into production migrations.

---

# 119. Fresh Installation Requirement

A completely empty database must be supported.

Expected:

```text
Empty database
 ↓
Flyway migrations
 ↓
No institution
 ↓
Setup wizard
 ↓
Create institution
 ↓
Create administrator
 ↓
Login
 ↓
ERP ready
```

This is a mandatory acceptance test.

---

# 120. Existing Project Transformation Strategy

When transforming an existing ERP:

First identify:

```text
Existing domain models
Existing APIs
Existing migrations
Existing pages
Existing authentication
Existing configuration
Existing seeds
Existing integrations
```

Then classify:

```text
KEEP
FIX
REFACTOR
REPLACE
REMOVE
```

Do not rewrite the whole project without evidence.

---

# 121. Current Known UX Problems to Avoid

Do not show users internal validation such as:

```text
Provide exactly one of programId (college/university mode) or schoolClassId (school mode)
```

Instead, determine the academic mode from institution configuration.

For example:

School:

```text
Class
Section
Subject
```

College:

```text
Program
Semester
Course
```

University:

```text
Faculty
Department
Program
Curriculum
Semester
Course
```

The UI should be contextual.

---

# 122. Settings

Settings should be organized:

```text
Institution
Branding
Academic
Finance
Grading
Notifications
Payments
Documents
Modules
Security
Users
Roles
Permissions
```

Institution settings must edit the existing single institution.

Do not create another institution through settings.

---

# 123. First Admin

Setup wizard should create the initial administrator.

Fields:

```text
Name
Email
Login ID
Password
Confirm Password
```

Password requirements should be enforced.

The administrator should receive the correct role.

---

# 124. Password Reset

Support:

```text
Forgot Password
 ↓
Enter email/login
 ↓
Verification token
 ↓
Set new password
 ↓
Invalidate old sessions where appropriate
```

Tokens must:

- expire
- be single-use
- be securely stored/hashed if persisted

---

# 125. Account Lockout

After repeated failed attempts:

```text
temporary lock
```

or progressive delay.

Audit security events.

Do not reveal whether an account exists during password reset if that would enable enumeration.

---

# 126. Audit UI

Authorized administrators should be able to inspect:

```text
Who
Did what
To which record
When
From where
Before
After
```

Filters:

```text
User
Action
Entity
Date
```

---

# 127. Notifications Center

Users should have:

```text
Unread
Read
All
```

Notifications should link to relevant records where authorized.

Example:

```text
Fee due
→ Open invoice
```

---

# 128. Global Configuration Cache

Institution configuration may be cached.

On update:

```text
Database update
 ↓
Cache invalidation
 ↓
Fresh configuration
```

Do not require application restart for ordinary branding changes.

---

# 129. Feature Flag Safety

If:

```text
LIBRARY_ENABLED = false
```

then:

- navigation hidden
- API protected
- direct route blocked
- dashboard widgets hidden

Do not rely only on frontend hiding.

---

# 130. Error Codes

Use stable codes:

```text
RESOURCE_NOT_FOUND
VALIDATION_ERROR
ACCESS_DENIED
AUTHENTICATION_REQUIRED
DUPLICATE_RESOURCE
BUSINESS_RULE_VIOLATION
PAYMENT_FAILED
PAYMENT_ALREADY_PROCESSED
RESULT_ALREADY_PUBLISHED
INVALID_STATE_TRANSITION
```

Frontend should map these to user-friendly messages.

---

# 131. State Machines

Important workflows should enforce legal transitions.

Example:

```text
DRAFT
 → SUBMITTED
 → UNDER_REVIEW
 → APPROVED
```

Do not allow:

```text
DRAFT → PUBLISHED
```

without required intermediate approvals.

---

# 132. Business State Examples

Admission:

```text
DRAFT
SUBMITTED
UNDER_REVIEW
DOCUMENT_VERIFICATION
ELIGIBILITY
SELECTED
WAITLISTED
REJECTED
ADMITTED
ENROLLED
```

Result:

```text
DRAFT
MARKS_ENTERED
VERIFIED
APPROVED
PUBLISHED
```

Payment:

```text
INITIATED
PENDING
SUCCESS
FAILED
CANCELLED
REFUNDED
```

---

# 133. Reporting Architecture

Reports should not destroy application performance.

For heavy reports:

```text
Request
 ↓
Background Job
 ↓
Generate
 ↓
Store
 ↓
Download
```

For simple reports:

```text
API
 ↓
Query
 ↓
Export
```

---

# 134. Pagination

Never load all students by default.

Use server-side pagination.

Default:

```text
20 or 25
```

Allow configurable page sizes.

---

# 135. Search Optimization

For high-volume data, use proper indexes and database search.

Do not perform:

```text
SELECT * FROM students
```

and filter in React.

---

# 136. Frontend State

Use:

```text
TanStack Query
```

for server state.

Use:

```text
Zustand
```

only for client/global state where appropriate.

Do not store entire server database in global state.

---

# 137. Form State

Use:

```text
React Hook Form
Zod
```

where practical.

Keep validation close to the form while still validating server-side.

---

# 138. Route Protection

Frontend routes should check authentication and permissions.

Example:

```text
/admin/students
```

But backend remains authoritative.

---

# 139. Accessibility

Support:

- keyboard navigation
- labels
- focus states
- readable contrast
- semantic HTML
- accessible dialogs
- form error association

---

# 140. Responsive Design

Support:

- desktop
- laptop
- tablet
- mobile for portal-critical workflows

Administrative ERP can prioritize desktop while remaining usable on smaller screens.

---

# 141. Mobile Priority

Mobile-friendly:

```text
Attendance
Parent Portal
Student Portal
Notifications
Fees
Results
Timetable
```

---

# 142. API Security

Protect endpoints by default.

Use explicit public endpoints.

Do not accidentally expose:

```text
/admin
/internal
/actuator
/private documents
```

---

# 143. CORS

Allow only known frontend origins.

Do not use:

```text
*
```

in production for credentialed requests.

---

# 144. Rate Limiting

Apply especially to:

```text
Login
Password reset
Public APIs
Payment initiation
File upload
```

Redis can support distributed rate limiting within the single deployment if necessary.

---

# 145. Database Connection Pool

Configure production pool carefully.

Monitor:

```text
active
idle
max
timeouts
```

Avoid excessive connections.

---

# 146. Health Checks

Health should check essential dependencies without exposing secrets.

Example:

```text
Database UP
Redis UP
Storage UP
```

Public health response should remain minimal.

---

# 147. Deployment Migration Safety

Before deployment:

```text
backup
 ↓
deploy application
 ↓
run Flyway
 ↓
verify
 ↓
health check
```

For dangerous migrations:

```text
expand
migrate
contract
```

pattern may be necessary.

---

# 148. Data Retention

Retention policies should be configurable for:

- audit logs
- notifications
- documents
- applications
- financial records

Financial/legal records may require longer retention.

---

# 149. Privacy

Only collect information necessary for educational operations.

Protect:

- student identity
- guardian data
- employee data
- financial information
- documents
- academic results

---

# 150. Commercial Product Boundary

The ERP should be sellable as:

```text
Education ERP
```

with customer-specific configuration.

The customer should not need a custom fork for:

- logo
- colors
- institution name
- academic year
- academic model
- programs
- fees
- grading
- modules

---

# 151. Final Acceptance Criteria

The product is considered commercially usable only when:

```text
[ ] Fresh install works
[ ] Setup wizard works
[ ] Institution branding works
[ ] Login branding works
[ ] Admin authentication works
[ ] RBAC works
[ ] Academic setup works
[ ] Admission workflow works
[ ] Student lifecycle works
[ ] Attendance works
[ ] Examination works
[ ] Result approval works
[ ] Result publishing works
[ ] Fees work
[ ] Payments work
[ ] Accounting integration works
[ ] HR works
[ ] Payroll works
[ ] Communication works
[ ] Parent portal works
[ ] Student portal works
[ ] Teacher portal works
[ ] Library works if enabled
[ ] Inventory works if enabled
[ ] Assets work if enabled
[ ] Documents work
[ ] Reports work
[ ] Imports work
[ ] Audit works
[ ] Search works
[ ] Security review passes
[ ] E2E tests pass
[ ] Backup tested
[ ] Production deployment tested
```

---

# 152. Final Agent Instruction

You are not building a school CRUD demo.

You are building a **commercial-grade Education ERP platform**.

The implementation must be:

- modular
- configurable
- secure
- auditable
- scalable
- maintainable
- white-labelable
- production-ready
- institution-agnostic

Do not optimize for number of screens.

Optimize for correct business workflows.

Do not create isolated CRUD features.

Create integrated lifecycle workflows.

Do not hardcode one institution.

Create reusable configuration.

Do not expose backend internals.

Create clear user-facing experiences.

Do not claim a feature is complete because code compiles.

Verify the complete workflow.

The final system must allow a new institution to be onboarded through configuration and setup rather than source-code modification.

---

# 153. Final Engineering Philosophy

The ERP should behave like:

```text
One Institution
        ↓
One Source of Truth
        ↓
Integrated Academic Lifecycle
        ↓
Integrated Financial Lifecycle
        ↓
Integrated People Lifecycle
        ↓
Integrated Operational Modules
        ↓
Secure Portals
        ↓
Auditable Operations
        ↓
Configurable Reports
```

The goal is not simply to create software that works.

The goal is to create a reusable ERP product that can be deployed, configured, branded, operated, audited, maintained, and sold to multiple educational institutions without rebuilding the application for every customer.

**END OF MASTER BLUEPRINT**

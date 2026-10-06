export interface AcademicYear {
  id: string
  name: string
  code: string
  startDate: string
  endDate: string
  calendar: string
  status: string
  current: boolean
}

export interface SchoolClass {
  id: string
  academicYearId: string
  name: string
  code: string
  ordinal: number | null
  active: boolean
}

export interface Section {
  id: string
  schoolClassId: string
  schoolClassName: string | null
  name: string
  code: string
  capacity: number | null
  room: string | null
  active: boolean
}

export interface Course {
  id: string
  code: string
  name: string
  description: string | null
  courseType: string
  departmentId: string | null
  programId: string | null
  creditHours: number | null
  active: boolean
}

export interface Semester {
  id: string
  academicYearId: string
  academicYearName: string
  name: string
  startDate: string
  endDate: string
  status?: string
}

export interface Campus {
  id: string
  code: string
  name: string
  address: string | null
  phone: string | null
  active: boolean
}

export interface Room {
  id: string
  code: string
  name: string
  building: string | null
  capacity: number | null
  roomType: string | null
  campusId: string | null
  campusName: string | null
  active: boolean
}

export interface TimeSlot {
  id: string
  name: string
  startTime: string
  endTime: string
  slotType: string
  ordinal: number
  active: boolean
}

export interface Faculty {
  id: string
  code: string
  name: string
  description: string | null
  campusId: string | null
  active: boolean
}

export interface Department {
  id: string
  code: string
  name: string
  description: string | null
  facultyId: string | null
  active: boolean
}

export interface Program {
  id: string
  code: string
  name: string
  level: string | null
  departmentId: string | null
  departmentName: string | null
}

export interface CourseOffering {
  id: string
  offeringCode: string
  academicModel: string
  courseId: string
  courseCode: string
  courseName: string
  academicYearId: string
  academicYearName: string
  semesterId: string | null
  semesterName: string | null
  programId: string | null
  programName: string | null
  schoolClassId: string | null
  schoolClassName: string | null
  sectionId: string | null
  sectionName: string | null
  teacherId: string | null
  teacherName: string | null
  roomId: string | null
}

export const COURSE_TYPES = ['SUBJECT', 'ELECTIVE', 'PROJECT', 'INTERNSHIP', 'LAB'] as const
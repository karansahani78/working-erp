package com.educationerp.attendance;

import com.educationerp.auth.role.Role;
import com.educationerp.support.CourseOfferingFixture;
import com.educationerp.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Object-scope enforcement on attendance: a teacher may only touch the classes they are
 * assigned to, and a register may only name students who are actually in that class.
 */
class AttendanceScopeIntegrationTest extends IntegrationTest {

    @Autowired
    private CourseOfferingFixture fixture;

    @Autowired
    private AttendanceRecordRepository records;

    private String admin;
    private String teacher;
    private String otherTeacher;
    private UUID myOffering;
    private UUID otherOffering;
    private UUID myStudent;
    private UUID otherStudent;
    private UUID unEnrolled;
    private LocalDate date;

    @BeforeEach
    void seed() throws Exception {
        testData.institution();
        admin = adminToken();
        UUID me = fixture.createTeacher("scope.teacher", "TeacherA123");
        UUID someoneElse = fixture.createTeacher("scope.other", "TeacherB123");
        teacher = loginToken("scope.teacher", "TeacherA123");
        otherTeacher = loginToken("scope.other", "TeacherB123");

        myOffering = fixture.createOffering(me);
        otherOffering = fixture.createOffering(someoneElse);
        myStudent = fixture.createStudent("scope.enrolled@example.edu");
        fixture.enroll(myStudent, myOffering);
        // A student who belongs to the other teacher's class, so foreign to my offering when
        // the two offerings share a class it would still be in the roster, so use a student
        // who is not enrolled anywhere to prove the roster check.
        otherStudent = fixture.createStudent("scope.foreign@example.edu");
        fixture.enroll(otherStudent, otherOffering);
        unEnrolled = fixture.createStudent("scope.unanrolled@example.edu");
        date = LocalDate.of(2024, 2, 5);
    }

    private void mark(String token, UUID offering, UUID student, String status) throws Exception {
        mockMvc.perform(post("/api/v1/attendance/registers")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "courseOfferingId", offering,
                                "attendanceDate", date.toString(),
                                "entries", List.of(Map.of("studentId", student, "status", status))))))
                .andExpect(status().isOk());
    }

    @Test
    void aTeacherCanRecordOnlyTheirOwnAssignedClass() throws Exception {
        mark(teacher, myOffering, myStudent, "PRESENT");
        assertThat(records.findByCourseOfferingIdAndAttendanceDateOrderByStudentIdAsc(myOffering, date)).hasSize(1);

        mockMvc.perform(post("/api/v1/attendance/registers")
                        .header("Authorization", bearer(teacher))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "courseOfferingId", otherOffering,
                                "attendanceDate", date.toString(),
                                "entries", List.of(Map.of("studentId", otherStudent, "status", "PRESENT"))))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        assertThat(records.findByCourseOfferingIdAndAttendanceDateOrderByStudentIdAsc(otherOffering, date)).isEmpty();
    }

    @Test
    void aRegisterCannotNameAStudentWhoIsNotInTheClass() throws Exception {
        mockMvc.perform(post("/api/v1/attendance/registers")
                        .header("Authorization", bearer(teacher))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "courseOfferingId", myOffering,
                                "attendanceDate", date.toString(),
                                "entries", List.of(Map.of("studentId", unEnrolled, "status", "PRESENT"))))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void aMixedRegisterIsRejectedAndNothingIsPersisted() throws Exception {
        mockMvc.perform(post("/api/v1/attendance/registers")
                        .header("Authorization", bearer(teacher))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "courseOfferingId", myOffering,
                                "attendanceDate", date.toString(),
                                "entries", List.of(
                                        Map.of("studentId", myStudent, "status", "PRESENT"),
                                        Map.of("studentId", unEnrolled, "status", "ABSENT"))))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        assertThat(records.findByCourseOfferingIdAndAttendanceDateOrderByStudentIdAsc(myOffering, date)).isEmpty();
    }

    @Test
    void aTeacherCannotReadAnotherTeachersClass() throws Exception {
        mark(admin, myOffering, myStudent, "PRESENT");

        mockMvc.perform(get("/api/v1/attendance/registers/{offering}/{date}", myOffering, date)
                        .header("Authorization", bearer(otherTeacher)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/attendance/registers/{offering}/{date}", otherOffering, date)
                        .header("Authorization", bearer(otherTeacher)))
                .andExpect(status().isOk());
    }

    @Test
    void aTeacherCannotSubmitOrApproveOutsideTheirOwnClass() throws Exception {
        mark(teacher, myOffering, myStudent, "PRESENT");
        mark(otherTeacher, otherOffering, otherStudent, "PRESENT");

        mockMvc.perform(post("/api/v1/attendance/registers/{offering}/{date}/submit", otherOffering, date)
                        .header("Authorization", bearer(teacher))
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/attendance/registers/{offering}/{date}/submit", myOffering, date)
                        .header("Authorization", bearer(teacher))
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    void anAdministratorCanManageAnyRegister() throws Exception {
        mark(admin, otherOffering, otherStudent, "PRESENT");

        mockMvc.perform(post("/api/v1/attendance/registers/{offering}/{date}/approve", otherOffering, date)
                        .header("Authorization", bearer(admin))
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk());

        assertThat(records.findByCourseOfferingIdAndAttendanceDateOrderByStudentIdAsc(otherOffering, date).get(0)
                .getWorkflowStatus()).isEqualTo(AttendanceRecord.WorkflowStatus.APPROVED);
    }

    @Test
    void aCorrectionIsScopedToTheTeachersOwnClass() throws Exception {
        mark(admin, otherOffering, otherStudent, "ABSENT");
        mockMvc.perform(post("/api/v1/attendance/registers/{offering}/{date}/approve", otherOffering, date)
                        .header("Authorization", bearer(admin))
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk());
        UUID otherRecordId = records.findByCourseOfferingIdAndAttendanceDateOrderByStudentIdAsc(
                        otherOffering, date).get(0).getId();

        // The assigned teacher may request a correction on their own record.
        mark(teacher, myOffering, myStudent, "ABSENT");
        mockMvc.perform(post("/api/v1/attendance/registers/{offering}/{date}/approve", myOffering, date)
                        .header("Authorization", bearer(admin))
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk());
        UUID myRecordId = records.findByCourseOfferingIdAndAttendanceDateOrderByStudentIdAsc(
                        myOffering, date).get(0).getId();

        mockMvc.perform(post("/api/v1/attendance/records/{id}/corrections", myRecordId)
                        .header("Authorization", bearer(teacher))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "newStatus", "PRESENT", "reason", "Forgot to mark"))))
                .andExpect(status().isOk());

        // But not on another teacher's record, even though the permission is the same.
        mockMvc.perform(post("/api/v1/attendance/records/{id}/corrections", otherRecordId)
                        .header("Authorization", bearer(teacher))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "newStatus", "PRESENT", "reason", "Trying to reach across"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void aPrincipalMayApproveButNotRecordAttendance() throws Exception {
        testData.user("scope.principal", "Principal", "scope.p@example.edu", Role.PRINCIPAL, "Principal123");
        String principal = loginToken("scope.principal", "Principal123");

        // Leadership holds ATTENDANCE_APPROVE but not ATTENDANCE_MARK.
        mockMvc.perform(post("/api/v1/attendance/registers")
                        .header("Authorization", bearer(principal))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "courseOfferingId", myOffering,
                                "attendanceDate", date.toString(),
                                "entries", List.of(Map.of("studentId", myStudent, "status", "PRESENT"))))))
                .andExpect(status().isForbidden());

        mark(teacher, myOffering, myStudent, "PRESENT");
        mockMvc.perform(post("/api/v1/attendance/registers/{offering}/{date}/approve", myOffering, date)
                        .header("Authorization", bearer(principal))
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk());

        assertThat(records.findByCourseOfferingIdAndAttendanceDateOrderByStudentIdAsc(myOffering, date).get(0)
                .getWorkflowStatus()).isEqualTo(AttendanceRecord.WorkflowStatus.APPROVED);
    }
}
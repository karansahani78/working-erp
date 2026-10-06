package com.educationerp.academic;

import com.educationerp.institution.AcademicModel;
import com.educationerp.institution.InstitutionRepository;
import com.educationerp.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The academic structure the blueprint lays out in sections 13-18 and 82: programmes and
 * their versions, curricula, terms, sections, rooms, course offerings and the timetable.
 */
class AcademicStructureIntegrationTest extends IntegrationTest {

    @Autowired
    private InstitutionRepository institutions;

    @Autowired
    private SchoolClassRepository classes;

    /** The id of a class the setup wizard already created, by its code. */
    private String classIdByCode(String yearId, String code) {
        return classes.findByAcademicYearIdAndCodeIgnoreCase(UUID.fromString(yearId), code)
                .orElseThrow(() -> new IllegalStateException("The setup did not seed class " + code))
                .getId().toString();
    }

    @Test
    @DisplayName("a programme is created once and a duplicate code is refused")
    void createsAProgramme() throws Exception {
        String token = adminToken();
        String id = postJson("/api/v1/academic/programs", """
                {"code":"bca","name":"Bachelor of Computer Applications","level":"UNDERGRADUATE",
                 "durationSemesters":6,"durationYears":3}""").path("id").asText();
        org.assertj.core.api.Assertions.assertThat(id).isNotBlank();

        // The code is normalised, so a second programme cannot dodge the check with case.
        postJsonExpecting("/api/v1/academic/programs", """
                {"code":"BCA","name":"Another BCA"}""", 409);
    }

    @Test
    @DisplayName("activating a programme version retires the one it replaces")
    void activatesOneProgrammeVersionAtATime() throws Exception {
        String token = adminToken();
        String programId = postJson("/api/v1/academic/programs", """
                {"code":"BIT","name":"Bachelor of Information Technology"}""").path("id").asText();
        String first = postJson("/api/v1/academic/programs/versions", """
                {"programId":"%s","label":"2024 Curriculum","effectiveFrom":"2024-01-01"}"""
                .formatted(programId)).path("id").asText();
        String second = postJson("/api/v1/academic/programs/versions", """
                {"programId":"%s","label":"2026 Curriculum","effectiveFrom":"2026-01-01"}"""
                .formatted(programId)).path("id").asText();

        postOk("/api/v1/academic/programs/versions/" + first + "/activate", token)
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
        postOk("/api/v1/academic/programs/versions/" + second + "/activate", token)
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        // A student stays attached to the version they started on, so exactly one is current.
        mockMvc.perform(get("/api/v1/academic/programs/{id}/versions", programId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.status == 'ACTIVE')].label")
                        .value(org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.data[?(@.status == 'RETIRED')].label").value("2024 Curriculum"));
    }

    @Test
    @DisplayName("a curriculum reports its credits as courses are placed in it")
    void tracksCurriculumCredits() throws Exception {
        String token = adminToken();
        String programId = postJson("/api/v1/academic/programs", """
                {"code":"BCA-C","name":"BCA with a curriculum"}""").path("id").asText();
        String versionId = postJson("/api/v1/academic/programs/versions", """
                {"programId":"%s","label":"2026","totalCredits":120}"""
                .formatted(programId)).path("id").asText();
        String curriculumId = postJson("/api/v1/academic/programs/curricula", """
                {"programVersionId":"%s","name":"BCA 2026 syllabus","totalCredits":30}"""
                .formatted(versionId)).path("id").asText();

        String semesterId = semester(token, "Semester 1", 1, "2023-04-01", "2023-09-30");
        String maths = course(token, "MTH101", "Mathematics", 4);
        String programming = course(token, "CS101", "Programming", 3);
        postJsonOk("/api/v1/academic/programs/curricula/" + curriculumId + "/courses", """
                {"courseId":"%s","semesterId":"%s","creditHours":4}"""
                .formatted(maths, semesterId))
                .andExpect(jsonPath("$.data.semesterName").value("Semester 1"));
        postJsonOk("/api/v1/academic/programs/curricula/" + curriculumId + "/courses", """
                {"courseId":"%s","semesterId":"%s"}"""
                .formatted(programming, semesterId))
                .andExpect(jsonPath("$.data.creditHours").value(3));

        // Seven of thirty credits placed, so the curriculum is visibly incomplete.
        mockMvc.perform(get("/api/v1/academic/programs/versions/{id}/curricula", versionId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].placedCredits").value(7))
                .andExpect(jsonPath("$.data[0].totalCredits").value(30))
                .andExpect(jsonPath("$.data[0].complete").value(false));

        mockMvc.perform(get("/api/v1/academic/programs/curricula/{id}/courses", curriculumId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].courseCode",
                        org.hamcrest.Matchers.containsInAnyOrder("MTH101", "CS101")));

        // The same course twice in one term is a mistake, not a second placement.
        postJsonExpecting("/api/v1/academic/programs/curricula/" + curriculumId + "/courses", """
                {"courseId":"%s","semesterId":"%s"}"""
                .formatted(maths, semesterId), 409);
    }

    @Test
    @DisplayName("a term must sit inside its academic year and not overlap another term")
    void constrainsTermDates() throws Exception {
        String token = adminToken();
        // The seeded year runs 2023-04-01 to 2024-03-31.
        semester(token, "Semester 1", 1, "2023-04-01", "2023-09-30");

        postJsonExpecting("/api/v1/academic/semesters",
                semesterBody("Too early", 2, "2022-01-01", "2022-06-30"), 400);
        postJsonExpecting("/api/v1/academic/semesters",
                semesterBody("Overlapping", 2, "2023-09-01", "2024-01-31"), 422);
        postJsonExpecting("/api/v1/academic/semesters",
                semesterBody("Same position", 1, "2023-10-01", "2024-02-28"), 409);
    }

    @Test
    @DisplayName("a term cannot be activated before it starts or completed before it ends")
    void guardsTheTermLifecycle() throws Exception {
        String token = adminToken();
        AcademicYear future = testData.academicYear("Future year", "FY-FUT",
                LocalDate.now().plusMonths(6), LocalDate.now().plusMonths(18));
        String termId = postJson("/api/v1/academic/semesters", """
                {"academicYearId":"%s","name":"Semester 1","ordinal":1,
                 "startDate":"%s","endDate":"%s"}"""
                .formatted(future.getId(), future.getStartDate(), future.getEndDate()))
                .path("id").asText();

        postExpecting("/api/v1/academic/semesters/{id}/activate".replace("{id}", termId), token, 422);
        postExpecting("/api/v1/academic/semesters/{id}/complete".replace("{id}", termId), token, 409);
    }

    @Test
    @DisplayName("a completed term cannot be reopened")
    void willNotReopenAClosedTerm() throws Exception {
        String token = adminToken();
        String termId = semester(token, "Semester 1", 1, "2023-04-01", "2023-09-30");
        postOk("/api/v1/academic/semesters/{id}/activate".replace("{id}", termId), token)
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
        postOk("/api/v1/academic/semesters/{id}/complete".replace("{id}", termId), token)
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
        postExpecting("/api/v1/academic/semesters/{id}/activate".replace("{id}", termId), token, 409);
    }

    @Test
    @DisplayName("section codes are unique within a class but may repeat across classes")
    void scopesSectionCodesToTheirClass() throws Exception {
        String token = adminToken();
        String yearId = testData.academicYear().getId().toString();
        // The setup seeded Grade 10 with sections A and B, and Grade 11 with section A.
        String first = classIdByCode(yearId, "G10");
        String other = schoolClass(token, yearId, "Grade 9", "G9X");

        // The same code in a different class is a different section, not a duplicate.
        postJsonOk("/api/v1/academic/sections", sectionBody(other, "A"));
        // Within one class it is the same section twice, however the case is written.
        postJsonExpecting("/api/v1/academic/sections", sectionBody(first, "a"), 409);
    }

    @Test
    @DisplayName("a school cannot offer a course by programme and term alone")
    void refusesACollegeShapedOfferingInASchool() throws Exception {
        String token = adminToken();
        String termId = semester(token, "Semester 1", 1, "2023-04-01", "2023-09-30");
        String programId = postJson("/api/v1/academic/programs", """
                {"code":"BCA-S","name":"BCA at a school"}""").path("id").asText();

        postJsonExpecting("/api/v1/academic/offerings", """
                {"courseId":"%s","academicYearId":"%s","programId":"%s","semesterId":"%s"}"""
                .formatted(course(token, "CS201", "Data Structures", 3),
                        testData.academicYear().getId(), programId, termId), 422);
    }

    @Test
    @DisplayName("an offering may not mix a class with a programme")
    void refusesAMixedOffering() throws Exception {
        String token = adminToken();
        String yearId = testData.academicYear().getId().toString();
        String classId = schoolClass(token, yearId, "Grade 9", "G9M");
        String sectionId = section(token, classId, "A");
        String termId = semester(token, "Semester 1", 1, "2023-04-01", "2023-09-30");
        String programId = postJson("/api/v1/academic/programs", """
                {"code":"BCA-M","name":"BCA mixed in"}""").path("id").asText();

        postJsonExpecting("/api/v1/academic/offerings", """
                {"courseId":"%s","academicYearId":"%s","sectionId":"%s","semesterId":"%s",
                 "programId":"%s"}"""
                .formatted(course(token, "CS202", "Databases", 3), yearId, sectionId, termId, programId), 422);
    }

    @Test
    @DisplayName("an offering is refused when its marks do not add up or its class will not fit")
    void validatesOfferingDelivery() throws Exception {
        String token = adminToken();
        String yearId = testData.academicYear().getId().toString();
        String classId = schoolClass(token, yearId, "Grade 9", "G9D");
        String sectionId = section(token, classId, "A");
        String small = room(token, "R-101", "Small room", 10);
        String courseId = course(token, "PHY101", "Physics", 4);

        // A pass mark above the total would fail every student.
        postJsonExpecting("/api/v1/academic/offerings",
                offeringBody(courseId, yearId, sectionId, small, 30, 60, 200, 8), 400);
        // Forty students will not fit in a room of ten.
        postJsonExpecting("/api/v1/academic/offerings",
                offeringBody(courseId, yearId, sectionId, small, 30, 60, 45, 40), 422);

        // A capacity the room can actually take is fine, and the total is summed for us.
        postJsonOk("/api/v1/academic/offerings", offeringBody(courseId, yearId, sectionId, small, 30, 60, 45, 8))
                .andExpect(jsonPath("$.data.totalMarks").value(90))
                .andExpect(jsonPath("$.data.availableSeats").value(8))
                .andExpect(jsonPath("$.data.academicModel").value("SCHOOL"))
                .andExpect(jsonPath("$.data.offeringCode").value("PHY101-A-2080-81"));

        // The same course into the same group twice is a duplicate.
        postJsonExpecting("/api/v1/academic/offerings",
                offeringBody(courseId, yearId, sectionId, small, 30, 60, 45, 8), 409);
    }

    @Test
    @DisplayName("a teacher cannot be in two places in the same slot")
    void refusesATeacherClash() throws Exception {
        String token = adminToken();
        String yearId = testData.academicYear().getId().toString();
        String classId = schoolClass(token, yearId, "Grade 9", "G9T");
        String sectionA = section(token, classId, "A");
        String sectionB = section(token, classId, "B");
        UUID slot = timeSlot(token, "Period 1", "08:00", "08:45", 1);
        UUID teacher = UUID.randomUUID();

        timetableOk(token, "MONDAY", slot, offering(token, yearId, classId, sectionA,
                course(token, "ENG201", "Literature", 3), teacher), null);
        // Same teacher, same slot, different section: a clash.
        postJsonExpecting("/api/v1/academic/timetable", timetableBody("MONDAY", slot,
                offering(token, yearId, classId, sectionB, course(token, "ENG202", "Linguistics", 3), teacher),
                null), 422);
    }

    @Test
    @DisplayName("a room cannot be double-booked in the same slot")
    void refusesARoomClash() throws Exception {
        String token = adminToken();
        String yearId = testData.academicYear().getId().toString();
        String classA = schoolClass(token, yearId, "Grade 9", "G9R1");
        String classB = schoolClass(token, yearId, "Grade 10", "G10R1");
        String sectionA = section(token, classA, "A");
        String sectionB = section(token, classB, "A");
        UUID slot = timeSlot(token, "Period 2", "09:00", "09:45", 2);
        UUID room = UUID.fromString(room(token, "R-201", "Shared lab", 30));

        timetableOk(token, "TUESDAY", slot, offering(token, yearId, classA, sectionA,
                course(token, "CHE201", "Chemistry", 4), UUID.randomUUID()), room);
        postJsonExpecting("/api/v1/academic/timetable", timetableBody("TUESDAY", slot,
                offering(token, yearId, classB, sectionB, course(token, "CHE202", "Chemistry lab", 2),
                        UUID.randomUUID()), room), 422);
    }

    @Test
    @DisplayName("a section cannot have two lessons in the same slot")
    void refusesASectionClash() throws Exception {
        String token = adminToken();
        String yearId = testData.academicYear().getId().toString();
        String classId = schoolClass(token, yearId, "Grade 9", "G9S");
        String sectionId = section(token, classId, "A");
        UUID slot = timeSlot(token, "Period 3", "10:00", "10:45", 3);

        timetableOk(token, "WEDNESDAY", slot, offering(token, yearId, classId, sectionId,
                course(token, "MAT301", "Algebra", 3), UUID.randomUUID()), null);
        postJsonExpecting("/api/v1/academic/timetable", timetableBody("WEDNESDAY", slot,
                offering(token, yearId, classId, sectionId, course(token, "MAT302", "Geometry", 3),
                        UUID.randomUUID()), null), 422);
    }

    @Test
    @DisplayName("a clash is judged on the day, so the same slot on another day is fine")
    void allowsTheSameSlotOnAnotherDay() throws Exception {
        String token = adminToken();
        String yearId = testData.academicYear().getId().toString();
        String classId = schoolClass(token, yearId, "Grade 9", "G9D2");
        String sectionId = section(token, classId, "A");
        UUID slot = timeSlot(token, "Period 1", "08:00", "08:45", 1);
        UUID teacher = UUID.randomUUID();
        UUID room = UUID.fromString(room(token, "R-301", "Day room", 30));

        timetableOk(token, "MONDAY", slot, offering(token, yearId, classId, sectionId,
                course(token, "HIS401", "History", 3), teacher), room);
        timetableOk(token, "TUESDAY", slot, offering(token, yearId, classId, sectionId,
                course(token, "HIS402", "Geography", 3), teacher), room);

        // The grid shows the Monday lesson only on Monday, with the slot times resolved.
        mockMvc.perform(get("/api/v1/academic/timetable")
                        .param("sectionId", sectionId)
                        .param("day", "MONDAY")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scope").value("SECTION"))
                .andExpect(jsonPath("$.data.slots[0].dayOfWeek").value("MONDAY"))
                .andExpect(jsonPath("$.data.slots[0].start").value("08:00"))
                .andExpect(jsonPath("$.data.slots[0].entries[0].courseCode").value("HIS401"))
                .andExpect(jsonPath("$.data.slots[0].entries[0].roomName").value("Day room"))
                .andExpect(jsonPath("$.data.slots[0].entries[0].teacherName").value("Teacher One"));
    }

    @Test
    @DisplayName("moving a lesson is not blocked by the row it replaces")
    void allowsAMoveIntoItsOwnSlot() throws Exception {
        String token = adminToken();
        String yearId = testData.academicYear().getId().toString();
        String classId = schoolClass(token, yearId, "Grade 9", "G9M2");
        String sectionId = section(token, classId, "A");
        UUID monday = timeSlot(token, "Period 1", "08:00", "08:45", 1);
        UUID tuesday = timeSlot(token, "Period 4", "11:00", "11:45", 4);
        String offeringId = offering(token, yearId, classId, sectionId,
                course(token, "ECO501", "Economics", 3), UUID.randomUUID());
        UUID room = UUID.fromString(room(token, "R-401", "Economics room", 30));
        String entryId = timetableOk(token, "MONDAY", monday, offeringId, room)
                .andReturn().getResponse().getContentAsString();
        entryId = objectMapper.readTree(entryId).path("data").path("id").asText();

        // The clash check has to ignore the row being moved, or nothing could ever shift.
        mockMvc.perform(put("/api/v1/academic/timetable/{id}", entryId)
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"dayOfWeek":"TUESDAY","timeSlotId":"%s","courseOfferingId":"%s",
                                 "roomId":"%s"}"""
                                .formatted(tuesday, offeringId, room)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dayOfWeek").value("TUESDAY"));
    }

    @Test
    @DisplayName("a college offering needs a programme and a term, not a class")
    void requiresTheShapeOfTheConfiguredModel() throws Exception {
        String token = adminToken();
        institutions.findFirstByOrderByCreatedAtAsc().ifPresent(institution -> {
            institution.setAcademicModel(AcademicModel.COLLEGE);
            institutions.save(institution);
        });
        String yearId = testData.academicYear().getId().toString();
        String termId = semester(token, "Semester 1", 1, "2023-04-01", "2023-09-30");
        String programId = postJson("/api/v1/academic/programs", """
                {"code":"BCA-C2","name":"BCA at a college"}""").path("id").asText();

        postJsonOk("/api/v1/academic/offerings", """
                {"courseId":"%s","academicYearId":"%s","programId":"%s","semesterId":"%s",
                 "internalMarks":40,"externalMarks":60}"""
                .formatted(course(token, "CS301", "Compilers", 4), yearId, programId, termId))
                .andExpect(jsonPath("$.data.academicModel").value("COLLEGE"))
                .andExpect(jsonPath("$.data.offeringCode").value("CS301-BCA-C2-2080-81"));

        // A class-and-section offering is now the wrong shape for this institution.
        String classId = schoolClass(token, yearId, "Grade 9", "G9C2");
        String sectionId = section(token, classId, "A");
        postJsonExpecting("/api/v1/academic/offerings", """
                {"courseId":"%s","academicYearId":"%s","sectionId":"%s"}"""
                .formatted(course(token, "CS302", "Operating Systems", 4), yearId, sectionId), 422);
    }

    // -------------------------------------------------------------------- helpers

    /** POSTs a JSON body as the administrator and returns the {@code data} node. */
    private JsonNode postJson(String path, String body) throws Exception {
        String response = mockMvc.perform(post(path)
                        .header("Authorization", bearer(adminToken()))
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data");
    }

    private org.springframework.test.web.servlet.ResultActions postJsonOk(String path, String body)
            throws Exception {
        return mockMvc.perform(post(path)
                .header("Authorization", bearer(adminToken()))
                .contentType("application/json")
                .content(body))
                .andExpect(status().isOk());
    }

    private void postJsonExpecting(String path, String body, int expected) throws Exception {
        mockMvc.perform(post(path)
                        .header("Authorization", bearer(adminToken()))
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().is(expected));
    }

    private org.springframework.test.web.servlet.ResultActions postOk(String path, String token)
            throws Exception {
        return mockMvc.perform(post(path)
                .header("Authorization", bearer(token))
                .contentType("application/json")
                .content("{}"))
                .andExpect(status().isOk());
    }

    private void postExpecting(String path, String token, int expected) throws Exception {
        mockMvc.perform(post(path)
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().is(expected));
    }

    private String semesterBody(String name, int ordinal, String from, String to) {
        return """
                {"academicYearId":"%s","name":"%s","ordinal":%d,"startDate":"%s","endDate":"%s"}"""
                .formatted(testData.academicYear().getId(), name, ordinal, from, to);
    }

    private String semester(String token, String name, int ordinal, String from, String to)
            throws Exception {
        return postJson("/api/v1/academic/semesters", semesterBody(name, ordinal, from, to))
                .path("id").asText();
    }

    private String schoolClass(String token, String yearId, String name, String code) throws Exception {
        return postJson("/api/v1/academic/classes", """
                {"academicYearId":"%s","name":"%s","code":"%s","ordinal":1,"active":true}"""
                .formatted(yearId, name, code)).path("id").asText();
    }

    private String sectionBody(String classId, String code) {
        return """
                {"schoolClassId":"%s","name":"Section %s","code":"%s","capacity":40}"""
                .formatted(classId, code, code);
    }

    private String section(String token, String classId, String code) throws Exception {
        return postJson("/api/v1/academic/sections", sectionBody(classId, code)).path("id").asText();
    }

    private String room(String token, String code, String name, int capacity) throws Exception {
        return postJson("/api/v1/academic/rooms", """
                {"code":"%s","name":"%s","capacity":%d,"roomType":"CLASSROOM"}"""
                .formatted(code, name, capacity)).path("id").asText();
    }

    private String course(String token, String code, String name, int credits) throws Exception {
        return postJson("/api/v1/academic/courses", """
                {"code":"%s","name":"%s","creditHours":%d,"active":true}"""
                .formatted(code, name, credits)).path("id").asText();
    }

    private String offeringBody(String courseId, String yearId, String sectionId, String roomId,
                                int internal, int external, int pass, int capacity) {
        return """
                {"courseId":"%s","academicYearId":"%s","sectionId":"%s","roomId":"%s",
                 "capacity":%d,"internalMarks":%d,"externalMarks":%d,"passMarks":%d}"""
                .formatted(courseId, yearId, sectionId, roomId, capacity, internal, external, pass);
    }

    private String offering(String token, String yearId, String classId, String sectionId,
                            String courseId, UUID teacherId) throws Exception {
        return postJson("/api/v1/academic/offerings", """
                {"courseId":"%s","academicYearId":"%s","schoolClassId":"%s","sectionId":"%s",
                 "teacherId":"%s","teacherName":"Teacher One","capacity":40,
                 "internalMarks":40,"externalMarks":60}"""
                .formatted(courseId, yearId, classId, sectionId, teacherId)).path("id").asText();
    }

    private String timetableBody(String day, UUID slot, String offeringId, UUID roomId) {
        String room = roomId == null ? "" : ",\"roomId\":\"%s\"".formatted(roomId);
        return """
                {"dayOfWeek":"%s","timeSlotId":"%s","courseOfferingId":"%s"%s}"""
                .formatted(day, slot, offeringId, room);
    }

    private org.springframework.test.web.servlet.ResultActions timetableOk(
            String token, String day, UUID slot, String offeringId, UUID roomId) throws Exception {
        return postJsonOk("/api/v1/academic/timetable", timetableBody(day, slot, offeringId, roomId));
    }

    private UUID timeSlot(String token, String name, String from, String to, int ordinal)
            throws Exception {
        return UUID.fromString(postJson("/api/v1/academic/time-slots", """
                {"name":"%s","startTime":"%s","endTime":"%s","ordinal":%d,"active":true}"""
                .formatted(name, from, to, ordinal)).path("id").asText());
    }
}

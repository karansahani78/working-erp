package com.educationerp.asset;

import com.educationerp.institution.ModuleKey;
import com.educationerp.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The asset register and the custody trail behind it.
 *
 * <p>The "who is holding what" view is the part worth pinning down. It used to answer with
 * a single query naming all three holder id columns at once, which the database cannot type
 * once any of them is empty. Asking for everybody of a kind, which is how the screen opens,
 * failed outright with a server error rather than an empty list.
 */
class AssetIntegrationTest extends IntegrationTest {

    @BeforeEach
    void enableAssets() {
        testData.enableModule(ModuleKey.ASSETS);
        // Employees are read through the HR module, so a holder of that kind needs it on.
        testData.enableModule(ModuleKey.HR);
    }

    @Test
    @DisplayName("an asset recorded with a cost is carried at that cost")
    void recordedAssetKeepsItsCost() throws Exception {
        String token = adminToken();
        String categoryId = category(token, "COMPUTER", "0.25", "4");
        String assetId = asset(token, categoryId, "Projector", "24500");

        mockMvc.perform(get("/api/v1/assets/overview")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalAssets").value(1))
                .andExpect(jsonPath("$.data.available").value(1))
                .andExpect(jsonPath("$.data.purchaseCost").value(24500.0));

        mockMvc.perform(get("/api/v1/assets/depreciation")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].assetId").value(assetId));

        // A quarter of 24500 comes off for every whole year since it was bought.
        BigDecimal cost = new BigDecimal("24500");
        long age = Math.max(0,
                ChronoUnit.YEARS.between(LocalDate.parse("2024-01-15"), LocalDate.now()));
        BigDecimal expected = cost.subtract(cost.multiply(new BigDecimal("0.25"))
                .multiply(BigDecimal.valueOf(age))).max(BigDecimal.ZERO);
        mockMvc.perform(get("/api/v1/assets/depreciation")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].currentValue").value(expected.doubleValue()))
                .andExpect(jsonPath("$.data[0].annualDepreciation").value(6125.0));
    }

    @Test
    @DisplayName("an asset with no date or cost is still worth recording")
    void anAssetWithoutACostIsRecorded() throws Exception {
        String token = adminToken();
        String categoryId = category(token, "FURNITURE", "0.10", "10");

        // Both fields are optional on the way in, so the register has to cope with their
        // absence rather than failing when it tries to work out the age of the thing.
        mockMvc.perform(post("/api/v1/assets")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "name", "Donated bookcase",
                                "categoryId", categoryId))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.currentValue").doesNotExist())
                .andExpect(jsonPath("$.data.assetNumber").value("AST-00001"));

        mockMvc.perform(get("/api/v1/assets/overview")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalAssets").value(1))
                .andExpect(jsonPath("$.data.purchaseCost").value(0.0));

        mockMvc.perform(get("/api/v1/assets/depreciation")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                // With no purchase date there is no age to work from, so it is left out
                // rather than reported as worth nothing.
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    @DisplayName("an asset out to a person is no longer on the shelf")
    void assignmentTakesTheAssetOffTheShelf() throws Exception {
        String token = adminToken();
        String categoryId = category(token, "FURNITURE", "0.10", "10");
        String assetId = asset(token, categoryId, "Steel desk", "6500");

        assign(token, assetId, "EXTERNAL", "Guest lecturer");

        mockMvc.perform(get("/api/v1/assets/overview")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(0))
                .andExpect(jsonPath("$.data.assigned").value(1));
    }

    @Test
    @DisplayName("the custody view answers for everybody of a kind")
    void heldByEveryoneOfAKindIsAnswered() throws Exception {
        String token = adminToken();
        String categoryId = category(token, "COMPUTER", "0.25", "4");

        String employeeAsset = asset(token, categoryId, "Department laptop", "98000");
        String studentAsset = asset(token, categoryId, "Exam desk", "6500");

        UUID employeeId = employee(token, "ast-emp-001");
        UUID studentId = student(token, "custody.student@example.test");

        assign(token, employeeAsset, "EMPLOYEE", "Asha Rai", null, employeeId, null);
        assign(token, studentAsset, "STUDENT", "Ananya Sharma", null, null, studentId);

        // No holder identifier at all: this is how the screen opens.
        mockMvc.perform(get("/api/v1/assets/held")
                        .param("holderType", "EMPLOYEE")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc.perform(get("/api/v1/assets/held")
                        .param("holderType", "STUDENT")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        // An external holder has no directory row, so their name has to come from the
        // assignment itself or the row would come back nameless.
        mockMvc.perform(get("/api/v1/assets/held")
                        .param("holderType", "EXTERNAL")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));

        mockMvc.perform(get("/api/v1/assets/held")
                        .param("holderType", "EMPLOYEE")
                        .param("holderEmployeeId", employeeId.toString())
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        // The student holds a different thing, so the employee's own list stays theirs.
        mockMvc.perform(get("/api/v1/assets/held")
                        .param("holderType", "STUDENT")
                        .param("holderStudentId", studentId.toString())
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc.perform(get("/api/v1/assets/held")
                        .param("holderType", "EMPLOYEE")
                        .param("holderEmployeeId", employeeId.toString())
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.data[0].id").value(employeeAsset));

        // An id that is not a holder of the kind asked for is a wrong address, not an empty
        // list: a student id is not an employee.
        mockMvc.perform(get("/api/v1/assets/held")
                        .param("holderType", "EMPLOYEE")
                        .param("holderEmployeeId", studentId.toString())
                        .header("Authorization", bearer(token)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("an asset can only go out once")
    void anAssignedAssetCannotGoOutAgain() throws Exception {
        String token = adminToken();
        String categoryId = category(token, "COMPUTER", "0.25", "4");
        String assetId = asset(token, categoryId, "Projector", "24500");

        assign(token, assetId, "EXTERNAL", "Guest lecturer");

        mockMvc.perform(post("/api/v1/assets/{id}/assignments", assetId)
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"holderType":"EXTERNAL","holderName":"Someone else"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("an external holder is listed by the name they gave")
    void anExternalHolderIsListedByName() throws Exception {
        String token = adminToken();
        String categoryId = category(token, "VEHICLE", "0.20", "5");
        String assetId = asset(token, categoryId, "Pool car", "1200000");

        assign(token, assetId, "EXTERNAL", "Guest lecturer");

        mockMvc.perform(get("/api/v1/assets/held")
                        .param("holderType", "EXTERNAL")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.data[0].holderName").value("Guest lecturer"))
                .andExpect(jsonPath("$.data.data[0].id").value(assetId));
    }

    @Test
    @DisplayName("a return puts the asset back and records the condition it came back in")
    void returningAnAssetRecordsItsCondition() throws Exception {
        String token = adminToken();
        String categoryId = category(token, "COMPUTER", "0.25", "4");
        String assetId = asset(token, categoryId, "Projector", "24500");

        assign(token, assetId, "EXTERNAL", "Guest lecturer");

        mockMvc.perform(patch("/api/v1/assets/{id}/return", assetId)
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"conditionIn":"FAIR","notes":"Lamp dimmer than it was"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.holderName").value("Guest lecturer"))
                .andExpect(jsonPath("$.data.conditionIn").value("FAIR"))
                .andExpect(jsonPath("$.data.open").value(false));

        mockMvc.perform(get("/api/v1/assets/{id}", assetId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.asset.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.asset.conditionStatus").value("FAIR"))
                .andExpect(jsonPath("$.data.asset.holderName").doesNotExist())
                // The custody trail keeps both halves of the loan.
                .andExpect(jsonPath("$.data.history.length()").value(1));

        mockMvc.perform(get("/api/v1/assets/held")
                        .param("holderType", "EXTERNAL")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    @DisplayName("an asset cannot be sent for servicing while it is out")
    void servicingWaitsForTheAssetToComeBack() throws Exception {
        String token = adminToken();
        String categoryId = category(token, "COMPUTER", "0.25", "4");
        String assetId = asset(token, categoryId, "Projector", "24500");

        String jobId = schedule(token, assetId, "REPAIR", "Bulb out");
        assign(token, assetId, "EXTERNAL", "Guest lecturer");

        mockMvc.perform(patch("/api/v1/assets/maintenance/{jobId}/start", jobId)
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity());

        mockMvc.perform(patch("/api/v1/assets/{id}/return", assetId)
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"conditionIn":"GOOD"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/assets/maintenance/{jobId}/start", jobId)
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/assets/overview")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.inMaintenance").value(1))
                .andExpect(jsonPath("$.data.available").value(0));

        mockMvc.perform(patch("/api/v1/assets/maintenance/{jobId}/complete", jobId)
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"cost":3200,"performedBy":"Rajesh Karki"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.cost").value(3200.0));

        mockMvc.perform(get("/api/v1/assets/overview")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.inMaintenance").value(0))
                .andExpect(jsonPath("$.data.available").value(1));
    }

    @Test
    @DisplayName("one holder identifier at a time")
    void aHolderMustBeOnePerson() throws Exception {
        String token = adminToken();
        String categoryId = category(token, "COMPUTER", "0.25", "4");
        String assetId = asset(token, categoryId, "Projector", "24500");

        UUID employeeId = employee(token, "ast-emp-002");
        UUID studentId = student(token, "twopeople.student@example.test");

        java.util.Map<String, Object> request = new java.util.LinkedHashMap<>();
        request.put("holderType", "EMPLOYEE");
        request.put("holderEmployeeId", employeeId);
        request.put("holderStudentId", studentId);
        request.put("holderName", "Two people");

        mockMvc.perform(post("/api/v1/assets/{id}/assignments", assetId)
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("an asset that goes missing while it is out stops being held by anybody")
    void anAssetOutOnLoanCanBeReportedMissing() throws Exception {
        String token = adminToken();
        String categoryId = category(token, "COMPUTER", "0.25", "4");
        String assetId = asset(token, categoryId, "Projector", "24500");
        assign(token, assetId, "EXTERNAL", "Guest lecturer");

        String reason = "Left in the auditorium after a power cut";
        mockMvc.perform(patch("/api/v1/assets/{id}/lost", assetId)
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                java.util.Map.of("reason", reason))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("LOST"))
                .andExpect(jsonPath("$.data.lostReason").value(reason))
                .andExpect(jsonPath("$.data.lostAt").exists());

        // The loan ends at the moment it went missing, rather than staying open forever.
        mockMvc.perform(get("/api/v1/assets/{id}", assetId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.history[0].returnedAt").exists())
                .andExpect(jsonPath("$.data.history[0].notes").value(
                        org.hamcrest.Matchers.containsString("Reported missing")));

        mockMvc.perform(get("/api/v1/assets/held")
                        .param("holderType", "EXTERNAL")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.data").isEmpty());

        mockMvc.perform(get("/api/v1/assets/overview")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lost").value(1))
                .andExpect(jsonPath("$.data.available").value(0));

        // Nothing may be handed out while the register does not know where it is.
        mockMvc.perform(post("/api/v1/assets/{id}/assignments", assetId)
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "holderType", "EXTERNAL",
                                "holderName", "Someone else"))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("a lost asset comes back onto the shelf but keeps the story of the loss")
    void aLostAssetIsFoundAgainButKeepsTheStory() throws Exception {
        String token = adminToken();
        String categoryId = category(token, "COMPUTER", "0.25", "4");
        String assetId = asset(token, categoryId, "Projector", "24500");
        String reason = "Walked out of the science block";

        mockMvc.perform(patch("/api/v1/assets/{id}/lost", assetId)
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                java.util.Map.of("reason", reason))))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/assets/{id}/found", assetId)
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"location":"Science block store","conditionStatus":"FAIR"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.location").value("Science block store"))
                .andExpect(jsonPath("$.data.conditionStatus").value("FAIR"))
                // Still on the row: an item that went missing once is not the same as one that
                // never did, and the status is what says whether it is missing right now.
                .andExpect(jsonPath("$.data.lostReason").value(reason))
                .andExpect(jsonPath("$.data.lostAt").exists());

        mockMvc.perform(get("/api/v1/assets/overview")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lost").value(0))
                .andExpect(jsonPath("$.data.available").value(1));

        mockMvc.perform(patch("/api/v1/assets/{id}/found", assetId)
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("writing an asset off leaves an account of it, not a gap")
    void writingAnAssetOffLeavesAnAccountOfIt() throws Exception {
        String token = adminToken();
        String categoryId = category(token, "COMPUTER", "0.25", "4");
        String assetId = asset(token, categoryId, "Projector", "24500");

        mockMvc.perform(patch("/api/v1/assets/{id}/dispose", assetId)
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"method":"SALE","value":1500,"notes":"Sold to the recycler"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISPOSED"))
                .andExpect(jsonPath("$.data.disposalMethod").value("SALE"))
                .andExpect(jsonPath("$.data.disposalValue").value(1500.0))
                .andExpect(jsonPath("$.data.disposalNotes").value("Sold to the recycler"))
                .andExpect(jsonPath("$.data.disposedAt").exists());

        mockMvc.perform(get("/api/v1/assets/overview")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.disposed").value(1))
                .andExpect(jsonPath("$.data.available").value(0));

        // Disposal is an ending: the row stays, but nothing else may touch it again.
        for (String path : java.util.List.of(
                "dispose", "lost")) {
            mockMvc.perform(patch("/api/v1/assets/{id}/" + path, assetId)
                            .header("Authorization", bearer(token))
                            .contentType("application/json")
                            .content(path.equals("lost")
                                    ? "{\"reason\":\"again\"}"
                                    : "{\"method\":\"SCRAPPED\"}"))
                    .andExpect(status().isUnprocessableEntity());
        }

        mockMvc.perform(post("/api/v1/assets/{id}/assignments", assetId)
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "holderType", "EXTERNAL",
                                "holderName", "Anyone"))))
                .andExpect(status().isUnprocessableEntity());

        mockMvc.perform(get("/api/v1/assets")
                        .param("status", "DISPOSED")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.data[0].id").value(assetId));
    }

    @Test
    @DisplayName("an asset written off while it is out ends the loan it was on")
    void anAssetWrittenOffWhileOutEndsTheLoan() throws Exception {
        String token = adminToken();
        String categoryId = category(token, "COMPUTER", "0.25", "4");
        String assetId = asset(token, categoryId, "Projector", "24500");
        assign(token, assetId, "EXTERNAL", "Guest lecturer");

        mockMvc.perform(patch("/api/v1/assets/{id}/dispose", assetId)
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("{\"method\":\"WRITE_OFF\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISPOSED"));

        mockMvc.perform(get("/api/v1/assets/{id}", assetId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.history[0].returnedAt").exists())
                .andExpect(jsonPath("$.data.history[0].notes").value(
                        org.hamcrest.Matchers.containsString("Written off")));

        mockMvc.perform(get("/api/v1/assets/held")
                        .param("holderType", "EXTERNAL")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.data").isEmpty());
    }

    @Test
    @DisplayName("servicing has to be settled before an asset can go or leave")
    void servicingHasToBeSettledFirst() throws Exception {
        String token = adminToken();
        String categoryId = category(token, "COMPUTER", "0.25", "4");
        String assetId = asset(token, categoryId, "Projector", "24500");
        String jobId = schedule(token, assetId, "REPAIR", "Bulb out");

        mockMvc.perform(patch("/api/v1/assets/maintenance/{jobId}/start", jobId)
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/assets/{id}/lost", assetId)
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("{\"reason\":\"Gone from the workshop\"}"))
                .andExpect(status().isUnprocessableEntity());

        mockMvc.perform(patch("/api/v1/assets/{id}/dispose", assetId)
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("{\"method\":\"SCRAPPED\"}"))
                .andExpect(status().isUnprocessableEntity());

        mockMvc.perform(patch("/api/v1/assets/maintenance/{jobId}/complete", jobId)
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("{\"cost\":3200,\"performedBy\":\"Rajesh Karki\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/assets/{id}/dispose", assetId)
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("{\"method\":\"WRITE_OFF\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISPOSED"));
    }

    @Test
    @DisplayName("a missing asset needs a reason, and only a missing one may be found")
    void aReasonIsRequiredToReportAMissingAsset() throws Exception {
        String token = adminToken();
        String categoryId = category(token, "COMPUTER", "0.25", "4");
        String assetId = asset(token, categoryId, "Projector", "24500");

        mockMvc.perform(patch("/api/v1/assets/{id}/lost", assetId)
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("{\"reason\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.reason").exists());

        mockMvc.perform(patch("/api/v1/assets/{id}/found", assetId)
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("{\"location\":\"Store\"}"))
                .andExpect(status().isUnprocessableEntity());

        // An unknown way of letting go of something is a validation problem, not a server error.
        mockMvc.perform(patch("/api/v1/assets/{id}/dispose", assetId)
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("{\"method\":\"THROWN_IN_RIVER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.method").exists());
    }

    private UUID employee(String token, String code) throws Exception {
        String body = mockMvc.perform(post("/api/v1/hr/employees")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "employeeCode", code,
                                "firstName", "Asha",
                                "lastName", "Rai",
                                "joinDate", "2023-04-01",
                                "employmentType", "FULL_TIME"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).path("data").path("id").asText());
    }

    private UUID student(String token, String email) throws Exception {
        String body = mockMvc.perform(post("/api/v1/students")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "firstName", "Ananya",
                                "lastName", "Sharma",
                                "email", email,
                                "phone", "+977-9800000010",
                                "nationality", "Nepal",
                                "status", "ACTIVE"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).path("data").path("id").asText());
    }

    // ------------------------------------------------------------------- helpers

    private String category(String token, String code, String rate, String life) throws Exception {
        String body = mockMvc.perform(post("/api/v1/assets/categories")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "code", code,
                                "name", code + " things",
                                "depreciationRate", new BigDecimal(rate),
                                "usefulLifeYears", Integer.parseInt(life)))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asText();
    }

    private String asset(String token, String categoryId, String name, String cost)
            throws Exception {
        String body = mockMvc.perform(post("/api/v1/assets")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "name", name,
                                "categoryId", categoryId,
                                "purchaseCost", new BigDecimal(cost),
                                "purchaseDate", "2024-01-15",
                                "conditionStatus", "NEW"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asText();
    }

    private void assign(String token, String assetId, String holderType, String holderName)
            throws Exception {
        assign(token, assetId, holderType, holderName, null, null, null);
    }

    private void assign(String token, String assetId, String holderType, String holderName,
                        UUID userId, UUID employeeId, UUID studentId) throws Exception {
        java.util.Map<String, Object> request = new java.util.LinkedHashMap<>();
        request.put("holderType", holderType);
        request.put("holderName", holderName);
        if (userId != null) request.put("holderUserId", userId);
        if (employeeId != null) request.put("holderEmployeeId", employeeId);
        if (studentId != null) request.put("holderStudentId", studentId);

        mockMvc.perform(post("/api/v1/assets/{id}/assignments", assetId)
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    private String schedule(String token, String assetId, String type, String description)
            throws Exception {
        String body = mockMvc.perform(post("/api/v1/assets/{id}/maintenance", assetId)
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "type", type,
                                "description", description,
                                "performedBy", "Rajesh Karki"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asText();
    }
}

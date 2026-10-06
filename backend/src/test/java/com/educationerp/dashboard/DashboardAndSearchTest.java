package com.educationerp.dashboard;

import com.educationerp.institution.ModuleKey;
import com.educationerp.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Checks that the dashboards and the search box tell the truth about what they show.
 *
 * <p>Both are hand-written queries guarded by permissions, and both have ways of being wrong that
 * no other test would notice: a count that reads the wrong column and always says one, a section
 * that shows figures to somebody who may not have them, an alert about a module the school has
 * switched off. Each is checked here against a real number rather than against its own shape.
 */
class DashboardAndSearchTest extends IntegrationTest {

    @Test
    @DisplayName("the principal's dashboard builds every section the administrator may see")
    void principalDashboardBuilds() throws Exception {
        JsonNode data = read("/api/v1/dashboards/principal");

        assertThat(data.path("sections").size()).isGreaterThan(3);
        assertThat(data.path("sections").toString()).contains("Students", "Staff");
        // Every figure says what it counts, so nobody has to guess what a number means.
        assertThat(data.path("sections").toString()).contains("detail");
    }

    @Test
    @DisplayName("the accountant's dashboard builds and labels what it shows")
    void accountantDashboardBuilds() throws Exception {
        JsonNode data = read("/api/v1/dashboards/accountant");

        assertThat(data.path("sections").toString()).contains("Collection", "Receivables");
        // The administrator holds every permission, so both optional sections are present.
        assertThat(data.path("sections").toString()).contains("Refunds", "Expenditure");
    }

    @Test
    @DisplayName("the low stock alert counts items, not groups of stock rows")
    void lowStockAlertCountsItems() throws Exception {
        testData.enableModule(ModuleKey.INVENTORY);
        // Three items, all of them at or below their reorder level. The count of the alert must be
        // three: reading the first column of the grouped query would always have answered one.
        jdbcTemplate.update("""
                insert into items (id, code, name, unit, reorder_level, reorder_quantity,
                                   track_batch, track_expiry, is_active, version,
                                   created_at, updated_at)
                select gen_random_uuid(), 'ALERT-' || n, 'Alert item ' || n, 'EACH', 10, 5,
                       false, false, true, 0, now(), now()
                from generate_series(1, 3) n
                """);
        // Stock is held at a store, and a store is what makes the row meaningful.
        jdbcTemplate.update("""
                insert into stores (id, code, name, is_active, created_at, updated_at)
                values (gen_random_uuid(), 'MAIN', 'Main store', true, now(), now())
                """);
        jdbcTemplate.update("""
                insert into stock (id, item_id, store_id, quantity, version, created_at, updated_at)
                select gen_random_uuid(), i.id,
                       (select id from stores where code = 'MAIN'), 1, 0, now(), now()
                from items i where i.code like 'ALERT-%'
                """);

        JsonNode data = read("/api/v1/dashboards/principal");
        JsonNode alert = find(data.path("alerts"), "inventory");

        assertThat(alert.path("count").asLong())
                .as("three low items must be reported as three, not as one group")
                .isEqualTo(3);
    }

    @Test
    @DisplayName("no alert is raised for a module the institution has switched off")
    void alertsRespectDisabledModules() throws Exception {
        jdbcTemplate.update("""
                insert into items (id, code, name, unit, reorder_level, reorder_quantity,
                                   track_batch, track_expiry, is_active, version,
                                   created_at, updated_at)
                values (gen_random_uuid(), 'OFF-1', 'Switched off', 'EACH', 10, 5,
                        false, false, true, 0, now(), now())
                """);
        jdbcTemplate.update("""
                insert into stores (id, code, name, is_active, created_at, updated_at)
                values (gen_random_uuid(), 'MAIN', 'Main store', true, now(), now())
                """);
        jdbcTemplate.update("""
                insert into stock (id, item_id, store_id, quantity, version, created_at, updated_at)
                select gen_random_uuid(), i.id,
                       (select id from stores where code = 'MAIN'), 0, 0, now(), now()
                from items i where i.code = 'OFF-1'
                """);
        testData.enableModule(ModuleKey.INVENTORY);
        assertThat(find(read("/api/v1/dashboards/principal").path("alerts"), "inventory")
                .path("count").asLong()).isGreaterThan(0);

        // Switching the module off must remove the alert, not merely hide it in the UI.
        jdbcTemplate.update("update module_settings set enabled = false "
                + "where module_key = 'INVENTORY'");

        assertThat(find(read("/api/v1/dashboards/principal").path("alerts"), "inventory") == null)
                .as("a disabled module cannot have anything to say to the principal")
                .isTrue();
    }

    @Test
    @DisplayName("global search answers across the sources the caller may read")
    void searchRuns() throws Exception {
        seedSearchableStudent();

        JsonNode data = json(mockMvc.perform(get("/api/v1/search")
                        .param("q", "sharma").header("Authorization", bearer(adminToken())))
                .andExpect(status().isOk()).andReturn()).path("data");

        assertThat(data.path("term").asText()).isEqualTo("sharma");
        assertThat(data.path("count").asInt())
                .as("two people are named Sharma, so both must be counted")
                .isEqualTo(data.path("results").size());
        assertThat(data.path("results").toString()).contains("Ananya").contains("Ravi");
    }

    /** A person the search can actually match, since no other fixture names anyone. */
    private void seedSearchableStudent() {
        jdbcTemplate.update("""
                insert into students (id, student_number, first_name, last_name, email,
                                      status, version, created_at, updated_at)
                values (gen_random_uuid(), 'STU-SEARCH-1', 'Ananya', 'Sharma',
                        'ananya.sharma@example.test', 'ENROLLED', 0, now(), now())
                """);
        jdbcTemplate.update("""
                insert into employees (id, employee_code, first_name, last_name, email,
                                       join_date, status, version, created_at, updated_at)
                values (gen_random_uuid(), 'EMP-SEARCH-1', 'Ravi', 'Sharma',
                        'ravi.sharma@example.test', current_date, 'ACTIVE', 0, now(), now())
                """);
    }

    @Test
    @DisplayName("search refuses a term too short to be worth running")
    void searchRefusesTinyTerms() throws Exception {
        mockMvc.perform(get("/api/v1/search")
                        .param("q", "a").header("Authorization", bearer(adminToken())))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("grouped search reports one group per source that answered")
    void groupedSearchRuns() throws Exception {
        seedSearchableStudent();

        JsonNode groups = json(mockMvc.perform(get("/api/v1/search/grouped")
                        .param("q", "sharma").header("Authorization", bearer(adminToken())))
                .andExpect(status().isOk()).andReturn()).path("data");

        assertThat(groups.isObject()).isTrue();
        assertThat(groups.path("Students").isArray()).as("students answer the grouped search").isTrue();
        assertThat(groups.path("Employees").isArray()).as("employees answer too").isTrue();
    }

    @Test
    @DisplayName("searching one source returns only that source")
    void singleSourceSearchRuns() throws Exception {
        seedSearchableStudent();

        JsonNode hits = json(mockMvc.perform(get("/api/v1/search/{source}", "STUDENT")
                        .param("q", "sharma").header("Authorization", bearer(adminToken())))
                .andExpect(status().isOk()).andReturn()).path("data");

        List<String> sources = new ArrayList<>();
        hits.forEach(hit -> sources.add(hit.path("source").asText()));
        assertThat(sources).containsExactly("STUDENT");
    }

    @Test
    @DisplayName("a source belonging to a disabled module is not searched")
    void searchRespectsDisabledModules() throws Exception {
        testData.enableModule(ModuleKey.LIBRARY);
        jdbcTemplate.update("""
                insert into publishers (id, name, created_at, updated_at)
                values (gen_random_uuid(), 'Zephyr Publishing', now(), now())
                """);
        jdbcTemplate.update("""
                insert into books (id, title, isbn, publisher_id, is_reference, version,
                                   created_at, updated_at)
                values (gen_random_uuid(), 'Zephyr Atlas', '9780000000001',
                        (select id from publishers where name = 'Zephyr Publishing'),
                        false, 0, now(), now())
                """);
        assertThat(searchFor("zephyr").path("results").toString()).contains("BOOK");

        jdbcTemplate.update("update module_settings set enabled = false "
                + "where module_key = 'LIBRARY'");
        assertThat(searchFor("zephyr").path("results").toString())
                .as("a book in a disabled module must not surface")
                .doesNotContain("BOOK");
    }

    // ------------------------------------------------------------------ helpers

    private JsonNode searchFor(String term) throws Exception {
        return json(mockMvc.perform(get("/api/v1/search")
                        .param("q", term).header("Authorization", bearer(adminToken())))
                .andExpect(status().isOk()).andReturn()).path("data");
    }

    /** Named read() rather than get() so it cannot shadow the MockMvc request builder. */
    private JsonNode read(String path) throws Exception {
        return json(mockMvc.perform(get(path).header("Authorization", bearer(adminToken())))
                .andExpect(status().isOk()).andReturn()).path("data");
    }

    /** The alert of the given kind, or null when there is none. */
    private JsonNode find(JsonNode alerts, String kind) {
        for (JsonNode alert : alerts) {
            if (kind.equals(alert.path("kind").asText())) {
                return alert;
            }
        }
        return null;
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
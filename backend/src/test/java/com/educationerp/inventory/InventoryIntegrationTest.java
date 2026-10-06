package com.educationerp.inventory;

import com.educationerp.institution.ModuleKey;
import com.educationerp.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Stock movement and the money it implies.
 *
 * <p>The weighted average cost is the part worth pinning down. Receiving stock used to
 * apply the movement first and then work out the new average, so the incoming quantity sat
 * in both halves of the sum and the average halved on every receipt. The shelf ended up
 * valued at half of what it cost, and the overview and low stock figures inherited it.
 */
class InventoryIntegrationTest extends IntegrationTest {

    @BeforeEach
    void enableInventory() {
        testData.enableModule(ModuleKey.INVENTORY);
    }

    @Test
    @DisplayName("a receipt values the shelf at what it actually cost")
    void receivingStockKeepsThePurchaseCost() throws Exception {
        String token = adminToken();
        String storeId = store(token, "MAIN");
        String itemId = item(token, "CHLK-001", "Chalk box");

        String purchaseId = purchase(token, storeId, itemId, 100, "45");
        String lineId = firstLine(token, purchaseId);
        receive(token, purchaseId, storeId, lineId, 100);

        assertThat(averageCost(token, storeId, itemId)).isEqualByComparingTo("45.00");
        assertThat(stockValue(token, storeId)).isEqualByComparingTo("4500.00");
    }

    @Test
    @DisplayName("a second receipt at a new price re-averages the shelf")
    void secondReceiptReAveragesTheShelf() throws Exception {
        String token = adminToken();
        String storeId = store(token, "MAIN");
        String itemId = item(token, "GLVE-001", "Nitrile gloves");

        String first = purchase(token, storeId, itemId, 100, "45");
        receive(token, first, storeId, firstLine(token, first), 100);

        String second = purchase(token, storeId, itemId, 100, "30");
        receive(token, second, storeId, firstLine(token, second), 100);

        // (45 x 100 + 30 x 100) / 200
        assertThat(averageCost(token, storeId, itemId)).isEqualByComparingTo("37.50");
        assertThat(stockValue(token, storeId)).isEqualByComparingTo("7500.00");
    }

    @Test
    @DisplayName("a part delivery leaves the rest of the order outstanding")
    void partDeliveryKeepsTheRemainderOutstanding() throws Exception {
        String token = adminToken();
        String storeId = store(token, "MAIN");
        String itemId = item(token, "PAPER-001", "Exam paper");

        String purchaseId = purchase(token, storeId, itemId, 100, "10");
        String lineId = firstLine(token, purchaseId);
        receive(token, purchaseId, storeId, lineId, 60);

        mockMvc.perform(get("/api/v1/inventory/purchases/{id}", purchaseId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PARTIAL"))
                .andExpect(jsonPath("$.data.items[0].quantityReceived").value(60.0))
                .andExpect(jsonPath("$.data.items[0].outstanding").value(40.0))
                .andExpect(jsonPath("$.data.items[0].fullyReceived").value(false));

        assertThat(quantity(token, storeId, itemId)).isEqualByComparingTo("60.00");
    }

    @Test
    @DisplayName("a full delivery closes the order")
    void fullDeliveryClosesTheOrder() throws Exception {
        String token = adminToken();
        String storeId = store(token, "MAIN");
        String itemId = item(token, "PEN-001", "Board marker");

        String purchaseId = purchase(token, storeId, itemId, 40, "25");
        receive(token, purchaseId, storeId, firstLine(token, purchaseId), 40);

        mockMvc.perform(get("/api/v1/inventory/purchases/{id}", purchaseId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RECEIVED"))
                .andExpect(jsonPath("$.data.receivedOn").isNotEmpty());
    }

    @Test
    @DisplayName("a stocktake corrects the ledger to what was counted")
    void stocktakeSetsTheLedgerToTheCountedQuantity() throws Exception {
        String token = adminToken();
        String storeId = store(token, "MAIN");
        String itemId = item(token, "ERAS-001", "Eraser");

        String purchaseId = purchase(token, storeId, itemId, 50, "5");
        receive(token, purchaseId, storeId, firstLine(token, purchaseId), 50);

        mockMvc.perform(post("/api/v1/inventory/adjustments")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"storeId":"%s","itemId":"%s","countedQuantity":47,"reason":"Annual stocktake"}
                                """.formatted(storeId, itemId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.previousQuantity").value(50.0))
                .andExpect(jsonPath("$.data.countedQuantity").value(47.0))
                .andExpect(jsonPath("$.data.difference").value(-3.0));

        assertThat(quantity(token, storeId, itemId)).isEqualByComparingTo("47.00");
    }

    @Test
    @DisplayName("issuing stock leaves the store and gives it back on return")
    void issueAndReturnMoveStockBothWays() throws Exception {
        String token = adminToken();
        String storeId = store(token, "MAIN");
        String itemId = item(token, "FILE-001", "File folder");

        String purchaseId = purchase(token, storeId, itemId, 30, "12");
        receive(token, purchaseId, storeId, firstLine(token, purchaseId), 30);

        String issueId = mockMvc.perform(post("/api/v1/inventory/issues")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"itemId":"%s","storeId":"%s","quantity":10,
                                 "issuedToType":"DEPARTMENT","issuedToName":"Science","reason":"Term start"}
                                """.formatted(itemId, storeId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.issueNumber").isNotEmpty())
                .andExpect(jsonPath("$.data.status").value("ISSUED"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(quantity(token, storeId, itemId)).isEqualByComparingTo("20.00");

        String id = objectMapper.readTree(issueId).path("data").path("id").asText();
        mockMvc.perform(patch("/api/v1/inventory/issues/{id}/return", id)
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"issueId":"%s","quantityReturned":4,"reason":"Unused"}
                                """.formatted(id)))
                .andExpect(status().isOk());

        assertThat(quantity(token, storeId, itemId)).isEqualByComparingTo("24.00");
    }

    @Test
    @DisplayName("a transfer only lands in the receiving store once it completes")
    void transferCompletesIntoTheReceivingStore() throws Exception {
        String token = adminToken();
        String from = store(token, "MAIN");
        String to = store(token, "SCI");
        String itemId = item(token, "LAB-001", "Bunsen burner");

        String purchaseId = purchase(token, from, itemId, 10, "300");
        receive(token, purchaseId, from, firstLine(token, purchaseId), 10);

        String transferId = mockMvc.perform(post("/api/v1/inventory/transfers")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"itemId":"%s","fromStoreId":"%s","toStoreId":"%s","quantity":4,
                                 "reason":"Rebalancing"}
                                """.formatted(itemId, from, to)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("REQUESTED"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Requested stock has not left the sending store yet.
        assertThat(quantity(token, from, itemId)).isEqualByComparingTo("10.00");
        assertThat(quantity(token, to, itemId)).isEqualByComparingTo("0.00");

        String id = objectMapper.readTree(transferId).path("data").path("id").asText();
        mockMvc.perform(patch("/api/v1/inventory/transfers/{id}/dispatch", id)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISPATCHED"));

        assertThat(quantity(token, from, itemId)).isEqualByComparingTo("6.00");

        mockMvc.perform(patch("/api/v1/inventory/transfers/{id}/complete", id)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        assertThat(quantity(token, to, itemId)).isEqualByComparingTo("4.00");
    }

    @Test
    @DisplayName("stock cannot be issued beyond what the store holds")
    void issuingMoreThanTheStoreHoldsIsRefused() throws Exception {
        String token = adminToken();
        String storeId = store(token, "MAIN");
        String itemId = item(token, "SCIS-001", "Scissors");

        String purchaseId = purchase(token, storeId, itemId, 5, "90");
        receive(token, purchaseId, storeId, firstLine(token, purchaseId), 5);

        mockMvc.perform(post("/api/v1/inventory/issues")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"itemId":"%s","storeId":"%s","quantity":6,
                                 "issuedToType":"DEPARTMENT","issuedToName":"Art"}
                                """.formatted(itemId, storeId)))
                .andExpect(status().is4xxClientError());

        assertThat(quantity(token, storeId, itemId)).isEqualByComparingTo("5.00");
    }

    @Test
    @DisplayName("an item below its reorder level is reported as needing a reorder")
    void lowStockIsReported() throws Exception {
        String token = adminToken();
        String storeId = store(token, "MAIN");
        String itemId = itemWithReorder(token, "TONE-001", "Printer toner", 10, 25);

        mockMvc.perform(get("/api/v1/inventory/low-stock")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.itemId == '%s')].suggestedOrderQuantity".formatted(itemId))
                        .value(25.0));
    }

    @Test
    @DisplayName("transfers list every status when no status is asked for")
    void unfilteredTransferListIsNotEmpty() throws Exception {
        String token = adminToken();
        String from = store(token, "MAIN");
        String to = store(token, "SCI");
        String itemId = item(token, "GLOV-001", "Glove box");

        String purchaseId = purchase(token, from, itemId, 10, "70");
        receive(token, purchaseId, from, firstLine(token, purchaseId), 10);

        mockMvc.perform(post("/api/v1/inventory/transfers")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"itemId":"%s","fromStoreId":"%s","toStoreId":"%s","quantity":2}
                                """.formatted(itemId, from, to)))
                .andExpect(status().isCreated());

        // The unfiltered list used to hand a null status to a byStatus query, which
        // matches nothing, so the transfers screen was permanently empty.
        mockMvc.perform(get("/api/v1/inventory/transfers")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc.perform(get("/api/v1/inventory/transfers")
                        .param("status", "REQUESTED")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc.perform(get("/api/v1/inventory/transfers")
                        .param("status", "COMPLETED")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    // ------------------------------------------------------------------- helpers

    @Test
    @DisplayName("two checkouts cannot both spend the same stock")
    void concurrentIssuesCannotSpendTheSameStock() throws Exception {
        String token = adminToken();
        String storeId = store(token, "MAIN");
        String itemId = item(token, "REAM-001", "A4 paper");

        String purchaseId = purchase(token, storeId, itemId, 10, "120");
        receive(token, purchaseId, storeId, firstLine(token, purchaseId), 10);
        assertThat(quantity(token, storeId, itemId)).isEqualByComparingTo("10");

        // Two desks and one shelf: each asks for six of the ten reams. The availability check
        // used to be a read of its own and the decrement a separate write, so both desks could
        // be told the stock was there and the shelf went out twice. Exactly one may win.
        CyclicBarrier bothAtTheShelf = new CyclicBarrier(2);
        ExecutorService desks = Executors.newFixedThreadPool(2);
        List<Integer> statuses = new ArrayList<>();
        List<String> bodies = new ArrayList<>();
        try {
            List<Future<String>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                results.add(desks.submit(() -> {
                    bothAtTheShelf.await(10, TimeUnit.SECONDS);
                    var response = mockMvc.perform(post("/api/v1/inventory/issues")
                                    .header("Authorization", bearer(token))
                                    .contentType("application/json")
                                    .content("""
                                            {"itemId":"%s","storeId":"%s","quantity":6,
                                             "issuedToType":"DEPARTMENT","issuedToName":"Science",
                                             "reason":"Term start"}
                                            """.formatted(itemId, storeId)))
                            .andReturn().getResponse();
                    return response.getStatus() + "\t" + response.getContentAsString();
                }));
            }
            for (Future<String> each : results) {
                String[] parts = each.get(60, TimeUnit.SECONDS).split("\t", 2);
                statuses.add(Integer.parseInt(parts[0]));
                bodies.add(parts[1]);
            }
        } finally {
            desks.shutdownNow();
        }

        assertThat(statuses).filteredOn(status -> status == 201)
                .as("exactly one of the two checkouts may be taken up").hasSize(1);
        assertThat(statuses).filteredOn(status -> status != 201)
                .as("the other is refused rather than quietly oversold").hasSize(1);
        assertThat(quantity(token, storeId, itemId)).isEqualByComparingTo("4");

        // Integrity alone is not the whole answer. Stock is also guarded by the row's version
        // column, so an unguarded checkout does not oversell either — it fails, but it fails as
        // an optimistic-locking conflict the desk cannot act on. The loser of a race for the
        // shelf should be told how much is there instead.
        String refused = bodies.get(statuses.indexOf(201) == 0 ? 1 : 0);
        assertThat(statuses).contains(422);
        assertThat(refused)
                .as("the refused checkout explains itself rather than erroring out")
                .contains("Only 4 PIECE of A4 paper are in");
    }

    private String store(String token, String code) throws Exception {
        return objectMapper
                .readTree(mockMvc.perform(post("/api/v1/inventory/stores")
                                .header("Authorization", bearer(token))
                                .contentType("application/json")
                                .content("""
                                        {"code":"%s","name":"Store %s"}
                                        """.formatted(code, code)))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString())
                .path("data").path("id").asText();
    }

    private String item(String token, String code, String name) throws Exception {
        return itemWithReorder(token, code, name, 0, 0);
    }

    private String itemWithReorder(String token, String code, String name,
                                   int reorderLevel, int reorderQuantity) throws Exception {
        return objectMapper
                .readTree(mockMvc.perform(post("/api/v1/inventory/items")
                                .header("Authorization", bearer(token))
                                .contentType("application/json")
                                .content("""
                                        {"code":"%s","name":"%s","unit":"PIECE",
                                         "reorderLevel":%d,"reorderQuantity":%d,"active":true}
                                        """.formatted(code, name, reorderLevel, reorderQuantity)))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString())
                .path("data").path("id").asText();
    }

    private String purchase(String token, String storeId, String itemId,
                            int quantity, String unitCost) throws Exception {
        String id = objectMapper
                .readTree(mockMvc.perform(post("/api/v1/inventory/purchases")
                                .header("Authorization", bearer(token))
                                .contentType("application/json")
                                .content("""
                                        {"supplierName":"Khwopa Traders","storeId":"%s",
                                         "taxAmount":0,"otherCosts":0,
                                         "items":[{"itemId":"%s","quantityOrdered":%d,"unitCost":%s}]}
                                        """.formatted(storeId, itemId, quantity, unitCost)))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.data.status").value("DRAFT"))
                        .andReturn().getResponse().getContentAsString())
                .path("data").path("id").asText();

        mockMvc.perform(patch("/api/v1/inventory/purchases/{id}/place-order", id)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ORDERED"));
        return id;
    }

    private String firstLine(String token, String purchaseId) throws Exception {
        return objectMapper
                .readTree(mockMvc.perform(get("/api/v1/inventory/purchases/{id}", purchaseId)
                                .header("Authorization", bearer(token)))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString())
                .path("data").path("items").get(0).path("id").asText();
    }

    private void receive(String token, String purchaseId, String storeId,
                         String lineId, int quantity) throws Exception {
        mockMvc.perform(post("/api/v1/inventory/purchases/{id}/receive", purchaseId)
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"storeId":"%s","lines":[{"purchaseItemId":"%s","quantityReceived":%d}]}
                                """.formatted(storeId, lineId, quantity)))
                .andExpect(status().isOk());
    }

    private BigDecimal quantity(String token, String storeId, String itemId) throws Exception {
        for (var row : objectMapper
                .readTree(mockMvc.perform(get("/api/v1/inventory/stores/{id}/stock", storeId)
                                .header("Authorization", bearer(token)))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString())
                .path("data")) {
            if (row.path("itemId").asText().equals(itemId)) {
                return row.path("quantity").decimalValue();
            }
        }
        return BigDecimal.ZERO;
    }

    private BigDecimal averageCost(String token, String storeId, String itemId) throws Exception {
        return objectMapper
                .readTree(mockMvc.perform(get("/api/v1/inventory/stores/{id}/stock", storeId)
                                .header("Authorization", bearer(token)))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString())
                .path("data").get(0).path("averageCost").decimalValue();
    }

    private BigDecimal stockValue(String token, String storeId) throws Exception {
        return objectMapper
                .readTree(mockMvc.perform(get("/api/v1/inventory/overview")
                                .header("Authorization", bearer(token)))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString())
                .path("data").path("stockValue").decimalValue();
    }
}
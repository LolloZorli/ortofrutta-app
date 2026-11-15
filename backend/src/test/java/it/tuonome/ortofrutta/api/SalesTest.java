package it.tuonome.ortofrutta.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Cassa: vendite con più prodotti, storno, corrispettivi per aliquota IVA e ritiro delle prenotazioni. */
class SalesTest extends IntegrationTestBase {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    @Test
    void multiItemSaleTotalsAndVoidRestoresStock() throws Exception {
        String admin = admin();
        long cash = lookupId(admin, "PAYMENT_METHOD", "CASH");
        long a = createProduct(admin, "Prodotto cassa A", "KG", "2.00", "IVA_4");
        long b = createProduct(admin, "Prodotto cassa B", "KG", "2.00", "IVA_4");
        receive(admin, a, "10", "1.00");
        receive(admin, b, "10", "1.00");

        String body = mvc.perform(post("/api/sales").header(AUTH, admin).contentType(JSON)
                        .content("{\"paymentMethodId\":" + cash + ",\"items\":[{\"productId\":" + a
                                + ",\"quantity\":2},{\"productId\":" + b + ",\"quantity\":3}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.total").value(10.0))
                .andExpect(jsonPath("$.lines.length()").value(2))
                .andExpect(jsonPath("$.paymentMethod").value("Contanti"))
                .andReturn().getResponse().getContentAsString();
        long saleId = json(body).get("id").asLong();
        assertThat(stockOf(admin, a)).isEqualTo(8.0);
        assertThat(stockOf(admin, b)).isEqualTo(7.0);

        // storno: la merce torna nei lotti
        mvc.perform(post("/api/sales/" + saleId + "/void").header(AUTH, admin).contentType(JSON)
                        .content("{\"reason\":\"Errore di battitura\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VOIDED"));
        assertThat(stockOf(admin, a)).isEqualTo(10.0);
        assertThat(stockOf(admin, b)).isEqualTo(10.0);

        // non si storna due volte
        mvc.perform(post("/api/sales/" + saleId + "/void").header(AUTH, admin).contentType(JSON)
                        .content("{\"reason\":\"Ancora\"}"))
                .andExpect(status().isConflict());

        // e lo storno resta nel registro delle attività
        mvc.perform(get("/api/audit?size=50").header(AUTH, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.action == 'SALE_VOID')]").isNotEmpty());
    }

    @Test
    void paymentMethodMustExist() throws Exception {
        String admin = admin();
        long productId = createProduct(admin, "Prodotto senza pagamento", "KG", "2.00", "IVA_4");
        receive(admin, productId, "5", "1.00");

        mvc.perform(post("/api/sales").header(AUTH, admin).contentType(JSON)
                        .content("{\"paymentMethodId\":999999,\"items\":[{\"productId\":" + productId + ",\"quantity\":1}]}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/sales").header(AUTH, admin).contentType(JSON)
                        .content("{\"paymentMethodId\":1,\"items\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void onlyUsersWithTheVoidPermissionCanVoid() throws Exception {
        String admin = admin();
        String staff = readyUser(admin, "stornostaff", "STAFF");
        long cash = lookupId(admin, "PAYMENT_METHOD", "CASH");
        long productId = createProduct(admin, "Prodotto storno", "KG", "2.00", "IVA_4");
        receive(admin, productId, "5", "1.00");
        long saleId = checkout(staff, cash, productId, "1").get("id").asLong();

        mvc.perform(post("/api/sales/" + saleId + "/void").header(AUTH, staff).contentType(JSON)
                        .content("{\"reason\":\"Provo\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void corrispettiviSplitGrossIntoNetAndVatPerRateAndIgnoreVoidedSales() throws Exception {
        String admin = admin();
        // aliquota usata solo da questo test, così i totali non si mescolano con altri test
        mvc.perform(post("/api/admin/lookups/VAT_RATE").header(AUTH, admin).contentType(JSON)
                        .content("{\"name\":\"IVA di prova 7%\",\"rate\":7}"))
                .andExpect(status().isCreated());
        long cash = lookupId(admin, "PAYMENT_METHOD", "CASH");
        long vat7 = lookupId(admin, "VAT_RATE", "IVA_DI_PROVA_7");
        String payload = "{\"name\":\"Prodotto IVA 7\",\"categoryId\":" + lookupId(admin, "CATEGORY", "FRUTTA")
                + ",\"unitId\":" + lookupId(admin, "UNIT", "KG") + ",\"salePrice\":2.20,\"vatRateId\":" + vat7 + "}";
        long productId = json(mvc.perform(post("/api/products").header(AUTH, admin).contentType(JSON).content(payload))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
        receive(admin, productId, "10", "1.00");

        long saleId = checkout(admin, cash, productId, "2").get("id").asLong();   // 2 kg x 2,20 = 4,40 IVA inclusa
        String today = LocalDate.now(ZoneId.of("Europe/Rome")).toString();

        JsonNode row = vatRow(admin, today, 7);
        assertThat(row).isNotNull();
        assertThat(row.get("gross").decimalValue()).isEqualByComparingTo(new BigDecimal("4.40"));
        assertThat(row.get("net").decimalValue()).isEqualByComparingTo(new BigDecimal("4.11"));   // 4,40 / 1,07
        assertThat(row.get("vat").decimalValue()).isEqualByComparingTo(new BigDecimal("0.29"));

        mvc.perform(post("/api/sales/" + saleId + "/void").header(AUTH, admin).contentType(JSON)
                        .content("{\"reason\":\"Prova\"}"))
                .andExpect(status().isOk());

        // la vendita stornata non conta più
        assertThat(vatRow(admin, today, 7)).isNull();
    }

    @Test
    void reservationPickupBecomesASaleWithAPaymentMethod() throws Exception {
        String admin = admin();
        long cash = lookupId(admin, "PAYMENT_METHOD", "CASH");
        long productId = createProduct(admin, "Prodotto prenotato", "KG", "3.00", "IVA_4");
        receive(admin, productId, "10", "1.00");

        long customerId = json(mvc.perform(post("/api/customers").header(AUTH, admin).contentType(JSON)
                        .content("{\"name\":\"Cliente prenotazione\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
        long orderId = json(mvc.perform(post("/api/customer-orders").header(AUTH, admin).contentType(JSON)
                        .content("{\"customerId\":" + customerId + ",\"pickupDate\":\"" + LocalDate.now().plusDays(1)
                                + "\",\"lines\":[{\"productId\":" + productId + ",\"quantity\":4}]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();

        // senza metodo di pagamento il ritiro non è valido
        mvc.perform(post("/api/customer-orders/" + orderId + "/pickup").header(AUTH, admin).contentType(JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/customer-orders/" + orderId + "/pickup").header(AUTH, admin).contentType(JSON)
                        .content("{\"paymentMethodId\":" + cash + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PICKED_UP"));

        assertThat(stockOf(admin, productId)).isEqualTo(6.0);
        mvc.perform(get("/api/sales").header(AUTH, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.total == 12.0)]").isNotEmpty());
    }

    @Test
    void staffSeesOnlyTheirOwnSales() throws Exception {
        String admin = admin();
        String first = readyUser(admin, "cassiere1", "STAFF");
        String second = readyUser(admin, "cassiere2", "STAFF");
        long cash = lookupId(admin, "PAYMENT_METHOD", "CASH");
        long productId = createProduct(admin, "Prodotto due cassieri", "KG", "2.00", "IVA_4");
        receive(admin, productId, "10", "1.00");
        long saleId = checkout(first, cash, productId, "1").get("id").asLong();

        mvc.perform(get("/api/sales").header(AUTH, first))
                .andExpect(jsonPath("$.content[?(@.id == " + saleId + ")]").isNotEmpty());
        mvc.perform(get("/api/sales").header(AUTH, second))
                .andExpect(jsonPath("$.content[?(@.createdBy == 'cassiere1')]").isEmpty());
        mvc.perform(get("/api/sales/" + saleId).header(AUTH, second)).andExpect(status().isNotFound());
        mvc.perform(get("/api/sales/" + saleId).header(AUTH, first)).andExpect(status().isOk());
    }

    /** Riga del riepilogo con quell'aliquota nella data indicata (null se non c'è). */
    private JsonNode vatRow(String auth, String date, int rate) throws Exception {
        String body = mvc.perform(get("/api/reports/corrispettivi?date=" + date).header(AUTH, auth))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        for (JsonNode n : json(body).get("byVat")) {
            if (n.get("rate").decimalValue().compareTo(BigDecimal.valueOf(rate)) == 0) {
                return n;
            }
        }
        return null;
    }
}

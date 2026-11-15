package it.tuonome.ortofrutta.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Flusso base: autenticazione, validazione, carico, vendita, scarto e regole del magazzino. */
class ApiFlowTest extends IntegrationTestBase {

    @Test
    void requestsWithoutTokenAreRejected() throws Exception {
        mvc.perform(get("/api/products")).andExpect(status().isUnauthorized());
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"sbagliata\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidProductReturnsFieldErrors() throws Exception {
        String auth = admin();
        mvc.perform(post("/api/products").header(AUTH, auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"categoryId\":1,\"unitId\":1,\"salePrice\":1,\"vatRateId\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists());
    }

    @Test
    void receiptSaleAndWasteUpdateStock() throws Exception {
        String auth = admin();
        long cash = lookupId(auth, "PAYMENT_METHOD", "CASH");
        long reason = lookupId(auth, "WASTE_REASON", "TROPPO_MATURE");
        long productId = createProduct(auth, "Pere Abate", "KG", "2.20", "IVA_4");

        receive(auth, productId, "10", "1.00");
        checkout(auth, cash, productId, "4");
        mvc.perform(post("/api/stock/waste").header(AUTH, auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + productId + ",\"quantity\":1,\"reasonId\":" + reason + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].reason").value("Troppo mature"));

        org.assertj.core.api.Assertions.assertThat(stockOf(auth, productId)).isEqualTo(5.0);

        // non si può vendere più di quanto c'è
        mvc.perform(post("/api/sales").header(AUTH, auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethodId\":" + cash + ",\"items\":[{\"productId\":" + productId + ",\"quantity\":6}]}"))
                .andExpect(status().isConflict());

        mvc.perform(get("/api/reports/dashboard").header(AUTH, auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revenue30").isNumber());
    }

    @Test
    void failedSaleLeavesNoTraceBecauseItIsAtomic() throws Exception {
        String auth = admin();
        long cash = lookupId(auth, "PAYMENT_METHOD", "CASH");
        long ok = createProduct(auth, "Kiwi atomico A", "KG", "2.00", "IVA_4");
        long poor = createProduct(auth, "Kiwi atomico B", "KG", "2.00", "IVA_4");
        receive(auth, ok, "10", "1.00");
        receive(auth, poor, "1", "1.00");

        // la seconda riga non ha abbastanza merce: la vendita intera deve fallire, anche per la prima riga
        mvc.perform(post("/api/sales").header(AUTH, auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethodId\":" + cash + ",\"items\":[{\"productId\":" + ok
                                + ",\"quantity\":3},{\"productId\":" + poor + ",\"quantity\":5}]}"))
                .andExpect(status().isConflict());

        org.assertj.core.api.Assertions.assertThat(stockOf(auth, ok)).isEqualTo(10.0);
    }

    @Test
    void pieceUnitsRejectFractionalQuantities() throws Exception {
        String auth = admin();
        long cash = lookupId(auth, "PAYMENT_METHOD", "CASH");
        long productId = createProduct(auth, "Lattuga test", "PIECE", "1.20", "IVA_4");
        receive(auth, productId, "10", "0.50");

        mvc.perform(post("/api/sales").header(AUTH, auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethodId\":" + cash + ",\"items\":[{\"productId\":" + productId + ",\"quantity\":1.5}]}"))
                .andExpect(status().isConflict());
    }

    @Test
    void movementsArePaginated() throws Exception {
        String auth = admin();
        long productId = createProduct(auth, "Susine paginate", "KG", "3.00", "IVA_4");
        receive(auth, productId, "5", "1.00");
        receive(auth, productId, "5", "1.00");
        receive(auth, productId, "5", "1.00");

        mvc.perform(get("/api/stock/movements?page=0&size=2").header(AUTH, auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").isNumber());
    }
}

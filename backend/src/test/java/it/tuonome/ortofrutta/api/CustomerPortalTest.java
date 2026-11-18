package it.tuonome.ortofrutta.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contratti del portale cliente: catalogo pubblico del negozio, ordini personali e confini dei permessi. */
class CustomerPortalTest extends IntegrationTestBase {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    @Test
    void customerCanBrowseCatalogAndCreateAnOrderAtThePublishedPrice() throws Exception {
        String admin = admin();
        long productId = createProduct(admin, "Catalogo cliente " + System.nanoTime(), "KG", "3.40", "IVA_4");
        String email = "portale-" + System.nanoTime() + "@example.test";
        registerCustomer("Cliente Portale", email);
        String customer = login(email, "password123");

        String catalogBody = mvc.perform(get("/api/catalog").header(AUTH, customer))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode catalog = json(catalogBody);
        JsonNode item = null;
        for (JsonNode candidate : catalog) {
            if (candidate.get("id").asLong() == productId) item = candidate;
        }
        assertThat(item).isNotNull();
        assertThat(item.get("salePrice").decimalValue()).isEqualByComparingTo("3.40");
        assertThat(item.has("stock")).isFalse();
        assertThat(item.has("unitCost")).isFalse();

        mvc.perform(post("/api/customer-orders/mine").header(AUTH, customer).contentType(JSON)
                        .content("{\"pickupDate\":\"" + LocalDate.now().plusDays(1)
                                + "\",\"notes\":\"Ritiro al mattino\",\"lines\":[{\"productId\":"
                                + productId + ",\"quantity\":2}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.lines[0].unitPrice").value(3.40))
                .andExpect(jsonPath("$.total").value(6.80));
    }

    @Test
    void customerCanReadOnlyTheirOwnOrdersAndCannotUseStaffOperations() throws Exception {
        String admin = admin();
        long productId = createProduct(admin, "Ordine privato " + System.nanoTime(), "KG", "2.00", "IVA_4");
        String emailA = "cliente-a-" + System.nanoTime() + "@example.test";
        String emailB = "cliente-b-" + System.nanoTime() + "@example.test";
        registerCustomer("Cliente A", emailA);
        registerCustomer("Cliente B", emailB);
        String customerA = login(emailA, "password123");
        String customerB = login(emailB, "password123");

        mvc.perform(post("/api/customer-orders/mine").header(AUTH, customerA).contentType(JSON)
                        .content("{\"pickupDate\":\"" + LocalDate.now().plusDays(1)
                                + "\",\"notes\":null,\"lines\":[{\"productId\":" + productId
                                + ",\"quantity\":1}]}"))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/customer-orders/mine").header(AUTH, customerA))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/customer-orders/mine").header(AUTH, customerB))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));

        mvc.perform(get("/api/customer-orders").header(AUTH, customerA)).andExpect(status().isForbidden());
        mvc.perform(get("/api/products").header(AUTH, customerA)).andExpect(status().isForbidden());
        mvc.perform(get("/api/suppliers").header(AUTH, customerA)).andExpect(status().isForbidden());
        mvc.perform(get("/api/reports/dashboard").header(AUTH, customerA)).andExpect(status().isForbidden());
    }

    @Test
    void customerCanRequestHomeDeliveryAndStaffCanCompleteItAsASale() throws Exception {
        String admin = admin();
        long productId = createProduct(admin, "Consegna " + System.nanoTime(), "KG", "2.50", "IVA_4");
        receive(admin, productId, "5", "1.00");
        String email = "consegna-" + System.nanoTime() + "@example.test";
        registerCustomer("Cliente Consegna", email);
        String customer = login(email, "password123");

        String body = mvc.perform(post("/api/customer-orders/mine").header(AUTH, customer).contentType(JSON)
                        .content("""
                                {"pickupDate":"%s","notes":null,"lines":[{"productId":%d,"quantity":2}],
                                 "fulfillmentType":"DELIVERY","deliveryAddress":"Via Roma 12",
                                 "deliveryCity":"Bologna","deliveryPostalCode":"40121",
                                 "deliveryPhone":"3331234567","deliveryNotes":"Citofono Rossi"}
                                """.formatted(LocalDate.now().plusDays(1), productId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fulfillmentType").value("DELIVERY"))
                .andExpect(jsonPath("$.deliveryAddress").value("Via Roma 12"))
                .andReturn().getResponse().getContentAsString();
        long orderId = json(body).get("id").asLong();

        mvc.perform(post("/api/customer-orders/" + orderId + "/pickup").header(AUTH, admin)
                        .contentType(JSON).content("{\"paymentMethodId\":" + lookupId(admin, "PAYMENT_METHOD", "CASH") + "}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/customer-orders/" + orderId + "/ready").header(AUTH, admin))
                .andExpect(status().isOk());
        mvc.perform(post("/api/customer-orders/" + orderId + "/deliver").header(AUTH, admin)
                        .contentType(JSON).content("{\"paymentMethodId\":" + lookupId(admin, "PAYMENT_METHOD", "CASH") + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"));
    }

    @Test
    void homeDeliveryRequiresAddressAndContactPhone() throws Exception {
        String admin = admin();
        long productId = createProduct(admin, "Indirizzo obbligatorio " + System.nanoTime(), "KG", "1.00", "IVA_4");
        String email = "indirizzo-" + System.nanoTime() + "@example.test";
        registerCustomer("Cliente Indirizzo", email);
        String customer = login(email, "password123");

        mvc.perform(post("/api/customer-orders/mine").header(AUTH, customer).contentType(JSON)
                        .content("""
                                {"pickupDate":"%s","lines":[{"productId":%d,"quantity":1}],
                                 "fulfillmentType":"DELIVERY","deliveryAddress":" ","deliveryCity":"Bologna",
                                 "deliveryPostalCode":"40121","deliveryPhone":"3331234567"}
                                """.formatted(LocalDate.now().plusDays(1), productId)))
                .andExpect(status().isConflict());
    }

    private void registerCustomer(String name, String email) throws Exception {
        mvc.perform(post("/api/auth/register").contentType(JSON)
                        .content("{\"name\":\"" + name + "\",\"email\":\"" + email
                                + "\",\"phone\":null,\"password\":\"password123\"}"))
                .andExpect(status().isCreated());
    }
}

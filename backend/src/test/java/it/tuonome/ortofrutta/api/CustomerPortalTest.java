package it.tuonome.ortofrutta.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contratti del portale cliente: catalogo pubblico del negozio, ordini personali e confini dei permessi. */
class CustomerPortalTest extends IntegrationTestBase {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    @Test
    void customerCannotRequestDeliveryOutsideLazio() throws Exception {
        String admin = admin();
        long productId = createProduct(admin, "Consegna libera " + System.nanoTime(), "KG", "2.00", "IVA_4");
        receive(admin, productId, "10", "1.00");
        String email = "consegna-libera-" + System.nanoTime() + "@example.test";
        registerCustomer("Cliente Consegna Libera", email);
        String customer = login(email, "password123");

        mvc.perform(post("/api/customer-orders/mine").header(AUTH, customer).contentType(JSON)
                        .content("""
                                {"pickupDate":"%s","lines":[{"productId":%d,"quantity":2}],
                                 "fulfillmentType":"DELIVERY","deliveryAddress":"Via Nuova 4",
                                 "deliveryCity":"Citta Non Configurata","deliveryPostalCode":"10100",
                                 "deliveryPhone":"3331234567","deliverySlotId":null}
                                """.formatted(LocalDate.now().plusDays(1), productId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Le consegne sono disponibili solo per le città del Lazio"));
    }

    @Test
    void customerCanBrowseCatalogAndCreateAnOrderAtThePublishedPrice() throws Exception {
        String admin = admin();
        long productId = createProduct(admin, "Catalogo cliente " + System.nanoTime(), "KG", "3.40", "IVA_4");
        receive(admin, productId, "10", "1.00");
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
        receive(admin, productId, "10", "1.00");
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
        long slotId = createDeliveryZone(admin, "Roma", "3.00", "1.00", 2);
        String email = "consegna-" + System.nanoTime() + "@example.test";
        registerCustomer("Cliente Consegna", email);
        String customer = login(email, "password123");

        String body = mvc.perform(post("/api/customer-orders/mine").header(AUTH, customer).contentType(JSON)
                        .content("""
                                {"pickupDate":"%s","notes":null,"lines":[{"productId":%d,"quantity":2}],
                                 "fulfillmentType":"DELIVERY","deliveryAddress":"  Via Roma 12  ",
                                 "deliveryCity":"Roma","deliveryPostalCode":"00100",
                                 "deliveryPhone":"3331234567","deliveryNotes":"Citofono Rossi","deliverySlotId":%d}
                                """.formatted(LocalDate.now().plusDays(1), productId, slotId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fulfillmentType").value("DELIVERY"))
                .andExpect(jsonPath("$.deliveryAddress").value("  Via Roma 12  "))
                .andExpect(jsonPath("$.deliverySlot").value("09:00-12:00"))
                .andExpect(jsonPath("$.deliveryFee").value(3.00))
                .andExpect(jsonPath("$.productSubtotal").value(5.00))
                .andExpect(jsonPath("$.total").value(8.00))
                .andReturn().getResponse().getContentAsString();
        long orderId = json(body).get("id").asLong();

        mvc.perform(get("/api/delivery-zones/options").header(AUTH, customer)
                        .param("city", "  rOmA ").param("date", LocalDate.now().plusDays(1).toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.slots[0].remainingOrders").value(1));
        mvc.perform(put("/api/delivery-zones/" + zoneIdForCity(admin, "Roma"))
                        .header(AUTH, admin).contentType(JSON)
                        .content(zoneBody("Roma", "9.00", "1.00", 2, "10:00-13:00")))
                .andExpect(status().isOk());

        mvc.perform(post("/api/customer-orders/" + orderId + "/pickup").header(AUTH, admin)
                        .contentType(JSON).content("{\"paymentMethodId\":" + lookupId(admin, "PAYMENT_METHOD", "CASH") + "}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/customer-orders/" + orderId + "/ready").header(AUTH, admin))
                .andExpect(status().isOk());
        mvc.perform(post("/api/customer-orders/" + orderId + "/deliver").header(AUTH, admin)
                        .contentType(JSON).content("{\"paymentMethodId\":" + lookupId(admin, "PAYMENT_METHOD", "CASH") + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"));
        mvc.perform(get("/api/sales").header(AUTH, admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].total").value(8.00));
    }

    @Test
    void deliveryEnforcesConfiguredMinimumAndSlotCapacity() throws Exception {
        String admin = admin();
        long productId = createProduct(admin, "Capienza " + System.nanoTime(), "KG", "5.00", "IVA_4");
        receive(admin, productId, "10", "1.00");
        String city = "Cassino";
        long slotId = createDeliveryZone(admin, city, "2.00", "10.00", 1);
        String email = "capienza-" + System.nanoTime() + "@example.test";
        registerCustomer("Cliente Capienza", email);
        String customer = login(email, "password123");

        String delivery = """
                {"pickupDate":"%s","lines":[{"productId":%d,"quantity":1}],
                 "fulfillmentType":"DELIVERY","deliveryAddress":"Via Test","deliveryCity":"  %s  ",
                 "deliveryPostalCode":"10100","deliveryPhone":"3331234567","deliverySlotId":%d}
                """.formatted(LocalDate.now().plusDays(1), productId, city.toUpperCase(), slotId);
        mvc.perform(post("/api/customer-orders/mine").header(AUTH, customer).contentType(JSON).content(delivery))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.detail").exists());

        long lessPriceId = createProduct(admin, "Minimo " + System.nanoTime(), "KG", "10.00", "IVA_4");
        receive(admin, lessPriceId, "10", "1.00");
        long slotForOrder = createDeliveryZone(admin, "Latina", "2.00", "10.00", 1);
        String order = delivery.replace("\"productId\":" + productId, "\"productId\":" + lessPriceId)
                .replace("\"deliveryCity\":\"  " + city.toUpperCase() + "  \"",
                        "\"deliveryCity\":\"Latina\"")
                .replace("\"deliverySlotId\":" + slotId, "\"deliverySlotId\":" + slotForOrder);
        mvc.perform(post("/api/customer-orders/mine").header(AUTH, customer).contentType(JSON).content(order))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/customer-orders/mine").header(AUTH, customer).contentType(JSON).content(order))
                .andExpect(status().isConflict());
    }

    @Test
    void concurrentDeliveryRequestsCannotOverbookTheLastSlot() throws Exception {
        String admin = admin();
        long firstProduct = createProduct(admin, "Concorrenza A " + System.nanoTime(), "KG", "10.00", "IVA_4");
        long secondProduct = createProduct(admin, "Concorrenza B " + System.nanoTime(), "KG", "10.00", "IVA_4");
        receive(admin, firstProduct, "10", "1.00");
        receive(admin, secondProduct, "10", "1.00");
        String city = "Rieti";
        long slotId = createDeliveryZone(admin, city, "0.00", "0.00", 1);
        String email = "parallela-" + System.nanoTime() + "@example.test";
        registerCustomer("Cliente Parallelo", email);
        String customer = login(email, "password123");
        String[] payloads = {deliveryPayload(firstProduct, city, slotId), deliveryPayload(secondProduct, city, slotId)};
        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var requests = List.of(0, 1).stream().map(index -> executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start signal timed out");
                return mvc.perform(post("/api/customer-orders/mine").header(AUTH, customer)
                                .contentType(JSON).content(payloads[index]))
                        .andReturn().getResponse().getStatus();
            })).toList();
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Integer> statuses = List.of(requests.get(0).get(15, TimeUnit.SECONDS),
                    requests.get(1).get(15, TimeUnit.SECONDS));
            assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        } finally {
            executor.shutdownNow();
        }
    }

    private String deliveryPayload(long productId, String city, long slotId) {
        return """
                {"pickupDate":"%s","lines":[{"productId":%d,"quantity":1}],
                 "fulfillmentType":"DELIVERY","deliveryAddress":"Via Test","deliveryCity":"%s",
                 "deliveryPostalCode":"10100","deliveryPhone":"3331234567","deliverySlotId":%d}
                """.formatted(LocalDate.now().plusDays(1), productId, city, slotId);
    }

    private long createDeliveryZone(String admin, String city, String fee, String minimum, int capacity) throws Exception {
        String body = mvc.perform(post("/api/delivery-zones").header(AUTH, admin).contentType(JSON)
                        .content(zoneBody(city, fee, minimum, capacity, "09:00-12:00")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json(body).get("slots").get(0).get("id").asLong();
    }

    private long zoneIdForCity(String admin, String city) throws Exception {
        String body = mvc.perform(get("/api/delivery-zones").header(AUTH, admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        for (JsonNode zone : json(body)) {
            if (city.equals(zone.get("city").asText())) return zone.get("id").asLong();
        }
        throw new AssertionError("Zona non trovata: " + city);
    }

    private String zoneBody(String city, String fee, String minimum, int capacity, String slotName) {
        return """
                {"city":"%s","deliveryFee":%s,"minimumOrderSubtotal":%s,"active":true,
                 "slots":[{"name":"%s","maxOrders":%d,"active":true}]}
                """.formatted(city, fee, minimum, slotName, capacity);
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
                                 "fulfillmentType":"DELIVERY","deliveryAddress":" ","deliveryCity":"Roma",
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

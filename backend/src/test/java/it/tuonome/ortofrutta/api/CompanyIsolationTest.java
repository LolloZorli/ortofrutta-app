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

/** Tenant isolation and shared customer identity contracts across independently registered shops. */
class CompanyIsolationTest extends IntegrationTestBase {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;
    private static final String COMPANY = "X-Company-Slug";

    @Test
    void selfRegisteredShopsHaveSeparateCatalogsOrdersAndRolesWithOneSharedCustomerAccount() throws Exception {
        String unique = Long.toString(System.nanoTime());
        String slugA = "negozio-a-" + unique;
        String slugB = "negozio-b-" + unique;
        String ownerA = "titolare-a-" + unique + "@example.test";
        String ownerB = "titolare-b-" + unique + "@example.test";
        registerShop("Frutta A " + unique, slugA, "Titolare A", ownerA);
        registerShop("Frutta B " + unique, slugB, "Titolare B", ownerB);
        mvc.perform(get("/api/companies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.slug == '" + slugA + "')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.slug == '" + slugB + "')]").isNotEmpty());

        String adminA = loginFor(ownerA, "password123", slugA);
        String adminB = loginFor(ownerB, "password123", slugB);
        long productA = createProductIn(adminA, slugA, "Pere A " + unique);
        long productB = createProductIn(adminB, slugB, "Pere B " + unique);
        mvc.perform(post("/api/stock/receipts").header(AUTH, adminA).header(COMPANY, slugA).contentType(JSON)
                        .content("{\"productId\":" + productA + ",\"quantity\":10,\"unitCost\":1.00}"))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/roles").header(AUTH, adminA).header(COMPANY, slugA).contentType(JSON)
                        .content("{\"name\":\"Ruolo privato " + unique + "\",\"permissions\":[\"STOCK_READ\"]}"))
                .andExpect(status().isCreated());
        JsonNode rolesB = json(mvc.perform(get("/api/roles").header(AUTH, adminB).header(COMPANY, slugB))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(containsRole(rolesB, "Ruolo privato " + unique)).isFalse();

        long staffRoleId = roleIdIn(adminA, slugA, "STAFF");
        String employee = "commesso-" + unique;
        mvc.perform(post("/api/users").header(AUTH, adminA).header(COMPANY, slugA).contentType(JSON)
                        .content("{\"username\":\"" + employee + "\",\"email\":\"" + employee
                                + "@example.test\",\"password\":\"temporanea1\",\"roleId\":" + staffRoleId + "}"))
                .andExpect(status().isCreated());
        JsonNode usersB = json(mvc.perform(get("/api/users").header(AUTH, adminB).header(COMPANY, slugB))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(containsUsername(usersB, employee)).isFalse();

        String customerEmail = "cliente-condiviso-" + unique + "@example.test";
        registerCustomerIn(customerEmail, slugA);
        registerCustomerIn(customerEmail, slugB);
        String customerA = loginFor(customerEmail, "password123", slugA);
        String customerB = loginFor(customerEmail, "password123", slugB);

        JsonNode catalogA = json(mvc.perform(get("/api/catalog").header(AUTH, customerA).header(COMPANY, slugA))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode catalogB = json(mvc.perform(get("/api/catalog").header(AUTH, customerB).header(COMPANY, slugB))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(hasProduct(catalogA, productA)).isTrue();
        assertThat(hasProduct(catalogA, productB)).isFalse();
        assertThat(hasProduct(catalogB, productB)).isTrue();
        assertThat(hasProduct(catalogB, productA)).isFalse();

        mvc.perform(get("/api/products/" + productA).header(AUTH, adminB).header(COMPANY, slugB))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/products/" + productB).header(AUTH, adminA).header(COMPANY, slugA))
                .andExpect(status().isNotFound());

        mvc.perform(post("/api/customer-orders/mine").header(AUTH, customerA).header(COMPANY, slugA)
                        .contentType(JSON).content("{\"pickupDate\":\"" + LocalDate.now().plusDays(1)
                                + "\",\"notes\":null,\"lines\":[{\"productId\":" + productA + ",\"quantity\":1}]}"))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/customer-orders/mine").header(AUTH, customerA).header(COMPANY, slugA))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/customer-orders/mine").header(AUTH, customerB).header(COMPANY, slugB))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    private void registerShop(String name, String slug, String ownerName, String email) throws Exception {
        mvc.perform(post("/api/companies/register").contentType(JSON)
                        .content("{\"companyName\":\"" + name + "\",\"slug\":\"" + slug
                                + "\",\"ownerName\":\"" + ownerName + "\",\"email\":\"" + email
                                + "\",\"password\":\"password123\",\"phone\":null}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value(slug));
    }

    private String loginFor(String username, String password, String slug) throws Exception {
        String body = mvc.perform(post("/api/auth/login").header(COMPANY, slug).contentType(JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + json(body).get("token").asText();
    }

    private void registerCustomerIn(String email, String slug) throws Exception {
        mvc.perform(post("/api/auth/register").header(COMPANY, slug).contentType(JSON)
                        .content("{\"name\":\"Cliente condiviso\",\"email\":\"" + email
                                + "\",\"phone\":null,\"password\":\"password123\"}"))
                .andExpect(status().isCreated());
    }

    private long createProductIn(String auth, String slug, String name) throws Exception {
        long category = lookupIdIn(auth, slug, "CATEGORY", "FRUTTA");
        long unit = lookupIdIn(auth, slug, "UNIT", "KG");
        long vat = lookupIdIn(auth, slug, "VAT_RATE", "IVA_4");
        String body = mvc.perform(post("/api/products").header(AUTH, auth).header(COMPANY, slug).contentType(JSON)
                        .content("{\"name\":\"" + name + "\",\"categoryId\":" + category + ",\"unitId\":" + unit
                                + ",\"salePrice\":2.50,\"minStock\":0,\"vatRateId\":" + vat + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json(body).get("id").asLong();
    }

    private long lookupIdIn(String auth, String slug, String type, String code) throws Exception {
        String body = mvc.perform(get("/api/lookups").header(AUTH, auth).header(COMPANY, slug))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        for (JsonNode item : json(body).get(type)) {
            if (code.equals(item.get("code").asText())) return item.get("id").asLong();
        }
        throw new AssertionError("Valore non trovato: " + type + "/" + code + " in " + slug);
    }

    private long roleIdIn(String auth, String slug, String code) throws Exception {
        String body = mvc.perform(get("/api/roles").header(AUTH, auth).header(COMPANY, slug))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        for (JsonNode role : json(body)) if (code.equals(role.get("code").asText())) return role.get("id").asLong();
        throw new AssertionError("Ruolo non trovato: " + code + " in " + slug);
    }

    private static boolean hasProduct(JsonNode items, long productId) {
        for (JsonNode item : items) if (item.get("id").asLong() == productId) return true;
        return false;
    }

    private static boolean containsRole(JsonNode items, String name) {
        for (JsonNode item : items) if (name.equals(item.get("name").asText())) return true;
        return false;
    }

    private static boolean containsUsername(JsonNode items, String username) {
        for (JsonNode item : items) if (username.equals(item.get("username").asText())) return true;
        return false;
    }
}

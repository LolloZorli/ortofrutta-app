package it.tuonome.ortofrutta.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base dei test di integrazione: applicazione completa + UN solo PostgreSQL vero (usa e getta) condiviso da tutte
 * le classi di test, così il contesto Spring si crea una volta sola. I test usano nomi univoci per non disturbarsi.
 */
@SpringBootTest(properties = "app.admin.must-change-password=false")
@AutoConfigureMockMvc
abstract class IntegrationTestBase {

    static final String AUTH = "Authorization";

    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    static {
        postgres.start();
    }

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper mapper;

    protected JsonNode json(String body) throws Exception {
        return mapper.readTree(body);
    }

    protected String login(String username, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + json(body).get("token").asText();
    }

    protected String admin() throws Exception {
        return login("admin", "admin123");
    }

    /** Id di un valore di tipologica a partire dal suo codice (es. UNIT / KG). */
    protected long lookupId(String auth, String type, String code) throws Exception {
        String body = mvc.perform(get("/api/lookups").header(AUTH, auth))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        for (JsonNode n : json(body).get(type)) {
            if (code.equals(n.get("code").asText())) {
                return n.get("id").asLong();
            }
        }
        throw new AssertionError("Valore non trovato: " + type + "/" + code);
    }

    protected long roleId(String adminAuth, String roleCode) throws Exception {
        String body = mvc.perform(get("/api/roles").header(AUTH, adminAuth))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        for (JsonNode n : json(body)) {
            if (roleCode.equals(n.get("code").asText())) {
                return n.get("id").asLong();
            }
        }
        throw new AssertionError("Ruolo non trovato: " + roleCode);
    }

    protected long createUser(String adminAuth, String username, long roleId, String password) throws Exception {
        String body = mvc.perform(post("/api/users").header(AUTH, adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password
                                + "\",\"roleId\":" + roleId + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json(body).get("id").asLong();
    }

    protected void changePassword(String auth, String current, String next) throws Exception {
        mvc.perform(post("/api/auth/change-password").header(AUTH, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + current + "\",\"newPassword\":\"" + next + "\"}"))
                .andExpect(status().isNoContent());
    }

    /** Crea un utente con il ruolo indicato e gli fa scegliere la password: restituisce il suo token pronto all'uso. */
    protected String readyUser(String adminAuth, String username, String roleCode) throws Exception {
        createUser(adminAuth, username, roleId(adminAuth, roleCode), "temporanea1");
        String token = login(username, "temporanea1");
        changePassword(token, "temporanea1", "miaPassword1");
        return token;
    }

    protected long createProduct(String auth, String name, String unitCode, String price, String vatCode) throws Exception {
        String payload = "{\"name\":\"" + name + "\",\"categoryId\":" + lookupId(auth, "CATEGORY", "FRUTTA")
                + ",\"unitId\":" + lookupId(auth, "UNIT", unitCode) + ",\"salePrice\":" + price
                + ",\"minStock\":5,\"vatRateId\":" + lookupId(auth, "VAT_RATE", vatCode) + "}";
        String body = mvc.perform(post("/api/products").header(AUTH, auth)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json(body).get("id").asLong();
    }

    protected void receive(String auth, long productId, String quantity, String cost) throws Exception {
        mvc.perform(post("/api/stock/receipts").header(AUTH, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + productId + ",\"quantity\":" + quantity + ",\"unitCost\":" + cost + "}"))
                .andExpect(status().isCreated());
    }

    /** Vendita con una sola riga; restituisce la risposta JSON della cassa. */
    protected JsonNode checkout(String auth, long paymentMethodId, long productId, String quantity) throws Exception {
        String body = mvc.perform(post("/api/sales").header(AUTH, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethodId\":" + paymentMethodId + ",\"items\":[{\"productId\":" + productId
                                + ",\"quantity\":" + quantity + "}]}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json(body);
    }

    protected double stockOf(String auth, long productId) throws Exception {
        String body = mvc.perform(get("/api/products/" + productId).header(AUTH, auth))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json(body).get("stock").asDouble();
    }
}

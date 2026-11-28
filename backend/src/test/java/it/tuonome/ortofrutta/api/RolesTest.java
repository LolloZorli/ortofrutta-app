package it.tuonome.ortofrutta.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Permessi: ruoli di base, ruoli personalizzati, password temporanee, utenti disattivati. */
class RolesTest extends IntegrationTestBase {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    @Test
    void newUserMustChangePasswordBeforeDoingAnythingElse() throws Exception {
        String admin = admin();
        createUser(admin, "mario", roleId(admin, "STAFF"), "temporanea1");
        String mario = login("mario", "temporanea1");

        // finché non cambia la password non ha nessun permesso, ma può vedere il proprio profilo
        mvc.perform(get("/api/products").header(AUTH, mario)).andExpect(status().isForbidden());
        mvc.perform(get("/api/auth/me").header(AUTH, mario))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(true));

        // password attuale sbagliata: rifiutata
        mvc.perform(post("/api/auth/change-password").header(AUTH, mario).contentType(JSON)
                        .content("{\"currentPassword\":\"sbagliata\",\"newPassword\":\"miaPassword1\"}"))
                .andExpect(status().isConflict());

        changePassword(mario, "temporanea1", "miaPassword1");

        // lo stesso token ora funziona, perché i permessi si leggono dal database
        mvc.perform(get("/api/products").header(AUTH, mario)).andExpect(status().isOk());
    }

    @Test
    void staffCanUseTheCounterAndCreateSupplierOrdersButCannotManageAdministration() throws Exception {
        String admin = admin();
        String staff = readyUser(admin, "giulia", "STAFF");

        mvc.perform(post("/api/products").header(AUTH, staff).contentType(JSON)
                        .content("{\"name\":\"Vietato\",\"categoryId\":1,\"unitId\":1,\"salePrice\":1,\"vatRateId\":1}"))
                .andExpect(status().isForbidden());
        for (String path : new String[]{"/api/users", "/api/roles", "/api/admin/lookups", "/api/audit",
                "/api/reports/products", "/api/reports/corrispettivi"}) {
            mvc.perform(get(path).header(AUTH, staff)).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/suppliers").header(AUTH, staff)).andExpect(status().isOk());
        mvc.perform(get("/api/supplier-orders").header(AUTH, staff)).andExpect(status().isOk());
        mvc.perform(post("/api/stock/receipts").header(AUTH, staff).contentType(JSON)
                        .content("{\"productId\":1,\"quantity\":1,\"unitCost\":1}"))
                .andExpect(status().isForbidden());

        // ma può consultare listino, tipologiche, prenotazioni e dashboard
        for (String path : new String[]{"/api/products", "/api/lookups", "/api/customer-orders", "/api/reports/dashboard"}) {
            mvc.perform(get(path).header(AUTH, staff)).andExpect(status().isOk());
        }

        long productId = createProduct(admin, "Ordine commesso " + System.nanoTime(), "KG", "2.00", "IVA_4");
        String supplier = mvc.perform(post("/api/suppliers").header(AUTH, admin).contentType(JSON)
                        .content("{\"name\":\"Fornitore ordine " + System.nanoTime() + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long supplierId = json(supplier).get("id").asLong();
        String createdOrder = mvc.perform(post("/api/supplier-orders").header(AUTH, staff).contentType(JSON)
                        .content("{\"supplierId\":" + supplierId + ",\"lines\":[{\"productId\":"
                                + productId + ",\"quantity\":3,\"unitCost\":1.20}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lines[0].unitCost").value(nullValue()))
                .andReturn().getResponse().getContentAsString();
        long supplierOrderId = json(createdOrder).get("id").asLong();
        mvc.perform(post("/api/supplier-orders/" + supplierOrderId + "/send").header(AUTH, staff))
                .andExpect(status().isForbidden());
    }

    @Test
    void staffAndManagerCanCreateCustomersButWarehouseCannot() throws Exception {
        String admin = admin();
        String staff = readyUser(admin, "clientecommesso", "STAFF");
        String manager = readyUser(admin, "clienteresponsabile", "MANAGER");
        String warehouse = readyUser(admin, "clientemagazziniere", "WAREHOUSE");
        String customer = "{\"name\":\"Cliente banco\",\"phone\":\"3331234567\",\"notes\":null}";

        mvc.perform(post("/api/customers").header(AUTH, staff).contentType(JSON).content(customer))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Cliente banco"));
        mvc.perform(post("/api/customers").header(AUTH, manager).contentType(JSON).content(customer))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/customers").header(AUTH, warehouse).contentType(JSON).content(customer))
                .andExpect(status().isForbidden());
    }

    @Test
    void customerSelfRegistrationDoesNotNeedStaffPermissions() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(JSON)
                        .content("{\"name\":\"Cliente online\",\"email\":\"online@example.test\",\"phone\":null,\"password\":\"password123\"}"))
                .andExpect(status().isCreated());

        String customer = login("online@example.test", "password123");
        mvc.perform(get("/api/customer-orders/mine").header(AUTH, customer)).andExpect(status().isOk());
        mvc.perform(get("/api/lookups").header(AUTH, customer)).andExpect(status().isOk());
        mvc.perform(get("/api/products").header(AUTH, customer)).andExpect(status().isForbidden());
        mvc.perform(get("/api/roles").header(AUTH, customer)).andExpect(status().isForbidden());
        mvc.perform(post("/api/customers").header(AUTH, customer).contentType(JSON)
                        .content("{\"name\":\"Cliente extra\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void managerCanManageProductsAndReportsButNotUsersOrLookups() throws Exception {
        String admin = admin();
        String manager = readyUser(admin, "luisa", "MANAGER");

        mvc.perform(post("/api/products").header(AUTH, manager).contentType(JSON)
                        .content("{\"name\":\"Prodotto responsabile\",\"categoryId\":1,\"unitId\":1,\"salePrice\":1,\"vatRateId\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Prodotto responsabile"));
        mvc.perform(get("/api/users").header(AUTH, manager)).andExpect(status().isOk());
        mvc.perform(get("/api/customers").header(AUTH, manager)).andExpect(status().isOk());
        mvc.perform(post("/api/users").header(AUTH, manager).contentType(JSON)
                        .content("{\"username\":\"vietato\",\"password\":\"password123\",\"roleId\":"
                                + roleId(admin, "STAFF") + "}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/lookups/CATEGORY").header(AUTH, manager).contentType(JSON)
                        .content("{\"name\":\"Non permesso\"}"))
                .andExpect(status().isForbidden());

        for (String path : new String[]{"/api/reports/products", "/api/reports/corrispettivi", "/api/suppliers",
                "/api/supplier-orders", "/api/audit"}) {
            mvc.perform(get(path).header(AUTH, manager)).andExpect(status().isOk());
        }
    }

    @Test
    void warehouseReceivesGoodsButCannotUseTheCashDesk() throws Exception {
        String admin = admin();
        String warehouse = readyUser(admin, "walter", "WAREHOUSE");
        long productId = createProduct(admin, "Fichi magazzino", "KG", "4.00", "IVA_4");
        long cash = lookupId(admin, "PAYMENT_METHOD", "CASH");

        mvc.perform(get("/api/suppliers").header(AUTH, warehouse)).andExpect(status().isOk());
        mvc.perform(get("/api/stock/lots").header(AUTH, warehouse)).andExpect(status().isOk());
        receive(warehouse, productId, "10", "2.00");

        mvc.perform(post("/api/sales").header(AUTH, warehouse).contentType(JSON)
                        .content("{\"paymentMethodId\":" + cash + ",\"items\":[{\"productId\":" + productId + ",\"quantity\":1}]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void staffSellsAtListPriceAndDoesNotSeeCosts() throws Exception {
        String admin = admin();
        String staff = readyUser(admin, "luca", "STAFF");
        long cash = lookupId(admin, "PAYMENT_METHOD", "CASH");
        long productId = createProduct(admin, "Kiwi test", "KG", "2.20", "IVA_4");
        receive(admin, productId, "10", "1.00");

        // il commesso prova a vendere a 0,10 €: il prezzo richiesto viene ignorato, vale il listino
        mvc.perform(post("/api/sales").header(AUTH, staff).contentType(JSON)
                        .content("{\"paymentMethodId\":" + cash + ",\"items\":[{\"productId\":" + productId
                                + ",\"quantity\":1,\"unitPrice\":0.10}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.total").value(2.2));

        // nessun costo visibile: lotti, movimenti e dashboard
        mvc.perform(get("/api/stock/lots").header(AUTH, staff))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.unitCost != null)]").isEmpty());
        mvc.perform(get("/api/stock/movements").header(AUTH, staff))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.unitCost != null)]").isEmpty());
        mvc.perform(get("/api/reports/dashboard").header(AUTH, staff))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockValue").value(nullValue()))
                .andExpect(jsonPath("$.margin30").value(nullValue()))
                .andExpect(jsonPath("$.todayRevenue").value(nullValue()));

        // l'amministratore vede tutto
        mvc.perform(get("/api/stock/lots").header(AUTH, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.unitCost != null)]").isNotEmpty());
    }

    @Test
    void customRoleGetsImpliedPermissionsAndNothingMore() throws Exception {
        String admin = admin();
        String created = mvc.perform(post("/api/roles").header(AUTH, admin).contentType(JSON)
                        .content("{\"name\":\"Solo cassa\",\"description\":\"Test\",\"permissions\":[\"SALES_CREATE\"]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.permissions[?(@ == 'PRODUCTS_READ')]").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        long newRoleId = json(created).get("id").asLong();

        createUser(admin, "cassiera", newRoleId, "temporanea1");
        String token = login("cassiera", "temporanea1");
        changePassword(token, "temporanea1", "miaPassword1");

        long cash = lookupId(admin, "PAYMENT_METHOD", "CASH");
        long productId = createProduct(admin, "Uva cassa", "KG", "3.00", "IVA_4");
        receive(admin, productId, "5", "1.00");

        checkout(token, cash, productId, "1");                                          // può vendere
        mvc.perform(get("/api/stock/lots").header(AUTH, token)).andExpect(status().isForbidden());   // ma non vede i lotti
        mvc.perform(get("/api/reports/products").header(AUTH, token)).andExpect(status().isForbidden());
    }

    @Test
    void adminOnlyPermissionsCannotBeGrantedToOtherRoles() throws Exception {
        String admin = admin();
        for (String perm : new String[]{"LOOKUPS_MANAGE", "USERS_MANAGE", "ROLES_MANAGE"}) {
            mvc.perform(post("/api/roles").header(AUTH, admin).contentType(JSON)
                            .content("{\"name\":\"Furbo " + perm + "\",\"permissions\":[\"" + perm + "\"]}"))
                    .andExpect(status().isConflict());
        }
    }

    @Test
    void adminRoleIsImmutableAndRolesInUseCannotBeDeleted() throws Exception {
        String admin = admin();
        long adminRole = roleId(admin, "ADMIN");
        mvc.perform(put("/api/roles/" + adminRole).header(AUTH, admin).contentType(JSON)
                        .content("{\"name\":\"Amministratore\",\"permissions\":[]}"))
                .andExpect(status().isConflict());
        mvc.perform(delete("/api/roles/" + adminRole).header(AUTH, admin)).andExpect(status().isConflict());

        String created = mvc.perform(post("/api/roles").header(AUTH, admin).contentType(JSON)
                        .content("{\"name\":\"Ruolo in uso\",\"permissions\":[\"STOCK_READ\"]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long roleId = json(created).get("id").asLong();
        createUser(admin, "inuso", roleId, "temporanea1");

        mvc.perform(delete("/api/roles/" + roleId).header(AUTH, admin)).andExpect(status().isConflict());
    }

    @Test
    void disabledUserLosesAccessImmediately() throws Exception {
        String admin = admin();
        long staffRole = roleId(admin, "STAFF");
        long id = createUser(admin, "anna", staffRole, "temporanea1");
        String anna = login("anna", "temporanea1");
        changePassword(anna, "temporanea1", "miaPassword1");
        mvc.perform(get("/api/products").header(AUTH, anna)).andExpect(status().isOk());

        mvc.perform(put("/api/users/" + id).header(AUTH, admin).contentType(JSON)
                        .content("{\"roleId\":" + staffRole + ",\"enabled\":false}"))
                .andExpect(status().isOk());

        // il token è ancora "valido", ma l'utente non lo è più
        mvc.perform(get("/api/products").header(AUTH, anna)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(JSON)
                        .content("{\"username\":\"anna\",\"password\":\"miaPassword1\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void roleChangeTakesEffectWithoutNewLogin() throws Exception {
        String admin = admin();
        long id = createUser(admin, "paolo", roleId(admin, "STAFF"), "temporanea1");
        String paolo = login("paolo", "temporanea1");
        changePassword(paolo, "temporanea1", "miaPassword1");
        mvc.perform(get("/api/audit").header(AUTH, paolo)).andExpect(status().isForbidden());

        mvc.perform(put("/api/users/" + id).header(AUTH, admin).contentType(JSON)
                        .content("{\"roleId\":" + roleId(admin, "MANAGER") + ",\"enabled\":true}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/audit").header(AUTH, paolo)).andExpect(status().isOk());
    }

    @Test
    void administratorCannotChangeOwnRoleOrDisableSelf() throws Exception {
        String admin = admin();
        String body = mvc.perform(get("/api/users").header(AUTH, admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long adminId = -1;
        for (JsonNode u : json(body)) {
            if ("admin".equals(u.get("username").asText())) {
                adminId = u.get("id").asLong();
            }
        }
        assertThat(adminId).isPositive();

        mvc.perform(put("/api/users/" + adminId).header(AUTH, admin).contentType(JSON)
                        .content("{\"roleId\":" + roleId(admin, "STAFF") + ",\"enabled\":true}"))
                .andExpect(status().isConflict());
    }

    @Test
    void duplicateOrInvalidUsernamesAreRejected() throws Exception {
        String admin = admin();
        long staffRole = roleId(admin, "STAFF");
        createUser(admin, "doppio", staffRole, "temporanea1");

        mvc.perform(post("/api/users").header(AUTH, admin).contentType(JSON)
                        .content("{\"username\":\"DOPPIO\",\"password\":\"temporanea1\",\"roleId\":" + staffRole + "}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/users").header(AUTH, admin).contentType(JSON)
                        .content("{\"username\":\"con spazi\",\"password\":\"temporanea1\",\"roleId\":" + staffRole + "}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/users").header(AUTH, admin).contentType(JSON)
                        .content("{\"username\":\"corta\",\"password\":\"123\",\"roleId\":" + staffRole + "}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminResetPasswordReplacesOldCredentialAndRequiresNewPasswordChange() throws Exception {
        String admin = admin();
        long id = createUser(admin, "nuova-credenziale", roleId(admin, "STAFF"), "temporanea1");
        String oldToken = login("nuova-credenziale", "temporanea1");

        mvc.perform(post("/api/users/" + id + "/reset-password").header(AUTH, admin).contentType(JSON)
                        .content("{\"newPassword\":\"passwordNuova1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(true));

        mvc.perform(post("/api/auth/login").contentType(JSON)
                        .content("{\"username\":\"nuova-credenziale\",\"password\":\"temporanea1\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(JSON)
                        .content("{\"username\":\"nuova-credenziale\",\"password\":\"passwordNuova1\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/products").header(AUTH, oldToken)).andExpect(status().isForbidden());
    }

    @Test
    void employeeCannotManageUsersRolesOrOtherStores() throws Exception {
        String admin = admin();
        String staff = readyUser(admin, "limiti-permissioni", "STAFF");

        mvc.perform(get("/api/users").header(AUTH, staff)).andExpect(status().isForbidden());
        mvc.perform(post("/api/users").header(AUTH, staff).contentType(JSON)
                        .content("{\"username\":\"vietato\",\"password\":\"password123\",\"roleId\":1}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/roles").header(AUTH, staff)).andExpect(status().isForbidden());
        mvc.perform(post("/api/roles").header(AUTH, staff).contentType(JSON)
                        .content("{\"name\":\"Vietato\",\"permissions\":[\"SALES_CREATE\"]}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/audit").header(AUTH, staff)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/lookups/CATEGORY").header(AUTH, staff)).andExpect(status().isForbidden());
    }
}

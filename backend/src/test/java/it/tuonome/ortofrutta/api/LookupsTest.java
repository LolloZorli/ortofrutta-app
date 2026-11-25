package it.tuonome.ortofrutta.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Tipologiche: solo l'amministratore le crea e le modifica; i valori disattivati non si assegnano più. */
class LookupsTest extends IntegrationTestBase {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    @Test
    void migrationSeedsTheStandardValues() throws Exception {
        String auth = admin();
        mvc.perform(get("/api/lookups").header(AUTH, auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.UNIT[?(@.code == 'KG')].shortLabel").value("kg"))
                .andExpect(jsonPath("$.VAT_RATE[?(@.code == 'IVA_4')].rate").value(4.0))
                .andExpect(jsonPath("$.PAYMENT_METHOD[?(@.code == 'CASH')]").isNotEmpty())
                .andExpect(jsonPath("$.WASTE_REASON[?(@.code == 'SCADUTO')]").isNotEmpty())
                .andExpect(jsonPath("$.SUPPLIER_TYPE[?(@.code == 'WHOLESALER')]").isNotEmpty());
    }

    @Test
    void adminAddsAValueAndEveryoneCanThenUseIt() throws Exception {
        String admin = admin();
        mvc.perform(post("/api/admin/lookups/CATEGORY").header(AUTH, admin).contentType(JSON)
                        .content("{\"name\":\"Frutta secca\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("FRUTTA_SECCA"))
                .andExpect(jsonPath("$.active").value(true));

        String staff = readyUser(admin, "lookupstaff", "STAFF");
        mvc.perform(get("/api/lookups").header(AUTH, staff))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.CATEGORY[?(@.code == 'FRUTTA_SECCA')]").isNotEmpty());
    }

    @Test
    void duplicateNamesAreRejectedIgnoringCase() throws Exception {
        String admin = admin();
        mvc.perform(post("/api/admin/lookups/ORIGIN").header(AUTH, admin).contentType(JSON)
                        .content("{\"name\":\"Puglia\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/admin/lookups/ORIGIN").header(AUTH, admin).contentType(JSON)
                        .content("{\"name\":\"PUGLIA\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void onlyAdministratorsCanCreateOrEditLookups() throws Exception {
        String admin = admin();
        String staff = readyUser(admin, "lookupstaff2", "STAFF");
        String manager = readyUser(admin, "lookupmanager", "MANAGER");

        for (String auth : new String[]{staff, manager}) {
            mvc.perform(post("/api/admin/lookups/CATEGORY").header(AUTH, auth).contentType(JSON)
                            .content("{\"name\":\"Abusiva\"}"))
                    .andExpect(status().isForbidden());
            mvc.perform(put("/api/admin/lookups/CATEGORY/1").header(AUTH, auth).contentType(JSON)
                            .content("{\"name\":\"Rinominata\"}"))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/api/admin/lookups/CATEGORY").header(AUTH, auth)).andExpect(status().isForbidden());
        }
    }

    @Test
    void deactivatedValuesCannotBeAssignedAnymore() throws Exception {
        String admin = admin();
        String created = mvc.perform(post("/api/admin/lookups/CATEGORY").header(AUTH, admin).contentType(JSON)
                        .content("{\"name\":\"Temporanea\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = json(created).get("id").asLong();

        mvc.perform(put("/api/admin/lookups/CATEGORY/" + id).header(AUTH, admin).contentType(JSON)
                        .content("{\"name\":\"Temporanea\",\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        // non compare più negli elenchi per i moduli
        mvc.perform(get("/api/lookups").header(AUTH, admin))
                .andExpect(jsonPath("$.CATEGORY[?(@.name == 'Temporanea')]").isEmpty());
        // ma l'amministratore la vede ancora (per poterla riattivare)
        mvc.perform(get("/api/admin/lookups/CATEGORY").header(AUTH, admin))
                .andExpect(jsonPath("$[?(@.name == 'Temporanea')]").isNotEmpty());

        // e non si può assegnare a un nuovo prodotto
        mvc.perform(post("/api/products").header(AUTH, admin).contentType(JSON)
                        .content("{\"name\":\"Prodotto con categoria spenta\",\"categoryId\":" + id
                                + ",\"unitId\":" + lookupId(admin, "UNIT", "KG") + ",\"salePrice\":1,\"vatRateId\":"
                                + lookupId(admin, "VAT_RATE", "IVA_4") + "}"))
                .andExpect(status().isConflict());
    }

    @Test
    void unitsNeedAShortLabelAndVatRatesNeedAPercentage() throws Exception {
        String admin = admin();
        mvc.perform(post("/api/admin/lookups/UNIT").header(AUTH, admin).contentType(JSON)
                        .content("{\"name\":\"Sacco\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/admin/lookups/UNIT").header(AUTH, admin).contentType(JSON)
                        .content("{\"name\":\"Sacco\",\"shortLabel\":\"sacco\",\"allowDecimals\":false}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortLabel").value("sacco"))
                .andExpect(jsonPath("$.allowDecimals").value(false));

        mvc.perform(post("/api/admin/lookups/VAT_RATE").header(AUTH, admin).contentType(JSON)
                        .content("{\"name\":\"IVA senza percentuale\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/admin/lookups/VAT_RATE").header(AUTH, admin).contentType(JSON)
                        .content("{\"name\":\"IVA 9%\",\"rate\":9}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rate").value(9.0));
    }

    @Test
    void unknownLookupTypeIsABadRequest() throws Exception {
        mvc.perform(get("/api/admin/lookups/INESISTENTE").header(AUTH, admin()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void creatingAProductChecksThatChosenValuesExist() throws Exception {
        String admin = admin();
        mvc.perform(post("/api/products").header(AUTH, admin).contentType(JSON)
                        .content("{\"name\":\"Prodotto fantasma\",\"categoryId\":999999,\"unitId\":"
                                + lookupId(admin, "UNIT", "KG") + ",\"salePrice\":1,\"vatRateId\":"
                                + lookupId(admin, "VAT_RATE", "IVA_4") + "}"))
                .andExpect(status().isConflict());
    }

    @Test
    void supplierTypesAreAssignedAndValidated() throws Exception {
        String admin = admin();
        long wholesaler = lookupId(admin, "SUPPLIER_TYPE", "WHOLESALER");

        mvc.perform(post("/api/suppliers").header(AUTH, admin).contentType(JSON)
                        .content("{\"name\":\"Grossista tipizzato\",\"typeId\":" + wholesaler + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.typeId").value(wholesaler))
                .andExpect(jsonPath("$.type").value("Grossista"));

        mvc.perform(post("/api/suppliers").header(AUTH, admin).contentType(JSON)
                        .content("{\"name\":\"Fornitore tipo inesistente\",\"typeId\":999999}"))
                .andExpect(status().isConflict());
    }
}

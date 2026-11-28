package it.tuonome.ortofrutta.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

class EmployeeRegistrationTest extends IntegrationTestBase {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    @Test
    void employeeSignupWaitsForManagerApprovalBeforeGrantingStoreAccess() throws Exception {
        String admin = admin();
        String email = "employee-" + System.nanoTime() + "@example.test";
        mvc.perform(post("/api/auth/register-employee").contentType(JSON)
                        .content("{\"name\":\"Nuovo Commesso\",\"email\":\"" + email
                                + "\",\"phone\":null,\"password\":\"password123\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"));

        mvc.perform(post("/api/auth/login").contentType(JSON)
                        .content("{\"username\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isUnauthorized());

        JsonNode users = json(mvc.perform(get("/api/users").header(AUTH, admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode employeeRow = null;
        for (JsonNode user : users) if (email.equals(user.path("email").asText())) employeeRow = user;
        assertThat(employeeRow).isNotNull();
        assertThat(employeeRow.path("approved").asBoolean()).isFalse();
        long userId = employeeRow.path("id").asLong();

        String manager = readyUser(admin, "manager-approver-" + System.nanoTime(), "MANAGER");
        JsonNode managerUsers = json(mvc.perform(get("/api/users").header(AUTH, manager))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode pendingRow = null;
        for (JsonNode user : managerUsers) if (email.equals(user.path("email").asText())) pendingRow = user;
        assertThat(pendingRow).isNotNull();
        assertThat(pendingRow.path("approved").asBoolean()).isFalse();
        mvc.perform(post("/api/users/" + userId + "/approve").header(AUTH, manager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approved").value(true))
                .andExpect(jsonPath("$.enabled").value(true));

        String employee = login(email, "password123");
        mvc.perform(get("/api/supplier-orders").header(AUTH, employee)).andExpect(status().isOk());
        mvc.perform(get("/api/users").header(AUTH, employee)).andExpect(status().isForbidden());
    }

    @Test
    void duplicateEmployeeRegistrationAndInvalidPasswordAreRejected() throws Exception {
        String email = "employee-doppio-" + System.nanoTime() + "@example.test";
        mvc.perform(post("/api/auth/register-employee").contentType(JSON)
                        .content("{\"name\":\"Primo tentativo\",\"email\":\"" + email
                                + "\",\"phone\":null,\"password\":\"password123\"}"))
                .andExpect(status().isAccepted());

        mvc.perform(post("/api/auth/register-employee").contentType(JSON)
                        .content("{\"name\":\"Secondo tentativo\",\"email\":\"" + email
                                + "\",\"phone\":null,\"password\":\"password123\"}"))
                .andExpect(status().isConflict());

        mvc.perform(post("/api/auth/register-employee").contentType(JSON)
                        .content("{\"name\":\"Password corta\",\"email\":\"corta-" + System.nanoTime()
                                + "@example.test\",\"phone\":null,\"password\":\"123\"}"))
                .andExpect(status().isBadRequest());
    }
}

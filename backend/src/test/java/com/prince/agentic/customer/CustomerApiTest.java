package com.prince.agentic.customer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CustomerApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    private static final String PW = "SecurePassword123!";

    private String registerAndLogin(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + PW + "\"}"))
                .andExpect(status().isCreated());
        MvcResult r = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + PW + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private long createCustomer(String token, String name, String email) throws Exception {
        String body = "{\"name\":\"" + name + "\"" + (email == null ? "" : ",\"email\":\"" + email + "\"") + "}";
        MvcResult r = mockMvc.perform(post("/api/v1/customers").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).get("id").asLong();
    }

    @Test
    void create_returns201WithLocation() throws Exception {
        String token = registerAndLogin("c-create@example.com");
        mockMvc.perform(post("/api/v1/customers").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Acme\",\"email\":\"acme@x.com\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.matchesRegex(".*/api/v1/customers/\\d+")))
                .andExpect(jsonPath("$.name").value("Acme"));
    }

    @Test
    void create_blankName_returns400() throws Exception {
        String token = registerAndLogin("c-blank@example.com");
        mockMvc.perform(post("/api/v1/customers").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"));
    }

    @Test
    void create_invalidEmail_returns400() throws Exception {
        String token = registerAndLogin("c-email@example.com");
        mockMvc.perform(post("/api/v1/customers").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"A\",\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void create_duplicateEmailForSameOwner_returns409() throws Exception {
        String token = registerAndLogin("c-dup@example.com");
        createCustomer(token, "A", "dup@x.com");
        mockMvc.perform(post("/api/v1/customers").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"B\",\"email\":\"dup@x.com\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"));
    }

    @Test
    void duplicateEmail_allowedAcrossDifferentOwners() throws Exception {
        String a = registerAndLogin("c-own-a@example.com");
        String b = registerAndLogin("c-own-b@example.com");
        createCustomer(a, "A", "shared@x.com");
        // Same email under a different owner is fine.
        mockMvc.perform(post("/api/v1/customers").header("Authorization", "Bearer " + b)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"B\",\"email\":\"shared@x.com\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void list_ownScoped_withSearch() throws Exception {
        String a = registerAndLogin("c-list-a@example.com");
        String b = registerAndLogin("c-list-b@example.com");
        createCustomer(a, "Globex", "info@globex.com");
        createCustomer(a, "Initech", "hi@initech.com");
        createCustomer(b, "Umbrella", "x@umbrella.com");
        mockMvc.perform(get("/api/v1/customers?search=glob").header("Authorization", "Bearer " + a))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.content[0].name").value("Globex"));
    }

    @Test
    void getById_nonOwner_returns404() throws Exception {
        String a = registerAndLogin("c-get-a@example.com");
        String b = registerAndLogin("c-get-b@example.com");
        long id = createCustomer(a, "Secret", null);
        mockMvc.perform(get("/api/v1/customers/" + id).header("Authorization", "Bearer " + b))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void update_fullReplacement_returns200() throws Exception {
        String token = registerAndLogin("c-upd@example.com");
        long id = createCustomer(token, "Old", null);
        mockMvc.perform(put("/api/v1/customers/" + id).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"New\",\"status\":\"INACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New"))
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    @Test
    void delete_owner_returns204() throws Exception {
        String token = registerAndLogin("c-del@example.com");
        long id = createCustomer(token, "X", null);
        mockMvc.perform(delete("/api/v1/customers/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/customers/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void endpoints_unauthenticated_return401() throws Exception {
        mockMvc.perform(get("/api/v1/customers")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/customers").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"x\"}")).andExpect(status().isUnauthorized());
    }
}

package com.supervisesuite.backend.auth.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.supervisesuite.backend.TestcontainersConfiguration;
import com.supervisesuite.backend.auth.AuthTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = {
        "APP_PORT=0",
        "JWT_SECRET=dGVzdC1zZWNyZXQtd2hpY2gtaXMtbG9uZy1lbm91Z2gtZm9yLXRlc3RpbmctcHVycG9zZXMtb25seQ=="
    }
)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AuthControllerSupervisorIT extends AuthTestBase {

    private static final String REGISTER_SUPERVISOR_URL = "/api/auth/register/supervisor";

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void cleanUp() {
        safeCleanup();
    }

    @Test
    void valid_sliit_email_returns_201() throws Exception {
        mockMvc.perform(post(REGISTER_SUPERVISOR_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(buildValidPayload()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.role").value("SUPERVISOR"))
            .andExpect(jsonPath("$.data.registrationNumber").isEmpty());
    }

    @Test
    void student_email_returns_400() throws Exception {
        String payload = buildValidPayload().replace("jane.doe@sliit.lk", "student@my.sliit.lk");

        mockMvc.perform(post(REGISTER_SUPERVISOR_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.error.details[*].field", hasItem("email")));
    }

    @Test
    void non_sliit_email_returns_400() throws Exception {
        String payload = buildValidPayload().replace("jane.doe@sliit.lk", "user@gmail.com");

        mockMvc.perform(post(REGISTER_SUPERVISOR_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.error.details[*].field", hasItem("email")));
    }

    @Test
    void missing_firstName_returns_400() throws Exception {
        String payload = """
            {
              "lastName": "Doe",
              "email": "jane.doe@sliit.lk",
              "password": "Test@1234",
              "confirmPassword": "Test@1234"
            }
            """;

        mockMvc.perform(post(REGISTER_SUPERVISOR_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.error.details[*].field", hasItem("firstName")));
    }

    @Test
    void weak_password_returns_400() throws Exception {
        String payload = buildValidPayload().replace("Test@1234", "password");

        mockMvc.perform(post(REGISTER_SUPERVISOR_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.error.details[*].field", hasItem("password")));
    }

    @Test
    void duplicate_email_returns_409() throws Exception {
        String payload = buildValidPayload();

        mockMvc.perform(post(REGISTER_SUPERVISOR_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isCreated());

        mockMvc.perform(post(REGISTER_SUPERVISOR_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("CONFLICT"));
    }

    private String buildValidPayload() {
        return """
            {
              "firstName": "Jane",
              "lastName": "Doe",
              "email": "jane.doe@sliit.lk",
              "password": "Test@1234",
              "confirmPassword": "Test@1234"
            }
            """;
    }
}

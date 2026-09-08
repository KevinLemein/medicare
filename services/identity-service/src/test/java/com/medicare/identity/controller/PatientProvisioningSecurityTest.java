package com.medicare.identity.controller;

import com.medicare.identity.AbstractIntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Confirms the "/internal/patients/provision" scope check
 * (hasAuthority("SCOPE_identity:provision-patient")) actually gates the
 * endpoint end-to-end, in both directions.
 */
@AutoConfigureMockMvc
class PatientProvisioningSecurityTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private String requestBody() {
        return """
                {"idempotencyKey":"%s","email":"patient-%s@test.local","firstName":"Test","lastName":"Patient"}
                """.formatted(UUID.randomUUID(), UUID.randomUUID());
    }

    @Test
    void withoutProvisioningScope_isForbidden() throws Exception {
        mockMvc.perform(post("/internal/patients/provision")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_some-other-scope")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    void withProvisioningScope_isCreated() throws Exception {
        mockMvc.perform(post("/internal/patients/provision")
                        .with(jwt()
                                .jwt(jwt -> jwt.subject("patient-service"))
                                .authorities(new SimpleGrantedAuthority("SCOPE_identity:provision-patient")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody()))
                .andExpect(status().isCreated());
    }
}

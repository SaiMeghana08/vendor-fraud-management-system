package com.vendorfraud.integration;

import com.vendorfraud.support.IntegrationTestBase;
import com.vendorfraud.support.TestFixtures;
import com.vendorfraud.user.RoleName;
import com.vendorfraud.user.dto.LoginRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthenticationIntegrationTest extends IntegrationTestBase {

    @Test
    @DisplayName("a valid login returns a bearer token and the caller's roles")
    void loginSucceeds() throws Exception {
        fixtures.createUser("maker.one", "Maya Kerr", true, RoleName.MAKER);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest("maker.one", TestFixtures.PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.roles[0]").value("ROLE_MAKER"))
                .andExpect(jsonPath("$.user.username").value("maker.one"));
    }

    @Test
    @DisplayName("a wrong password is rejected without revealing which part was wrong")
    void wrongPasswordRejected() throws Exception {
        fixtures.createUser("maker.one", "Maya Kerr", true, RoleName.MAKER);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest("maker.one", "not-the-password"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("a soft-deleted user cannot log in")
    void inactiveUserCannotLogIn() throws Exception {
        fixtures.createUser("former.employee", "Former Employee", false, RoleName.MAKER);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest("former.employee", TestFixtures.PASSWORD))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("protected endpoints reject an absent or forged token")
    void protectedEndpointsRequireAValidToken() throws Exception {
        mockMvc.perform(get("/api/vendors"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/vendors").header("Authorization", "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("health is open; everything else is not")
    void healthIsPublic() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("there is no registration endpoint")
    void noRegistrationEndpointExists() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"intruder\",\"password\":\"x\"}"))
                .andExpect(status().is4xxClientError());
    }
}

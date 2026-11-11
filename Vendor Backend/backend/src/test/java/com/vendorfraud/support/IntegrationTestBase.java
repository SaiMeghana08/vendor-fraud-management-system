package com.vendorfraud.support;

import com.vendorfraud.user.dto.LoginRequest;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Shared setup for the HTTP-level tests. Every test starts from a clean
 * database and authenticates exactly as a browser would - through
 * {@code POST /api/auth/login} - so the token path is exercised too.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected TestFixtures fixtures;

    @BeforeEach
    void resetDatabase() {
        fixtures.reset();
    }

    protected String json(Object value) {
        return objectMapper.writeValueAsString(value);
    }

    protected String tokenFor(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest(username, TestFixtures.PASSWORD))))
                .andReturn();
        if (result.getResponse().getStatus() != 200) {
            throw new IllegalStateException("Login failed for " + username
                    + ": HTTP " + result.getResponse().getStatus());
        }
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asString();
    }

    protected MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder builder,
                                                   String token) {
        return builder.header("Authorization", "Bearer " + token);
    }

    /** Reads a numeric field out of a JSON response body. */
    protected long longField(String body, String... path) throws Exception {
        var node = objectMapper.readTree(body);
        for (String segment : path) {
            node = node.get(segment);
        }
        return node.asLong();
    }

    protected String stringField(String body, String... path) throws Exception {
        var node = objectMapper.readTree(body);
        for (String segment : path) {
            node = node.get(segment);
        }
        return node.asString();
    }
}

package com.interswitch.walletapp.base;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interswitch.walletapp.models.request.LoginRequest;
import com.interswitch.walletapp.models.response.AuthResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
@AutoConfigureMockMvc
public abstract class BaseControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    protected String superAdminToken;

    protected String merchantToken;

    protected AuthResponse login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, password))))
                .andExpect(status().isOk())
                .andReturn();

        String json = result.getResponse().getContentAsString();
        return objectMapper.readValue(
                objectMapper.readTree(json).get("data").toString(),
                AuthResponse.class
        );
    }

    protected String loginAndGetAccessToken(String email, String password) throws Exception {
        return login(email, password).accessToken();
    }

    protected String loginAndGetRefreshToken(String email, String password) throws Exception {
        return login(email, password).refreshToken();
    }

    protected String bearerToken(String token) {
        return "Bearer " + token;
    }
}
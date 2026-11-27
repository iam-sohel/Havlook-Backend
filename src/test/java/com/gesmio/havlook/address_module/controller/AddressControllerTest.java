package com.gesmio.havlook.address_module.controller;

import com.gesmio.havlook.address_module.service.AddressService;
import com.gesmio.havlook.auth_module.config.SecurityConfig;
import com.gesmio.havlook.auth_module.util.JwtAuthenticationFilter;
import com.gesmio.havlook.auth_module.util.JwtUtil;
import com.gesmio.havlook.user_module.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AddressController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
@ActiveProfiles("test")
@DisplayName("AddressController RBAC Tests")
class AddressControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AddressService addressService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    @DisplayName("GET /api/v1/addresses -> 401/403 without auth")
    void getAddresses_withoutAuth_shouldBeRejected() throws Exception {
        mockMvc.perform(get("/api/v1/addresses"))
                .andExpect(status().is(anyOf(is(401), is(403))));
    }

    @Test
    @DisplayName("GET /api/v1/addresses -> 403 for SELLER")
    @WithMockUser(username = "2", roles = "SELLER")
    void getAddresses_asSeller_shouldReturn403() throws Exception {
        mockMvc.perform(get("/api/v1/addresses"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/addresses -> 200 for CUSTOMER")
    @WithMockUser(username = "1", roles = "CUSTOMER")
    void getAddresses_asCustomer_shouldReturn200() throws Exception {
        when(addressService.getUserAddresses(1L)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/addresses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}

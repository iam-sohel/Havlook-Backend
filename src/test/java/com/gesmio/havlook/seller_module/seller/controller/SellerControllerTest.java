package com.gesmio.havlook.seller_module.seller.controller;

import com.gesmio.havlook.auth_module.config.SecurityConfig;
import com.gesmio.havlook.auth_module.util.JwtAuthenticationFilter;
import com.gesmio.havlook.auth_module.util.JwtUtil;
import com.gesmio.havlook.seller_module.auth.SellerAuthorizationService;
import com.gesmio.havlook.seller_module.seller.dto.SellerResponse;
import com.gesmio.havlook.seller_module.seller.entity.Seller;
import com.gesmio.havlook.seller_module.seller.service.SellerService;
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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SellerController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
@ActiveProfiles("test")
@DisplayName("SellerController RBAC Tests")
class SellerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SellerService sellerService;

    @MockitoBean
    private SellerAuthorizationService sellerAuthorizationService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    @DisplayName("GET /api/v1/sellers/me -> 403 for CUSTOMER (SECURITY FIX verify)")
    @WithMockUser(username = "1", roles = "CUSTOMER")
    void getProfile_asCustomer_shouldReturn403() throws Exception {
        mockMvc.perform(get("/api/v1/sellers/me"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/sellers/me -> 200 for SELLER")
    @WithMockUser(username = "2", roles = "SELLER")
    void getProfile_asSeller_shouldReturn200() throws Exception {
        when(sellerAuthorizationService.getAuthorizedSeller(2L)).thenReturn(new Seller());
        when(sellerService.getMySellerProfile(2L)).thenReturn(new SellerResponse());

        mockMvc.perform(get("/api/v1/sellers/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}

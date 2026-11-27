package com.gesmio.havlook.seller_module.sellerBank.controller;

import com.gesmio.havlook.auth_module.config.SecurityConfig;
import com.gesmio.havlook.auth_module.util.JwtAuthenticationFilter;
import com.gesmio.havlook.auth_module.util.JwtUtil;
import com.gesmio.havlook.seller_module.auth.SellerAuthorizationService;
import com.gesmio.havlook.seller_module.seller.entity.Seller;
import com.gesmio.havlook.seller_module.sellerBank.dto.SellerBankResponse;
import com.gesmio.havlook.seller_module.sellerBank.service.SellerBankService;
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

@WebMvcTest(controllers = SellerBankController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
@ActiveProfiles("test")
@DisplayName("SellerBankController RBAC Tests")
class SellerBankControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SellerBankService sellerBankService;

    @MockitoBean
    private SellerAuthorizationService sellerAuthorizationService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    @DisplayName("GET /api/v1/sellers/bank -> 403 for CUSTOMER (SECURITY FIX verify)")
    @WithMockUser(username = "1", roles = "CUSTOMER")
    void getMyBank_asCustomer_shouldReturn403() throws Exception {
        mockMvc.perform(get("/api/v1/sellers/bank"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/sellers/bank -> 200 for SELLER")
    @WithMockUser(username = "2", roles = "SELLER")
    void getMyBank_asSeller_shouldReturn200() throws Exception {
        when(sellerAuthorizationService.getAuthorizedSeller(2L)).thenReturn(new Seller());
        when(sellerBankService.getMyBankAccount(2L)).thenReturn(new SellerBankResponse());

        mockMvc.perform(get("/api/v1/sellers/bank"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}

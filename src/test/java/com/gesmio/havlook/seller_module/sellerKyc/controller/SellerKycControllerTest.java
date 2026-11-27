package com.gesmio.havlook.seller_module.sellerKyc.controller;

import com.gesmio.havlook.auth_module.config.SecurityConfig;
import com.gesmio.havlook.auth_module.util.JwtAuthenticationFilter;
import com.gesmio.havlook.auth_module.util.JwtUtil;
import com.gesmio.havlook.seller_module.sellerKyc.dto.SellerKycResponse;
import com.gesmio.havlook.seller_module.sellerKyc.entity.KycStatus;
import com.gesmio.havlook.seller_module.sellerKyc.service.SellerKycService;
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

import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SellerKycController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
@ActiveProfiles("test")
@DisplayName("SellerKycController Tests")
class SellerKycControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SellerKycService sellerKycService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    @DisplayName("GET /api/v1/sellers/kyc -> 401/403 without auth")
    void getMyKyc_withoutAuth_shouldBeRejected() throws Exception {
        mockMvc.perform(get("/api/v1/sellers/kyc"))
                .andExpect(status().is(anyOf(is(401), is(403))));
    }

    @Test
    @DisplayName("GET /api/v1/sellers/kyc -> 403 for CUSTOMER (BUG FIX @PreAuthorize verify)")
    @WithMockUser(username = "1", roles = "CUSTOMER")
    void getMyKyc_asCustomer_shouldReturn403() throws Exception {
        mockMvc.perform(get("/api/v1/sellers/kyc"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/sellers/kyc -> 200 for SELLER")
    @WithMockUser(username = "2", roles = "SELLER")
    void getMyKyc_asSeller_shouldReturn200() throws Exception {
        SellerKycResponse response = SellerKycResponse.builder()
                .sellerId(20L)
                .status(KycStatus.PENDING)
                .build();

        when(sellerKycService.getMyKyc(2L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/sellers/kyc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    @DisplayName("GET /api/v1/sellers/kyc/documents/pan/signed-url -> 200 for SELLER")
    @WithMockUser(username = "2", roles = "SELLER")
    void getSignedUrl_asSeller_shouldReturn200() throws Exception {
        when(sellerKycService.getSignedDocumentUrl(2L, "pan"))
                .thenReturn("https://test.supabase.co/storage/v1/object/sign/sellerekyc/20/pan/doc.pdf?token=abc");

        mockMvc.perform(get("/api/v1/sellers/kyc/documents/pan/signed-url"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}

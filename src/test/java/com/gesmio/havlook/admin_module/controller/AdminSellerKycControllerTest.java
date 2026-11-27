package com.gesmio.havlook.admin_module.controller;

import com.gesmio.havlook.admin_module.service.AdminSellerKycService;
import com.gesmio.havlook.auth_module.config.SecurityConfig;
import com.gesmio.havlook.auth_module.security.CustomUserDetails;
import com.gesmio.havlook.auth_module.util.JwtAuthenticationFilter;
import com.gesmio.havlook.auth_module.util.JwtUtil;
import com.gesmio.havlook.user_module.entity.Role;
import com.gesmio.havlook.user_module.entity.User;
import com.gesmio.havlook.user_module.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminSellerKycController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
@ActiveProfiles("test")
@DisplayName("AdminSellerKycController Tests")
class AdminSellerKycControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminSellerKycService adminSellerKycService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private UserRepository userRepository;

    private CustomUserDetails adminDetails;
    private CustomUserDetails sellerDetails;

    @BeforeEach
    void setUp() {
        Role adminRole = new Role();
        adminRole.setName("ADMIN");
        User admin = User.builder().id(99L).email("admin@test.com").password("p").role(adminRole).enabled(true).build();
        adminDetails = new CustomUserDetails(admin);

        Role sellerRole = new Role();
        sellerRole.setName("SELLER");
        User seller = User.builder().id(2L).email("seller@test.com").password("p").role(sellerRole).enabled(true).build();
        sellerDetails = new CustomUserDetails(seller);
    }

    @Test
    @DisplayName("GET /api/v1/admin/sellers/kyc/pending -> 403 for SELLER")
    void getPendingKyc_asSeller_shouldReturn403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/sellers/kyc/pending")
                        .with(user(sellerDetails)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/admin/sellers/kyc/pending -> 200 for ADMIN")
    void getPendingKyc_asAdmin_shouldReturn200() throws Exception {
        when(adminSellerKycService.getPendingKyc(any())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/admin/sellers/kyc/pending")
                        .with(user(adminDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("PUT /api/v1/admin/sellers/10/kyc/approve -> 200 for ADMIN")
    void approveKyc_asAdmin_shouldReturn200() throws Exception {
        doNothing().when(adminSellerKycService).approveKyc(10L, 99L);

        mockMvc.perform(put("/api/v1/admin/sellers/10/kyc/approve")
                        .with(user(adminDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("PUT /api/v1/admin/sellers/10/kyc/reject -> 400 when rejectionReason is blank")
    void rejectKyc_blankReason_shouldReturn400() throws Exception {
        mockMvc.perform(put("/api/v1/admin/sellers/10/kyc/reject")
                        .with(user(adminDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rejectionReason\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}

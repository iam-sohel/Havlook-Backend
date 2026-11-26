package com.gesmio.havlook.auth_module.controller;

import com.gesmio.havlook.auth_module.config.SecurityConfig;
import com.gesmio.havlook.auth_module.util.JwtAuthenticationFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security & RBAC Tests
 * Verifies that all endpoints enforce correct role-based access control.
 */
@WebMvcTest(controllers = {})
@Import(SecurityConfig.class)
@ActiveProfiles("test")
@DisplayName("Security & RBAC Tests")
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    // =========================================================
    // PUBLIC ENDPOINTS - No auth required
    // =========================================================

    @Nested
    @DisplayName("Public Endpoints - Should be accessible without auth")
    class PublicEndpointsTest {

        @Test
        @DisplayName("GET /api/health -> 200 (no auth needed)")
        void healthEndpoint_withoutAuth_shouldBeAccessible() throws Exception {
            mockMvc.perform(get("/api/health"))
                    .andExpect(status().isNotFound()); // 404 = no controller, NOT 401 = security works
        }

        @Test
        @DisplayName("GET /api/v1/products -> accessible without token")
        void products_withoutAuth_shouldBeAccessible() throws Exception {
            mockMvc.perform(get("/api/v1/products"))
                    .andExpect(status().isNotFound()); // 404 not 401
        }

        @Test
        @DisplayName("GET /api/v1/categories/** -> accessible without token")
        void categories_withoutAuth_shouldBeAccessible() throws Exception {
            mockMvc.perform(get("/api/v1/categories/1"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("POST /api/v1/auth/login -> accessible without token")
        void login_withoutAuth_shouldBeAccessible() throws Exception {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType("application/json")
                            .content("{\"email\":\"test@test.com\",\"password\":\"pass\"}"))
                    .andExpect(status().isNotFound()); // 404 not 401
        }

        @Test
        @DisplayName("POST /api/v1/payments/webhook/razorpay -> accessible without token (BUG FIX verify)")
        void razorpayWebhook_withoutAuth_shouldBeAccessible() throws Exception {
            mockMvc.perform(post("/api/v1/payments/webhook/razorpay")
                            .contentType("application/json")
                            .content("{}"))
                    .andExpect(status().isNotFound()); // 404 not 401 - our fix works!
        }
    }

    // =========================================================
    // PROTECTED ENDPOINTS - Must require authentication
    // =========================================================

    @Nested
    @DisplayName("Protected Endpoints - Should require auth")
    class ProtectedEndpointsTest {

        @Test
        @DisplayName("GET /api/v1/cart -> 401 without token")
        void cart_withoutAuth_shouldReturn401() throws Exception {
            mockMvc.perform(get("/api/v1/cart"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("POST /api/v1/checkout -> 401 without token")
        void checkout_withoutAuth_shouldReturn401() throws Exception {
            mockMvc.perform(post("/api/v1/checkout"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("GET /api/v1/wishlist -> 401 without token")
        void wishlist_withoutAuth_shouldReturn401() throws Exception {
            mockMvc.perform(get("/api/v1/wishlist"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("GET /api/v1/orders -> 401 without token")
        void orders_withoutAuth_shouldReturn401() throws Exception {
            mockMvc.perform(get("/api/v1/orders"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("POST /api/v1/payments/create -> 401 without token")
        void payments_withoutAuth_shouldReturn401() throws Exception {
            mockMvc.perform(post("/api/v1/payments/create"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // =========================================================
    // ADMIN ENDPOINTS - Must require ADMIN role
    // =========================================================

    @Nested
    @DisplayName("Admin Endpoints - RBAC Tests")
    class AdminRbacTest {

        @Test
        @DisplayName("GET /api/v1/admin/brands -> 401 without any auth")
        void adminBrands_withoutAuth_shouldReturn401() throws Exception {
            mockMvc.perform(get("/api/v1/admin/brands"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("GET /api/v1/admin/brands -> 403 for CUSTOMER role")
        @WithMockUser(roles = "CUSTOMER")
        void adminBrands_asCustomer_shouldReturn403() throws Exception {
            mockMvc.perform(get("/api/v1/admin/brands"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("GET /api/v1/admin/brands -> 403 for SELLER role")
        @WithMockUser(roles = "SELLER")
        void adminBrands_asSeller_shouldReturn403() throws Exception {
            mockMvc.perform(get("/api/v1/admin/brands"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("POST /api/v1/admin/brands -> 403 for CUSTOMER role")
        @WithMockUser(roles = "CUSTOMER")
        void adminBrandsCreate_asCustomer_shouldReturn403() throws Exception {
            mockMvc.perform(post("/api/v1/admin/brands")
                            .contentType("application/json")
                            .content("{\"name\":\"TestBrand\"}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("PUT /api/v1/admin/brands/1 -> 403 for SELLER role")
        @WithMockUser(roles = "SELLER")
        void adminBrandsUpdate_asSeller_shouldReturn403() throws Exception {
            mockMvc.perform(put("/api/v1/admin/brands/1")
                            .contentType("application/json")
                            .content("{\"name\":\"UpdatedBrand\"}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("GET /api/v1/admin/customers -> 403 for CUSTOMER role")
        @WithMockUser(roles = "CUSTOMER")
        void adminCustomers_asCustomer_shouldReturn403() throws Exception {
            mockMvc.perform(get("/api/v1/admin/customers"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("GET /api/v1/admin/sellers -> 403 for SELLER role")
        @WithMockUser(roles = "SELLER")
        void adminSellers_asSeller_shouldReturn403() throws Exception {
            mockMvc.perform(get("/api/v1/admin/sellers"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("GET /api/v1/admin/orders -> 403 for CUSTOMER role")
        @WithMockUser(roles = "CUSTOMER")
        void adminOrders_asCustomer_shouldReturn403() throws Exception {
            mockMvc.perform(get("/api/v1/admin/orders"))
                    .andExpect(status().isForbidden());
        }
    }

    // =========================================================
    // CORS PREFLIGHT - OPTIONS must always pass
    // =========================================================

    @Nested
    @DisplayName("CORS Preflight - OPTIONS must always be permitted")
    class CorsPreflightTest {

        @Test
        @DisplayName("OPTIONS /** -> 200 always (no auth required)")
        void options_withoutAuth_shouldBePermitted() throws Exception {
            mockMvc.perform(options("/api/v1/cart")
                            .header("Origin", "http://localhost:3000")
                            .header("Access-Control-Request-Method", "GET"))
                    .andExpect(status().isOk());
        }
    }
}

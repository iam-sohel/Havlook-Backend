package com.gesmio.havlook.seller_module.warehouse_module.controller;

import com.gesmio.havlook.auth_module.config.SecurityConfig;
import com.gesmio.havlook.auth_module.util.JwtAuthenticationFilter;
import com.gesmio.havlook.auth_module.util.JwtUtil;
import com.gesmio.havlook.seller_module.warehouse_module.service.WarehouseService;
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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = WarehouseController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
@ActiveProfiles("test")
@DisplayName("WarehouseController RBAC Tests")
class WarehouseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WarehouseService warehouseService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    @DisplayName("GET /api/v1/sellers/warehouses -> 403 for CUSTOMER (SECURITY FIX verify)")
    @WithMockUser(username = "1", roles = "CUSTOMER")
    void getWarehouses_asCustomer_shouldReturn403() throws Exception {
        mockMvc.perform(get("/api/v1/sellers/warehouses"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/sellers/warehouses -> 200 for SELLER")
    @WithMockUser(username = "2", roles = "SELLER")
    void getWarehouses_asSeller_shouldReturn200() throws Exception {
        when(warehouseService.getMyWarehouses(2L)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/sellers/warehouses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}

package com.gesmio.havlook.order_module.controller;

import com.gesmio.havlook.auth_module.config.SecurityConfig;
import com.gesmio.havlook.auth_module.util.JwtAuthenticationFilter;
import com.gesmio.havlook.auth_module.util.JwtUtil;
import com.gesmio.havlook.order_module.OrderController;
import com.gesmio.havlook.order_module.OrderService;
import com.gesmio.havlook.order_module.OrderStatus;
import com.gesmio.havlook.order_module.dto.OrderResponseDTO;
import com.gesmio.havlook.user_module.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = OrderController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
@ActiveProfiles("test")
@DisplayName("OrderController Tests")
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    @DisplayName("GET /api/v1/orders/my -> 401/403 without auth")
    void getMyOrders_withoutAuth_shouldBeRejected() throws Exception {
        mockMvc.perform(get("/api/v1/orders/my"))
                .andExpect(status().is(anyOf(is(401), is(403))));
    }

    @Test
    @DisplayName("GET /api/v1/orders/my -> 200 for CUSTOMER (BUG FIX /v1/ verify)")
    @WithMockUser(username = "1", roles = "CUSTOMER")
    void getMyOrders_asCustomer_shouldReturn200() throws Exception {
        when(orderService.getMyOrders(eq("1"), any())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/orders/my"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("GET /api/v1/orders/my -> 403 for SELLER")
    @WithMockUser(username = "2", roles = "SELLER")
    void getMyOrders_asSeller_shouldReturn403() throws Exception {
        mockMvc.perform(get("/api/v1/orders/my"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/orders/my (old unversioned URL) -> 404 for CUSTOMER")
    @WithMockUser(username = "1", roles = "CUSTOMER")
    void getMyOrders_oldUrl_shouldReturn404() throws Exception {
        mockMvc.perform(get("/api/orders/my"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PATCH /api/v1/orders/1/cancel -> 200 for CUSTOMER")
    @WithMockUser(username = "1", roles = "CUSTOMER")
    void cancelOrder_asCustomer_shouldReturn200() throws Exception {
        OrderResponseDTO dto = new OrderResponseDTO();
        dto.setId(1L);
        dto.setStatus(OrderStatus.CANCELLED);

        when(orderService.cancelOrder("1", 1L)).thenReturn(dto);

        mockMvc.perform(patch("/api/v1/orders/1/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("PATCH /api/v1/orders/admin/1/status/SHIPPED -> 403 for CUSTOMER")
    @WithMockUser(username = "1", roles = "CUSTOMER")
    void updateOrderStatus_asCustomer_shouldReturn403() throws Exception {
        mockMvc.perform(patch("/api/v1/orders/admin/1/status/SHIPPED"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PATCH /api/v1/orders/admin/1/status/SHIPPED -> 200 for ADMIN")
    @WithMockUser(username = "99", roles = "ADMIN")
    void updateOrderStatus_asAdmin_shouldReturn200() throws Exception {
        OrderResponseDTO dto = new OrderResponseDTO();
        dto.setId(1L);
        dto.setStatus(OrderStatus.SHIPPED);

        when(orderService.updateOrderStatus(1L, OrderStatus.SHIPPED)).thenReturn(dto);

        mockMvc.perform(patch("/api/v1/orders/admin/1/status/SHIPPED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("SHIPPED"));
    }
}

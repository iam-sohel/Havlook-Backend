package com.gesmio.havlook.cart_module;

import com.gesmio.havlook.auth_module.config.SecurityConfig;
import com.gesmio.havlook.auth_module.util.JwtAuthenticationFilter;
import com.gesmio.havlook.auth_module.util.JwtUtil;
import com.gesmio.havlook.cart_module.dto.CartResponseDTO;
import com.gesmio.havlook.user_module.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CartController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
@ActiveProfiles("test")
@DisplayName("CartController Tests")
class CartControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CartService cartService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    @DisplayName("GET /api/v1/cart -> 401/403 without auth")
    void getCart_withoutAuth_shouldBeRejected() throws Exception {
        mockMvc.perform(get("/api/v1/cart"))
                .andExpect(status().is(anyOf(is(401), is(403))));
    }

    @Test
    @DisplayName("GET /api/v1/cart -> 403 for SELLER")
    @WithMockUser(username = "2", roles = "SELLER")
    void getCart_asSeller_shouldReturn403() throws Exception {
        mockMvc.perform(get("/api/v1/cart"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/cart -> 200 for CUSTOMER")
    @WithMockUser(username = "1", roles = "CUSTOMER")
    void getCart_asCustomer_shouldReturn200() throws Exception {
        when(cartService.getCart("1")).thenReturn(new CartResponseDTO());

        mockMvc.perform(get("/api/v1/cart"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("POST /api/v1/cart/items -> 201 Created for valid item")
    @WithMockUser(username = "1", roles = "CUSTOMER")
    void addItem_valid_shouldReturn201() throws Exception {
        when(cartService.addItem(eq("1"), any())).thenReturn(new CartResponseDTO());

        mockMvc.perform(post("/api/v1/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productVariantId\":5,\"quantity\":2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("POST /api/v1/cart/items -> 400 when quantity exceeds max (99)")
    @WithMockUser(username = "1", roles = "CUSTOMER")
    void addItem_excessQuantity_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productVariantId\":5,\"quantity\":100}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("DELETE /api/v1/cart -> 200 OK")
    @WithMockUser(username = "1", roles = "CUSTOMER")
    void clearCart_asCustomer_shouldReturn200() throws Exception {
        doNothing().when(cartService).clearCart("1");

        mockMvc.perform(delete("/api/v1/cart"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}

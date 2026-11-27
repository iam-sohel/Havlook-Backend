package com.gesmio.havlook.checkout_module;

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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CheckoutController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
@ActiveProfiles("test")
@DisplayName("CheckoutController Tests")
class CheckoutControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CheckoutService checkoutService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private UserRepository userRepository;

    private CustomUserDetails customerDetails;
    private CustomUserDetails sellerDetails;

    @BeforeEach
    void setUp() {
        Role customerRole = new Role();
        customerRole.setName("CUSTOMER");
        User customer = User.builder().id(1L).email("cust@test.com").password("p").role(customerRole).enabled(true).build();
        customerDetails = new CustomUserDetails(customer);

        Role sellerRole = new Role();
        sellerRole.setName("SELLER");
        User seller = User.builder().id(2L).email("seller@test.com").password("p").role(sellerRole).enabled(true).build();
        sellerDetails = new CustomUserDetails(seller);
    }

    @Test
    @DisplayName("POST /api/v1/checkout -> 401/403 without auth")
    void checkout_withoutAuth_shouldBeRejected() throws Exception {
        mockMvc.perform(post("/api/v1/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shippingAddressId\":1}"))
                .andExpect(status().is(anyOf(is(401), is(403))));
    }

    @Test
    @DisplayName("POST /api/v1/checkout -> 403 for SELLER role")
    void checkout_asSeller_shouldReturn403() throws Exception {
        mockMvc.perform(post("/api/v1/checkout")
                        .with(user(sellerDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shippingAddressId\":1}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/v1/checkout -> 400 when shippingAddressId is null")
    void checkout_nullAddress_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/checkout")
                        .with(user(customerDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/checkout -> 200 OK for valid CUSTOMER request")
    void checkout_validRequest_shouldReturn200() throws Exception {
        CheckoutResponseDTO dto = CheckoutResponseDTO.builder()
                .orderTotal(BigDecimal.valueOf(999))
                .currency("INR")
                .build();

        when(checkoutService.checkout(eq("1"), any())).thenReturn(dto);

        mockMvc.perform(post("/api/v1/checkout")
                        .with(user(customerDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shippingAddressId\":10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.currency").value("INR"));
    }
}

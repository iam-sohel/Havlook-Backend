package com.gesmio.havlook.payment_module.controller;

import com.gesmio.havlook.auth_module.config.SecurityConfig;
import com.gesmio.havlook.auth_module.security.CustomUserDetails;
import com.gesmio.havlook.auth_module.util.JwtAuthenticationFilter;
import com.gesmio.havlook.auth_module.util.JwtUtil;
import com.gesmio.havlook.payment_module.dto.CreatePaymentResponseDTO;
import com.gesmio.havlook.payment_module.dto.PaymentResponseDTO;
import com.gesmio.havlook.payment_module.entity.PaymentStatusEnum;
import com.gesmio.havlook.payment_module.service.PaymentService;
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
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PaymentController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
@ActiveProfiles("test")
@DisplayName("PaymentController Tests")
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentService paymentService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private UserRepository userRepository;

    private CustomUserDetails customUserDetails;

    @BeforeEach
    void setUp() {
        User testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("customer@test.com");
        testUser.setPassword("encoded");
        customUserDetails = new CustomUserDetails(testUser);
    }

    @Test
    @DisplayName("POST /api/v1/payments/create -> 401/403 without auth")
    void createPayment_withoutAuth_shouldBeRejected() throws Exception {
        mockMvc.perform(post("/api/v1/payments/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":1}"))
                .andExpect(status().is(anyOf(is(401), is(403))));
    }

    @Test
    @DisplayName("POST /api/v1/payments/create -> 201 Created for authenticated user")
    void createPayment_authenticated_shouldReturn201() throws Exception {
        CreatePaymentResponseDTO response = CreatePaymentResponseDTO.builder()
                .orderId(1L)
                .orderNumber("ORD-001")
                .razorpayOrderId("order_rzp_123")
                .amount(99900L)
                .currency("INR")
                .keyId("rzp_test_dummy")
                .build();

        when(paymentService.createRazorpayOrder(eq("1"), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/payments/create")
                        .with(user(customUserDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.razorpayOrderId").value("order_rzp_123"))
                .andExpect(jsonPath("$.data.currency").value("INR"));
    }

    @Test
    @DisplayName("POST /api/v1/payments/create -> 400 when orderId is null")
    void createPayment_nullOrderId_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/payments/create")
                        .with(user(customUserDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/payments/verify -> 200 OK when payment verified")
    void verifyPayment_validRequest_shouldReturn200() throws Exception {
        PaymentResponseDTO response = PaymentResponseDTO.builder()
                .transactionId(10L)
                .orderId(1L)
                .razorpayOrderId("order_rzp_123")
                .razorpayPaymentId("pay_rzp_456")
                .amount(BigDecimal.valueOf(999))
                .status(PaymentStatusEnum.SUCCESS)
                .build();

        when(paymentService.verifyPayment(eq("1"), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/payments/verify")
                        .with(user(customUserDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderId": 1,
                                  "razorpayOrderId": "order_rzp_123",
                                  "razorpayPaymentId": "pay_rzp_456",
                                  "razorpaySignature": "valid_sig"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"));
    }

    @Test
    @DisplayName("POST /api/v1/payments/verify -> 400 when signature is missing")
    void verifyPayment_missingSignature_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/payments/verify")
                        .with(user(customUserDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderId": 1,
                                  "razorpayOrderId": "order_rzp_123",
                                  "razorpayPaymentId": "pay_rzp_456"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/v1/payments/order/{orderId} -> 200 OK for authenticated user")
    void getPaymentStatus_authenticated_shouldReturn200() throws Exception {
        PaymentResponseDTO response = PaymentResponseDTO.builder()
                .orderId(1L)
                .status(PaymentStatusEnum.SUCCESS)
                .build();

        when(paymentService.getPaymentStatusByOrderId("1", 1L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/payments/order/1")
                        .with(user(customUserDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"));
    }

    @Test
    @DisplayName("POST /api/v1/payments/reconcile -> 200 OK for authenticated user")
    void reconcilePayment_authenticated_shouldReturn200() throws Exception {
        PaymentResponseDTO response = PaymentResponseDTO.builder()
                .orderId(1L)
                .status(PaymentStatusEnum.SUCCESS)
                .build();

        when(paymentService.reconcilePayment("1", 1L)).thenReturn(response);

        mockMvc.perform(post("/api/v1/payments/reconcile")
                        .param("orderId", "1")
                        .with(user(customUserDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("POST /api/v1/payments/order/{orderId}/refund -> 200 OK for authenticated user")
    void refundPayment_authenticated_shouldReturn200() throws Exception {
        PaymentResponseDTO response = PaymentResponseDTO.builder()
                .orderId(1L)
                .status(PaymentStatusEnum.REFUNDED)
                .build();

        when(paymentService.refundPayment("1", 1L)).thenReturn(response);

        mockMvc.perform(post("/api/v1/payments/order/1/refund")
                        .with(user(customUserDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("REFUNDED"));
    }

    @Test
    @DisplayName("POST /api/v1/payments/webhook/razorpay -> 200 OK without auth (BUG FIX verify)")
    void handleRazorpayWebhook_withoutAuth_shouldReturn200() throws Exception {
        doNothing().when(paymentService).handleRazorpayWebhook(any(), any());

        mockMvc.perform(post("/api/v1/payments/webhook/razorpay")
                        .header("X-Razorpay-Signature", "test_hmac_signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"event\":\"payment.captured\"}"))
                .andExpect(status().isOk());

        verify(paymentService).handleRazorpayWebhook(
                "{\"event\":\"payment.captured\"}",
                "test_hmac_signature"
        );
    }

    @Test
    @DisplayName("POST /api/v1/payments/webhook/razorpay -> 400 when X-Razorpay-Signature header missing")
    void handleRazorpayWebhook_missingHeader_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/payments/webhook/razorpay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"event\":\"payment.captured\"}"))
                .andExpect(status().isBadRequest());
    }
}

package com.gesmio.havlook.payment_module;

import com.gesmio.havlook.order_module.OrderEntity;
import com.gesmio.havlook.order_module.OrderRepository;
import com.gesmio.havlook.order_module.OrderStatus;
import com.gesmio.havlook.payment_module.dto.CreatePaymentRequestDTO;
import com.gesmio.havlook.payment_module.dto.PaymentResponseDTO;
import com.gesmio.havlook.payment_module.dto.VerifyPaymentRequestDTO;
import com.gesmio.havlook.payment_module.entity.PaymentStatusEnum;
import com.gesmio.havlook.payment_module.entity.PaymentTransaction;
import com.gesmio.havlook.payment_module.exceptions.PaymentNotFoundException;
import com.gesmio.havlook.payment_module.exceptions.PaymentValidationException;
import com.gesmio.havlook.payment_module.exceptions.PaymentVerificationException;
import com.gesmio.havlook.payment_module.repository.PaymentTransactionRepository;
import com.gesmio.havlook.payment_module.service.PaymentServiceImpl;
import com.gesmio.havlook.seller_module.payment_module.service.SellerEarningService;
import com.gesmio.havlook.user_module.entity.User;
import com.gesmio.havlook.user_module.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PaymentServiceImpl.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService Unit Tests")
class PaymentServiceImplTest {

    @InjectMocks
    private PaymentServiceImpl paymentService;

    @Mock
    private PaymentTransactionRepository paymentRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SellerEarningService sellerEarningService;

    private User testUser;
    private OrderEntity testOrder;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(paymentService, "keyId", "rzp_test_dummy");
        ReflectionTestUtils.setField(paymentService, "keySecret", "TEST_ONLY_NOT_A_REAL_SECRET");
        ReflectionTestUtils.setField(paymentService, "webhookSecret", "TEST_ONLY_NOT_A_REAL_SECRET");

        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("customer@test.com");

        testOrder = new OrderEntity();
        testOrder.setId(1L);
        testOrder.setOrderNumber("ORD-001");
        testOrder.setTotalAmount(BigDecimal.valueOf(999));
        testOrder.setCurrency("INR");
        testOrder.setStatus(OrderStatus.PENDING);
        testOrder.setPaymentExpiresAt(LocalDateTime.now().plusMinutes(15));
        testOrder.setUser(testUser);
    }

    // =========================================================
    // CREATE RAZORPAY ORDER TESTS
    // =========================================================

    @Nested
    @DisplayName("createRazorpayOrder()")
    class CreatePaymentTests {

        @Test
        @DisplayName("Should throw PaymentNotFoundException when order not found")
        void createPayment_orderNotFound_shouldThrow() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(orderRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

            CreatePaymentRequestDTO req = new CreatePaymentRequestDTO();
            req.setOrderId(99L);

            assertThatThrownBy(() -> paymentService.createRazorpayOrder("1", req))
                    .isInstanceOf(PaymentNotFoundException.class);

            verify(orderRepository).findByIdForUpdate(99L);
            verifyNoInteractions(paymentRepository);
        }

        @Test
        @DisplayName("Should throw when order belongs to another user")
        void createPayment_orderBelongsToAnotherUser_shouldThrow() {
            User otherUser = new User();
            otherUser.setId(99L);
            testOrder.setUser(otherUser);

            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testOrder));

            CreatePaymentRequestDTO req = new CreatePaymentRequestDTO();
            req.setOrderId(1L);

            assertThatThrownBy(() -> paymentService.createRazorpayOrder("1", req))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("Should throw PaymentValidationException when order already confirmed")
        void createPayment_orderAlreadyPaid_shouldThrow() {
            testOrder.setStatus(OrderStatus.CONFIRMED);

            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testOrder));

            CreatePaymentRequestDTO req = new CreatePaymentRequestDTO();
            req.setOrderId(1L);

            assertThatThrownBy(() -> paymentService.createRazorpayOrder("1", req))
                    .isInstanceOf(PaymentValidationException.class);
        }
    }

    // =========================================================
    // VERIFY PAYMENT TESTS
    // =========================================================

    @Nested
    @DisplayName("verifyPayment()")
    class VerifyPaymentTests {

        @Test
        @DisplayName("Should throw PaymentNotFoundException when transaction not found")
        void verifyPayment_transactionNotFound_shouldThrow() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testOrder));
            when(paymentRepository.findByRazorpayOrderIdForUpdate("order_xyz"))
                    .thenReturn(Optional.empty());

            VerifyPaymentRequestDTO req = VerifyPaymentRequestDTO.builder()
                    .orderId(1L)
                    .razorpayOrderId("order_xyz")
                    .razorpayPaymentId("pay_abc")
                    .razorpaySignature("invalid_sig")
                    .build();

            assertThatThrownBy(() -> paymentService.verifyPayment("1", req))
                    .isInstanceOf(PaymentNotFoundException.class);
        }

        @Test
        @DisplayName("Should return existing response idempotently when already SUCCESS")
        void verifyPayment_alreadyVerified_shouldReturnIdempotently() {
            PaymentTransaction existing = new PaymentTransaction();
            existing.setId(10L);
            existing.setOrder(testOrder);
            existing.setRazorpayOrderId("order_xyz");
            existing.setRazorpayPaymentId("pay_abc");
            existing.setAmountInPaise(99900L);
            existing.setCurrency("INR");
            existing.setStatus(PaymentStatusEnum.SUCCESS);

            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testOrder));
            when(paymentRepository.findByRazorpayOrderIdForUpdate("order_xyz"))
                    .thenReturn(Optional.of(existing));

            VerifyPaymentRequestDTO req = VerifyPaymentRequestDTO.builder()
                    .orderId(1L)
                    .razorpayOrderId("order_xyz")
                    .razorpayPaymentId("pay_abc")
                    .razorpaySignature("some_sig")
                    .build();

            PaymentResponseDTO res = paymentService.verifyPayment("1", req);
            assertThat(res.getStatus()).isEqualTo(PaymentStatusEnum.SUCCESS);
            assertThat(res.getRazorpayOrderId()).isEqualTo("order_xyz");
        }

        @Test
        @DisplayName("Should throw PaymentVerificationException when transaction already REFUNDED")
        void verifyPayment_alreadyRefunded_shouldThrow() {
            PaymentTransaction refunded = new PaymentTransaction();
            refunded.setOrder(testOrder);
            refunded.setRazorpayOrderId("order_xyz");
            refunded.setStatus(PaymentStatusEnum.REFUNDED);

            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testOrder));
            when(paymentRepository.findByRazorpayOrderIdForUpdate("order_xyz"))
                    .thenReturn(Optional.of(refunded));

            VerifyPaymentRequestDTO req = VerifyPaymentRequestDTO.builder()
                    .orderId(1L)
                    .razorpayOrderId("order_xyz")
                    .razorpayPaymentId("pay_abc")
                    .razorpaySignature("some_sig")
                    .build();

            assertThatThrownBy(() -> paymentService.verifyPayment("1", req))
                    .isInstanceOf(PaymentVerificationException.class);
        }
    }

    // =========================================================
    // REFUND TESTS
    // =========================================================

    @Nested
    @DisplayName("refundPayment()")
    class RefundTests {

        @Test
        @DisplayName("Should throw PaymentNotFoundException when no payment for order")
        void refund_noPaymentFound_shouldThrow() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testOrder));
            when(paymentRepository.findByOrderForUpdate(testOrder)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> paymentService.refundPayment("1", 1L))
                    .isInstanceOf(PaymentNotFoundException.class);
        }

        @Test
        @DisplayName("Should throw PaymentValidationException when refunding a non-SUCCESS payment")
        void refund_notSuccessfulPayment_shouldThrow() {
            PaymentTransaction failedTx = new PaymentTransaction();
            failedTx.setStatus(PaymentStatusEnum.FAILED);
            failedTx.setOrder(testOrder);

            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testOrder));
            when(paymentRepository.findByOrderForUpdate(testOrder)).thenReturn(Optional.of(failedTx));

            assertThatThrownBy(() -> paymentService.refundPayment("1", 1L))
                    .isInstanceOf(PaymentValidationException.class);
        }
    }
}

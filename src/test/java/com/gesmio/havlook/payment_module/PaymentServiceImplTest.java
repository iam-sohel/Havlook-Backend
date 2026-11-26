package com.gesmio.havlook.payment_module;

import com.gesmio.havlook.order_module.OrderEntity;
import com.gesmio.havlook.order_module.OrderRepository;
import com.gesmio.havlook.order_module.OrderStatus;
import com.gesmio.havlook.payment_module.dto.CreatePaymentRequestDTO;
import com.gesmio.havlook.payment_module.dto.VerifyPaymentRequestDTO;
import com.gesmio.havlook.payment_module.entity.PaymentStatusEnum;
import com.gesmio.havlook.payment_module.entity.PaymentTransaction;
import com.gesmio.havlook.payment_module.exceptions.PaymentNotFoundException;
import com.gesmio.havlook.payment_module.exceptions.PaymentValidationException;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PaymentServiceImpl.
 * External Razorpay calls are intentionally not invoked here (mocked at service level).
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
        // Inject @Value fields
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
        testOrder.setStatus(OrderStatus.PENDING);
        testOrder.setUser(testUser);
    }

    // =========================================================
    // CREATE PAYMENT TESTS
    // =========================================================

    @Nested
    @DisplayName("createPayment()")
    class CreatePaymentTests {

        @Test
        @DisplayName("Should throw PaymentValidationException when order not found")
        void createPayment_orderNotFound_shouldThrow() {
            when(orderRepository.findById(99L)).thenReturn(Optional.empty());

            CreatePaymentRequestDTO req = new CreatePaymentRequestDTO();
            req.setOrderId(99L);

            assertThatThrownBy(() -> paymentService.createPayment(req, 1L))
                    .isInstanceOf(PaymentNotFoundException.class);

            verify(orderRepository).findById(99L);
            verifyNoInteractions(paymentRepository);
        }

        @Test
        @DisplayName("Should throw PaymentValidationException when order belongs to another user")
        void createPayment_orderBelongsToAnotherUser_shouldThrow() {
            User otherUser = new User();
            otherUser.setId(99L);
            testOrder.setUser(otherUser);

            when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

            CreatePaymentRequestDTO req = new CreatePaymentRequestDTO();
            req.setOrderId(1L);

            assertThatThrownBy(() -> paymentService.createPayment(req, 1L))
                    .isInstanceOf(PaymentValidationException.class);
        }

        @Test
        @DisplayName("Should throw PaymentValidationException when order already paid")
        void createPayment_orderAlreadyPaid_shouldThrow() {
            testOrder.setStatus(OrderStatus.CONFIRMED);

            when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

            CreatePaymentRequestDTO req = new CreatePaymentRequestDTO();
            req.setOrderId(1L);

            assertThatThrownBy(() -> paymentService.createPayment(req, 1L))
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
            when(paymentRepository.findByRazorpayOrderId("order_xyz"))
                    .thenReturn(Optional.empty());

            VerifyPaymentRequestDTO req = new VerifyPaymentRequestDTO();
            req.setRazorpayOrderId("order_xyz");
            req.setRazorpayPaymentId("pay_abc");
            req.setRazorpaySignature("invalid_sig");

            assertThatThrownBy(() -> paymentService.verifyPayment(req))
                    .isInstanceOf(PaymentNotFoundException.class);
        }

        @Test
        @DisplayName("Should throw PaymentValidationException when transaction already verified")
        void verifyPayment_alreadyVerified_shouldThrow() {
            PaymentTransaction existing = new PaymentTransaction();
            existing.setRazorpayOrderId("order_xyz");
            existing.setStatus(PaymentStatusEnum.SUCCESS);

            when(paymentRepository.findByRazorpayOrderId("order_xyz"))
                    .thenReturn(Optional.of(existing));

            VerifyPaymentRequestDTO req = new VerifyPaymentRequestDTO();
            req.setRazorpayOrderId("order_xyz");
            req.setRazorpayPaymentId("pay_abc");
            req.setRazorpaySignature("some_sig");

            assertThatThrownBy(() -> paymentService.verifyPayment(req))
                    .isInstanceOf(PaymentValidationException.class);
        }
    }

    // =========================================================
    // REFUND TESTS
    // =========================================================

    @Nested
    @DisplayName("processRefund()")
    class RefundTests {

        @Test
        @DisplayName("Should throw PaymentNotFoundException when no payment for order")
        void refund_noPaymentFound_shouldThrow() {
            when(paymentRepository.findByOrderId(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> paymentService.processRefund(1L, 1L))
                    .isInstanceOf(PaymentNotFoundException.class);
        }

        @Test
        @DisplayName("Should throw PaymentValidationException when refunding a non-SUCCESS payment")
        void refund_notSuccessfulPayment_shouldThrow() {
            PaymentTransaction failedTx = new PaymentTransaction();
            failedTx.setStatus(PaymentStatusEnum.FAILED);
            failedTx.setOrder(testOrder);

            when(paymentRepository.findByOrderId(1L)).thenReturn(Optional.of(failedTx));
            when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));

            assertThatThrownBy(() -> paymentService.processRefund(1L, 1L))
                    .isInstanceOf(PaymentValidationException.class);
        }
    }
}

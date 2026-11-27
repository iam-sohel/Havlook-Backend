package com.gesmio.havlook.auth_module.service;

import com.gesmio.havlook.auth_module.dto.*;
import com.gesmio.havlook.auth_module.entity.*;
import com.gesmio.havlook.auth_module.exceptions.*;
import com.gesmio.havlook.auth_module.repository.*;
import com.gesmio.havlook.auth_module.util.JwtUtil;
import com.gesmio.havlook.customer_module.repository.CustomerRepository;
import com.gesmio.havlook.seller_module.seller.repository.SellerRepository;
import com.gesmio.havlook.user_module.entity.Role;
import com.gesmio.havlook.user_module.entity.User;
import com.gesmio.havlook.user_module.exception.UserAlreadyExistsException;
import com.gesmio.havlook.user_module.repository.RoleRepository;
import com.gesmio.havlook.user_module.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthServiceImpl Unit Tests")
class AuthServiceImplTest {

    @InjectMocks
    private AuthServiceImpl authService;

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtil jwtUtil;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private SellerRepository sellerRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private EmailOtpRepository emailOtpRepository;
    @Mock private PhoneOtpRepository phoneOtpRepository;
    @Mock private PasswordResetOtpRepository passwordResetOtpRepository;
    @Mock private PendingRegistrationRepository pendingRegistrationRepository;
    @Mock private PendingSellerRegistrationRepository pendingSellerRegistrationRepository;
    @Mock private EmailService emailService;
    @Mock private SmsService smsService;
    @Mock private Msg91WidgetService msg91WidgetService;

    private Role customerRole;
    private User activeCustomer;

    @BeforeEach
    void setUp() {
        customerRole = new Role();
        customerRole.setId(1L);
        customerRole.setName("CUSTOMER");

        activeCustomer = User.builder()
                .id(10L)
                .firstName("Sohel")
                .lastName("Khan")
                .email("sohel@test.com")
                .password("encoded-pass")
                .role(customerRole)
                .enabled(true)
                .build();
    }

    private String sha256Base64(String val) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256")
                .digest(val.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    }

    @Nested
    @DisplayName("register()")
    class RegisterTests {

        @Test
        @DisplayName("Should throw UserAlreadyExistsException when email already exists")
        void register_duplicateEmail_shouldThrow() {
            RegisterRequest req = RegisterRequest.builder()
                    .firstName("Sohel")
                    .lastName("Khan")
                    .email("sohel@test.com")
                    .password("Pass@123")
                    .build();

            when(userRepository.existsByEmailIgnoreCase("sohel@test.com")).thenReturn(true);

            assertThatThrownBy(() -> authService.register(req))
                    .isInstanceOf(UserAlreadyExistsException.class);
        }

        @Test
        @DisplayName("Should throw InvalidAuthRequestException when both email and phone provided")
        void register_bothEmailAndPhone_shouldThrow() {
            RegisterRequest req = RegisterRequest.builder()
                    .firstName("Sohel")
                    .lastName("Khan")
                    .email("sohel@test.com")
                    .phone("9876543210")
                    .password("Pass@123")
                    .build();

            assertThatThrownBy(() -> authService.register(req))
                    .isInstanceOf(InvalidAuthRequestException.class);
        }

        @Test
        @DisplayName("Should save PendingRegistration and send email OTP for valid request")
        void register_validEmail_shouldSaveAndSendOtp() {
            RegisterRequest req = RegisterRequest.builder()
                    .firstName("Sohel")
                    .lastName("Khan")
                    .email("SOHEL@TEST.COM")
                    .password("Pass@123")
                    .build();

            when(userRepository.existsByEmailIgnoreCase("sohel@test.com")).thenReturn(false);
            when(roleRepository.findByNameIgnoreCase("CUSTOMER")).thenReturn(Optional.of(customerRole));
            when(pendingRegistrationRepository.findByEmailIgnoreCase("sohel@test.com")).thenReturn(Optional.empty());
            when(passwordEncoder.encode("Pass@123")).thenReturn("hashed-pass");

            RegisterResponse res = authService.register(req);

            assertThat(res.getEmail()).isEqualTo("sohel@test.com");
            assertThat(res.isEmailOtpSent()).isTrue();
            verify(pendingRegistrationRepository).save(any(PendingRegistration.class));
            verify(emailService).sendEmail(eq("sohel@test.com"), anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("login() / adminLogin()")
    class LoginTests {

        @Test
        @DisplayName("Should return tokens for valid credentials")
        void login_validCredentials_shouldReturnTokens() {
            LoginRequest req = LoginRequest.builder()
                    .email("sohel@test.com")
                    .password("Pass@123")
                    .build();

            when(userRepository.findByEmailIgnoreCase("sohel@test.com")).thenReturn(Optional.of(activeCustomer));
            when(passwordEncoder.matches("Pass@123", "encoded-pass")).thenReturn(true);
            when(jwtUtil.generateAccessToken(10L, "sohel@test.com", "CUSTOMER")).thenReturn("access-jwt");
            when(refreshTokenService.createRefreshToken(activeCustomer)).thenReturn("refresh-jwt");

            LoginResponse res = authService.login(req);

            assertThat(res.getAccessToken()).isEqualTo("access-jwt");
            assertThat(res.getRefreshToken()).isEqualTo("refresh-jwt");
            assertThat(res.getRole()).isEqualTo("CUSTOMER");
        }

        @Test
        @DisplayName("Should throw InvalidCredentialsException for wrong password")
        void login_wrongPassword_shouldThrow() {
            LoginRequest req = LoginRequest.builder()
                    .email("sohel@test.com")
                    .password("WrongPass")
                    .build();

            when(userRepository.findByEmailIgnoreCase("sohel@test.com")).thenReturn(Optional.of(activeCustomer));
            when(passwordEncoder.matches("WrongPass", "encoded-pass")).thenReturn(false);

            assertThatThrownBy(() -> authService.login(req))
                    .isInstanceOf(InvalidCredentialsException.class);
        }

        @Test
        @DisplayName("Should throw InvalidCredentialsException when user is disabled")
        void login_disabledUser_shouldThrow() {
            activeCustomer.setEnabled(false);
            LoginRequest req = LoginRequest.builder()
                    .email("sohel@test.com")
                    .password("Pass@123")
                    .build();

            when(userRepository.findByEmailIgnoreCase("sohel@test.com")).thenReturn(Optional.of(activeCustomer));

            assertThatThrownBy(() -> authService.login(req))
                    .isInstanceOf(InvalidCredentialsException.class);
        }

        @Test
        @DisplayName("Should reject CUSTOMER trying adminLogin")
        void adminLogin_asCustomer_shouldThrow() {
            LoginRequest req = LoginRequest.builder()
                    .email("sohel@test.com")
                    .password("Pass@123")
                    .build();

            when(userRepository.findByEmailIgnoreCase("sohel@test.com")).thenReturn(Optional.of(activeCustomer));

            assertThatThrownBy(() -> authService.adminLogin(req))
                    .isInstanceOf(InvalidCredentialsException.class);
        }
    }

    @Nested
    @DisplayName("refreshAccessToken()")
    class RefreshTokenTests {

        @Test
        @DisplayName("Should throw TokenException when refresh token is blank")
        void refresh_blankToken_shouldThrow() {
            assertThatThrownBy(() -> authService.refreshAccessToken(new RefreshTokenRequest("  ")))
                    .isInstanceOf(TokenException.class);
        }

        @Test
        @DisplayName("Should revoke old token and return new tokens for valid request")
        void refresh_validToken_shouldRotateTokens() {
            RefreshToken oldToken = RefreshToken.builder()
                    .id(1L)
                    .user(activeCustomer)
                    .build();

            when(refreshTokenService.validateRefreshToken("raw-refresh")).thenReturn(oldToken);
            when(jwtUtil.generateAccessToken(10L, "sohel@test.com", "CUSTOMER")).thenReturn("new-access");
            when(refreshTokenService.createRefreshToken(activeCustomer)).thenReturn("new-refresh");

            TokenRefreshResponse res = authService.refreshAccessToken(new RefreshTokenRequest("raw-refresh"));

            assertThat(res.getAccessToken()).isEqualTo("new-access");
            assertThat(res.getRefreshToken()).isEqualTo("new-refresh");
            verify(refreshTokenService).revokeToken(oldToken);
        }
    }

    @Nested
    @DisplayName("OTP Verification & Brute-force Guards")
    class OtpTests {

        @Test
        @DisplayName("Should throw InvalidAuthRequestException (not NPE) when OTP is null")
        void verifyEmailOtp_nullOtp_shouldThrowValidationException() {
            VerifyEmailOtpRequest req = VerifyEmailOtpRequest.builder()
                    .email("sohel@test.com")
                    .otp(null)
                    .build();

            assertThatThrownBy(() -> authService.verifyEmailOtp(req))
                    .isInstanceOf(InvalidAuthRequestException.class);
        }

        @Test
        @DisplayName("Should increment and save attempts when wrong OTP is provided")
        void verifyEmailOtp_wrongOtp_shouldIncrementAttempts() throws Exception {
            EmailOtp otpEntity = EmailOtp.builder()
                    .email("sohel@test.com")
                    .otpHash(sha256Base64("123456"))
                    .expiresAt(LocalDateTime.now().plusMinutes(5))
                    .attempts(0)
                    .verified(false)
                    .build();

            when(emailOtpRepository.findTopByEmailIgnoreCaseAndVerifiedFalseOrderByCreatedAtDesc("sohel@test.com"))
                    .thenReturn(Optional.of(otpEntity));

            VerifyEmailOtpRequest req = VerifyEmailOtpRequest.builder()
                    .email("sohel@test.com")
                    .otp("999999")
                    .build();

            assertThatThrownBy(() -> authService.verifyEmailOtp(req))
                    .isInstanceOf(OtpVerificationException.class)
                    .hasMessageContaining("Invalid OTP");

            assertThat(otpEntity.getAttempts()).isEqualTo(1);
            verify(emailOtpRepository).save(otpEntity);
        }

        @Test
        @DisplayName("Should block when max OTP attempts (5) exceeded")
        void verifyEmailOtp_maxAttemptsExceeded_shouldThrow() throws Exception {
            EmailOtp otpEntity = EmailOtp.builder()
                    .email("sohel@test.com")
                    .otpHash(sha256Base64("123456"))
                    .expiresAt(LocalDateTime.now().plusMinutes(5))
                    .attempts(5)
                    .verified(false)
                    .build();

            when(emailOtpRepository.findTopByEmailIgnoreCaseAndVerifiedFalseOrderByCreatedAtDesc("sohel@test.com"))
                    .thenReturn(Optional.of(otpEntity));

            VerifyEmailOtpRequest req = VerifyEmailOtpRequest.builder()
                    .email("sohel@test.com")
                    .otp("123456")
                    .build();

            assertThatThrownBy(() -> authService.verifyEmailOtp(req))
                    .isInstanceOf(OtpVerificationException.class)
                    .hasMessageContaining("Maximum OTP attempts exceeded");
        }
    }

    @Nested
    @DisplayName("resetPassword()")
    class ResetPasswordTests {

        @Test
        @DisplayName("Should reject blank password")
        void resetPassword_blankPassword_shouldThrow() {
            ResetPasswordRequest req = ResetPasswordRequest.builder()
                    .resetToken("some-token")
                    .newPassword("   ")
                    .confirmPassword("   ")
                    .build();

            assertThatThrownBy(() -> authService.resetPassword(req))
                    .isInstanceOf(InvalidAuthRequestException.class);
        }

        @Test
        @DisplayName("Should encode new password, revoke all tokens, and invalidate reset token")
        void resetPassword_validRequest_shouldUpdatePasswordAndRevokeTokens() throws Exception {
            String rawToken = "valid-reset-token";
            PasswordResetOtp resetOtp = PasswordResetOtp.builder()
                    .email("sohel@test.com")
                    .verified(true)
                    .resetTokenHash(sha256Base64(rawToken))
                    .resetTokenExpiresAt(LocalDateTime.now().plusMinutes(5))
                    .build();

            when(passwordResetOtpRepository.findByResetTokenHash(sha256Base64(rawToken)))
                    .thenReturn(Optional.of(resetOtp));
            when(userRepository.findByEmailIgnoreCase("sohel@test.com"))
                    .thenReturn(Optional.of(activeCustomer));
            when(passwordEncoder.encode("NewPass@123")).thenReturn("new-encoded");

            ResetPasswordRequest req = ResetPasswordRequest.builder()
                    .resetToken(rawToken)
                    .newPassword("NewPass@123")
                    .confirmPassword("NewPass@123")
                    .build();

            authService.resetPassword(req);

            assertThat(activeCustomer.getPassword()).isEqualTo("new-encoded");
            assertThat(resetOtp.getResetTokenHash()).isNull();
            verify(refreshTokenService).revokeAllUserTokens(10L);
            verify(userRepository).save(activeCustomer);
            verify(passwordResetOtpRepository).save(resetOtp);
        }
    }
}

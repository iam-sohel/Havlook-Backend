package com.gesmio.havlook.seller_module.sellerKyc.service;

import com.gesmio.havlook.seller_module.seller.entity.Seller;
import com.gesmio.havlook.seller_module.seller.repository.SellerRepository;
import com.gesmio.havlook.seller_module.sellerKyc.dto.SellerKycRequest;
import com.gesmio.havlook.seller_module.sellerKyc.dto.SellerKycResponse;
import com.gesmio.havlook.seller_module.sellerKyc.entity.BusinessType;
import com.gesmio.havlook.seller_module.sellerKyc.entity.KycStatus;
import com.gesmio.havlook.seller_module.sellerKyc.entity.SellerKyc;
import com.gesmio.havlook.seller_module.sellerKyc.exception.SellerKycStateException;
import com.gesmio.havlook.seller_module.sellerKyc.exception.SellerKycValidationException;
import com.gesmio.havlook.seller_module.sellerKyc.repository.SellerKycRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SellerKycServiceImpl Unit Tests")
class SellerKycServiceImplTest {

    @InjectMocks
    private SellerKycServiceImpl sellerKycService;

    @Mock
    private SellerRepository sellerRepository;

    @Mock
    private SellerKycRepository sellerKycRepository;

    private Seller seller;
    private SellerKyc kyc;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(sellerKycService, "supabaseUrl", "https://test-project.supabase.co");
        ReflectionTestUtils.setField(sellerKycService, "supabaseSecretKey", "sb_secret_test_key");
        ReflectionTestUtils.setField(sellerKycService, "kycBucket", "sellerekyc");

        seller = new Seller();
        seller.setId(50L);

        kyc = SellerKyc.builder()
                .id(1L)
                .seller(seller)
                .businessType(BusinessType.PROPRIETORSHIP)
                .aadhaarNumber("123456789012")
                .status(KycStatus.DRAFT)
                .build();
    }

    @Test
    @DisplayName("getMyKyc should mask Aadhaar number")
    void getMyKyc_shouldMaskAadhaar() {
        when(sellerRepository.findByUserId(1L)).thenReturn(Optional.of(seller));
        when(sellerKycRepository.findBySellerId(50L)).thenReturn(Optional.of(kyc));

        SellerKycResponse res = sellerKycService.getMyKyc(1L);

        assertThat(res.getAadhaarNumber()).isEqualTo("XXXX-XXXX-9012");
    }

    @Test
    @DisplayName("submitKyc should throw SellerKycStateException when already VERIFIED")
    void submitKyc_verified_shouldThrow() {
        kyc.setStatus(KycStatus.VERIFIED);
        when(sellerRepository.findByUserId(1L)).thenReturn(Optional.of(seller));
        when(sellerKycRepository.findBySellerId(50L)).thenReturn(Optional.of(kyc));

        SellerKycRequest req = SellerKycRequest.builder()
                .businessType(BusinessType.PROPRIETORSHIP)
                .ownerName("Test")
                .panNumber("ABCDE1234F")
                .dateOfBirth(LocalDate.of(1995, 1, 1))
                .businessAddress("Addr")
                .city("Delhi")
                .state("DL")
                .postalCode("110001")
                .build();

        assertThatThrownBy(() -> sellerKycService.submitKyc(1L, req))
                .isInstanceOf(SellerKycStateException.class);
    }

    @Test
    @DisplayName("uploadKycDocuments should reject fake PDF without %PDF magic bytes (SECURITY FIX verify)")
    void uploadKycDocuments_fakePdfContent_shouldThrow() {
        when(sellerRepository.findByUserId(1L)).thenReturn(Optional.of(seller));
        when(sellerKycRepository.findBySellerId(50L)).thenReturn(Optional.of(kyc));

        MockMultipartFile fakePdf = new MockMultipartFile(
                "panDocument",
                "malicious.pdf",
                "application/pdf",
                "<script>alert(1)</script>".getBytes()
        );

        assertThatThrownBy(() -> sellerKycService.uploadKycDocuments(1L, fakePdf, null, null, null, null))
                .isInstanceOf(SellerKycValidationException.class)
                .hasMessageContaining("Invalid PDF file content");
    }

    @Test
    @DisplayName("getSignedDocumentUrl should reject unsupported documentType")
    void getSignedDocumentUrl_unsupportedType_shouldThrow() {
        when(sellerRepository.findByUserId(1L)).thenReturn(Optional.of(seller));
        when(sellerKycRepository.findBySellerId(50L)).thenReturn(Optional.of(kyc));

        assertThatThrownBy(() -> sellerKycService.getSignedDocumentUrl(1L, "../etc/passwd"))
                .isInstanceOf(SellerKycValidationException.class)
                .hasMessageContaining("Unsupported KYC document type");
    }

    @Test
    @DisplayName("getSignedDocumentUrl should reject document belonging to another seller")
    void getSignedDocumentUrl_otherSellerObject_shouldThrow() {
        kyc.setPanDocumentUrl("https://test-project.supabase.co/storage/v1/object/sellerekyc/999/pan/file.pdf");
        when(sellerRepository.findByUserId(1L)).thenReturn(Optional.of(seller));
        when(sellerKycRepository.findBySellerId(50L)).thenReturn(Optional.of(kyc));

        assertThatThrownBy(() -> sellerKycService.getSignedDocumentUrl(1L, "pan"))
                .isInstanceOf(SellerKycValidationException.class)
                .hasMessageContaining("does not belong to this seller");
    }
}

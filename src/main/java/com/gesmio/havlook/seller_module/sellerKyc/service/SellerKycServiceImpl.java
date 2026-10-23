package com.gesmio.havlook.seller_module.sellerKyc.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.gesmio.havlook.seller_module.seller.entity.Seller;
import com.gesmio.havlook.seller_module.seller.repository.SellerRepository;
import com.gesmio.havlook.seller_module.sellerKyc.dto.SellerKycRequest;
import com.gesmio.havlook.seller_module.sellerKyc.dto.SellerKycResponse;
import com.gesmio.havlook.seller_module.sellerKyc.entity.KycStatus;
import com.gesmio.havlook.seller_module.sellerKyc.entity.SellerKyc;
import com.gesmio.havlook.seller_module.sellerKyc.exception.SellerKycDocumentUploadException;
import com.gesmio.havlook.seller_module.sellerKyc.exception.SellerKycNotFoundException;
import com.gesmio.havlook.seller_module.sellerKyc.exception.SellerKycStateException;
import com.gesmio.havlook.seller_module.sellerKyc.exception.SellerKycValidationException;
import com.gesmio.havlook.seller_module.sellerKyc.repository.SellerKycRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class SellerKycServiceImpl implements SellerKycService {

    /**
     * Maximum allowed size for each KYC PDF.
     * <p>
     * 2 MB = 2 * 1024 * 1024 bytes
     */
    private static final long MAX_FILE_SIZE =
            2L * 1024L * 1024L;
    private final SellerRepository sellerRepository;
    private final SellerKycRepository sellerKycRepository;

    // =========================================================
    // SUPABASE CONFIGURATION
    // =========================================================
    /*
     * Used for Supabase Storage API calls.
     */
    private final RestTemplate restTemplate = new RestTemplate();
    @Value("${supabase.url}")
    private String supabaseUrl;
    /*
     * New Supabase secret key.
     *
     * Example:
     * sb_secret_...
     *
     * This key is sent using the "apikey" header.
     */
    @Value("${supabase.service-role-key}")
    private String supabaseSecretKey;
    /*
     * Legacy Supabase service_role JWT.
     *
     * This key is sent using:
     *
     * Authorization: Bearer <JWT>
     */
    @Value("${supabase.storage-authorization-key}")
    private String supabaseStorageAuthorizationKey;

    // =========================================================
    // CONSTANTS
    // =========================================================
    @Value("${supabase.kyc-bucket:sellerekyc}")
    private String kycBucket;
    // =========================================================
    // SUBMIT KYC
    // =========================================================

    @Override
    public SellerKycResponse submitKyc(
            Long userId,
            SellerKycRequest request
    ) {

        if (request == null) {

            throw new SellerKycValidationException(
                    "KYC request is required"
            );
        }

        Seller seller =
                getSellerByUserId(userId);

        SellerKyc kyc =
                sellerKycRepository
                        .findBySellerId(
                                seller.getId()
                        )
                        .orElse(null);

        // -----------------------------------------------------
        // VERIFIED KYC CANNOT BE MODIFIED
        // -----------------------------------------------------

        if (kyc != null &&
                kyc.getStatus() == KycStatus.VERIFIED) {

            throw new SellerKycStateException(
                    "KYC is already verified and cannot be modified"
            );
        }

        // -----------------------------------------------------
        // CREATE NEW KYC
        // -----------------------------------------------------

        if (kyc == null) {

            kyc = SellerKyc.builder()

                    .seller(seller)

                    .businessType(
                            request.getBusinessType()
                    )

                    .gstNumber(
                            normalize(
                                    request.getGstNumber()
                            )
                    )

                    .registrationNumber(
                            normalize(
                                    request.getRegistrationNumber()
                            )
                    )

                    .ownerName(
                            normalize(
                                    request.getOwnerName()
                            )
                    )

                    .dateOfBirth(
                            request.getDateOfBirth()
                    )

                    .panNumber(
                            normalizeUpper(
                                    request.getPanNumber()
                            )
                    )

                    .aadhaarNumber(
                            normalize(
                                    request.getAadhaarNumber()
                            )
                    )

                    .businessAddress(
                            normalize(
                                    request.getBusinessAddress()
                            )
                    )

                    .city(
                            normalize(
                                    request.getCity()
                            )
                    )

                    .state(
                            normalize(
                                    request.getState()
                            )
                    )

                    .postalCode(
                            normalize(
                                    request.getPostalCode()
                            )
                    )

                    .country(
                            getCountry(
                                    request.getCountry()
                            )
                    )

                    .status(
                            KycStatus.PENDING
                    )

                    .submittedAt(
                            LocalDateTime.now()
                    )

                    .build();

        } else {

            // -------------------------------------------------
            // UPDATE EXISTING KYC
            // -------------------------------------------------

            kyc.setBusinessType(
                    request.getBusinessType()
            );

            kyc.setGstNumber(
                    normalize(
                            request.getGstNumber()
                    )
            );

            kyc.setRegistrationNumber(
                    normalize(
                            request.getRegistrationNumber()
                    )
            );

            kyc.setOwnerName(
                    normalize(
                            request.getOwnerName()
                    )
            );

            kyc.setDateOfBirth(
                    request.getDateOfBirth()
            );

            kyc.setPanNumber(
                    normalizeUpper(
                            request.getPanNumber()
                    )
            );

            kyc.setAadhaarNumber(
                    normalize(
                            request.getAadhaarNumber()
                    )
            );

            kyc.setBusinessAddress(
                    normalize(
                            request.getBusinessAddress()
                    )
            );

            kyc.setCity(
                    normalize(
                            request.getCity()
                    )
            );

            kyc.setState(
                    normalize(
                            request.getState()
                    )
            );

            kyc.setPostalCode(
                    normalize(
                            request.getPostalCode()
                    )
            );

            kyc.setCountry(
                    getCountry(
                            request.getCountry()
                    )
            );

            // -------------------------------------------------
            // NEW REVIEW CYCLE
            // -------------------------------------------------

            kyc.setStatus(
                    KycStatus.PENDING
            );

            kyc.setRejectionReason(null);

            kyc.setReviewedAt(null);

            kyc.setReviewedBy(null);

            kyc.setSubmittedAt(
                    LocalDateTime.now()
            );
        }

        SellerKyc savedKyc =
                sellerKycRepository.save(kyc);

        return mapToResponse(savedKyc);
    }

    // =========================================================
    // GET MY KYC
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public SellerKycResponse getMyKyc(
            Long userId
    ) {

        Seller seller =
                getSellerByUserId(userId);

        SellerKyc kyc =
                sellerKycRepository
                        .findBySellerId(
                                seller.getId()
                        )
                        .orElseThrow(() ->
                                new SellerKycNotFoundException(
                                        "KYC details not found"
                                )
                        );

        return mapToResponse(kyc);
    }

    // =========================================================
    // GET KYC STATUS
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public SellerKycResponse getKycStatus(
            Long userId
    ) {

        Seller seller =
                getSellerByUserId(userId);

        SellerKyc kyc =
                sellerKycRepository
                        .findBySellerId(
                                seller.getId()
                        )
                        .orElseThrow(() ->
                                new SellerKycNotFoundException(
                                        "KYC details not found"
                                )
                        );

        return mapToResponse(kyc);
    }

    // =========================================================
    // UPDATE KYC
    // =========================================================

    @Override
    public SellerKycResponse updateKyc(
            Long userId,
            SellerKycRequest request
    ) {

        if (request == null) {

            throw new SellerKycValidationException(
                    "KYC update request is required"
            );
        }

        Seller seller =
                getSellerByUserId(userId);

        SellerKyc kyc =
                sellerKycRepository
                        .findBySellerId(
                                seller.getId()
                        )
                        .orElseThrow(() ->
                                new SellerKycNotFoundException(
                                        "KYC details not found"
                                )
                        );

        // -----------------------------------------------------
        // VERIFIED KYC CANNOT BE MODIFIED
        // -----------------------------------------------------

        if (kyc.getStatus() == KycStatus.VERIFIED) {

            throw new SellerKycStateException(
                    "Verified KYC cannot be modified"
            );
        }

        // -----------------------------------------------------
        // UPDATE DETAILS
        // -----------------------------------------------------

        kyc.setBusinessType(
                request.getBusinessType()
        );

        kyc.setGstNumber(
                normalize(
                        request.getGstNumber()
                )
        );

        kyc.setRegistrationNumber(
                normalize(
                        request.getRegistrationNumber()
                )
        );

        kyc.setOwnerName(
                normalize(
                        request.getOwnerName()
                )
        );

        kyc.setDateOfBirth(
                request.getDateOfBirth()
        );

        kyc.setPanNumber(
                normalizeUpper(
                        request.getPanNumber()
                )
        );

        kyc.setAadhaarNumber(
                normalize(
                        request.getAadhaarNumber()
                )
        );

        kyc.setBusinessAddress(
                normalize(
                        request.getBusinessAddress()
                )
        );

        kyc.setCity(
                normalize(
                        request.getCity()
                )
        );

        kyc.setState(
                normalize(
                        request.getState()
                )
        );

        kyc.setPostalCode(
                normalize(
                        request.getPostalCode()
                )
        );

        kyc.setCountry(
                getCountry(
                        request.getCountry()
                )
        );

        // -----------------------------------------------------
        // NEW REVIEW REQUIRED
        // -----------------------------------------------------

        kyc.setStatus(
                KycStatus.PENDING
        );

        kyc.setRejectionReason(null);

        kyc.setReviewedAt(null);

        kyc.setReviewedBy(null);

        kyc.setSubmittedAt(
                LocalDateTime.now()
        );

        SellerKyc updatedKyc =
                sellerKycRepository.save(kyc);

        return mapToResponse(updatedKyc);
    }

    // =========================================================
    // UPLOAD KYC DOCUMENTS
    // =========================================================

    @Override
    public SellerKycResponse uploadKycDocuments(
            Long userId,
            MultipartFile panDocument,
            MultipartFile aadhaarDocument,
            MultipartFile gstDocument,
            MultipartFile registrationDocument,
            MultipartFile addressDocument
    ) {

        Seller seller =
                getSellerByUserId(userId);

        SellerKyc kyc =
                sellerKycRepository
                        .findBySellerId(
                                seller.getId()
                        )
                        .orElseThrow(() ->
                                new SellerKycValidationException(
                                        "Submit KYC details before uploading documents"
                                )
                        );

        // -----------------------------------------------------
        // VERIFIED KYC CANNOT BE MODIFIED
        // -----------------------------------------------------

        if (kyc.getStatus() == KycStatus.VERIFIED) {

            throw new SellerKycStateException(
                    "Verified KYC documents cannot be modified"
            );
        }

        // -----------------------------------------------------
        // CHECK AT LEAST ONE DOCUMENT
        // -----------------------------------------------------

        if (!hasFile(panDocument)
                && !hasFile(aadhaarDocument)
                && !hasFile(gstDocument)
                && !hasFile(registrationDocument)
                && !hasFile(addressDocument)) {

            throw new SellerKycValidationException(
                    "At least one KYC document is required"
            );
        }

        // -----------------------------------------------------
        // PAN DOCUMENT
        // -----------------------------------------------------

        if (hasFile(panDocument)) {

            validatePdf(panDocument);

            String url =
                    uploadToSupabase(
                            panDocument,
                            seller.getId(),
                            "pan"
                    );

            kyc.setPanDocumentUrl(url);
        }

        // -----------------------------------------------------
        // AADHAAR DOCUMENT
        // -----------------------------------------------------

        if (hasFile(aadhaarDocument)) {

            validatePdf(aadhaarDocument);

            String url =
                    uploadToSupabase(
                            aadhaarDocument,
                            seller.getId(),
                            "aadhaar"
                    );

            kyc.setAadhaarDocumentUrl(url);
        }

        // -----------------------------------------------------
        // GST DOCUMENT
        // -----------------------------------------------------

        if (hasFile(gstDocument)) {

            validatePdf(gstDocument);

            String url =
                    uploadToSupabase(
                            gstDocument,
                            seller.getId(),
                            "gst"
                    );

            kyc.setGstDocumentUrl(url);
        }

        // -----------------------------------------------------
        // REGISTRATION DOCUMENT
        // -----------------------------------------------------

        if (hasFile(registrationDocument)) {

            validatePdf(registrationDocument);

            String url =
                    uploadToSupabase(
                            registrationDocument,
                            seller.getId(),
                            "registration"
                    );

            kyc.setRegistrationDocumentUrl(url);
        }

        // -----------------------------------------------------
        // ADDRESS DOCUMENT
        // -----------------------------------------------------

        if (hasFile(addressDocument)) {

            validatePdf(addressDocument);

            String url =
                    uploadToSupabase(
                            addressDocument,
                            seller.getId(),
                            "address"
                    );

            kyc.setAddressDocumentUrl(url);
        }

        // -----------------------------------------------------
        // NEW REVIEW CYCLE
        // -----------------------------------------------------

        kyc.setStatus(
                KycStatus.PENDING
        );

        kyc.setRejectionReason(null);

        kyc.setReviewedAt(null);

        kyc.setReviewedBy(null);

        kyc.setSubmittedAt(
                LocalDateTime.now()
        );

        SellerKyc savedKyc =
                sellerKycRepository.save(kyc);

        return mapToResponse(savedKyc);
    }

    // =========================================================
    // FIND SELLER BY USER ID
    // =========================================================

    private Seller getSellerByUserId(
            Long userId
    ) {

        if (userId == null || userId <= 0) {

            throw new SellerKycValidationException(
                    "Valid user ID is required"
            );
        }

        return sellerRepository
                .findByUserId(userId)
                .orElseThrow(() ->
                        new SellerKycValidationException(
                                "Seller profile not found"
                        )
                );
    }

    // =========================================================
    // PDF VALIDATION
    // =========================================================

    private void validatePdf(
            MultipartFile file
    ) {

        if (file == null ||
                file.isEmpty()) {

            throw new SellerKycValidationException(
                    "Document cannot be empty"
            );
        }

        // -----------------------------------------------------
        // SIZE
        // -----------------------------------------------------

        if (file.getSize() > MAX_FILE_SIZE) {

            throw new SellerKycValidationException(
                    "Document size must not exceed 2 MB"
            );
        }

        // -----------------------------------------------------
        // CONTENT TYPE
        // -----------------------------------------------------

        String contentType =
                file.getContentType();

        if (!MediaType.APPLICATION_PDF_VALUE
                .equalsIgnoreCase(contentType)) {

            throw new SellerKycValidationException(
                    "Only PDF documents are allowed"
            );
        }

        // -----------------------------------------------------
        // FILE EXTENSION
        // -----------------------------------------------------

        String filename =
                file.getOriginalFilename();

        if (filename == null ||
                !filename
                        .toLowerCase(Locale.ROOT)
                        .endsWith(".pdf")) {

            throw new SellerKycValidationException(
                    "Only .pdf files are allowed"
            );
        }
    }

    // =========================================================
    // UPLOAD TO SUPABASE STORAGE
    // =========================================================

    private String uploadToSupabase(
            MultipartFile file,
            Long sellerId,
            String documentType
    ) {
        try {
            if (supabaseUrl == null || supabaseUrl.isBlank()) {
                throw new SellerKycDocumentUploadException(
                        "Supabase URL is not configured"
                );
            }
            if (supabaseSecretKey == null || supabaseSecretKey.isBlank()) {
                throw new SellerKycDocumentUploadException(
                        "Supabase API key is not configured"
                );
            }
            if (supabaseStorageAuthorizationKey == null
                    || supabaseStorageAuthorizationKey.isBlank()) {
                throw new SellerKycDocumentUploadException(
                        "Supabase authorization key is not configured"
                );
            }
            if (kycBucket == null || kycBucket.isBlank()) {
                throw new SellerKycDocumentUploadException(
                        "Supabase KYC bucket is not configured"
                );
            }

            String baseUrl = supabaseUrl.trim().replaceAll("/+$", "");
            String bucket = kycBucket.trim();
            String apiKey = supabaseSecretKey.trim();
            String authorizationKey = supabaseStorageAuthorizationKey.trim();

            if (!apiKey.startsWith("sb_secret_")) {
                throw new SellerKycDocumentUploadException(
                        "Invalid Supabase secret key configuration"
                );
            }
            if (!authorizationKey.startsWith("eyJ")) {
                throw new SellerKycDocumentUploadException(
                        "Invalid Supabase storage authorization key configuration"
                );
            }

            URI baseUri = URI.create(baseUrl);
            if (!"https".equalsIgnoreCase(baseUri.getScheme())
                    || baseUri.getHost() == null
                    || baseUri.getRawUserInfo() != null
                    || baseUri.getPort() != -1
                    || (baseUri.getRawPath() != null
                    && !baseUri.getRawPath().isEmpty()
                    && !"/".equals(baseUri.getRawPath()))) {
                throw new SellerKycDocumentUploadException(
                        "Supabase URL configuration is invalid"
                );
            }

            String fileName = UUID.randomUUID() + ".pdf";
            String objectPath = sellerId + "/" + documentType + "/" + fileName;
            String encodedObjectPath = java.util.Arrays.stream(objectPath.split("/"))
                    .map(segment -> java.net.URLEncoder.encode(
                            segment,
                            java.nio.charset.StandardCharsets.UTF_8
                    ).replace("+", "%20"))
                    .collect(java.util.stream.Collectors.joining("/"));

            String uploadUrl = baseUrl
                    + "/storage/v1/object/"
                    + bucket + "/" + encodedObjectPath;

            HttpHeaders headers = new HttpHeaders();
            headers.set("apikey", apiKey);
            headers.setBearerAuth(authorizationKey);
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.set("x-upsert", "false");

            // Log non-sensitive diagnostic details only.
            System.out.println("Supabase project host: " + baseUri.getHost());
            System.out.println("Supabase KYC bucket: " + bucket);
            System.out.println("KYC object path: " + objectPath);

            HttpEntity<byte[]> request = new HttpEntity<>(file.getBytes(), headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    uploadUrl,
                    HttpMethod.POST,
                    request,
                    String.class
            );

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new SellerKycDocumentUploadException(
                        "Supabase upload failed. HTTP "
                                + response.getStatusCode().value()
                                + " - " + response.getBody()
                );
            }

            // Store the canonical object URL; generate a signed URL when reading.
            return baseUrl + "/storage/v1/object/" + bucket + "/" + encodedObjectPath;

        } catch (HttpStatusCodeException e) {
            // The response body helps diagnose Storage errors; never log credentials.
            System.err.println("Supabase upload HTTP status: "
                    + e.getStatusCode().value());
            System.err.println("Supabase upload error response: "
                    + e.getResponseBodyAsString());
            throw new SellerKycDocumentUploadException(
                    "Supabase upload failed with HTTP "
                            + e.getStatusCode().value(),
                    e
            );
        } catch (IOException e) {
            throw new SellerKycDocumentUploadException(
                    "Failed to read KYC document", e
            );
        } catch (SellerKycDocumentUploadException e) {
            throw e;
        } catch (Exception e) {
            throw new SellerKycDocumentUploadException(
                    "Failed to upload KYC document to Supabase", e
            );
        }
    }

    // =========================================================
    // ENTITY -> RESPONSE DTO
    // =========================================================

    private SellerKycResponse mapToResponse(
            SellerKyc kyc
    ) {

        return SellerKycResponse.builder()

                .id(
                        kyc.getId()
                )

                .sellerId(
                        kyc.getSeller()
                                .getId()
                )

                .businessType(
                        kyc.getBusinessType()
                )

                .gstNumber(
                        kyc.getGstNumber()
                )

                .gstDocumentUrl(
                        kyc.getGstDocumentUrl()
                )

                .registrationNumber(
                        kyc.getRegistrationNumber()
                )

                .registrationDocumentUrl(
                        kyc.getRegistrationDocumentUrl()
                )

                .ownerName(
                        kyc.getOwnerName()
                )

                .dateOfBirth(
                        kyc.getDateOfBirth()
                )

                .panNumber(
                        kyc.getPanNumber()
                )

                .panDocumentUrl(
                        kyc.getPanDocumentUrl()
                )

                .aadhaarNumber(
                        maskAadhaar(
                                kyc.getAadhaarNumber()
                        )
                )

                .aadhaarDocumentUrl(
                        kyc.getAadhaarDocumentUrl()
                )

                .businessAddress(
                        kyc.getBusinessAddress()
                )

                .city(
                        kyc.getCity()
                )

                .state(
                        kyc.getState()
                )

                .postalCode(
                        kyc.getPostalCode()
                )

                .country(
                        kyc.getCountry()
                )

                .addressDocumentUrl(
                        kyc.getAddressDocumentUrl()
                )

                .status(
                        kyc.getStatus()
                )

                .rejectionReason(
                        kyc.getRejectionReason()
                )

                .submittedAt(
                        kyc.getSubmittedAt()
                )

                .reviewedAt(
                        kyc.getReviewedAt()
                )

                .reviewedBy(
                        kyc.getReviewedBy()
                )

                .createdAt(
                        kyc.getCreatedAt()
                )

                .updatedAt(
                        kyc.getUpdatedAt()
                )

                .build();
    }

    // =========================================================
    // MASK AADHAAR
    // =========================================================

    private String maskAadhaar(
            String aadhaarNumber
    ) {

        if (aadhaarNumber == null ||
                aadhaarNumber.length() != 12) {

            return null;
        }

        return "XXXX-XXXX-"
                + aadhaarNumber.substring(8);
    }

    // =========================================================
    // CHECK FILE
    // =========================================================

    private boolean hasFile(
            MultipartFile file
    ) {

        return file != null &&
                !file.isEmpty();
    }

    // =========================================================
    // NORMALIZE STRING
    // =========================================================

    private String normalize(
            String value
    ) {

        if (value == null) {
            return null;
        }

        String trimmed =
                value.trim();

        return trimmed.isEmpty()
                ? null
                : trimmed;
    }

    // =========================================================
    // NORMALIZE UPPERCASE
    // =========================================================

    private String normalizeUpper(
            String value
    ) {

        String normalized =
                normalize(value);

        return normalized == null
                ? null
                : normalized.toUpperCase(
                Locale.ROOT
        );
    }

    // =========================================================
    // DEFAULT COUNTRY
    // =========================================================

    private String getCountry(
            String country
    ) {

        String normalized =
                normalize(country);

        return normalized == null
                ? "India"
                : normalized;
    }

    // =========================================================
// GENERATE SIGNED URL FOR A SELLER'S KYC DOCUMENT
// =========================================================

    @Override
    @Transactional(readOnly = true)
    public String getSignedDocumentUrl(Long userId, String documentType) {

        Seller seller = getSellerByUserId(userId);

        SellerKyc kyc = sellerKycRepository
                .findBySellerId(seller.getId())
                .orElseThrow(() ->
                        new SellerKycNotFoundException(
                                "KYC details not found"
                        )
                );

        if (documentType == null || documentType.isBlank()) {
            throw new SellerKycValidationException(
                    "Document type is required"
            );
        }

        String normalizedType =
                documentType.trim().toLowerCase(Locale.ROOT);

        Set<String> allowedDocumentTypes = Set.of(
                "pan", "aadhaar", "gst", "registration", "address"
        );

        if (!allowedDocumentTypes.contains(normalizedType)) {
            throw new SellerKycValidationException(
                    "Unsupported KYC document type"
            );
        }

        String storedUrl;

        switch (normalizedType) {
            case "pan" -> storedUrl = kyc.getPanDocumentUrl();
            case "aadhaar" -> storedUrl = kyc.getAadhaarDocumentUrl();
            case "gst" -> storedUrl = kyc.getGstDocumentUrl();
            case "registration" ->
                    storedUrl = kyc.getRegistrationDocumentUrl();
            case "address" ->
                    storedUrl = kyc.getAddressDocumentUrl();
            default -> throw new SellerKycValidationException(
                    "Unsupported KYC document type"
            );
        }

        if (storedUrl == null || storedUrl.isBlank()) {
            throw new SellerKycNotFoundException(
                    "The requested KYC document has not been uploaded"
            );
        }

        String baseUrl = supabaseUrl == null
                ? ""
                : supabaseUrl.trim().replaceAll("/+$", "");

        String bucket = kycBucket == null
                ? ""
                : kycBucket.trim();

        String apiKey = supabaseSecretKey == null
                ? ""
                : supabaseSecretKey.trim();

        String authorizationKey =
                supabaseStorageAuthorizationKey == null
                        ? ""
                        : supabaseStorageAuthorizationKey.trim();

        if (baseUrl.isBlank()
                || bucket.isBlank()
                || apiKey.isBlank()
                || authorizationKey.isBlank()) {

            throw new SellerKycDocumentUploadException(
                    "Supabase Storage configuration is incomplete"
            );
        }

        final URI configuredUri;

        try {
            configuredUri = URI.create(baseUrl);
        } catch (IllegalArgumentException e) {
            throw new SellerKycDocumentUploadException(
                    "Supabase URL configuration is invalid"
            );
        }

        if (!"https".equalsIgnoreCase(configuredUri.getScheme())
                || configuredUri.getHost() == null
                || configuredUri.getRawUserInfo() != null
                || configuredUri.getPort() != -1
                || (configuredUri.getRawPath() != null
                && !configuredUri.getRawPath().isEmpty()
                && !"/".equals(configuredUri.getRawPath()))) {

            throw new SellerKycDocumentUploadException(
                    "Supabase URL configuration is invalid"
            );
        }

        String configuredHost = configuredUri.getHost();
        String objectPrefix =
                "/storage/v1/object/" + bucket + "/";

        String objectPath;

        try {
            URI storedUri = URI.create(storedUrl.trim());

            String storedHost = storedUri.getHost();
            String storedPath = storedUri.getRawPath();

            // The stored URL must belong to the configured Supabase project.
            System.out.println("KYC configured host: [" + configuredHost + "]");
            System.out.println("KYC stored host: [" + storedHost + "]");

            if (!"https".equalsIgnoreCase(storedUri.getScheme())
                    || storedHost == null
                    || !configuredHost.equalsIgnoreCase(storedHost)
                    || storedUri.getRawUserInfo() != null
                    || storedUri.getPort() != -1
                    || storedUri.getRawQuery() != null
                    || storedUri.getRawFragment() != null) {

                throw new SellerKycValidationException(
                        "Stored KYC document URL is invalid"
                );
            }

            // Verify the expected Storage bucket.
            if (storedPath == null
                    || !storedPath.startsWith(objectPrefix)) {

                throw new SellerKycValidationException(
                        "Stored KYC document URL is invalid"
                );
            }

            objectPath =
                    storedPath.substring(objectPrefix.length());

            if (objectPath.isBlank()
                    || objectPath.startsWith("/")
                    || objectPath.contains("\\")
                    || objectPath.contains("..")
                    || objectPath.contains("%")) {

                throw new SellerKycValidationException(
                        "Stored KYC document path is invalid"
                );
            }

            // Verify that the requested document belongs to this seller.
            String expectedPrefix =
                    seller.getId() + "/" + normalizedType + "/";

            if (!objectPath.startsWith(expectedPrefix)) {
                throw new SellerKycValidationException(
                        "The requested document does not belong to this seller"
                );
            }

            // Allow only a single PDF filename after the seller/type folders.
            String[] pathSegments = objectPath.split("/", -1);

            if (pathSegments.length != 3
                    || pathSegments[0].isBlank()
                    || pathSegments[1].isBlank()
                    || pathSegments[2].isBlank()
                    || !pathSegments[2]
                    .toLowerCase(Locale.ROOT)
                    .endsWith(".pdf")) {

                throw new SellerKycValidationException(
                        "Stored KYC document path is invalid"
                );
            }

        } catch (SellerKycValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new SellerKycValidationException(
                    "Unable to read the stored KYC document path"
            );
        }

        // Encode each path segment separately.
        String encodedObjectPath = java.util.Arrays.stream(
                        objectPath.split("/")
                )
                .map(segment -> java.net.URLEncoder.encode(
                        segment,
                        java.nio.charset.StandardCharsets.UTF_8
                ).replace("+", "%20"))
                .collect(java.util.stream.Collectors.joining("/"));

        String signUrl = baseUrl
                + "/storage/v1/object/sign/"
                + bucket
                + "/"
                + encodedObjectPath;

        HttpHeaders headers = new HttpHeaders();
        headers.set("apikey", apiKey);
        headers.set(
                HttpHeaders.AUTHORIZATION,
                "Bearer " + authorizationKey
        );
        headers.setContentType(MediaType.APPLICATION_JSON);

        String requestBody = """
            {
              "expiresIn": 300
            }
            """;

        HttpEntity<String> request =
                new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<SupabaseSignedUrlResponse> response =
                    restTemplate.exchange(
                            signUrl,
                            HttpMethod.POST,
                            request,
                            SupabaseSignedUrlResponse.class
                    );

            SupabaseSignedUrlResponse body = response.getBody();

            if (!response.getStatusCode().is2xxSuccessful()
                    || body == null
                    || body.getSignedURL() == null
                    || body.getSignedURL().isBlank()) {

                throw new SellerKycDocumentUploadException(
                        "Supabase did not return a signed document URL"
                );
            }

            String signedPath = body.getSignedURL().trim();

            // Supabase commonly returns a relative path such as:
            // /object/sign/<bucket>/<object-path>?token=...
            // Some responses may include /storage/v1 already.
            URI signedUri;

            if (signedPath.startsWith("https://")
                    || signedPath.startsWith("http://")) {
                signedUri = URI.create(signedPath);
            } else {
                String relativePath = signedPath.startsWith("/")
                        ? signedPath
                        : "/" + signedPath;

                if (relativePath.startsWith("/object/")) {
                    relativePath = "/storage/v1" + relativePath;
                }

                if (!relativePath.startsWith("/storage/v1/")) {
                    throw new SellerKycDocumentUploadException(
                            "Supabase returned an unexpected signed URL path"
                    );
                }

                signedUri = URI.create(baseUrl + relativePath);
            }

            String signedUriPath = signedUri.getRawPath();
            String expectedSignedPrefix =
                    "/storage/v1/object/sign/" + bucket + "/";

            if (!"https".equalsIgnoreCase(signedUri.getScheme())
                    || !configuredHostMatches(baseUrl, signedUri.getHost())
                    || signedUri.getRawUserInfo() != null
                    || signedUri.getPort() != -1
                    || signedUri.getRawQuery() == null
                    || signedUri.getRawQuery().isBlank()
                    || signedUri.getRawFragment() != null
                    || signedUriPath == null
                    || !signedUriPath.startsWith(expectedSignedPrefix)) {

                System.err.println("KYC signed URL validation failed.");
                System.err.println("Signed URL host: [" + signedUri.getHost() + "]");
                System.err.println("Signed URL path: [" + signedUriPath + "]");
                throw new SellerKycDocumentUploadException(
                        "Supabase returned an invalid signed URL"
                );
            }

            return signedUri.toString();

        } catch (HttpStatusCodeException e) {
            // Log status only; never log keys, authorization headers, or signed URLs.
            System.err.println("Supabase signed URL API returned HTTP: "
                    + e.getStatusCode().value());
            throw new SellerKycDocumentUploadException(
                    "Unable to generate the signed KYC document URL. "
                            + "Supabase returned HTTP "
                            + e.getStatusCode().value(),
                    e
            );

        } catch (SellerKycDocumentUploadException e) {
            System.err.println("KYC signed URL error: " + e.getMessage());
            throw e;

        } catch (Exception e) {
            // Print the root cause for debugging, but never print request headers.
            System.err.println("KYC signed URL error type: "
                    + e.getClass().getName());
            System.err.println("KYC signed URL error message: "
                    + e.getMessage());
            e.printStackTrace();
            throw new SellerKycDocumentUploadException(
                    "Unable to generate the signed KYC document URL",
                    e
            );
        }
    }

    /**
     * Checks the signed URL host against the configured Supabase host.
     */
    private boolean configuredHostMatches(
            String configuredBaseUrl,
            String candidateHost
    ) {
        if (candidateHost == null || candidateHost.isBlank()) {
            return false;
        }

        String configuredHost = URI.create(configuredBaseUrl).getHost();

        return configuredHost != null
                && configuredHost.equalsIgnoreCase(candidateHost);
    }

    /**
     * Supabase Storage's signed URL response.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SupabaseSignedUrlResponse {

        @JsonProperty("signedURL")
        private String signedURL;

        public String getSignedURL() {
            return signedURL;
        }

        public void setSignedURL(String signedURL) {
            this.signedURL = signedURL;
        }
    }
}


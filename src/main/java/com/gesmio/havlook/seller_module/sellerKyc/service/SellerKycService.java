
package com.gesmio.havlook.seller_module.sellerKyc.service;

import com.gesmio.havlook.seller_module.sellerKyc.dto.SellerKycRequest;
import com.gesmio.havlook.seller_module.sellerKyc.dto.SellerKycResponse;
import org.springframework.web.multipart.MultipartFile;

public interface SellerKycService {

    SellerKycResponse submitKyc(
            Long userId,
            SellerKycRequest request
    );

    SellerKycResponse getMyKyc(Long userId);

    SellerKycResponse updateKyc(
            Long userId,
            SellerKycRequest request
    );

    SellerKycResponse getKycStatus(Long userId);

    SellerKycResponse uploadKycDocuments(
            Long userId,
            MultipartFile panDocument,
            MultipartFile aadhaarDocument,
            MultipartFile gstDocument,
            MultipartFile registrationDocument,
            MultipartFile addressDocument
    );

    /**
     * Generates a short-lived signed URL for a document
     * belonging to the authenticated seller.
     */
    String getSignedDocumentUrl(
            Long userId,
            String documentType
    );
}

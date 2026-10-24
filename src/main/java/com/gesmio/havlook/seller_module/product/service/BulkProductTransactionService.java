package com.gesmio.havlook.seller_module.product.service;

import com.gesmio.havlook.seller_module.product.dto.SellerProductCreateRequest;
import com.gesmio.havlook.seller_module.product.dto.SellerProductResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BulkProductTransactionService {

    private final SellerProductService sellerProductService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SellerProductResponse createOne(
            Long userId,
            SellerProductCreateRequest request) {

        return sellerProductService.createProduct(
                userId,
                request,
                null
        );
    }
}
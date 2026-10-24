package com.gesmio.havlook.seller_module.product.dto;

public record BulkProductItemResult(
        int index,
        String productName,
        boolean success,
        String message,
        SellerProductResponse product
) {}
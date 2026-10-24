package com.gesmio.havlook.seller_module.product.dto;

import java.util.List;

public record BulkProductCreateResponse(
        int total,
        int successCount,
        int failureCount,
        List<BulkProductItemResult> results
) {}
package com.gesmio.havlook.seller_module.product.service;

import com.gesmio.havlook.seller_module.product.dto.*;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BulkProductService {

    private final BulkProductTransactionService transactionService;
    private final Validator validator;

    public BulkProductCreateResponse createProducts(
            Long userId,
            List<SellerProductCreateRequest> requests) {

        if (requests == null || requests.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one product is required");
        }

        // Limit the size of each request
        if (requests.size() > 100) {
            throw new IllegalArgumentException(
                    "Maximum 100 products allowed per request");
        }

        List<BulkProductItemResult> results = new ArrayList<>();
        int successCount = 0;

        for (int i = 0; i < requests.size(); i++) {
            SellerProductCreateRequest request = requests.get(i);

            String productName = request.getName() == null
                    ? "Unknown"
                    : request.getName();

            try {
                // Validate each product separately
                List<String> errors = validator.validate(request)
                        .stream()
                        .map(ConstraintViolation::getMessage)
                        .collect(Collectors.toList());

                if (!errors.isEmpty()) {
                    results.add(new BulkProductItemResult(
                            i + 1,
                            productName,
                            false,
                            String.join(", ", errors),
                            null
                    ));
                    continue;
                }

                SellerProductResponse product =
                        transactionService.createOne(userId, request);

                results.add(new BulkProductItemResult(
                        i + 1,
                        productName,
                        true,
                        "Product created successfully",
                        product
                ));

                successCount++;

            } catch (Exception ex) {
                results.add(new BulkProductItemResult(
                        i + 1,
                        productName,
                        false,
                        getErrorMessage(ex),
                        null
                ));
            }
        }

        return new BulkProductCreateResponse(
                requests.size(),
                successCount,
                requests.size() - successCount,
                results
        );
    }

    private String getErrorMessage(Exception ex) {
        String message = ex.getMessage();

        if (message == null || message.isBlank()) {
            return "Product creation failed";
        }

        return message;
    }
}
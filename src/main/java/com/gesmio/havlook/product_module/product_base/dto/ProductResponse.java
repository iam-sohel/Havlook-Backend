
package com.gesmio.havlook.product_module.product_base.dto;

import com.gesmio.havlook.product_module.product_base.entity.ProductStatus;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductResponse {

    private Long id;

    private Long categoryId;

    private String categoryName;

    private Long subCategoryId;

    private String subCategoryName;

    private Long brandId;

    private String name;

    private String slug;

    private String description;

    private ProductStatus status;

    private Instant createdAt;

    private Instant updatedAt;

    // Backward-compatible constructor
    public ProductResponse(
            Long id,
            Long categoryId,
            Long subCategoryId,
            Long brandId,
            String name,
            String slug,
            String description,
            ProductStatus status,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.categoryId = categoryId;
        this.subCategoryId = subCategoryId;
        this.brandId = brandId;
        this.name = name;
        this.slug = slug;
        this.description = description;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}

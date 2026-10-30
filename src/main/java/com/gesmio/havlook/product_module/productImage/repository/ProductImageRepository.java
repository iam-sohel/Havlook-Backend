package com.gesmio.havlook.product_module.productImage.repository;

import com.gesmio.havlook.product_module.productImage.entity.ProductImageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductImageRepository
        extends JpaRepository<ProductImageEntity, Long> {

    List<ProductImageEntity>
    findByProductEntity_IdOrderByDisplayOrderAsc(
            Long productId
    );

    // Batch fetch images for all products on a page
    List<ProductImageEntity>
    findByProductEntity_IdInOrderByDisplayOrderAsc(
            List<Long> productIds
    );

    boolean existsByProductEntity_IdAndIsPrimaryTrue(
            Long productId
    );

    List<ProductImageEntity> findByProductEntity_IdAndIsPrimaryTrue(
            Long productId
    );

    void deleteByProductEntity_Id(Long productId);
}
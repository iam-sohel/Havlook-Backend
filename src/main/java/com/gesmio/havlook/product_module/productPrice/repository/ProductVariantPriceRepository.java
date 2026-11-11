package com.gesmio.havlook.product_module.productPrice.repository;

import com.gesmio.havlook.product_module.productPrice.entity.ProductVariantPriceEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductVariantPriceRepository
        extends JpaRepository<ProductVariantPriceEntity, Long> {

    Optional<ProductVariantPriceEntity> findByProductVariantId(
            Long productVariantId
    );

    List<ProductVariantPriceEntity> findByProductVariantIdIn(
            Collection<Long> variantIds
    );

    boolean existsByProductVariantId(
            Long productVariantId
    );
}
package com.gesmio.havlook.product_module.productSpecification.repository;

import com.gesmio.havlook.product_module.productSpecification.entity.ProductSpecification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductSpecificationRepository
        extends JpaRepository<ProductSpecification, Long> {

    List<ProductSpecification>
    findByProductEntity_IdOrderBySpecificationNameAsc(
            Long productId
    );

    // Batch fetch specifications for all products on a page
    List<ProductSpecification>
    findByProductEntity_IdInOrderBySpecificationNameAsc(
            List<Long> productIds
    );

    Optional<ProductSpecification>
    findByProductEntity_IdAndSpecificationNameIgnoreCase(
            Long productId,
            String specificationName
    );

    boolean existsByProductEntity_IdAndSpecificationNameIgnoreCase(
            Long productId,
            String specificationName
    );

    boolean existsByProductEntity_IdAndSpecificationNameIgnoreCaseAndIdNot(
            Long productId,
            String specificationName,
            Long id
    );

    void deleteByProductEntity_Id(Long productId);
}
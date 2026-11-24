
package com.gesmio.havlook.product_module.product_base.repository;

import com.gesmio.havlook.product_module.product_base.dto.ProductResponse;
import com.gesmio.havlook.product_module.product_base.entity.ProductStatus;
import com.gesmio.havlook.product_module.product_base.entity.ProductEntity;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<ProductEntity, Long> {

    boolean existsBySlugIgnoreCase(String slug);

    boolean existsBySlugIgnoreCaseAndIdNot(
            String slug,
            Long id
    );

    Optional<ProductEntity> findByIdAndStatus(
            Long id,
            ProductStatus status
    );

    Optional<ProductEntity> findBySlugIgnoreCaseAndStatus(
            String slug,
            ProductStatus status
    );

    Page<ProductEntity> findAllByStatus(
            ProductStatus status,
            Pageable pageable
    );

    Page<ProductEntity> findAllByCategoryIdAndStatus(
            Long categoryId,
            ProductStatus status,
            Pageable pageable
    );

    // ============================
    // Category Products - DTO Projection
    // ============================

    @Query(
            value = """
            SELECT new com.gesmio.havlook.product_module.product_base.dto.ProductResponse(
                p.id,
                c.id,
                c.name,
                sc.id,
                sc.name,
                b.id,
                p.name,
                p.slug,
                p.description,
                p.status,
                p.createdAt,
                p.updatedAt
            )
            FROM ProductEntity p
            JOIN p.category c
            JOIN p.subCategory sc
            LEFT JOIN p.brand b
            WHERE c.id = :categoryId
              AND p.status = :status
            ORDER BY p.name ASC
            """,
            countQuery = """
            SELECT COUNT(p)
            FROM ProductEntity p
            JOIN p.category c
            JOIN p.subCategory sc
            WHERE c.id = :categoryId
              AND p.status = :status
            """
    )
    Page<ProductResponse> findCategoryProductResponses(
            @Param("categoryId") Long categoryId,
            @Param("status") ProductStatus status,
            Pageable pageable
    );

    // ============================
    // SubCategory Products
    // ============================

    Page<ProductEntity> findAllBySubCategoryIdAndStatus(
            Long subCategoryId,
            ProductStatus status,
            Pageable pageable
    );

    // ============================
    // Brand Products
    // ============================

    Page<ProductEntity> findAllByBrandIdAndStatus(
            Long brandId,
            ProductStatus status,
            Pageable pageable
    );

    // ============================
    // Seller Products
    // ============================

    Optional<ProductEntity> findByIdAndSellerId(
            Long id,
            Long sellerId
    );

    Page<ProductEntity> findAllBySellerId(
            Long sellerId,
            Pageable pageable
    );

    Page<ProductEntity> findAllBySellerIdAndStatus(
            Long sellerId,
            ProductStatus status,
            Pageable pageable
    );

    boolean existsByIdAndSellerId(
            Long id,
            Long sellerId
    );

    // ============================
    // Seller Dashboard
    // ============================

    long countBySellerId(Long sellerId);

    long countBySellerIdAndStatus(
            Long sellerId,
            ProductStatus status
    );

    // ============================
    // Product Search
    // ============================

    @Query("""
        SELECT p
        FROM ProductEntity p
        WHERE p.status = :status
          AND (
              LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
              OR LOWER(p.slug) LIKE LOWER(CONCAT('%', :keyword, '%'))
              OR LOWER(p.description) LIKE LOWER(CONCAT('%', :keyword, '%'))
          )
        """)
    List<ProductEntity> searchByKeyword(
            @Param("keyword") String keyword,
            @Param("status") ProductStatus status
    );
}

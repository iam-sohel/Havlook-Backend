package com.gesmio.havlook.seller_module.inventory_module.repository;

import com.gesmio.havlook.seller_module.inventory_module.entity.InventoryItem;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryItemRepository
        extends JpaRepository<InventoryItem, Long> {

    List<InventoryItem> findByInventoryId(Long inventoryId);

    List<InventoryItem> findByProductVariantId(Long productVariantId);

    List<InventoryItem> findByProductVariantIdIn(Collection<Long> variantIds);

    Optional<InventoryItem> findByInventoryIdAndProductVariantId(
            Long inventoryId, Long productVariantId);

    boolean existsByInventoryIdAndProductVariantId(
            Long inventoryId, Long productVariantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT i
            FROM InventoryItem i
            WHERE i.id = :inventoryItemId
            """)
    Optional<InventoryItem> findByIdForUpdate(
            @Param("inventoryItemId") Long inventoryItemId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT i
            FROM InventoryItem i
            WHERE i.inventory.id = :inventoryId
              AND i.productVariant.id = :productVariantId
            """)
    Optional<InventoryItem> findByInventoryIdAndProductVariantIdForUpdate(
            @Param("inventoryId") Long inventoryId,
            @Param("productVariantId") Long productVariantId);

    // Seller statistics: seller is reached through inventory.warehouse.seller.

    @Query("""
            SELECT COUNT(i)
            FROM InventoryItem i
            WHERE i.inventory.warehouse.seller.id = :sellerId
            """)
    long countBySellerId(@Param("sellerId") Long sellerId);

    @Query("""
            SELECT COUNT(i)
            FROM InventoryItem i
            WHERE i.inventory.warehouse.seller.id = :sellerId
              AND COALESCE(i.availableStock, 0) <= :availableStock
            """)
    long countBySellerIdAndAvailableStockLessThanEqual(
            @Param("sellerId") Long sellerId,
            @Param("availableStock") Integer availableStock);

    @Query("""
            SELECT COALESCE(SUM(i.availableStock), 0)
            FROM InventoryItem i
            WHERE i.inventory.warehouse.seller.id = :sellerId
            """)
    Long sumAvailableStockBySellerId(@Param("sellerId") Long sellerId);

    @Query("""
            SELECT COALESCE(SUM(i.reservedStock), 0)
            FROM InventoryItem i
            WHERE i.inventory.warehouse.seller.id = :sellerId
            """)
    Long sumReservedStockBySellerId(@Param("sellerId") Long sellerId);

    @Query("""
            SELECT i
            FROM InventoryItem i
            WHERE i.productVariant.id = :productVariantId
              AND i.inventory.warehouse.seller.id = :sellerId
            ORDER BY i.id ASC
            """)
    List<InventoryItem> findByProductVariantAndSeller(
            @Param("productVariantId") Long productVariantId,
            @Param("sellerId") Long sellerId);

    @Query("""
            SELECT COALESCE(SUM(i.availableStock), 0)
            FROM InventoryItem i
            WHERE i.productVariant.id = :productVariantId
              AND i.inventory.warehouse.seller.id = :sellerId
            """)
    Long sumAvailableStockByProductVariantAndSeller(
            @Param("productVariantId") Long productVariantId,
            @Param("sellerId") Long sellerId);

    // Batch lock all inventory rows for the requested order variants.

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT i
            FROM InventoryItem i
            WHERE i.productVariant.id IN :variantIds
            ORDER BY i.productVariant.id ASC, i.id ASC
            """)
    List<InventoryItem> findInventoryItemsForVariantsForUpdate(
            @Param("variantIds") Collection<Long> variantIds);

    // Retained for backward compatibility with existing callers.

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT i
            FROM InventoryItem i
            WHERE i.productVariant.id = :productVariantId
              AND COALESCE(i.availableStock, 0) >= :quantity
            ORDER BY i.id ASC
            """)
    List<InventoryItem> findAvailableStockItemsForUpdate(
            @Param("productVariantId") Long productVariantId,
            @Param("quantity") Integer quantity,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT i
            FROM InventoryItem i
            WHERE i.productVariant.id = :productVariantId
              AND COALESCE(i.reservedStock, 0) >= :quantity
            ORDER BY i.id ASC
            """)
    List<InventoryItem> findReservedStockItemsForUpdate(
            @Param("productVariantId") Long productVariantId,
            @Param("quantity") Integer quantity);
}

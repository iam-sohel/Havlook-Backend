package com.gesmio.havlook.seller_module.inventory_module.service;

import com.gesmio.havlook.seller_module.inventory_module.dto.InventoryCreateRequest;
import com.gesmio.havlook.seller_module.inventory_module.dto.InventoryItemResponse;
import com.gesmio.havlook.seller_module.inventory_module.dto.InventoryResponse;
import com.gesmio.havlook.seller_module.inventory_module.dto.InventoryUpdateRequest;

import java.util.List;
import java.util.Map;

public interface InventoryService {

    InventoryResponse createInventory(Long userId, InventoryCreateRequest request);

    InventoryResponse getInventoryByWarehouse(Long userId, Long warehouseId);

    InventoryResponse updateInventoryItem(
            Long userId, Long inventoryItemId, InventoryUpdateRequest request);

    void deleteInventoryItem(Long userId, Long inventoryItemId);

    List<InventoryItemResponse> getInventoryItemsByProductVariantId(Long productVariantId);

    Integer getTotalAvailableStock(Long productVariantId);

    List<InventoryItemResponse> getInventoryItemsByProductVariantIdForSeller(
            Long userId, Long productVariantId);

    Integer getTotalAvailableStockForSeller(Long userId, Long productVariantId);

    // Warehouse-aware operations

    void addStock(Long warehouseId, Long productVariantId, Integer quantity);
    void reserveStock(Long warehouseId, Long productVariantId, Integer quantity);
    void releaseStock(Long warehouseId, Long productVariantId, Integer quantity);
    void deductStock(Long warehouseId, Long productVariantId, Integer quantity);
    void restoreStock(Long warehouseId, Long productVariantId, Integer quantity);

    // Backward-compatible operations

    void addStock(Long productVariantId, Integer quantity);
    void reserveStock(Long productVariantId, Integer quantity);
    void releaseStock(Long productVariantId, Integer quantity);
    void deductStock(Long productVariantId, Integer quantity);
    void restoreStock(Long productVariantId, Integer quantity);

    // Order-specific inventory allocation operations

    Long reserveStockForOrder(Long productVariantId, Integer quantity);

    Map<Long, Long> reserveStockForOrder(
            Map<Long, Integer> quantitiesByVariantId);

    void releaseReservedStockForOrder(
            Long inventoryItemId, Long productVariantId, Integer quantity);

    void deductReservedStockForOrder(
            Long inventoryItemId, Long productVariantId, Integer quantity);

    void restoreReturnedStockForOrder(
            Long inventoryItemId, Long productVariantId, Integer quantity);
}

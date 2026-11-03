package com.gesmio.havlook.seller_module.inventory_module.service;

import com.gesmio.havlook.product_module.productVariant.entity.ProductVariantEntity;
import com.gesmio.havlook.product_module.productVariant.repository.ProductVariantRepository;
import com.gesmio.havlook.seller_module.auth.exceptions.SellerNotFoundException;
import com.gesmio.havlook.seller_module.inventory_module.dto.InventoryCreateRequest;
import com.gesmio.havlook.seller_module.inventory_module.dto.InventoryItemResponse;
import com.gesmio.havlook.seller_module.inventory_module.dto.InventoryResponse;
import com.gesmio.havlook.seller_module.inventory_module.dto.InventoryUpdateRequest;
import com.gesmio.havlook.seller_module.inventory_module.entity.Inventory;
import com.gesmio.havlook.seller_module.inventory_module.entity.InventoryItem;
import com.gesmio.havlook.seller_module.inventory_module.exceptions.InventoryItemNotFoundException;
import com.gesmio.havlook.seller_module.inventory_module.exceptions.InventoryNotFoundException;
import com.gesmio.havlook.seller_module.inventory_module.exceptions.InventoryProductVariantAlreadyExistsException;
import com.gesmio.havlook.seller_module.inventory_module.exceptions.InventoryProductVariantNotFoundException;
import com.gesmio.havlook.seller_module.inventory_module.exceptions.InventoryStockException;
import com.gesmio.havlook.seller_module.inventory_module.exceptions.InventoryValidationException;
import com.gesmio.havlook.seller_module.inventory_module.exceptions.InventoryWarehouseException;
import com.gesmio.havlook.seller_module.inventory_module.exceptions.InventoryWarehouseInactiveException;
import com.gesmio.havlook.seller_module.inventory_module.repository.InventoryItemRepository;
import com.gesmio.havlook.seller_module.inventory_module.repository.InventoryRepository;
import com.gesmio.havlook.seller_module.seller.entity.Seller;
import com.gesmio.havlook.seller_module.seller.repository.SellerRepository;
import com.gesmio.havlook.seller_module.warehouse_module.entity.Warehouse;
import com.gesmio.havlook.seller_module.warehouse_module.repository.WarehouseRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ArrayList;

@Service
@RequiredArgsConstructor
@Transactional
public class InventoryServiceImpl implements InventoryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final SellerRepository sellerRepository;
    private final WarehouseRepository warehouseRepository;
    private final ProductVariantRepository productVariantRepository;

    // =========================================================
    // CREATE INVENTORY
    // =========================================================

    @Override
    public InventoryResponse createInventory(
            Long userId,
            InventoryCreateRequest request
    ) {
        validateNonNegativeQuantity(
                request.getAvailableStock(),
                "Available stock"
        );

        Seller seller = getSellerByUserId(userId);

        Warehouse warehouse = warehouseRepository
                .findByIdAndSeller(request.getWarehouseId(), seller)
                .orElseThrow(() ->
                        new InventoryWarehouseException(
                                "Warehouse not found or does not belong to seller"
                        )
                );

        if (!"ACTIVE".equalsIgnoreCase(warehouse.getStatus().name())) {
            throw new InventoryWarehouseInactiveException(
                    "Warehouse is not active"
            );
        }

        Inventory inventory = inventoryRepository
                .findByWarehouse(warehouse)
                .orElseGet(() ->
                        inventoryRepository.save(
                                Inventory.builder()
                                        .warehouse(warehouse)
                                        .build()
                        )
                );

        ProductVariantEntity productVariant = productVariantRepository
                .findById(request.getProductVariantId())
                .orElseThrow(() ->
                        new InventoryProductVariantNotFoundException(
                                "Product variant not found"
                        )
                );

        if (productVariant.getProductEntity() == null
                || productVariant.getProductEntity().getSeller() == null
                || productVariant.getProductEntity().getSeller().getId() == null
                || !productVariant.getProductEntity().getSeller().getId()
                .equals(seller.getId())) {

            throw new InventoryProductVariantNotFoundException(
                    "Product variant not found or does not belong to seller"
            );
        }

        if (inventoryItemRepository.existsByInventoryIdAndProductVariantId(
                inventory.getId(),
                productVariant.getId()
        )) {
            throw new InventoryProductVariantAlreadyExistsException(
                    "Product variant already exists in this warehouse inventory"
            );
        }

        InventoryItem item = InventoryItem.builder()
                .inventory(inventory)
                .productVariant(productVariant)
                .availableStock(request.getAvailableStock())
                .reservedStock(0)
                .build();

        inventoryItemRepository.save(item);

        return mapToResponse(inventory);
    }

    // =========================================================
    // GET INVENTORY BY WAREHOUSE
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public InventoryResponse getInventoryByWarehouse(
            Long userId,
            Long warehouseId
    ) {
        Seller seller = getSellerByUserId(userId);

        Warehouse warehouse = warehouseRepository
                .findByIdAndSeller(warehouseId, seller)
                .orElseThrow(() ->
                        new InventoryWarehouseException(
                                "Warehouse not found or does not belong to seller"
                        )
                );

        Inventory inventory = inventoryRepository
                .findByWarehouse(warehouse)
                .orElseThrow(() ->
                        new InventoryNotFoundException(
                                "Inventory not found for warehouse"
                        )
                );

        return mapToResponse(inventory);
    }

    // =========================================================
    // UPDATE INVENTORY ITEM
    // =========================================================

    @Override
    public InventoryResponse updateInventoryItem(
            Long userId,
            Long inventoryItemId,
            InventoryUpdateRequest request
    ) {
        validateNonNegativeQuantity(
                request.getAvailableStock(),
                "Available stock"
        );

        Seller seller = getSellerByUserId(userId);

        InventoryItem item = inventoryItemRepository
                .findById(inventoryItemId)
                .orElseThrow(() ->
                        new InventoryItemNotFoundException(
                                "Inventory item not found"
                        )
                );

        Warehouse warehouse = item.getInventory().getWarehouse();

        verifyWarehouseOwnership(warehouse, seller);

        int reserved = safeStock(item.getReservedStock());

        if (request.getAvailableStock() < reserved) {
            throw new InventoryStockException(
                    "Available stock cannot be less than reserved stock. "
                            + "Reserved stock: " + reserved
                            + ", requested: " + request.getAvailableStock()
            );
        }

        item.setAvailableStock(request.getAvailableStock());

        inventoryItemRepository.save(item);

        return mapToResponse(item.getInventory());
    }

    // =========================================================
    // DELETE INVENTORY ITEM
    // =========================================================

    @Override
    public void deleteInventoryItem(
            Long userId,
            Long inventoryItemId
    ) {
        Seller seller = getSellerByUserId(userId);

        InventoryItem item = inventoryItemRepository
                .findById(inventoryItemId)
                .orElseThrow(() ->
                        new InventoryItemNotFoundException(
                                "Inventory item not found"
                        )
                );

        verifyWarehouseOwnership(
                item.getInventory().getWarehouse(),
                seller
        );

        if (safeStock(item.getReservedStock()) > 0) {
            throw new InventoryStockException(
                    "Cannot delete inventory item while stock is reserved"
            );
        }

        inventoryItemRepository.delete(item);
    }

    // =========================================================
    // GET TOTAL AVAILABLE STOCK
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public Integer getTotalAvailableStock(Long productVariantId) {
        validateId(productVariantId, "Product variant id");

        long total = inventoryItemRepository
                .findByProductVariantId(productVariantId)
                .stream()
                .mapToLong(item -> safeStock(item.getAvailableStock()))
                .sum();

        if (total > Integer.MAX_VALUE) {
            throw new InventoryStockException(
                    "Total available stock exceeds maximum allowed value"
            );
        }

        return (int) total;
    }

    // =========================================================
    // ADD STOCK - WAREHOUSE AWARE
    // =========================================================

    @Override
    public void addStock(
            Long warehouseId,
            Long productVariantId,
            Integer quantity
    ) {
        validatePositiveQuantity(quantity, "Stock quantity");

        InventoryItem item = getLockedItem(
                warehouseId,
                productVariantId
        );

        item.setAvailableStock(
                safeAdd(safeStock(item.getAvailableStock()), quantity)
        );

        inventoryItemRepository.save(item);
    }

    // =========================================================
    // RESERVE STOCK - WAREHOUSE AWARE
    // =========================================================

    @Override
    public void reserveStock(
            Long warehouseId,
            Long productVariantId,
            Integer quantity
    ) {
        validatePositiveQuantity(quantity, "Reservation quantity");

        InventoryItem item = getLockedItem(
                warehouseId,
                productVariantId
        );

        int available = safeStock(item.getAvailableStock());

        if (available < quantity) {
            throw new InventoryStockException(
                    "Insufficient stock for product variant: "
                            + productVariantId
            );
        }

        item.setAvailableStock(available - quantity);

        item.setReservedStock(
                safeAdd(safeStock(item.getReservedStock()), quantity)
        );

        inventoryItemRepository.save(item);
    }

    // =========================================================
    // RELEASE STOCK - WAREHOUSE AWARE
    // =========================================================

    @Override
    public void releaseStock(
            Long warehouseId,
            Long productVariantId,
            Integer quantity
    ) {
        validatePositiveQuantity(quantity, "Release quantity");

        InventoryItem item = getLockedItem(
                warehouseId,
                productVariantId
        );

        int reserved = safeStock(item.getReservedStock());

        if (reserved < quantity) {
            throw new InventoryStockException(
                    "Cannot release more stock than reserved"
            );
        }

        item.setReservedStock(reserved - quantity);

        item.setAvailableStock(
                safeAdd(safeStock(item.getAvailableStock()), quantity)
        );

        inventoryItemRepository.save(item);
    }

    // =========================================================
    // DEDUCT STOCK - WAREHOUSE AWARE
    // =========================================================

    @Override
    public void deductStock(
            Long warehouseId,
            Long productVariantId,
            Integer quantity
    ) {
        validatePositiveQuantity(quantity, "Deduction quantity");

        InventoryItem item = getLockedItem(
                warehouseId,
                productVariantId
        );

        int reserved = safeStock(item.getReservedStock());

        if (reserved < quantity) {
            throw new InventoryStockException(
                    "Cannot deduct more stock than reserved"
            );
        }

        item.setReservedStock(reserved - quantity);

        inventoryItemRepository.save(item);
    }

    // =========================================================
    // RESTORE STOCK - WAREHOUSE AWARE
    // =========================================================

    @Override
    public void restoreStock(
            Long warehouseId,
            Long productVariantId,
            Integer quantity
    ) {
        validatePositiveQuantity(quantity, "Restore quantity");

        InventoryItem item = getLockedItem(
                warehouseId,
                productVariantId
        );

        item.setAvailableStock(
                safeAdd(safeStock(item.getAvailableStock()), quantity)
        );

        inventoryItemRepository.save(item);
    }

    // =========================================================
    // OPTION A - BATCH RESERVE FROM ONE INVENTORY ITEM PER VARIANT
    // =========================================================

    @Override
    public Long reserveStockForOrder(
            Long productVariantId,
            Integer quantity
    ) {
        validateId(productVariantId, "Product variant id");
        validatePositiveQuantity(quantity, "Reservation quantity");

        return reserveStockForOrder(
                Map.of(productVariantId, quantity)
        ).get(productVariantId);
    }

    @Override
    public Map<Long, Long> reserveStockForOrder(
            Map<Long, Integer> quantitiesByVariantId
    ) {
        if (quantitiesByVariantId == null || quantitiesByVariantId.isEmpty()) {
            throw new InventoryValidationException(
                    "At least one product variant is required for inventory reservation"
            );
        }

        Map<Long, Integer> requestedQuantities = new LinkedHashMap<>();

        quantitiesByVariantId.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    validateId(entry.getKey(), "Product variant id");
                    validatePositiveQuantity(entry.getValue(), "Reservation quantity");
                    requestedQuantities.put(entry.getKey(), entry.getValue());
                });

        /*
         * Lock every inventory row for these variants in one query.
         * The repository orders locks by variant ID and inventory item ID.
         */
        List<InventoryItem> lockedItems =
                inventoryItemRepository.findInventoryItemsForVariantsForUpdate(
                        requestedQuantities.keySet()
                );

        Map<Long, List<InventoryItem>> itemsByVariantId =
                new LinkedHashMap<>();

        for (InventoryItem item : lockedItems) {
            Long variantId = item.getProductVariant().getId();
            itemsByVariantId
                    .computeIfAbsent(variantId, ignored -> new ArrayList<>())
                    .add(item);
        }

        /*
         * Select every allocation before changing any stock.
         * Each variant must fit completely in one InventoryItem.
         */
        Map<Long, InventoryItem> selectedItems = new LinkedHashMap<>();

        for (Map.Entry<Long, Integer> entry : requestedQuantities.entrySet()) {
            Long variantId = entry.getKey();
            Integer requiredQuantity = entry.getValue();

            InventoryItem selectedItem = itemsByVariantId
                    .getOrDefault(variantId, List.of())
                    .stream()
                    .filter(item ->
                            safeStock(item.getAvailableStock()) >= requiredQuantity
                    )
                    .findFirst()
                    .orElse(null);

            if (selectedItem == null) {
                throw new InventoryStockException(
                        "Insufficient stock in a single warehouse for product variant: "
                                + variantId
                                + ", required quantity: "
                                + requiredQuantity
                );
            }

            selectedItems.put(variantId, selectedItem);
        }

        List<InventoryItem> itemsToSave = new ArrayList<>();
        Map<Long, Long> allocations = new LinkedHashMap<>();

        for (Map.Entry<Long, Integer> entry : requestedQuantities.entrySet()) {
            Long variantId = entry.getKey();
            Integer quantity = entry.getValue();
            InventoryItem item = selectedItems.get(variantId);

            item.setAvailableStock(
                    safeStock(item.getAvailableStock()) - quantity
            );
            item.setReservedStock(
                    safeAdd(safeStock(item.getReservedStock()), quantity)
            );

            itemsToSave.add(item);
            allocations.put(variantId, item.getId());
        }

        inventoryItemRepository.saveAll(itemsToSave);
        return allocations;
    }

    // =========================================================
    // OPTION A - RELEASE FROM EXACT INVENTORY ITEM
    // =========================================================

    @Override
    public void releaseReservedStockForOrder(
            Long inventoryItemId,
            Long productVariantId,
            Integer quantity
    ) {
        validatePositiveQuantity(quantity, "Release quantity");

        InventoryItem item = getLockedOrderInventoryItem(
                inventoryItemId,
                productVariantId
        );

        int reserved = safeStock(item.getReservedStock());

        if (reserved < quantity) {
            throw new InventoryStockException(
                    "Cannot release more stock than reserved in inventory item: "
                            + inventoryItemId
            );
        }

        item.setReservedStock(reserved - quantity);

        item.setAvailableStock(
                safeAdd(safeStock(item.getAvailableStock()), quantity)
        );

        inventoryItemRepository.save(item);
    }

    // =========================================================
    // OPTION A - DEDUCT FROM EXACT INVENTORY ITEM
    // =========================================================

    @Override
    public void deductReservedStockForOrder(
            Long inventoryItemId,
            Long productVariantId,
            Integer quantity
    ) {
        validatePositiveQuantity(quantity, "Deduction quantity");

        InventoryItem item = getLockedOrderInventoryItem(
                inventoryItemId,
                productVariantId
        );

        int reserved = safeStock(item.getReservedStock());

        if (reserved < quantity) {
            throw new InventoryStockException(
                    "Cannot deduct more stock than reserved in inventory item: "
                            + inventoryItemId
            );
        }

        item.setReservedStock(reserved - quantity);

        inventoryItemRepository.save(item);
    }

    // =========================================================
    // OPTION A - RESTORE RETURN TO EXACT INVENTORY ITEM
    // =========================================================

    @Override
    public void restoreReturnedStockForOrder(
            Long inventoryItemId,
            Long productVariantId,
            Integer quantity
    ) {
        validatePositiveQuantity(quantity, "Return quantity");

        InventoryItem item = getLockedOrderInventoryItem(
                inventoryItemId,
                productVariantId
        );

        item.setAvailableStock(
                safeAdd(safeStock(item.getAvailableStock()), quantity)
        );

        inventoryItemRepository.save(item);
    }

    // =========================================================
    // BACKWARD-COMPATIBLE ORDER SERVICE METHODS
    // =========================================================

    @Override
    public void reserveStock(Long productVariantId, Integer quantity) {
        validatePositiveQuantity(quantity, "Reservation quantity");

        InventoryItem item = findLockedItemWithAvailableStock(
                productVariantId,
                quantity
        );

        item.setAvailableStock(
                safeStock(item.getAvailableStock()) - quantity
        );

        item.setReservedStock(
                safeAdd(safeStock(item.getReservedStock()), quantity)
        );

        inventoryItemRepository.save(item);
    }

    @Override
    public void releaseStock(Long productVariantId, Integer quantity) {
        validatePositiveQuantity(quantity, "Release quantity");

        InventoryItem item = findLockedItemWithReservedStock(
                productVariantId,
                quantity
        );

        item.setReservedStock(
                safeStock(item.getReservedStock()) - quantity
        );

        item.setAvailableStock(
                safeAdd(safeStock(item.getAvailableStock()), quantity)
        );

        inventoryItemRepository.save(item);
    }

    @Override
    public void deductStock(Long productVariantId, Integer quantity) {
        validatePositiveQuantity(quantity, "Deduction quantity");

        InventoryItem item = findLockedItemWithReservedStock(
                productVariantId,
                quantity
        );

        item.setReservedStock(
                safeStock(item.getReservedStock()) - quantity
        );

        inventoryItemRepository.save(item);
    }

    @Override
    public void addStock(Long productVariantId, Integer quantity) {
        restoreStock(productVariantId, quantity);
    }

    @Override
    public void restoreStock(Long productVariantId, Integer quantity) {
        validatePositiveQuantity(quantity, "Restore quantity");

        InventoryItem item = findLockedItem(productVariantId);

        item.setAvailableStock(
                safeAdd(safeStock(item.getAvailableStock()), quantity)
        );

        inventoryItemRepository.save(item);
    }

    // =========================================================
    // GET LOCKED ITEM BY WAREHOUSE
    // =========================================================

    private InventoryItem getLockedItem(
            Long warehouseId,
            Long productVariantId
    ) {
        validateId(warehouseId, "Warehouse id");
        validateId(productVariantId, "Product variant id");

        Inventory inventory = inventoryRepository
                .findByWarehouseIdForUpdate(warehouseId)
                .orElseThrow(() ->
                        new InventoryNotFoundException(
                                "Inventory not found for warehouse: "
                                        + warehouseId
                        )
                );

        return inventoryItemRepository
                .findByInventoryIdAndProductVariantIdForUpdate(
                        inventory.getId(),
                        productVariantId
                )
                .orElseThrow(() ->
                        new InventoryProductVariantNotFoundException(
                                "Product variant is not available in this warehouse"
                        )
                );
    }

    // =========================================================
    // GET LOCKED ORDER INVENTORY ITEM
    // =========================================================

    private InventoryItem getLockedOrderInventoryItem(
            Long inventoryItemId,
            Long productVariantId
    ) {
        validateId(inventoryItemId, "Inventory item id");
        validateId(productVariantId, "Product variant id");

        InventoryItem item = inventoryItemRepository
                .findByIdForUpdate(inventoryItemId)
                .orElseThrow(() ->
                        new InventoryItemNotFoundException(
                                "Inventory item not found: " + inventoryItemId
                        )
                );

        if (item.getProductVariant() == null
                || item.getProductVariant().getId() == null
                || !item.getProductVariant().getId().equals(productVariantId)) {

            throw new InventoryStockException(
                    "Inventory item does not belong to product variant: "
                            + productVariantId
            );
        }

        return item;
    }

    // =========================================================
    // FIND LOCKED ITEM
    // =========================================================

    private InventoryItem findLockedItem(Long productVariantId) {
        validateId(productVariantId, "Product variant id");

        List<InventoryItem> items =
                inventoryItemRepository.findByProductVariantId(productVariantId);

        if (items.isEmpty()) {
            throw new InventoryNotFoundException(
                    "Inventory not found for product variant: "
                            + productVariantId
            );
        }

        InventoryItem first = items.get(0);

        return inventoryItemRepository
                .findByInventoryIdAndProductVariantIdForUpdate(
                        first.getInventory().getId(),
                        productVariantId
                )
                .orElseThrow(() ->
                        new InventoryItemNotFoundException(
                                "Inventory item not found for product variant: "
                                        + productVariantId
                        )
                );
    }

    // =========================================================
    // FIND LOCKED ITEM WITH AVAILABLE STOCK
    // =========================================================

    private InventoryItem findLockedItemWithAvailableStock(
            Long productVariantId,
            Integer quantity
    ) {
        validateId(productVariantId, "Product variant id");

        for (InventoryItem item :
                inventoryItemRepository.findByProductVariantId(productVariantId)) {

            InventoryItem locked =
                    inventoryItemRepository
                            .findByInventoryIdAndProductVariantIdForUpdate(
                                    item.getInventory().getId(),
                                    productVariantId
                            )
                            .orElse(null);

            if (locked != null
                    && safeStock(locked.getAvailableStock()) >= quantity) {
                return locked;
            }
        }

        throw new InventoryStockException(
                "Insufficient stock for product variant: " + productVariantId
        );
    }

    // =========================================================
    // FIND LOCKED ITEM WITH RESERVED STOCK
    // =========================================================

    private InventoryItem findLockedItemWithReservedStock(
            Long productVariantId,
            Integer quantity
    ) {
        validateId(productVariantId, "Product variant id");

        for (InventoryItem item :
                inventoryItemRepository.findByProductVariantId(productVariantId)) {

            InventoryItem locked =
                    inventoryItemRepository
                            .findByInventoryIdAndProductVariantIdForUpdate(
                                    item.getInventory().getId(),
                                    productVariantId
                            )
                            .orElse(null);

            if (locked != null
                    && safeStock(locked.getReservedStock()) >= quantity) {
                return locked;
            }
        }

        throw new InventoryStockException(
                "Reserved inventory not found for product variant: "
                        + productVariantId
        );
    }

    // =========================================================
    // GET SELLER
    // =========================================================

    private Seller getSellerByUserId(Long userId) {
        validateId(userId, "User id");

        return sellerRepository
                .findByUserId(userId)
                .orElseThrow(() ->
                        new SellerNotFoundException(
                                "Seller profile not found"
                        )
                );
    }

    // =========================================================
    // VERIFY WAREHOUSE OWNERSHIP
    // =========================================================

    private void verifyWarehouseOwnership(
            Warehouse warehouse,
            Seller seller
    ) {
        if (warehouse == null
                || warehouse.getSeller() == null
                || warehouse.getSeller().getId() == null
                || !warehouse.getSeller().getId().equals(seller.getId())) {

            throw new InventoryWarehouseException(
                    "Warehouse does not belong to seller"
            );
        }
    }

    // =========================================================
    // MAP INVENTORY RESPONSE
    // =========================================================

    private InventoryResponse mapToResponse(Inventory inventory) {
        List<InventoryItemResponse> items =
                inventoryItemRepository.findByInventoryId(inventory.getId())
                        .stream()
                        .map(this::mapItem)
                        .toList();

        return InventoryResponse.builder()
                .id(inventory.getId())
                .warehouseId(inventory.getWarehouse().getId())
                .warehouseName(
                        inventory.getWarehouse().getWarehouseName()
                )
                .items(items)
                .createdAt(inventory.getCreatedAt())
                .updatedAt(inventory.getUpdatedAt())
                .build();
    }

    // =========================================================
    // MAP INVENTORY ITEM
    // =========================================================

    private InventoryItemResponse mapItem(InventoryItem item) {
        int available = safeStock(item.getAvailableStock());
        int reserved = safeStock(item.getReservedStock());

        return InventoryItemResponse.builder()
                .id(item.getId())
                .productVariantId(item.getProductVariant().getId())
                .sku(item.getProductVariant().getSku())
                .availableStock(available)
                .reservedStock(reserved)
                .totalStock(safeAdd(available, reserved))
                .stockStatus(
                        available > 0 ? "IN_STOCK" : "OUT_OF_STOCK"
                )
                .build();
    }

    // =========================================================
    // VALIDATE ID
    // =========================================================

    private void validateId(Long id, String fieldName) {
        if (id == null || id <= 0) {
            throw new InventoryValidationException(
                    fieldName + " must be greater than zero"
            );
        }
    }

    // =========================================================
    // VALIDATE NON-NEGATIVE QUANTITY
    // =========================================================

    private void validateNonNegativeQuantity(
            Integer quantity,
            String fieldName
    ) {
        if (quantity == null || quantity < 0) {
            throw new InventoryValidationException(
                    fieldName + " cannot be negative"
            );
        }
    }

    // =========================================================
    // VALIDATE POSITIVE QUANTITY
    // =========================================================

    private void validatePositiveQuantity(
            Integer quantity,
            String fieldName
    ) {
        if (quantity == null || quantity <= 0) {
            throw new InventoryValidationException(
                    fieldName + " must be greater than zero"
            );
        }
    }

    // =========================================================
    // SAFE STOCK
    // =========================================================

    private int safeStock(Integer stock) {
        return stock == null ? 0 : stock;
    }

    // =========================================================
    // SAFE STOCK ADDITION
    // =========================================================

    private int safeAdd(int current, int quantity) {
        long result = (long) current + quantity;

        if (result > Integer.MAX_VALUE) {
            throw new InventoryStockException(
                    "Stock quantity exceeds maximum allowed value"
            );
        }

        return (int) result;
    }

    // =========================================================
    // GET INVENTORY ITEMS BY PRODUCT VARIANT
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public List<InventoryItemResponse> getInventoryItemsByProductVariantId(
            Long productVariantId
    ) {
        validateId(productVariantId, "Product variant id");

        return inventoryItemRepository
                .findByProductVariantId(productVariantId)
                .stream()
                .map(this::mapItem)
                .toList();
    }

    // =========================================================
    // GET INVENTORY ITEMS BY PRODUCT VARIANT FOR SELLER
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public List<InventoryItemResponse>
    getInventoryItemsByProductVariantIdForSeller(
            Long userId,
            Long productVariantId
    ) {
        validateId(productVariantId, "Product variant id");

        Seller seller = getSellerByUserId(userId);

        return inventoryItemRepository
                .findByProductVariantAndSeller(
                        productVariantId,
                        seller.getId()
                )
                .stream()
                .map(this::mapItem)
                .toList();
    }

    // =========================================================
    // GET TOTAL AVAILABLE STOCK FOR SELLER
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public Integer getTotalAvailableStockForSeller(
            Long userId,
            Long productVariantId
    ) {
        validateId(productVariantId, "Product variant id");

        Seller seller = getSellerByUserId(userId);

        Long totalStock = inventoryItemRepository
                .sumAvailableStockByProductVariantAndSeller(
                        productVariantId,
                        seller.getId()
                );

        if (totalStock == null) {
            return 0;
        }

        if (totalStock > Integer.MAX_VALUE) {
            throw new InventoryStockException(
                    "Total available stock exceeds maximum allowed value"
            );
        }

        return totalStock.intValue();
    }
}

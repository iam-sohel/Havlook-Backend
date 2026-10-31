package com.gesmio.havlook.product_module.product_base.service;
import com.gesmio.havlook.auth_module.security.CustomUserDetails;
import com.gesmio.havlook.seller_module.seller.entity.Seller;
import com.gesmio.havlook.seller_module.seller.repository.SellerRepository;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Objects;
import com.gesmio.havlook.brand_module.entity.Brand;
import com.gesmio.havlook.brand_module.entity.BrandStatus;
import com.gesmio.havlook.brand_module.repository.BrandRepository;

import com.gesmio.havlook.category_module.entity.Category;
import com.gesmio.havlook.category_module.entity.CategoryStatus;
import com.gesmio.havlook.category_module.repository.CategoryRepository;

import com.gesmio.havlook.product_module.product_base.exceptions.ProductAlreadyActiveException;
import com.gesmio.havlook.product_module.product_base.exceptions.ProductAlreadyInactiveException;
import com.gesmio.havlook.product_module.product_base.exceptions.ProductNotFoundException;
import com.gesmio.havlook.product_module.product_base.exceptions.ProductSlugAlreadyExistsException;
import com.gesmio.havlook.product_module.product_base.exceptions.ProductValidationException;

import com.gesmio.havlook.product_module.productImage.entity.ProductImageEntity;
import com.gesmio.havlook.product_module.productImage.repository.ProductImageRepository;
import com.gesmio.havlook.product_module.productImage.dto.ProductImageResponse;

import com.gesmio.havlook.product_module.productPrice.entity.ProductVariantPriceEntity;
import com.gesmio.havlook.product_module.productPrice.repository.ProductVariantPriceRepository;
import com.gesmio.havlook.product_module.productPrice.dto.ProductVariantPriceResponse;

import com.gesmio.havlook.product_module.productSpecification.entity.ProductSpecification;
import com.gesmio.havlook.product_module.productSpecification.repository.ProductSpecificationRepository;
import com.gesmio.havlook.product_module.productSpecification.dto.ProductSpecificationResponse;

import com.gesmio.havlook.product_module.productVariant.entity.ProductVariantEntity;
import com.gesmio.havlook.product_module.productVariant.repository.ProductVariantRepository;
import com.gesmio.havlook.product_module.productVariant.entity.ProductVariantStatus;
import com.gesmio.havlook.product_module.productVariant.dto.ProductVariantResponse;

import com.gesmio.havlook.product_module.product_base.mapper.ProductMapper;
import com.gesmio.havlook.product_module.product_base.entity.ProductStatus;
import com.gesmio.havlook.product_module.product_base.dto.ProductCreateRequest;
import com.gesmio.havlook.product_module.product_base.dto.ProductDetailsResponse;
import com.gesmio.havlook.product_module.product_base.dto.ProductResponse;
import com.gesmio.havlook.product_module.product_base.dto.ProductUpdateRequest;
import com.gesmio.havlook.product_module.product_base.entity.ProductEntity;
import com.gesmio.havlook.product_module.product_base.repository.ProductRepository;

import com.gesmio.havlook.product_module.variantAttribute.entity.VariantAttributeEntity;
import com.gesmio.havlook.product_module.variantAttribute.repository.VariantAttributeRepository;
import com.gesmio.havlook.product_module.variantAttribute.dto.VariantAttributeResponse;

import com.gesmio.havlook.seller_module.inventory_module.entity.InventoryItem;
import com.gesmio.havlook.seller_module.inventory_module.repository.InventoryItemRepository;

import com.gesmio.havlook.subcategory_module.entity.SubCategory;
import com.gesmio.havlook.subcategory_module.entity.SubCategoryStatus;
import com.gesmio.havlook.subcategory_module.repository.SubCategoryRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ProductServiceImpl implements ProductService {
    private final ProductRepository productRepository;

    private final ProductMapper productMapper;

    private final CategoryRepository categoryRepository;

    private final SubCategoryRepository subCategoryRepository;

    private final BrandRepository brandRepository;

    private final ProductVariantRepository productVariantRepository;

    private final ProductVariantPriceRepository productVariantPriceRepository;

    private final VariantAttributeRepository variantAttributeRepository;

    private final InventoryItemRepository inventoryItemRepository;

    private final ProductSpecificationRepository productSpecificationRepository;

    private final ProductImageRepository productImageRepository;

    // Used to resolve the seller associated with the authenticated JWT user.
    private final SellerRepository sellerRepository;


    private final JdbcTemplate jdbcTemplate;
    // =========================================================
    // AUTHENTICATION AND SELLER OWNERSHIP
    // =========================================================

    private CustomUserDetails getAuthenticatedUserDetails() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null ||
                !authentication.isAuthenticated() ||
                !(authentication.getPrincipal() instanceof CustomUserDetails userDetails)) {
            throw new AccessDeniedException("Authenticated user not found");
        }

        return userDetails;
    }

    private boolean isAdmin() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        return authentication.getAuthorities().stream()
                .anyMatch(authority ->
                        "ROLE_ADMIN".equals(authority.getAuthority()));
    }

    private Seller getAuthenticatedSeller() {
        Long userId = getAuthenticatedUserDetails().getUserId();

        if (userId == null) {
            throw new AccessDeniedException("Authenticated user ID not found");
        }

        return sellerRepository.findByUserId(userId)
                .orElseThrow(() ->
                        new AccessDeniedException(
                                "Seller profile not found for authenticated user"));
    }

    private void validateProductOwnership(ProductEntity product) {
        if (product == null) {
            throw new ProductNotFoundException("Product not found");
        }

        // Admin can manage products belonging to any seller.
        if (isAdmin()) {
            return;
        }

        Seller authenticatedSeller = getAuthenticatedSeller();

        if (product.getSeller() == null ||
                product.getSeller().getId() == null ||
                !Objects.equals(
                        product.getSeller().getId(),
                        authenticatedSeller.getId())) {
            throw new AccessDeniedException(
                    "You are not authorized to modify this product");
        }
    }

    // =========================================================
    // CREATE PRODUCT
    // =========================================================

    @Override
    public ProductResponse createProduct(
            ProductCreateRequest request
    ) {
        // Seller identity comes only from the authenticated JWT principal.
        // Admin seller assignment is intentionally not inferred from request data.
        Seller authenticatedSeller = getAuthenticatedSeller();

        validateSlugForCreate(request.getSlug());

        Category category =
                getActiveCategory(request.getCategoryId());

        SubCategory subCategory =
                getActiveSubCategory(request.getSubCategoryId());

        validateSubCategoryBelongsToCategory(
                subCategory,
                category
        );

        Brand brand =
                getActiveBrand(request.getBrandId());

        ProductEntity product =
                productMapper.toEntity(request);

        product.setCategory(category);
        product.setSubCategory(subCategory);
        product.setBrand(brand);
        product.setSeller(authenticatedSeller);

        ProductEntity savedProduct =
                productRepository.save(product);

        return productMapper.toResponse(savedProduct);
    }


    // =========================================================
    // GET PRODUCT BY ID
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getProductById(Long id) {

        ProductEntity product =
                getActiveProductById(id);

        return productMapper.toResponse(product);
    }


    // =========================================================
    // GET PRODUCT BY SLUG
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getProductBySlug(String slug) {

        ProductEntity product =
                getActiveProductBySlug(slug);

        return productMapper.toResponse(product);
    }


    // =========================================================
    // COMPLETE PRODUCT DETAILS BY ID
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public ProductDetailsResponse getProductDetailsById(
            Long id
    ) {

        ProductEntity product =
                getActiveProductById(id);

        return buildProductDetails(product);
    }


    // =========================================================
    // COMPLETE PRODUCT DETAILS BY SLUG
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public ProductDetailsResponse getProductDetailsBySlug(
            String slug
    ) {

        ProductEntity product =
                getActiveProductBySlug(slug);

        return buildProductDetails(product);
    }


    // =========================================================
    // BUILD COMPLETE PRODUCT DETAILS
    // =========================================================

    private ProductDetailsResponse buildProductDetails(
            ProductEntity product
    ) {

        Long productId = product.getId();


        // =====================================================
        // SPECIFICATIONS
        // =====================================================

        List<ProductSpecificationResponse> specifications =
                productSpecificationRepository
                        .findByProductEntity_IdOrderBySpecificationNameAsc(
                                productId
                        )
                        .stream()
                        .map(this::toSpecificationResponse)
                        .toList();


        // =====================================================
        // IMAGES
        // =====================================================

        List<ProductImageResponse> images =
                productImageRepository
                        .findByProductEntity_IdOrderByDisplayOrderAsc(
                                productId
                        )
                        .stream()
                        .map(this::toImageResponse)
                        .toList();


        // =====================================================
        // ACTIVE VARIANTS
        // =====================================================
        // OPTIMIZATION:
        // Filter ACTIVE variants directly in the database.
        // Inactive variants are not loaded into application memory.

        List<ProductVariantEntity> variants =
                productVariantRepository
                        .findByProductEntity_IdAndStatus(
                                productId,
                                ProductVariantStatus.ACTIVE
                        );


        // =====================================================
        // VARIANT IDS
        // =====================================================

        List<Long> variantIds =
                variants.stream()
                        .map(ProductVariantEntity::getId)
                        .toList();


        // =====================================================
        // BATCH LOAD ATTRIBUTES
        // =====================================================
        // Existing optimization preserved.

        Map<Long, List<VariantAttributeEntity>>
                attributesByVariantId;

        if (variantIds.isEmpty()) {

            attributesByVariantId =
                    Collections.emptyMap();

        } else {

            attributesByVariantId =
                    variantAttributeRepository
                            .findByVariantIdInOrderByAttributeNameAsc(
                                    variantIds
                            )
                            .stream()
                            .collect(
                                    Collectors.groupingBy(
                                            attribute ->
                                                    attribute
                                                            .getVariant()
                                                            .getId()
                                    )
                            );
        }


        // =====================================================
        // BATCH LOAD PRICES
        // =====================================================
        // OPTIMIZATION:
        // One query for all variant prices.

        Map<Long, ProductVariantPriceEntity>
                pricesByVariantId;

        if (variantIds.isEmpty()) {

            pricesByVariantId =
                    Collections.emptyMap();

        } else {

            pricesByVariantId =
                    productVariantPriceRepository
                            .findByProductVariantIdIn(
                                    variantIds
                            )
                            .stream()
                            .collect(
                                    Collectors.toMap(
                                            price ->
                                                    price
                                                            .getProductVariant()
                                                            .getId(),
                                            price -> price,
                                            (first, second) -> first
                                    )
                            );
        }


        // =====================================================
        // BATCH LOAD INVENTORY
        // =====================================================
        // OPTIMIZATION:
        // One query for inventory of all variants.

        Map<Long, List<InventoryItem>>
                inventoryByVariantId;

        if (variantIds.isEmpty()) {

            inventoryByVariantId =
                    Collections.emptyMap();

        } else {

            inventoryByVariantId =
                    inventoryItemRepository
                            .findByProductVariantIdIn(
                                    variantIds
                            )
                            .stream()
                            .collect(
                                    Collectors.groupingBy(
                                            item ->
                                                    item
                                                            .getProductVariant()
                                                            .getId()
                                    )
                            );
        }


        // =====================================================
        // BUILD VARIANT RESPONSES
        // =====================================================

        List<ProductVariantResponse> variantResponses =
                variants.stream()
                        .map(variant -> {

                            Long variantId =
                                    variant.getId();


                            // ---------------------------------
                            // ATTRIBUTES
                            // ---------------------------------

                            List<VariantAttributeResponse>
                                    attributes =
                                    attributesByVariantId
                                            .getOrDefault(
                                                    variantId,
                                                    Collections.emptyList()
                                            )
                                            .stream()
                                            .map(
                                                    this::toAttributeResponse
                                            )
                                            .toList();


                            // ---------------------------------
                            // PRICE
                            // ---------------------------------

                            ProductVariantPriceResponse price =
                                    Optional.ofNullable(
                                                    pricesByVariantId.get(
                                                            variantId
                                                    )
                                            )
                                            .map(
                                                    this::toPriceResponse
                                            )
                                            .orElse(null);


                            // ---------------------------------
                            // INVENTORY
                            // ---------------------------------

                            List<InventoryItem> inventoryItems =
                                    inventoryByVariantId
                                            .getOrDefault(
                                                    variantId,
                                                    Collections.emptyList()
                                            );


                            // ---------------------------------
                            // TOTAL AVAILABLE STOCK
                            // ---------------------------------

                            int totalAvailableStock =
                                    inventoryItems.stream()
                                            .mapToInt(item ->
                                                    item.getAvailableStock() == null
                                                            ? 0
                                                            : item.getAvailableStock()
                                            )
                                            .sum();


                            // ---------------------------------
                            // STOCK STATUS
                            // ---------------------------------

                            String stockStatus =
                                    calculateStockStatus(
                                            totalAvailableStock
                                    );


                            // ---------------------------------
                            // AVAILABLE
                            // ---------------------------------

                            boolean available =
                                    totalAvailableStock > 0;


                            // ---------------------------------
                            // RESPONSE
                            // ---------------------------------

                            return ProductVariantResponse
                                    .builder()
                                    .id(variantId)
                                    .productId(productId)
                                    .sku(variant.getSku())
                                    .status(variant.getStatus())
                                    .attributes(attributes)
                                    .price(price)
                                    .stockStatus(stockStatus)
                                    .available(available)
                                    .build();

                        })
                        .toList();


        // =====================================================
        // FINAL PRODUCT DETAILS RESPONSE
        // =====================================================

        return ProductDetailsResponse
                .builder()
                .id(product.getId())
                .name(product.getName())
                .slug(product.getSlug())
                .description(product.getDescription())
                .categoryId(
                        product.getCategory().getId()
                )
                .subCategoryId(
                        product.getSubCategory().getId()
                )
                .brandId(
                        product.getBrand() != null
                                ? product.getBrand().getId()
                                : null
                )
                .specifications(specifications)
                .images(images)
                .variants(variantResponses)
                .build();
    }

    // =========================================================
    // STOCK STATUS
    // =========================================================

    private String calculateStockStatus(
            Integer availableStock
    ) {

        if (availableStock == null ||
                availableStock <= 0) {

            return "OUT_OF_STOCK";
        }

        if (availableStock <= 5) {
            return "LOW_STOCK";
        }

        return "IN_STOCK";
    }


    // =========================================================
    // IMAGE MAPPING
    // =========================================================

    private ProductImageResponse toImageResponse(
            ProductImageEntity image
    ) {

        return ProductImageResponse
                .builder()
                .id(image.getId())
                .productId(
                        image.getProductEntity().getId()
                )
                .imageUrl(
                        image.getImageUrl()
                )
                .isPrimary(
                        image.getIsPrimary()
                )
                .displayOrder(
                        image.getDisplayOrder()
                )
                .build();
    }


    // =========================================================
    // SPECIFICATION MAPPING
    // =========================================================

    private ProductSpecificationResponse
    toSpecificationResponse(
            ProductSpecification specification
    ) {

        return ProductSpecificationResponse
                .builder()
                .id(specification.getId())
                .productId(
                        specification
                                .getProductEntity()
                                .getId()
                )
                .specificationName(
                        specification
                                .getSpecificationName()
                )
                .specificationValue(
                        specification
                                .getSpecificationValue()
                )
                .build();
    }


    // =========================================================
    // ATTRIBUTE MAPPING
    // =========================================================

    private VariantAttributeResponse
    toAttributeResponse(
            VariantAttributeEntity attribute
    ) {

        return VariantAttributeResponse
                .builder()
                .id(attribute.getId())
                .variantId(
                        attribute
                                .getVariant()
                                .getId()
                )
                .attributeName(
                        attribute.getAttributeName()
                )
                .attributeValue(
                        attribute.getAttributeValue()
                )
                .build();
    }


    // =========================================================
    // PRICE MAPPING
    // =========================================================

    private ProductVariantPriceResponse
    toPriceResponse(
            ProductVariantPriceEntity price
    ) {

        return ProductVariantPriceResponse
                .builder()
                .id(price.getId())
                .productVariantId(
                        price
                                .getProductVariant()
                                .getId()
                )
                .mrp(price.getMrp())
                .sellingPrice(
                        price.getSellingPrice()
                )
                .currency(
                        price.getCurrency()
                )
                .build();
    }


    // =========================================================
    // GET ALL PRODUCTS
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public Page<ProductDetailsResponse> getAllProducts(
            Pageable pageable
    ) {
        Page<ProductEntity> productPage =
                productRepository.findAllByStatus(
                        ProductStatus.ACTIVE,
                        pageable
                );

        if (productPage.isEmpty()) {
            return productPage.map(this::buildProductDetails);
        }

        Map<Long, ProductDetailsResponse> detailsByProductId =
                buildProductDetailsBatch(productPage.getContent());

        return productPage.map(
                product -> detailsByProductId.get(product.getId())
        );
    }private Map<Long, ProductDetailsResponse> buildProductDetailsBatch(
            List<ProductEntity> products
    ) {
        List<Long> productIds = products.stream()
                .map(ProductEntity::getId)
                .toList();

        // 1. Batch-load specifications
        Map<Long, List<ProductSpecification>> specificationsByProductId =
                productSpecificationRepository
                        .findByProductEntity_IdInOrderBySpecificationNameAsc(
                                productIds
                        )
                        .stream()
                        .collect(Collectors.groupingBy(
                                specification ->
                                        specification.getProductEntity().getId()
                        ));

        // 2. Batch-load images
        Map<Long, List<ProductImageEntity>> imagesByProductId =
                productImageRepository
                        .findByProductEntity_IdInOrderByDisplayOrderAsc(
                                productIds
                        )
                        .stream()
                        .collect(Collectors.groupingBy(
                                image -> image.getProductEntity().getId()
                        ));

        // 3. Batch-load active variants
        List<ProductVariantEntity> variants =
                productVariantRepository.findByProductEntity_IdInAndStatus(
                        productIds,
                        ProductVariantStatus.ACTIVE
                );

        List<Long> variantIds = variants.stream()
                .map(ProductVariantEntity::getId)
                .toList();

        // 4. Batch-load attributes
        Map<Long, List<VariantAttributeEntity>> attributesByVariantId;

        if (variantIds.isEmpty()) {
            attributesByVariantId = Collections.emptyMap();
        } else {
            attributesByVariantId =
                    variantAttributeRepository
                            .findByVariantIdInOrderByAttributeNameAsc(
                                    variantIds
                            )
                            .stream()
                            .collect(Collectors.groupingBy(
                                    attribute ->
                                            attribute.getVariant().getId()
                            ));
        }

        // 5. Batch-load prices
        Map<Long, ProductVariantPriceEntity> pricesByVariantId;

        if (variantIds.isEmpty()) {
            pricesByVariantId = Collections.emptyMap();
        } else {
            pricesByVariantId =
                    productVariantPriceRepository
                            .findByProductVariantIdIn(variantIds)
                            .stream()
                            .collect(Collectors.toMap(
                                    price -> price.getProductVariant().getId(),
                                    price -> price,
                                    (first, second) -> first
                            ));
        }

        // 6. Batch-load inventory
        Map<Long, List<InventoryItem>> inventoryByVariantId;

        if (variantIds.isEmpty()) {
            inventoryByVariantId = Collections.emptyMap();
        } else {
            inventoryByVariantId =
                    inventoryItemRepository
                            .findByProductVariantIdIn(variantIds)
                            .stream()
                            .collect(Collectors.groupingBy(
                                    item ->
                                            item.getProductVariant().getId()
                            ));
        }

        // 7. Group variants by product ID
        Map<Long, List<ProductVariantEntity>> variantsByProductId =
                variants.stream()
                        .collect(Collectors.groupingBy(
                                variant ->
                                        variant.getProductEntity().getId()
                        ));

        // 8. Assemble existing response DTOs
        return products.stream()
                .collect(Collectors.toMap(
                        ProductEntity::getId,
                        product -> {
                            Long productId = product.getId();

                            List<ProductSpecificationResponse> specifications =
                                    specificationsByProductId
                                            .getOrDefault(
                                                    productId,
                                                    Collections.emptyList()
                                            )
                                            .stream()
                                            .map(this::toSpecificationResponse)
                                            .toList();

                            List<ProductImageResponse> images =
                                    imagesByProductId
                                            .getOrDefault(
                                                    productId,
                                                    Collections.emptyList()
                                            )
                                            .stream()
                                            .map(this::toImageResponse)
                                            .toList();

                            List<ProductVariantResponse> variantResponses =
                                    variantsByProductId
                                            .getOrDefault(
                                                    productId,
                                                    Collections.emptyList()
                                            )
                                            .stream()
                                            .map(variant -> {
                                                Long variantId = variant.getId();

                                                List<VariantAttributeResponse>
                                                        attributes =
                                                        attributesByVariantId
                                                                .getOrDefault(
                                                                        variantId,
                                                                        Collections.emptyList()
                                                                )
                                                                .stream()
                                                                .map(this::toAttributeResponse)
                                                                .toList();

                                                ProductVariantPriceResponse price =
                                                        Optional.ofNullable(
                                                                        pricesByVariantId.get(
                                                                                variantId
                                                                        )
                                                                )
                                                                .map(this::toPriceResponse)
                                                                .orElse(null);

                                                List<InventoryItem> inventoryItems =
                                                        inventoryByVariantId
                                                                .getOrDefault(
                                                                        variantId,
                                                                        Collections.emptyList()
                                                                );

                                                int totalAvailableStock =
                                                        inventoryItems.stream()
                                                                .mapToInt(item ->
                                                                        item.getAvailableStock() == null
                                                                                ? 0
                                                                                : item.getAvailableStock()
                                                                )
                                                                .sum();

                                                return ProductVariantResponse
                                                        .builder()
                                                        .id(variantId)
                                                        .productId(productId)
                                                        .sku(variant.getSku())
                                                        .status(variant.getStatus())
                                                        .attributes(attributes)
                                                        .price(price)
                                                        .stockStatus(
                                                                calculateStockStatus(
                                                                        totalAvailableStock
                                                                )
                                                        )
                                                        .available(
                                                                totalAvailableStock > 0
                                                        )
                                                        .build();
                                            })
                                            .toList();

                            return ProductDetailsResponse.builder()
                                    .id(product.getId())
                                    .name(product.getName())
                                    .slug(product.getSlug())
                                    .description(product.getDescription())
                                    .categoryId(
                                            product.getCategory().getId()
                                    )
                                    .subCategoryId(
                                            product.getSubCategory().getId()
                                    )
                                    .brandId(
                                            product.getBrand() != null
                                                    ? product.getBrand().getId()
                                                    : null
                                    )
                                    .specifications(specifications)
                                    .images(images)
                                    .variants(variantResponses)
                                    .build();
                        }
                ));
    }
    // =========================================================
    // GET PRODUCTS BY CATEGORY
    // =========================================================



    @Override
    @Transactional(readOnly = true)
    public Page<ProductResponse> getProductsByCategory(
            Long categoryId,
            Pageable pageable
    ) {
        long start = System.nanoTime();

        getActiveCategory(categoryId);

        long categoryCheckEnd = System.nanoTime();

        Page<ProductResponse> result =
                productRepository.findCategoryProductResponses(
                        categoryId,
                        ProductStatus.ACTIVE,
                        pageable
                );

        long queryEnd = System.nanoTime();

        System.out.println(
                "CATEGORY_VALIDATION_MS: " +
                        (categoryCheckEnd - start) / 1_000_000.0
        );

        System.out.println(
                "PRODUCT_QUERY_AND_MAPPING_MS: " +
                        (queryEnd - categoryCheckEnd) / 1_000_000.0
        );

        System.out.println(
                "TOTAL_SERVICE_MS: " +
                        (queryEnd - start) / 1_000_000.0
        );

        return result;
    }



    // =========================================================
    // GET PRODUCTS BY SUBCATEGORY
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public Page<ProductResponse> getProductsBySubCategory(
            Long subCategoryId,
            Pageable pageable
    ) {

        getActiveSubCategory(subCategoryId);

        return productRepository
                .findAllBySubCategoryIdAndStatus(
                        subCategoryId,
                        ProductStatus.ACTIVE,
                        pageable
                )
                .map(productMapper::toResponse);
    }


    // =========================================================
    // GET PRODUCTS BY BRAND
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public Page<ProductResponse> getProductsByBrand(
            Long brandId,
            Pageable pageable
    ) {

        if (brandId == null) {
            throw new ProductValidationException("Brand id is required");
        }

        getActiveBrand(brandId);

        return productRepository
                .findAllByBrandIdAndStatus(
                        brandId,
                        ProductStatus.ACTIVE,
                        pageable
                )
                .map(productMapper::toResponse);
    }


    // =========================================================
    // SEARCH PRODUCTS BY KEYWORD
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public List<ProductResponse> searchProducts(
            String keyword
    ) {

        String normalizedKeyword =
                keyword == null
                        ? ""
                        : keyword.trim();

        if (normalizedKeyword.isEmpty()) {

            throw new ProductValidationException(
                    "Search keyword is required"
            );
        }

        return productRepository
                .searchByKeyword(
                        normalizedKeyword,
                        ProductStatus.ACTIVE
                )
                .stream()
                .map(productMapper::toResponse)
                .toList();
    }


    // =========================================================
    // UPDATE PRODUCT
    // =========================================================

    @Override
    public ProductResponse updateProduct(
            Long id,
            ProductUpdateRequest request
    ) {
        ProductEntity product =
                productRepository.findById(id)
                        .orElseThrow(() ->
                                new ProductNotFoundException(
                                        "Product not found with id: " + id));

        // Check ownership before applying any changes.
        validateProductOwnership(product);

        // Preserve the original owner; update DTO cannot transfer ownership.
        Seller existingSeller = product.getSeller();

        validateSlugForUpdate(request.getSlug(), id);

        Category category =
                getActiveCategory(request.getCategoryId());

        SubCategory subCategory =
                getActiveSubCategory(request.getSubCategoryId());

        validateSubCategoryBelongsToCategory(
                subCategory,
                category
        );

        Brand brand =
                getActiveBrand(request.getBrandId());

        productMapper.updateEntity(request, product);

        product.setCategory(category);
        product.setSubCategory(subCategory);
        product.setBrand(brand);
        product.setSeller(existingSeller);

        ProductEntity updated =
                productRepository.save(product);

        return productMapper.toResponse(updated);
    }


    // =========================================================
    // DEACTIVATE PRODUCT
    // =========================================================

    @Override
    public void deactivateProduct(Long id) {

        ProductEntity product =
                productRepository
                        .findById(id)
                        .orElseThrow(
                                () ->
                                        new ProductNotFoundException(
                                                "Product not found with id: "
                                                        + id
                                        )
                        );

        validateProductOwnership(product);

        if (product.getStatus() ==
                ProductStatus.INACTIVE) {

            throw new ProductAlreadyInactiveException(
                    "Product is already inactive"
            );
        }

        product.setStatus(
                ProductStatus.INACTIVE
        );
    }


    // =========================================================
    // RESTORE PRODUCT
    // =========================================================

    @Override
    public ProductResponse restoreProduct(Long id) {

        ProductEntity product =
                productRepository
                        .findById(id)
                        .orElseThrow(
                                () ->
                                        new ProductNotFoundException(
                                                "Product not found with id: "
                                                        + id
                                        )
                        );

        validateProductOwnership(product);

        if (product.getStatus() ==
                ProductStatus.ACTIVE) {

            throw new ProductAlreadyActiveException(
                    "Product is already active"
            );
        }

        validateRestoreDependencies(product);

        product.setStatus(
                ProductStatus.ACTIVE
        );

        return productMapper.toResponse(product);
    }


    // =========================================================
    // GET ACTIVE PRODUCT BY ID
    // =========================================================

    private ProductEntity getActiveProductById(
            Long id
    ) {

        if (id == null) {

            throw new ProductValidationException(
                    "Product id is required"
            );
        }

        return productRepository
                .findByIdAndStatus(
                        id,
                        ProductStatus.ACTIVE
                )
                .orElseThrow(
                        () ->
                                new ProductNotFoundException(
                                        "Product not found with id: "
                                                + id
                                )
                );
    }


    // =========================================================
    // GET ACTIVE PRODUCT BY SLUG
    // =========================================================

    private ProductEntity getActiveProductBySlug(
            String slug
    ) {

        if (slug == null ||
                slug.isBlank()) {

            throw new ProductValidationException(
                    "Product slug is required"
            );
        }

        String normalizedSlug =
                slug.trim();

        return productRepository
                .findBySlugIgnoreCaseAndStatus(
                        normalizedSlug,
                        ProductStatus.ACTIVE
                )
                .orElseThrow(
                        () ->
                                new ProductNotFoundException(
                                        "Product not found with slug: "
                                                + normalizedSlug
                                )
                );
    }


    // =========================================================
    // VALIDATE CREATE SLUG
    // =========================================================

    private void validateSlugForCreate(
            String slug
    ) {

        if (slug == null ||
                slug.isBlank()) {

            throw new ProductValidationException(
                    "Product slug is required"
            );
        }

        String normalizedSlug =
                slug.trim();

        if (productRepository
                .existsBySlugIgnoreCase(
                        normalizedSlug
                )) {

            throw new ProductSlugAlreadyExistsException(
                    "Product slug already exists: "
                            + normalizedSlug
            );
        }
    }


    // =========================================================
    // VALIDATE UPDATE SLUG
    // =========================================================

    private void validateSlugForUpdate(
            String slug,
            Long productId
    ) {

        if (slug == null ||
                slug.isBlank()) {

            throw new ProductValidationException(
                    "Product slug is required"
            );
        }

        String normalizedSlug =
                slug.trim();

        if (productRepository
                .existsBySlugIgnoreCaseAndIdNot(
                        normalizedSlug,
                        productId
                )) {

            throw new ProductSlugAlreadyExistsException(
                    "Product slug already exists: "
                            + normalizedSlug
            );
        }
    }


    // =========================================================
    // GET ACTIVE CATEGORY
    // =========================================================

    private Category getActiveCategory(Long categoryId) {

        if (categoryId == null) {
            throw new ProductValidationException(
                    "Category id is required"
            );
        }

        long start = System.nanoTime();

        var result = categoryRepository.findByIdAndStatus(
                categoryId,
                CategoryStatus.ACTIVE
        );

        long repositoryEnd = System.nanoTime();

        System.out.println(
                "CATEGORY_REPOSITORY_MS: "
                        + (repositoryEnd - start) / 1_000_000.0
        );

        long mappingStart = System.nanoTime();

        Category category = result.orElseThrow(
                () -> new ProductValidationException(
                        "Active category not found with id: " + categoryId
                )
        );

        long mappingEnd = System.nanoTime();

        System.out.println(
                "CATEGORY_RESULT_PROCESSING_MS: "
                        + (mappingEnd - mappingStart) / 1_000_000.0
        );

        return category;
    }
    // =========================================================
    // GET ACTIVE SUBCATEGORY
    // =========================================================

    private SubCategory getActiveSubCategory(
            Long subCategoryId
    ) {

        if (subCategoryId == null) {

            throw new ProductValidationException(
                    "SubCategory id is required"
            );
        }

        return subCategoryRepository
                .findByIdAndStatus(
                        subCategoryId,
                        SubCategoryStatus.ACTIVE
                )
                .orElseThrow(
                        () ->
                                new ProductValidationException(
                                        "Active subcategory not found with id: "
                                                + subCategoryId
                                )
                );
    }


    // =========================================================
    // GET ACTIVE BRAND
    // =========================================================

    private Brand getActiveBrand(
            Long brandId
    ) {

        // Brand is optional for a product. Return null when no brand is selected.
        if (brandId == null) {
            return null;
        }

        return brandRepository
                .findByIdAndStatus(
                        brandId,
                        BrandStatus.ACTIVE
                )
                .orElseThrow(
                        () ->
                                new ProductValidationException(
                                        "Active brand not found with id: "
                                                + brandId
                                )
                );
    }


    // =========================================================
    // SUBCATEGORY → CATEGORY VALIDATION
    // =========================================================

    private void validateSubCategoryBelongsToCategory(
            SubCategory subCategory,
            Category category
    ) {

        if (subCategory == null ||
                category == null) {

            throw new ProductValidationException(
                    "Category and subcategory are required"
            );
        }

        if (subCategory.getCategory() == null ||
                subCategory.getCategory().getId() == null ||
                !subCategory.getCategory()
                        .getId()
                        .equals(category.getId())) {

            throw new ProductValidationException(
                    "SubCategory does not belong to the selected category"
            );
        }
    }


    // =========================================================
    // RESTORE DEPENDENCIES
    // =========================================================

    private void validateRestoreDependencies(
            ProductEntity product
    ) {

        if (product.getCategory() == null) {

            throw new ProductValidationException(
                    "Product category is missing"
            );
        }

        if (product.getSubCategory() == null) {

            throw new ProductValidationException(
                    "Product subcategory is missing"
            );
        }

        getActiveCategory(
                product.getCategory().getId()
        );

        getActiveSubCategory(
                product.getSubCategory().getId()
        );

        // Validate the brand only when this product has one assigned.
        if (product.getBrand() != null) {
            getActiveBrand(product.getBrand().getId());
        }

        validateSubCategoryBelongsToCategory(
                product.getSubCategory(),
                product.getCategory()
        );
    }

}


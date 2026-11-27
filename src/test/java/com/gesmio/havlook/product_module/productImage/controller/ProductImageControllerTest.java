package com.gesmio.havlook.product_module.productImage.controller;

import com.gesmio.havlook.auth_module.config.SecurityConfig;
import com.gesmio.havlook.auth_module.util.JwtAuthenticationFilter;
import com.gesmio.havlook.auth_module.util.JwtUtil;
import com.gesmio.havlook.product_module.productImage.dto.ProductImageResponse;
import com.gesmio.havlook.product_module.productImage.service.ProductImageService;
import com.gesmio.havlook.user_module.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ProductImageController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
@ActiveProfiles("test")
@DisplayName("ProductImageController RBAC Tests")
class ProductImageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductImageService productImageService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    @DisplayName("POST /api/v1/product-images -> 403 for CUSTOMER (SECURITY FIX verify)")
    @WithMockUser(roles = "CUSTOMER")
    void createImage_asCustomer_shouldReturn403() throws Exception {
        mockMvc.perform(post("/api/v1/product-images")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":1,\"imageUrl\":\"https://example.com/img.png\",\"isPrimary\":true,\"displayOrder\":0}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PUT /api/v1/product-images/1 -> 403 for CUSTOMER (SECURITY FIX verify)")
    @WithMockUser(roles = "CUSTOMER")
    void updateImage_asCustomer_shouldReturn403() throws Exception {
        mockMvc.perform(put("/api/v1/product-images/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"imageUrl\":\"https://example.com/img.png\",\"isPrimary\":true,\"displayOrder\":0}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DELETE /api/v1/product-images/1 -> 403 for CUSTOMER (SECURITY FIX verify)")
    @WithMockUser(roles = "CUSTOMER")
    void deleteImage_asCustomer_shouldReturn403() throws Exception {
        mockMvc.perform(delete("/api/v1/product-images/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DELETE /api/v1/product-images/1 -> 200 for SELLER")
    @WithMockUser(roles = "SELLER")
    void deleteImage_asSeller_shouldReturn200() throws Exception {
        doNothing().when(productImageService).deleteImage(1L);

        mockMvc.perform(delete("/api/v1/product-images/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("GET /api/v1/product-images/product/1 -> 200 for authenticated user")
    @WithMockUser(roles = "CUSTOMER")
    void getImagesByProduct_asCustomer_shouldReturn200() throws Exception {
        when(productImageService.getImagesByProductId(1L)).thenReturn(List.of(new ProductImageResponse()));

        mockMvc.perform(get("/api/v1/product-images/product/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}

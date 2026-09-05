package com.qrserve.menu.service;

import com.qrserve.menu.entity.ProductEntity;
import com.qrserve.menu.repository.CategoryRepository;
import com.qrserve.menu.repository.MenuRepository;
import com.qrserve.menu.repository.MenuTemplateRepository;
import com.qrserve.menu.repository.ProductRepository;
import com.qrserve.menu.storage.MediaStorageService;
import com.qrserve.shared.exceptions.BusinessException;
import com.qrserve.shared.exceptions.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** updateProductImage: validation and delegation to MediaStorageService. */
class MenuServiceMediaTest {

    private ProductRepository productRepository;
    private MediaStorageService mediaStorageService;
    private MenuService service;
    private static final Long PRODUCT_ID = 9L;
    private static final UUID MERCHANT = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        productRepository = mock(ProductRepository.class);
        mediaStorageService = mock(MediaStorageService.class);
        service = new MenuService(mock(CategoryRepository.class), productRepository, mock(MenuRepository.class),
                mock(RestTemplate.class), mock(PlatformTransactionManager.class), mediaStorageService,
                mock(MenuTemplateRepository.class));
    }

    private static ProductEntity existingProduct() {
        return ProductEntity.builder().id(PRODUCT_ID).merchantId(MERCHANT).price(BigDecimal.TEN)
                .image("https://images.unsplash.com/old-preset.jpg").build();
    }

    @Test
    void uploadsTheFileAndUpdatesTheProductsImageUrl() throws Exception {
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(existingProduct()));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(mediaStorageService.store(anyString(), any(byte[].class), anyString()))
                .thenReturn("/api/media/products/9-abc.jpg");
        MultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "bytes".getBytes());

        ProductEntity result = service.updateProductImage(PRODUCT_ID, file);

        assertEquals("/api/media/products/9-abc.jpg", result.getImage());
        verify(mediaStorageService).store(argThat(key -> key.startsWith("products/" + PRODUCT_ID + "-") && key.endsWith(".jpg")),
                aryEq("bytes".getBytes()), eq("image/jpeg"));
    }

    @Test
    void picksTheExtensionFromContentType() throws Exception {
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(existingProduct()));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(mediaStorageService.store(anyString(), any(byte[].class), anyString())).thenReturn("/api/media/products/9-abc.png");
        MultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", "bytes".getBytes());

        service.updateProductImage(PRODUCT_ID, file);

        verify(mediaStorageService).store(argThat(key -> key.endsWith(".png")), any(), anyString());
    }

    @Test
    void rejectsAnUnsupportedContentType() {
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(existingProduct()));
        MultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", "bytes".getBytes());

        assertThrows(BusinessException.class, () -> service.updateProductImage(PRODUCT_ID, file));
        verify(mediaStorageService, never()).store(anyString(), any(), anyString());
    }

    @Test
    void rejectsAnEmptyFile() {
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(existingProduct()));
        MultipartFile file = new MockMultipartFile("file", "empty.jpg", "image/jpeg", new byte[0]);

        assertThrows(BusinessException.class, () -> service.updateProductImage(PRODUCT_ID, file));
    }

    @Test
    void throwsResourceNotFoundForAMissingProduct() {
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.empty());
        MultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "bytes".getBytes());

        assertThrows(ResourceNotFoundException.class, () -> service.updateProductImage(PRODUCT_ID, file));
    }
}

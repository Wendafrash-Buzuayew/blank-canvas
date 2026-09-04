package com.qrserve.merchant.service;

import com.qrserve.merchant.entity.MerchantEntity;
import com.qrserve.merchant.repository.MerchantRepository;
import com.qrserve.merchant.storage.MediaStorageService;
import com.qrserve.shared.exceptions.BusinessException;
import com.qrserve.shared.exceptions.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** updateMerchantLogo: validation and delegation to MediaStorageService. */
class MerchantServiceLogoTest {

    private MerchantRepository merchantRepository;
    private MediaStorageService mediaStorageService;
    private MerchantService service;
    private static final UUID MERCHANT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        merchantRepository = mock(MerchantRepository.class);
        mediaStorageService = mock(MediaStorageService.class);
        service = new MerchantService(merchantRepository, mock(TenantCacheInvalidator.class), mediaStorageService);
    }

    private static MerchantEntity existingMerchant() {
        return MerchantEntity.builder().id(MERCHANT_ID).name("Sunrise Coffee").slug("sunrise-coffee")
                .phone("0700000000").city("Nairobi").address("Main St").category("Cafe")
                .logoUrl("https://images.unsplash.com/old-logo.jpg").build();
    }

    @Test
    void uploadsTheFileAndUpdatesTheMerchantsLogoUrl() throws Exception {
        when(merchantRepository.findById(MERCHANT_ID)).thenReturn(Optional.of(existingMerchant()));
        when(merchantRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(mediaStorageService.store(anyString(), any(byte[].class), anyString()))
                .thenReturn("/api/media/branding/" + MERCHANT_ID + "-abc.png");
        MultipartFile file = new MockMultipartFile("file", "logo.png", "image/png", "bytes".getBytes());

        MerchantEntity result = service.updateMerchantLogo(MERCHANT_ID, file);

        assertEquals("/api/media/branding/" + MERCHANT_ID + "-abc.png", result.getLogoUrl());
        verify(mediaStorageService).store(argThat(key -> key.startsWith("branding/" + MERCHANT_ID + "-") && key.endsWith(".png")),
                aryEq("bytes".getBytes()), eq("image/png"));
    }

    @Test
    void rejectsAnUnsupportedContentType() {
        when(merchantRepository.findById(MERCHANT_ID)).thenReturn(Optional.of(existingMerchant()));
        MultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", "bytes".getBytes());

        assertThrows(BusinessException.class, () -> service.updateMerchantLogo(MERCHANT_ID, file));
        verify(mediaStorageService, never()).store(anyString(), any(), anyString());
    }

    @Test
    void rejectsAnEmptyFile() {
        when(merchantRepository.findById(MERCHANT_ID)).thenReturn(Optional.of(existingMerchant()));
        MultipartFile file = new MockMultipartFile("file", "empty.jpg", "image/jpeg", new byte[0]);

        assertThrows(BusinessException.class, () -> service.updateMerchantLogo(MERCHANT_ID, file));
    }

    @Test
    void throwsResourceNotFoundForAMissingMerchant() {
        when(merchantRepository.findById(MERCHANT_ID)).thenReturn(Optional.empty());
        MultipartFile file = new MockMultipartFile("file", "logo.jpg", "image/jpeg", "bytes".getBytes());

        assertThrows(ResourceNotFoundException.class, () -> service.updateMerchantLogo(MERCHANT_ID, file));
    }
}

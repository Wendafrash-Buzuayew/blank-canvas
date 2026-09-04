package com.qrserve.merchant.service;

import com.qrserve.merchant.dto.DigitalMenuResolutionResponse;
import com.qrserve.merchant.entity.BranchEntity;
import com.qrserve.merchant.entity.MerchantEntity;
import com.qrserve.shared.exceptions.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PublicDigitalMenuResolutionServiceTest {

    private MerchantService merchantService;
    private BranchService branchService;
    private PublicDigitalMenuResolutionService service;
    private static final UUID MERCHANT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        merchantService = mock(MerchantService.class);
        branchService = mock(BranchService.class);
        service = new PublicDigitalMenuResolutionService(merchantService, branchService);
    }

    @Test
    void resolvePrimaryReturnsTheMerchantsPrimaryBranch() {
        when(merchantService.getMerchantBySlug("sunrise")).thenReturn(
                MerchantEntity.builder().id(MERCHANT_ID).slug("sunrise").build());
        when(branchService.getBranchesByMerchant(MERCHANT_ID)).thenReturn(List.of(
                BranchEntity.builder().id(1L).slug("annex").isPrimary(false).name("Annex").build(),
                BranchEntity.builder().id(2L).slug("main").isPrimary(true).name("Main").build()));

        DigitalMenuResolutionResponse response = service.resolvePrimary("sunrise");

        assertEquals("main", response.getBranchSlug());
        assertEquals(2L, response.getBranchId());
    }

    @Test
    void resolvePrimaryRefusesAMerchantWithNoPrimaryBranch() {
        when(merchantService.getMerchantBySlug("sunrise")).thenReturn(
                MerchantEntity.builder().id(MERCHANT_ID).slug("sunrise").build());
        when(branchService.getBranchesByMerchant(MERCHANT_ID)).thenReturn(List.of());

        assertThrows(ResourceNotFoundException.class, () -> service.resolvePrimary("sunrise"));
    }

    @Test
    void resolveBranchReturnsTheNamedBranch() {
        when(merchantService.getMerchantBySlug("sunrise")).thenReturn(
                MerchantEntity.builder().id(MERCHANT_ID).slug("sunrise").build());
        when(branchService.getBranchByMerchantAndSlug(MERCHANT_ID, "annex")).thenReturn(
                BranchEntity.builder().id(1L).slug("annex").name("Annex").build());

        DigitalMenuResolutionResponse response = service.resolveBranch("sunrise", "annex");

        assertEquals(1L, response.getBranchId());
        assertEquals("Annex", response.getBranchName());
    }
}

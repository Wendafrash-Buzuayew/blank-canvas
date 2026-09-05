package com.qrserve.merchant.service;

import com.qrserve.merchant.dto.CreateBranchRequest;
import com.qrserve.merchant.dto.UpdateBranchRequest;
import com.qrserve.merchant.entity.BranchEntity;
import com.qrserve.merchant.repository.BranchRepository;
import com.qrserve.shared.exceptions.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BranchServiceTest {

    private BranchRepository repository;
    private BranchService service;
    private static final UUID MERCHANT = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        repository = mock(BranchRepository.class);
        service = new BranchService(repository);
    }

    @Test
    void firstBranchForAMerchantIsAutoPrimary() {
        when(repository.findByMerchantIdAndSlug(MERCHANT, "main")).thenReturn(Optional.empty());
        when(repository.findByMerchantId(MERCHANT)).thenReturn(List.of());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BranchEntity created = service.createBranch(CreateBranchRequest.builder()
                .merchantId(MERCHANT).name("Main").slug("main")
                .phone("0700000000").address("Addis Ababa").build());

        assertTrue(created.isPrimary(), "the merchant's first branch has nothing to be secondary to");
    }

    @Test
    void secondBranchIsNotAutoPrimary() {
        when(repository.findByMerchantIdAndSlug(MERCHANT, "annex")).thenReturn(Optional.empty());
        when(repository.findByMerchantId(MERCHANT)).thenReturn(
                List.of(BranchEntity.builder().id(1L).merchantId(MERCHANT).isPrimary(true).build()));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BranchEntity created = service.createBranch(CreateBranchRequest.builder()
                .merchantId(MERCHANT).name("Annex").slug("annex")
                .phone("0700000001").address("Bole").build());

        assertFalse(created.isPrimary());
    }

    @Test
    void setPrimaryBranchSwapsTheFlag() {
        BranchEntity oldPrimary = BranchEntity.builder().id(1L).merchantId(MERCHANT).isPrimary(true).build();
        BranchEntity newPrimary = BranchEntity.builder().id(2L).merchantId(MERCHANT).isPrimary(false).build();
        when(repository.findByMerchantId(MERCHANT)).thenReturn(List.of(oldPrimary, newPrimary));
        when(repository.findById(2L)).thenReturn(Optional.of(newPrimary));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BranchEntity result = service.setPrimaryBranch(MERCHANT, 2L);

        assertTrue(result.isPrimary());
        assertFalse(oldPrimary.isPrimary(), "exactly one primary branch per merchant");
        verify(repository, times(2)).save(any());
    }

    @Test
    void setPrimaryBranchRefusesABranchFromAnotherMerchant() {
        BranchEntity foreign = BranchEntity.builder().id(9L).merchantId(UUID.randomUUID()).build();
        when(repository.findById(9L)).thenReturn(Optional.of(foreign));

        assertThrows(ResourceNotFoundException.class, () -> service.setPrimaryBranch(MERCHANT, 9L));
    }

    @Test
    void updateBranchChangesNamePhoneAndAddressButNeverTheSlug() {
        BranchEntity existing = BranchEntity.builder().id(1L).merchantId(MERCHANT).name("Old").slug("old-slug").phone("0700000000").address("Old Address").build();
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BranchEntity updated = service.updateBranch(1L, UpdateBranchRequest.builder()
                .name("New Name").phone("0711111111").address("New Address").build());

        assertEquals("New Name", updated.getName());
        assertEquals("0711111111", updated.getPhone());
        assertEquals("New Address", updated.getAddress());
        assertEquals("old-slug", updated.getSlug(), "the slug is a permanent path segment in printed public menu URLs");
    }

    @Test
    void updateBranchThrowsForAMissingBranch() {
        when(repository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.updateBranch(404L, UpdateBranchRequest.builder().name("X").phone("Y").build()));
    }

    @Test
    void deleteBranchRemovesIt() {
        BranchEntity existing = BranchEntity.builder().id(1L).merchantId(MERCHANT).build();
        when(repository.findById(1L)).thenReturn(Optional.of(existing));

        service.deleteBranch(1L);

        verify(repository).delete(existing);
    }

    @Test
    void deleteBranchThrowsForAMissingBranch() {
        when(repository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.deleteBranch(404L));
    }
}

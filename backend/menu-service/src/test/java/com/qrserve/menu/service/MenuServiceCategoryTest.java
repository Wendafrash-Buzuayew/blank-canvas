package com.qrserve.menu.service;

import com.qrserve.menu.dto.CreateCategoryRequest;
import com.qrserve.menu.entity.CategoryEntity;
import com.qrserve.menu.entity.MenuEntity;
import com.qrserve.menu.repository.CategoryRepository;
import com.qrserve.menu.repository.MenuRepository;
import com.qrserve.menu.repository.MenuTemplateRepository;
import com.qrserve.menu.repository.ProductRepository;
import com.qrserve.menu.storage.MediaStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class MenuServiceCategoryTest {

    private CategoryRepository categoryRepository;
    private ProductRepository productRepository;
    private MenuRepository menuRepository;
    private RestTemplate restTemplate;
    private MenuService service;
    private static final Long BRANCH = 5L;
    private static final UUID MERCHANT = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        categoryRepository = mock(CategoryRepository.class);
        productRepository = mock(ProductRepository.class);
        menuRepository = mock(MenuRepository.class);
        restTemplate = mock(RestTemplate.class);
        service = new MenuService(categoryRepository, productRepository, menuRepository, restTemplate,
                mock(PlatformTransactionManager.class), mock(MediaStorageService.class), mock(MenuTemplateRepository.class));
    }

    /** Stubs the merchant-service branch-ownership lookup used by fetchBranchMerchantId. */
    @SuppressWarnings("unchecked")
    private void stubBranchOwner(Long branchId, UUID ownerMerchantId) {
        when(restTemplate.exchange(
                any(String.class), eq(HttpMethod.GET), any(HttpEntity.class), any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(Map.of("merchantId", ownerMerchantId.toString())));
    }

    @Test
    void getOrCreateMenuForBranchCreatesADraftMenuOnFirstUse() {
        when(menuRepository.findByBranchId(BRANCH)).thenReturn(Optional.empty());
        when(menuRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MenuEntity menu = service.getOrCreateMenuForBranch(BRANCH, MERCHANT);

        assertEquals(BRANCH, menu.getBranchId());
        assertEquals(MERCHANT, menu.getMerchantId());
        assertEquals(MenuEntity.Status.DRAFT, menu.getStatus());
    }

    @Test
    void getOrCreateMenuForBranchIsIdempotent() {
        MenuEntity existing = MenuEntity.builder().branchId(BRANCH).merchantId(MERCHANT)
                .status(MenuEntity.Status.PUBLISHED).build();
        when(menuRepository.findByBranchId(BRANCH)).thenReturn(Optional.of(existing));

        MenuEntity menu = service.getOrCreateMenuForBranch(BRANCH, MERCHANT);

        assertSame(existing, menu);
        verify(menuRepository, never()).save(any());
    }

    @Test
    void createCategoryAttachesItToTheBranchsMenu() {
        MenuEntity menu = MenuEntity.builder().id(UUID.randomUUID()).branchId(BRANCH).merchantId(MERCHANT)
                .status(MenuEntity.Status.DRAFT).build();
        when(menuRepository.findByBranchId(BRANCH)).thenReturn(Optional.of(menu));
        when(categoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        stubBranchOwner(BRANCH, MERCHANT);

        CategoryEntity category = service.createCategory(CreateCategoryRequest.builder()
                .branchId(BRANCH).merchantId(MERCHANT).name("Drinks").build(), MERCHANT);

        assertEquals(menu.getId(), category.getMenuId());
        assertEquals(MERCHANT, category.getMerchantId());
    }

    @Test
    void createCategoryRejectsABranchThatBelongsToAnotherMerchant() {
        UUID otherMerchant = UUID.randomUUID();
        stubBranchOwner(BRANCH, otherMerchant);

        CreateCategoryRequest request = CreateCategoryRequest.builder()
                .branchId(BRANCH).merchantId(MERCHANT).name("Drinks").build();

        assertThrows(AccessDeniedException.class, () -> service.createCategory(request, MERCHANT));
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void createCategorySucceedsWhenCallerOwnsTheBranch() {
        MenuEntity menu = MenuEntity.builder().id(UUID.randomUUID()).branchId(BRANCH).merchantId(MERCHANT)
                .status(MenuEntity.Status.DRAFT).build();
        when(menuRepository.findByBranchId(BRANCH)).thenReturn(Optional.of(menu));
        when(categoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        stubBranchOwner(BRANCH, MERCHANT);

        CategoryEntity category = service.createCategory(CreateCategoryRequest.builder()
                .branchId(BRANCH).merchantId(MERCHANT).name("Drinks").build(), MERCHANT);

        assertEquals(MERCHANT, category.getMerchantId());
    }
}

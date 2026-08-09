package com.qrserve.merchant.service;

import com.qrserve.merchant.dto.CreateMerchantRequest;
import com.qrserve.merchant.entity.MerchantEntity;
import com.qrserve.merchant.repository.MerchantRepository;
import com.qrserve.shared.exceptions.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MerchantService {

    private final MerchantRepository merchantRepository;

    @Transactional
    public MerchantEntity createMerchant(CreateMerchantRequest request) {
        String slug = request.getName().toLowerCase().replaceAll("[^a-z0-9]", "-");
        
        MerchantEntity merchant = MerchantEntity.builder()
                .name(request.getName())
                .slug(slug)
                .phone(request.getPhone())
                .city(request.getCity())
                .address(request.getAddress())
                .category(request.getCategory())
                .build();

        return merchantRepository.save(merchant);
    }

    public MerchantEntity getMerchant(UUID id) {
        return merchantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Merchant not found with ID: " + id));
    }

    public MerchantEntity getMerchantBySlug(String slug) {
        return merchantRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Merchant not found with slug: " + slug));
    }

    @Transactional
    public MerchantEntity updateMerchant(UUID id, CreateMerchantRequest request) {
        MerchantEntity merchant = getMerchant(id);
        merchant.setName(request.getName());
        merchant.setPhone(request.getPhone());
        merchant.setCity(request.getCity());
        merchant.setAddress(request.getAddress());
        merchant.setCategory(request.getCategory());
        return merchantRepository.save(merchant);
    }

    public List<MerchantEntity> getAllMerchants() {
        return merchantRepository.findAll();
    }
}

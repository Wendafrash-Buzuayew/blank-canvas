package com.qrserve.merchant.service;

import com.qrserve.merchant.dto.CreateMerchantRequest;
import com.qrserve.merchant.entity.MerchantEntity;
import com.qrserve.merchant.repository.MerchantRepository;
import com.qrserve.merchant.storage.MediaStorageService;
import com.qrserve.shared.common.Slugs;
import com.qrserve.shared.exceptions.BusinessException;
import com.qrserve.shared.exceptions.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MerchantService {

    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final MerchantRepository merchantRepository;
    private final TenantCacheInvalidator tenantCacheInvalidator;
    private final MediaStorageService mediaStorageService;

    /**
     * Highest suffix tried before giving up. Ten near-identical names is a strong
     * signal the owner should pick a distinctive slug rather than accept
     * "sunrise-11" as their public address.
     */
    private static final int MAX_SLUG_SUFFIX = 10;

    @Transactional
    public MerchantEntity createMerchant(CreateMerchantRequest request) {
        // Throws IllegalArgumentException -> 400 with a message naming the problem.
        String requested = Slugs.toDnsLabel(request.getSlug());
        String slug = firstAvailableSlug(requested);

        MerchantEntity merchant = MerchantEntity.builder()
                .name(request.getName())
                .slug(slug)
                .phone(request.getPhone())
                .city(request.getCity())
                .address(request.getAddress())
                .category(request.getCategory())
                .shortCode(request.getShortCode())
                .build();

        MerchantEntity saved = merchantRepository.save(merchant);

        // A bot probing this subdomain before the tenant existed leaves a negative
        // cache entry that would otherwise 404 the new owner's first visit to
        // their own site.
        tenantCacheInvalidator.invalidate(saved.getSlug());
        return saved;
    }

    /**
     * {@code sunrise}, then {@code sunrise-2}, {@code sunrise-3}, ...
     *
     * <p>A deterministic suffix rather than a raw constraint violation: the slug is
     * globally unique because it is a hostname, and two unrelated businesses
     * choosing the same name is ordinary, not exceptional.
     */
    private String firstAvailableSlug(String requested) {
        if (merchantRepository.findBySlug(requested).isEmpty()) {
            return requested;
        }
        for (int suffix = 2; suffix <= MAX_SLUG_SUFFIX; suffix++) {
            String candidate = requested + "-" + suffix;
            // Re-check the length: the suffix can push a 39-character slug past the cap.
            if (candidate.length() <= Slugs.DNS_LABEL_MAX_LENGTH
                    && merchantRepository.findBySlug(candidate).isEmpty()) {
                return candidate;
            }
        }
        throw new BusinessException(
                "The slug '" + requested + "' and its variants are all taken. Please choose a different one.");
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

        // The slug is this tenant's hostname and is printed onto physical QR
        // stands. Changing it would break every printed code and every bookmarked
        // staff URL, and the alias table that would let old hostnames keep
        // resolving is deliberately not built yet. Refuse clearly rather than
        // ignore the field, which is what happened before: the caller's new slug
        // was silently dropped and the response looked like a success.
        if (request.getSlug() != null && !request.getSlug().isBlank()) {
            String requested = Slugs.toDnsLabel(request.getSlug());
            if (!requested.equals(merchant.getSlug())) {
                throw new BusinessException(
                        "slug is permanent and cannot be changed (current: '" + merchant.getSlug() + "')");
            }
        }

        merchant.setName(request.getName());
        merchant.setPhone(request.getPhone());
        merchant.setCity(request.getCity());
        merchant.setAddress(request.getAddress());
        merchant.setCategory(request.getCategory());

        // No cache invalidation here: the slug is the only field the gateway caches
        // and it is immutable (see the guard above). If renames are ever allowed,
        // this is one of the two places that has to change.
        return merchantRepository.save(merchant);
    }

    public List<MerchantEntity> getAllMerchants() {
        return merchantRepository.findAll();
    }

    /**
     * Uploads a real logo/branding image, replacing whatever was in
     * logoUrl. The key embeds the merchant id and a random suffix so
     * re-uploads never collide with (or silently overwrite) a previous logo
     * still cached by a client.
     */
    @Transactional
    public MerchantEntity updateMerchantLogo(UUID id, MultipartFile file) {
        MerchantEntity merchant = getMerchant(id);
        validateImageFile(file);

        String extension = extensionFor(file.getContentType());
        String key = "branding/" + id + "-" + UUID.randomUUID() + extension;
        String url;
        try {
            url = mediaStorageService.store(key, file.getBytes(), file.getContentType());
        } catch (IOException e) {
            throw new BusinessException("Failed to read uploaded file", e);
        }

        merchant.setLogoUrl(url);
        return merchantRepository.save(merchant);
    }

    private void validateImageFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("No file uploaded");
        }
        if (!ALLOWED_IMAGE_TYPES.contains(file.getContentType())) {
            throw new BusinessException("Unsupported image type: " + file.getContentType()
                    + " (allowed: " + ALLOWED_IMAGE_TYPES + ")");
        }
    }

    private String extensionFor(String contentType) {
        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }
}

package com.qrserve.merchant.storage;

/**
 * Abstraction over where uploaded media (merchant branding/logo) actually
 * lives. Mirrors menu-service's identical interface for product images —
 * duplicated rather than shared, since shared:common is component-scanned
 * by all 9 services and this needs S3 SDK config only menu-service and
 * merchant-service actually use. Two implementations exist:
 * {@link FilesystemMediaStorageService} (a mounted directory — in a real
 * deployment, a PersistentVolumeClaim) and an S3-compatible one (MinIO
 * locally, real S3 in a cloud deployment). Selected via app.media.backend.
 */
public interface MediaStorageService {

    /**
     * Stores content under the given key and returns a URL a client can GET
     * to retrieve it. `key` is already namespaced by the caller (e.g.
     * "branding/{merchantId}-3f9c1a.png").
     */
    String store(String key, byte[] content, String contentType);
}

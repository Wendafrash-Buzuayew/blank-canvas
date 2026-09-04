package com.qrserve.menu.storage;

/**
 * Abstraction over where uploaded media (product photos, merchant branding)
 * actually lives. Two implementations exist: {@link FilesystemMediaStorageService}
 * (writes to a mounted directory — in a real deployment, a PersistentVolumeClaim)
 * and an S3-compatible one (MinIO locally, real S3 in a cloud deployment).
 * Selected via app.media.backend; callers never know which is active.
 */
public interface MediaStorageService {

    /**
     * Stores content under the given key and returns a URL a client can GET
     * to retrieve it. `key` is already namespaced by the caller (e.g.
     * "products/42-3f9c1a.jpg") — implementations do not invent structure or
     * generate ids, only persist bytes at the given key.
     */
    String store(String key, byte[] content, String contentType);
}

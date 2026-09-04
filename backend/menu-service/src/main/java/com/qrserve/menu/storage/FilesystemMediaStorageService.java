package com.qrserve.menu.storage;

import com.qrserve.shared.exceptions.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Writes to a local directory. In a real deployment that directory is a
 * mounted PersistentVolumeClaim, so files survive pod restarts/rescheduling
 * — from this class's perspective it is just a path, k8s does the rest.
 * Default backend (matchIfMissing) since it needs no external service to
 * run, unlike the S3-compatible alternative.
 */
@Service
@ConditionalOnProperty(name = "app.media.backend", havingValue = "filesystem", matchIfMissing = true)
public class FilesystemMediaStorageService implements MediaStorageService {

    @Value("${app.media.upload-dir:./uploads}")
    private String uploadDir;

    @Value("${app.media.public-base-path:/api/media}")
    private String publicBasePath;

    @Override
    public String store(String key, byte[] content, String contentType) {
        Path root = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Invalid media key: " + key);
        }
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException e) {
            throw new BusinessException("Failed to store media at " + key, e);
        }
        return publicBasePath + "/" + key;
    }
}

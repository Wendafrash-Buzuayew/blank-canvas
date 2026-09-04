package com.qrserve.menu.controller;

import com.qrserve.shared.exceptions.ResourceNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Serves files written by FilesystemMediaStorageService. Only relevant when
 * app.media.backend=filesystem (the default) — under the S3-compatible
 * backend, store() already returns a URL pointing directly at the object
 * store, and this controller is simply never linked to.
 */
@RestController
@RequestMapping("/api/media")
@Tag(name = "Media", description = "Uploaded product/branding images (filesystem storage backend)")
public class MediaController {

    @Value("${app.media.upload-dir:./uploads}")
    private String uploadDir;

    @GetMapping("/products/{filename}")
    @Operation(summary = "Fetch an uploaded product image")
    public ResponseEntity<Resource> getProductImage(@PathVariable String filename) throws IOException {
        return serve("products", filename);
    }

    private ResponseEntity<Resource> serve(String subdir, String filename) throws IOException {
        Path root = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path file = root.resolve(subdir).resolve(filename).normalize();
        if (!file.startsWith(root) || !Files.exists(file) || !Files.isRegularFile(file)) {
            throw new ResourceNotFoundException("Media not found: " + filename);
        }
        String contentType = Files.probeContentType(file);
        Resource resource;
        try {
            resource = new UrlResource(file.toUri());
        } catch (MalformedURLException e) {
            throw new ResourceNotFoundException("Media not found: " + filename);
        }
        return ResponseEntity.ok()
                .contentType(contentType != null ? MediaType.parseMediaType(contentType) : MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }
}

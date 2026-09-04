package com.qrserve.merchant.storage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real filesystem round-trip (a temp directory, not a mock) — mirrors
 * menu-service's identical test for product images.
 */
class FilesystemMediaStorageServiceTest {

    private Path tempDir;
    private FilesystemMediaStorageService service;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("media-storage-test");
        service = new FilesystemMediaStorageService();
        setField("uploadDir", tempDir.toString());
        setField("publicBasePath", "/api/media");
    }

    @AfterEach
    void tearDown() throws IOException {
        try (var paths = Files.walk(tempDir)) {
            paths.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException ignored) { }
            });
        }
    }

    private void setField(String name, String value) {
        try {
            var field = FilesystemMediaStorageService.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(service, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void writesTheContentAndReturnsAUrlUnderTheConfiguredPublicPath() throws IOException {
        byte[] content = "fake-logo-bytes".getBytes(StandardCharsets.UTF_8);

        String url = service.store("branding/merchant1-abc.png", content, "image/png");

        assertEquals("/api/media/branding/merchant1-abc.png", url);
        Path written = tempDir.resolve("branding/merchant1-abc.png");
        assertTrue(Files.exists(written));
        assertArrayEquals(content, Files.readAllBytes(written));
    }

    @Test
    void rejectsAKeyThatEscapesTheUploadDirectory() {
        assertThrows(IllegalArgumentException.class,
                () -> service.store("../../etc/passwd", "x".getBytes(StandardCharsets.UTF_8), "image/jpeg"));
    }
}

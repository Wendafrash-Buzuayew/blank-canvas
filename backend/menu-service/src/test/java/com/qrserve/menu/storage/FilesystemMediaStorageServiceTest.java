package com.qrserve.menu.storage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real filesystem round-trip (a temp directory, not a mock) — this class
 * exists specifically to prove bytes actually land where store() says they
 * do, not just that the method returns without throwing.
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
        byte[] content = "fake-image-bytes".getBytes(StandardCharsets.UTF_8);

        String url = service.store("products/1-abc.jpg", content, "image/jpeg");

        assertEquals("/api/media/products/1-abc.jpg", url);
        Path written = tempDir.resolve("products/1-abc.jpg");
        assertTrue(Files.exists(written));
        assertArrayEquals(content, Files.readAllBytes(written));
    }

    @Test
    void createsIntermediateDirectoriesAsNeeded() throws IOException {
        service.store("products/nested/2-def.png", "x".getBytes(StandardCharsets.UTF_8), "image/png");

        assertTrue(Files.exists(tempDir.resolve("products/nested/2-def.png")));
    }

    @Test
    void rejectsAKeyThatEscapesTheUploadDirectory() {
        assertThrows(IllegalArgumentException.class,
                () -> service.store("../../etc/passwd", "x".getBytes(StandardCharsets.UTF_8), "image/jpeg"));
    }

    @Test
    void reUploadingUnderADifferentKeyDoesNotOverwriteThePreviousFile() throws IOException {
        service.store("products/3-first.jpg", "one".getBytes(StandardCharsets.UTF_8), "image/jpeg");
        service.store("products/3-second.jpg", "two".getBytes(StandardCharsets.UTF_8), "image/jpeg");

        assertEquals("one", Files.readString(tempDir.resolve("products/3-first.jpg")));
        assertEquals("two", Files.readString(tempDir.resolve("products/3-second.jpg")));
    }
}

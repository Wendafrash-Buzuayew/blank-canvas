package com.qrserve.merchant.storage;

import com.qrserve.shared.exceptions.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Mirrors menu-service's identical test for its S3 backend. */
class S3MediaStorageServiceTest {

    private S3Client s3Client;
    private S3MediaStorageService service;

    @BeforeEach
    void setUp() {
        s3Client = mock(S3Client.class);
        service = new S3MediaStorageService(s3Client);
        setField("bucket", "qrserve-media");
        setField("publicUrlBase", "http://localhost:9000/qrserve-media");
    }

    private void setField(String name, String value) {
        try {
            var field = S3MediaStorageService.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(service, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void putsTheObjectAndReturnsAUrlUnderThePublicBase() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        String url = service.store("branding/1-abc.png", "bytes".getBytes(), "image/png");

        assertEquals("http://localhost:9000/qrserve-media/branding/1-abc.png", url);
        verify(s3Client).putObject(
                argThat((PutObjectRequest r) -> r.bucket().equals("qrserve-media")
                        && r.key().equals("branding/1-abc.png")
                        && r.contentType().equals("image/png")),
                any(RequestBody.class));
    }

    @Test
    void wrapsAnS3FailureAsABusinessException() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow((S3Exception) S3Exception.builder().message("access denied").statusCode(403).build());

        assertThrows(BusinessException.class, () -> service.store("branding/1.png", "x".getBytes(), "image/png"));
    }

    @Test
    void createsTheBucketWhenItDoesNotExistYet() {
        when(s3Client.headBucket(any(HeadBucketRequest.class)))
                .thenThrow((NoSuchBucketException) NoSuchBucketException.builder().message("no bucket").build());

        service.ensureBucketExists();

        verify(s3Client).createBucket(argThat((CreateBucketRequest r) -> r.bucket().equals("qrserve-media")));
    }
}

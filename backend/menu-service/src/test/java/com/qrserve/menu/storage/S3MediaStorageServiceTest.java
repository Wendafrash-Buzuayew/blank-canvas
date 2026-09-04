package com.qrserve.menu.storage;

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

        String url = service.store("products/1-abc.jpg", "bytes".getBytes(), "image/jpeg");

        assertEquals("http://localhost:9000/qrserve-media/products/1-abc.jpg", url);
        verify(s3Client).putObject(
                argThat((PutObjectRequest r) -> r.bucket().equals("qrserve-media")
                        && r.key().equals("products/1-abc.jpg")
                        && r.contentType().equals("image/jpeg")),
                any(RequestBody.class));
    }

    @Test
    void wrapsAnS3FailureAsABusinessException() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow((S3Exception) S3Exception.builder().message("access denied").statusCode(403).build());

        assertThrows(BusinessException.class, () -> service.store("products/1.jpg", "x".getBytes(), "image/jpeg"));
    }

    @Test
    void createsTheBucketWhenItDoesNotExistYet() {
        when(s3Client.headBucket(any(HeadBucketRequest.class)))
                .thenThrow((NoSuchBucketException) NoSuchBucketException.builder().message("no bucket").build());

        service.ensureBucketExists();

        verify(s3Client).createBucket(argThat((CreateBucketRequest r) -> r.bucket().equals("qrserve-media")));
    }

    @Test
    void doesNothingWhenTheBucketAlreadyExists() {
        when(s3Client.headBucket(any(HeadBucketRequest.class))).thenReturn(null);

        service.ensureBucketExists();

        verify(s3Client, never()).createBucket(any(CreateBucketRequest.class));
    }
}

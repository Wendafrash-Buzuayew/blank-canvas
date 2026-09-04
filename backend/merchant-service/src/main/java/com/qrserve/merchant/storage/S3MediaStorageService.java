package com.qrserve.merchant.storage;

import com.qrserve.shared.exceptions.BusinessException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * S3-compatible backend: MinIO (self-hosted, real S3 API) locally, real AWS
 * S3 or any other S3-compatible provider in the cloud — same client code
 * either way, only the S3Client bean's endpoint/credentials change (see
 * AppConfig.s3Client). Only active when app.media.backend=s3.
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.media.backend", havingValue = "s3")
@Slf4j
public class S3MediaStorageService implements MediaStorageService {

    private final S3Client s3Client;

    @Value("${app.media.s3.bucket}")
    private String bucket;

    @Value("${app.media.s3.public-url-base}")
    private String publicUrlBase;

    /** Local/dev convenience: a fresh MinIO instance has no buckets yet. */
    @PostConstruct
    void ensureBucketExists() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (NoSuchBucketException e) {
            log.info("Media bucket {} does not exist yet — creating it", bucket);
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
        }
    }

    @Override
    public String store(String key, byte[] content, String contentType) {
        try {
            s3Client.putObject(
                    PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build(),
                    RequestBody.fromBytes(content));
        } catch (S3Exception e) {
            throw new BusinessException("Failed to store media at " + key, e);
        }
        return publicUrlBase + "/" + key;
    }
}

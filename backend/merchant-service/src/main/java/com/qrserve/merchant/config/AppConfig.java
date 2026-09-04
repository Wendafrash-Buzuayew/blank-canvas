package com.qrserve.merchant.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

@Configuration
public class AppConfig {

    /**
     * Only constructed when app.media.backend=s3 — building this eagerly
     * under the default filesystem backend would require S3 credentials
     * that were never configured. forcePathStyle is required for MinIO and
     * most self-hosted S3-compatible stores.
     */
    @Bean
    @ConditionalOnProperty(name = "app.media.backend", havingValue = "s3")
    public S3Client s3Client(
            @Value("${app.media.s3.endpoint}") String endpoint,
            @Value("${app.media.s3.region}") String region,
            @Value("${app.media.s3.access-key}") String accessKey,
            @Value("${app.media.s3.secret-key}") String secretKey) {
        return S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .forcePathStyle(true)
                .build();
    }
}

package com.filehome.storage;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.filehome.config.AppProperties;
import com.filehome.config.MinioConfig;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import jakarta.annotation.PostConstruct;

@Component
public class MinioObjectStorageAdapter implements ObjectStoragePort {

    private final MinioClient minioClient;
    private final String bucket;

    public MinioObjectStorageAdapter(MinioClient minioClient, AppProperties properties) {
        this.minioClient = minioClient;
        this.bucket = properties.storage().bucket();
    }

    @Override
    public String bucketName() {
        return bucket;
    }

    @PostConstruct
    public void initializeBucket() {
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot initialize MinIO bucket: " + bucket, exception);
        }
    }

    @Override
    public void ensureUserRoot(String username) {
        byte[] marker = new byte[0];
        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(username + "/.filehome-root")
                            .stream(new ByteArrayInputStream(marker), 0, -1)
                            .contentType("application/x-filehome-folder")
                            .build());
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot create user storage root", exception);
        }
    }

    @Override
    public StoredObject put(
            String username,
            UUID entryId,
            long size,
            String contentType,
            InputStream inputStream) {
        return putObject(
                username + "/" + entryId,
                size,
                contentType,
                inputStream);
    }

    @Override
    public StoredObject putObject(
            String objectKey,
            long size,
            String contentType,
            InputStream inputStream) {
        if (objectKey == null
                || objectKey.isBlank()
                || objectKey.startsWith("/")
                || objectKey.contains("..")) {
            throw new IllegalArgumentException("Invalid storage object key");
        }
        String normalizedContentType =
                contentType == null || contentType.isBlank()
                        ? "application/octet-stream"
                        : contentType;
        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .stream(inputStream, size, -1)
                            .contentType(normalizedContentType)
                            .build());
            return new StoredObject(
                    bucket,
                    objectKey,
                    size,
                    normalizedContentType);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot upload object to MinIO", exception);
        }
    }

    @Override
    public InputStream get(String bucketName, String objectKey) {
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .build());
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot read object from MinIO", exception);
        }
    }

    @Override
    public InputStream get(
            String bucketName,
            String objectKey,
            long offset,
            long length) {
        if (offset < 0 || length <= 0) {
            throw new IllegalArgumentException("Object range must be positive");
        }
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .offset(offset)
                            .length(length)
                            .build());
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot read object range from MinIO", exception);
        }
    }

    @Override
    public void delete(String bucketName, String objectKey) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .build());
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot delete MinIO object", exception);
        }
    }
}

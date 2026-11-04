package com.filehome.storage;

import java.io.InputStream;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

import com.filehome.config.AppProperties;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

import io.minio.CreateMultipartUploadResponse;
import io.minio.MinioAsyncClient;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.UploadPartResponse;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.Part;

@Component
public class MinioMultipartStorageAdapter implements MultipartUploadStoragePort {

    private static final long PART_TIMEOUT_MINUTES = 15;
    private static final long CONTROL_TIMEOUT_SECONDS = 60;

    private final MinioAsyncClient asyncClient;
    private final MinioClient syncClient;

    public MinioMultipartStorageAdapter(
            MinioClient syncClient,
            AppProperties properties) {
        this.syncClient = syncClient;
        this.asyncClient = MinioAsyncClient.builder()
                .endpoint(properties.storage().endpoint())
                .credentials(
                        properties.storage().accessKey(),
                        properties.storage().secretKey())
                .build();
    }

    @Override
    public String create(
            String bucket,
            String objectKey,
            String contentType) {
        Multimap<String, String> headers = ArrayListMultimap.create();
        headers.put("Content-Type", contentType);
        try {
            CreateMultipartUploadResponse response = asyncClient
                    .createMultipartUploadAsync(
                            bucket,
                            null,
                            objectKey,
                            headers,
                            ArrayListMultimap.create())
                    .get(CONTROL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return response.result().uploadId();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Interrupted while creating multipart upload",
                    exception);
        } catch (Exception exception) {
            throw failure("create multipart upload", exception);
        }
    }

    @Override
    public StoredPart uploadPart(
            String bucket,
            String objectKey,
            String uploadId,
            int partNumber,
            long size,
            InputStream inputStream) {
        try {
            UploadPartResponse response = asyncClient.uploadPartAsync(
                            bucket,
                            null,
                            objectKey,
                            inputStream,
                            size,
                            uploadId,
                            partNumber,
                            ArrayListMultimap.create(),
                            ArrayListMultimap.create())
                    .get(PART_TIMEOUT_MINUTES, TimeUnit.MINUTES);
            return new StoredPart(partNumber, response.etag(), size);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Interrupted while uploading multipart part",
                    exception);
        } catch (Exception exception) {
            throw failure("upload multipart part", exception);
        }
    }

    @Override
    public void complete(
            String bucket,
            String objectKey,
            String uploadId,
            List<StoredPart> parts) {
        Part[] minioParts = parts.stream()
                .sorted(java.util.Comparator.comparingInt(StoredPart::partNumber))
                .map(part -> new Part(part.partNumber(), part.etag()))
                .toArray(Part[]::new);
        try {
            asyncClient.completeMultipartUploadAsync(
                            bucket,
                            null,
                            objectKey,
                            uploadId,
                            minioParts,
                            ArrayListMultimap.create(),
                            ArrayListMultimap.create())
                    .get(CONTROL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Interrupted while completing multipart upload",
                    exception);
        } catch (Exception exception) {
            throw failure("complete multipart upload", exception);
        }
    }

    @Override
    public void abort(String bucket, String objectKey, String uploadId) {
        try {
            asyncClient.abortMultipartUploadAsync(
                            bucket,
                            null,
                            objectKey,
                            uploadId,
                            ArrayListMultimap.create(),
                            ArrayListMultimap.create())
                    .get(CONTROL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (Exception exception) {
            if (!(exception.getCause() instanceof ErrorResponseException)) {
                throw failure("abort multipart upload", exception);
            }
        }
    }

    @Override
    public OptionalLong objectSize(String bucket, String objectKey) {
        try {
            StatObjectResponse stat = syncClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .build());
            return OptionalLong.of(stat.size());
        } catch (ErrorResponseException exception) {
            if ("NoSuchKey".equals(exception.errorResponse().code())) {
                return OptionalLong.empty();
            }
            throw new IllegalStateException(
                    "Cannot stat multipart object",
                    exception);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Cannot stat multipart object",
                    exception);
        }
    }

    private IllegalStateException failure(String operation, Exception exception) {
        Throwable cause = exception.getCause() == null
                ? exception
                : exception.getCause();
        return new IllegalStateException(
                "Cannot " + operation + " in MinIO",
                cause);
    }
}

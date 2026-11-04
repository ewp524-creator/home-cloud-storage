package com.filehome.storage;

import java.io.InputStream;
import java.util.List;
import java.util.OptionalLong;

public interface MultipartUploadStoragePort {

    String create(
            String bucket,
            String objectKey,
            String contentType);

    StoredPart uploadPart(
            String bucket,
            String objectKey,
            String uploadId,
            int partNumber,
            long size,
            InputStream inputStream);

    void complete(
            String bucket,
            String objectKey,
            String uploadId,
            List<StoredPart> parts);

    void abort(
            String bucket,
            String objectKey,
            String uploadId);

    OptionalLong objectSize(String bucket, String objectKey);

    record StoredPart(int partNumber, String etag, long size) {
    }
}

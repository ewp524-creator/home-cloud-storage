package com.filehome.storage;

import java.io.InputStream;
import java.util.UUID;

public interface ObjectStoragePort {

    String bucketName();

    void ensureUserRoot(String username);

    StoredObject put(
            String username,
            UUID entryId,
            long size,
            String contentType,
            InputStream inputStream);

    StoredObject putObject(
            String objectKey,
            long size,
            String contentType,
            InputStream inputStream);

    InputStream get(String bucket, String objectKey);

    InputStream get(
            String bucket,
            String objectKey,
            long offset,
            long length);

    void delete(String bucket, String objectKey);
}

package com.filehome.storage;

public record StoredObject(
        String bucket,
        String objectKey,
        long size,
        String contentType) {
}

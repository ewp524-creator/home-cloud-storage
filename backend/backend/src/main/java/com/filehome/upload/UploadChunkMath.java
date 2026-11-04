package com.filehome.upload;

import com.filehome.common.ApiException;

public final class UploadChunkMath {

    private static final int MAX_PARTS = 10_000;

    private UploadChunkMath() {
    }

    public static int totalParts(long sizeBytes, long partSizeBytes) {
        if (sizeBytes <= 0 || partSizeBytes <= 0) {
            throw ApiException.badRequest(
                    "INVALID_UPLOAD_SIZE",
                    "\u4e0a\u4f20\u5927\u5c0f\u5fc5\u987b\u5927\u4e8e\u96f6");
        }
        long count = (sizeBytes + partSizeBytes - 1) / partSizeBytes;
        if (count < 1 || count > MAX_PARTS) {
            throw ApiException.badRequest(
                    "TOO_MANY_PARTS",
                    "\u6587\u4ef6\u5206\u7247\u6570\u91cf\u8d85\u51fa\u9650\u5236");
        }
        return (int) count;
    }

    public static long partSize(
            long sizeBytes,
            long partSizeBytes,
            int totalParts,
            int partNumber) {
        if (partNumber < 1 || partNumber > totalParts) {
            throw ApiException.badRequest(
                    "INVALID_PART_NUMBER",
                    "\u5206\u7247\u5e8f\u53f7\u8d85\u51fa\u8303\u56f4");
        }
        if (partNumber < totalParts) {
            return partSizeBytes;
        }
        return sizeBytes - partSizeBytes * (totalParts - 1L);
    }

    public static String normalizeSha256(String value) {
        if (value == null || !value.matches("^[a-fA-F0-9]{64}$")) {
            throw ApiException.badRequest(
                    "INVALID_CHUNK_HASH",
                    "\u5206\u7247 SHA-256 \u683c\u5f0f\u4e0d\u6b63\u786e");
        }
        return value.toLowerCase();
    }
}

package com.filehome.file;

public record FileStorageStats(
        long usedBytes,
        long fileCount,
        long folderCount) {
}

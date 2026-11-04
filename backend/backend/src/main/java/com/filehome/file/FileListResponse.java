package com.filehome.file;

import java.util.List;
import java.util.UUID;

public record FileListResponse(
        List<FileView> items,
        FileView currentFolder,
        List<FileView> breadcrumbs,
        UUID rootFolderId,
        long usedBytes,
        long quotaBytes,
        long fileCount,
        long folderCount,
        boolean hasMore,
        String nextCursor) {
}

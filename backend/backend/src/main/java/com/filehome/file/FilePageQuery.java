package com.filehome.file;

import java.util.UUID;

public record FilePageQuery(
        UUID ownerId,
        UUID parentId,
        String query,
        String type,
        String tag,
        String sort,
        FilePageCursor cursor,
        boolean includeDeleted,
        int limit) {
}

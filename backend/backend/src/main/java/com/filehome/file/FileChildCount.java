package com.filehome.file;

import java.util.UUID;

public record FileChildCount(UUID parentId, long itemCount) {
}

package com.filehome.file;

import java.util.UUID;

public interface SharedPreviewAuthorizer {

    void requireActive(UUID ownerId, UUID shareId);
}

package com.filehome.file;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface FileShareStatus {

    Set<UUID> activeSharedEntryIds(UUID ownerId, List<UUID> entryIds);
}

package com.filehome.share;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.filehome.file.FileShareStatus;

@Component
public class ShareFileStatusAdapter implements FileShareStatus {

    private final ShareRepository shareRepository;

    public ShareFileStatusAdapter(ShareRepository shareRepository) {
        this.shareRepository = shareRepository;
    }

    @Override
    public Set<UUID> activeSharedEntryIds(
            UUID ownerId,
            List<UUID> entryIds) {
        if (entryIds == null || entryIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(
                shareRepository.findActiveSharedRootIds(ownerId, entryIds));
    }
}

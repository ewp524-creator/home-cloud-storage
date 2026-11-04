package com.filehome.file;

import java.util.List;
import java.util.UUID;

public final class FileOperationDtos {

    private FileOperationDtos() {
    }

    public record CreateFolderRequest(UUID parentId, String name) {
    }

    public record RenameRequest(String name) {
    }

    public record StarRequest(boolean starred) {
    }

    public record MoveRequest(List<UUID> ids, UUID targetParentId) {
    }

    public record CopyRequest(
            List<UUID> ids,
            UUID targetParentId,
            List<NameOverride> nameOverrides) {
    }

    public record NameOverride(UUID id, String name) {
    }

    public record IdsRequest(List<UUID> ids) {
    }

    public record OperationResult(List<UUID> ids, int count) {
    }
}

package com.filehome.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class FilePageCursorTest {

    private final FileEntry entry = new FileEntry(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            FileEntry.EntryKind.FILE,
            "benchmark-file.txt",
            Instant.parse("2026-11-01T08:00:00Z"),
            Instant.parse("2026-11-01T09:00:00Z"),
            null,
            "file-home",
            "test/object",
            4096,
            "text/plain",
            false,
            List.of(),
            false);

    @Test
    void roundTripsModifiedCursor() {
        String token = FilePageCursor.encode(1, "modified-desc", entry);
        FilePageCursor cursor = FilePageCursor.decode(token, "modified-desc")
                .orElseThrow();

        assertEquals(1, cursor.folderRank());
        assertEquals(entry.updatedAt(), cursor.modifiedAt());
        assertEquals(entry.id(), cursor.id());
    }

    @Test
    void roundTripsNameAndSizeCursors() {
        FilePageCursor name = FilePageCursor.decode(
                FilePageCursor.encode(0, "name-asc", entry),
                "name-asc").orElseThrow();
        FilePageCursor size = FilePageCursor.decode(
                FilePageCursor.encode(1, "size-desc", entry),
                "size-desc").orElseThrow();

        assertEquals(entry.name(), name.name());
        assertEquals(entry.size(), size.size());
    }

    @Test
    void rejectsCursorForDifferentSort() {
        String token = FilePageCursor.encode(1, "name-asc", entry);

        assertTrue(FilePageCursor.decode(token, "modified-desc").isEmpty());
    }
}

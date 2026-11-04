package com.filehome.file;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

public record FilePageCursor(
        int folderRank,
        Instant modifiedAt,
        String name,
        long size,
        UUID id) {

    public static String encode(
            int folderRank,
            String sort,
            FileEntry entry) {
        String value = switch (sort) {
            case "name-asc" -> entry.name();
            case "size-desc" -> Long.toString(entry.size());
            default -> entry.updatedAt().toString();
        };
        String raw = String.join(
                "|",
                Integer.toString(folderRank),
                sort,
                Base64.getUrlEncoder().withoutPadding().encodeToString(
                        value.getBytes(StandardCharsets.UTF_8)),
                entry.id().toString());
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                raw.getBytes(StandardCharsets.UTF_8));
    }

    public static Optional<FilePageCursor> decode(String token, String sort) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            String raw = new String(
                    Base64.getUrlDecoder().decode(token),
                    StandardCharsets.UTF_8);
            String[] parts = raw.split("\\|", 4);
            if (parts.length != 4 || !sort.equals(parts[1])) {
                return Optional.empty();
            }
            String value = new String(
                    Base64.getUrlDecoder().decode(parts[2]),
                    StandardCharsets.UTF_8);
            int folderRank = Integer.parseInt(parts[0]);
            UUID id = UUID.fromString(parts[3]);
            return Optional.of(switch (sort) {
                case "name-asc" -> new FilePageCursor(
                        folderRank, null, value, 0, id);
                case "size-desc" -> new FilePageCursor(
                        folderRank, null, null, Long.parseLong(value), id);
                default -> new FilePageCursor(
                        folderRank, Instant.parse(value), null, 0, id);
            });
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }
}

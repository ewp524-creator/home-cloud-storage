package com.filehome.share;

import java.util.UUID;

public record ShareAccessCount(
        UUID shareId,
        long visits,
        long downloads) {
}

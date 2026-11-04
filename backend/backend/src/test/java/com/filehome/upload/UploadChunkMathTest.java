package com.filehome.upload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.filehome.common.ApiException;

class UploadChunkMathTest {

    private static final long MIB = 1024L * 1024;

    @Test
    void calculatesStablePartBoundariesIncludingShortLastPart() {
        long size = 33 * MIB + 7;
        long partSize = 16 * MIB;

        int total = UploadChunkMath.totalParts(size, partSize);

        assertThat(total).isEqualTo(3);
        assertThat(UploadChunkMath.partSize(size, partSize, total, 1))
                .isEqualTo(partSize);
        assertThat(UploadChunkMath.partSize(size, partSize, total, 2))
                .isEqualTo(partSize);
        assertThat(UploadChunkMath.partSize(size, partSize, total, 3))
                .isEqualTo(MIB + 7);
    }

    @Test
    void rejectsInvalidPartNumbersAndChunkHashes() {
        assertThatThrownBy(() ->
                UploadChunkMath.partSize(10, 8, 2, 3))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() ->
                UploadChunkMath.normalizeSha256("not-a-hash"))
                .isInstanceOf(ApiException.class);
        assertThat(UploadChunkMath.normalizeSha256("A".repeat(64)))
                .isEqualTo("a".repeat(64));
    }
}

package com.filehome.file;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ByteRangeParserTest {

    @Test
    void parsesBoundedRange() {
        ByteRangeParser.Result result = ByteRangeParser.parse("bytes=10-99", 1000);

        assertThat(result.valid()).isTrue();
        assertThat(result.partial()).isTrue();
        assertThat(result.start()).isEqualTo(10);
        assertThat(result.end()).isEqualTo(99);
        assertThat(result.length()).isEqualTo(90);
    }

    @Test
    void parsesOpenEndedRange() {
        ByteRangeParser.Result result = ByteRangeParser.parse("bytes=900-", 1000);

        assertThat(result.start()).isEqualTo(900);
        assertThat(result.end()).isEqualTo(999);
    }

    @Test
    void parsesSuffixRange() {
        ByteRangeParser.Result result = ByteRangeParser.parse("bytes=-25", 1000);

        assertThat(result.start()).isEqualTo(975);
        assertThat(result.end()).isEqualTo(999);
    }

    @Test
    void clampsEndToFileSize() {
        ByteRangeParser.Result result = ByteRangeParser.parse("bytes=10-5000", 100);

        assertThat(result.end()).isEqualTo(99);
    }

    @Test
    void rejectsMalformedAndMultipleRanges() {
        assertThat(ByteRangeParser.parse("bytes=100-200", 100).valid()).isFalse();
        assertThat(ByteRangeParser.parse("bytes=0-10,20-30", 100).valid()).isFalse();
        assertThat(ByteRangeParser.parse("items=0-10", 100).valid()).isFalse();
    }
}

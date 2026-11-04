package com.filehome.file;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FilePreviewTypeTest {

    @Test
    void recognizesHeaderAsCodeRegardlessOfUploadedMime() {
        assertThat(FilePreviewType.previewKind("sample.h", "application/octet-stream"))
                .isEqualTo("code");
        assertThat(FilePreviewType.contentType("sample.h", "application/octet-stream"))
                .isEqualTo("text/x-c");
    }

    @Test
    void recognizesCommonMediaFormats() {
        assertThat(FilePreviewType.previewKind("clip.mp4", "application/octet-stream"))
                .isEqualTo("video");
        assertThat(FilePreviewType.previewKind("photo.jpg", "application/octet-stream"))
                .isEqualTo("image");
        assertThat(FilePreviewType.previewKind("readme.txt", "application/octet-stream"))
                .isEqualTo("text");
    }

    @Test
    void recognizesDocxForReadonlyWordPreview() {
        assertThat(FilePreviewType.previewKind(
                "report.docx",
                "application/octet-stream"))
                .isEqualTo("word");
        assertThat(FilePreviewType.contentType(
                "report.docx",
                "application/octet-stream"))
                .isEqualTo(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    }

    @Test
    void keepsUnknownFormatsHonest() {
        assertThat(FilePreviewType.previewKind("archive.zip", "application/zip"))
                .isEqualTo("unsupported");
    }
}

package com.filehome.file;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.http.MediaType;

public final class FilePreviewType {

    private static final Set<String> IMAGE_EXTENSIONS = Set.of(
            "png", "jpg", "jpeg", "webp", "gif", "bmp", "svg", "ico",
            "avif", "tif", "tiff", "heic", "heif");
    private static final Set<String> VIDEO_EXTENSIONS = Set.of(
            "mp4", "m4v", "mov", "webm", "ogv", "mkv", "avi", "flv",
            "wmv", "mpeg", "mpg", "3gp", "mts", "m2ts");
    private static final Set<String> AUDIO_EXTENSIONS = Set.of(
            "mp3", "wav", "flac", "aac", "ogg", "oga", "m4a", "opus",
            "wma", "aiff", "aif", "mid", "midi");
    private static final Set<String> TEXT_EXTENSIONS = Set.of(
            "txt", "md", "markdown", "log", "tex", "rst", "adoc", "csv",
            "tsv", "srt", "vtt", "ics", "vcf");
    private static final Set<String> WORD_EXTENSIONS = Set.of(
            "doc", "docx", "docm", "dotx", "odt", "rtf");
    private static final Set<String> CODE_EXTENSIONS = Set.of(
            "js", "mjs", "cjs", "jsx", "ts", "tsx", "vue", "java", "kt",
            "kts", "groovy", "scala", "py", "pyw", "go", "rs", "c", "h",
            "cc", "cpp", "cxx", "hpp", "hh", "cs", "fs", "vb", "php",
            "rb", "swift", "m", "mm", "dart", "lua", "pl", "r", "sql",
            "sh", "bash", "zsh", "fish", "ps1", "bat", "cmd", "json",
            "jsonc", "json5", "yaml", "yml", "xml", "html", "htm", "xhtml",
            "css", "scss", "sass", "less", "toml", "ini", "conf", "cfg",
            "properties", "env", "gradle", "cmake", "dockerfile", "makefile",
            "ipynb", "rmd", "graphql", "gql", "proto", "asm", "s", "sol",
            "pem", "cer", "crt", "key");
    private static final Map<String, String> CONTENT_TYPES = Map.ofEntries(
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("webp", "image/webp"),
            Map.entry("gif", "image/gif"),
            Map.entry("bmp", "image/bmp"),
            Map.entry("svg", "image/svg+xml"),
            Map.entry("ico", "image/x-icon"),
            Map.entry("avif", "image/avif"),
            Map.entry("tif", "image/tiff"),
            Map.entry("tiff", "image/tiff"),
            Map.entry("heic", "image/heic"),
            Map.entry("heif", "image/heif"),
            Map.entry("mp4", "video/mp4"),
            Map.entry("m4v", "video/x-m4v"),
            Map.entry("mov", "video/quicktime"),
            Map.entry("webm", "video/webm"),
            Map.entry("ogv", "video/ogg"),
            Map.entry("mkv", "video/x-matroska"),
            Map.entry("avi", "video/x-msvideo"),
            Map.entry("flv", "video/x-flv"),
            Map.entry("wmv", "video/x-ms-wmv"),
            Map.entry("mpeg", "video/mpeg"),
            Map.entry("mpg", "video/mpeg"),
            Map.entry("3gp", "video/3gpp"),
            Map.entry("ts", "text/typescript"),
            Map.entry("mts", "video/mp2t"),
            Map.entry("m2ts", "video/mp2t"),
            Map.entry("mp3", "audio/mpeg"),
            Map.entry("wav", "audio/wav"),
            Map.entry("flac", "audio/flac"),
            Map.entry("aac", "audio/aac"),
            Map.entry("ogg", "audio/ogg"),
            Map.entry("oga", "audio/ogg"),
            Map.entry("m4a", "audio/mp4"),
            Map.entry("opus", "audio/opus"),
            Map.entry("wma", "audio/x-ms-wma"),
            Map.entry("aiff", "audio/aiff"),
            Map.entry("aif", "audio/aiff"),
            Map.entry("mid", "audio/midi"),
            Map.entry("midi", "audio/midi"),
            Map.entry("pdf", "application/pdf"),
            Map.entry(
                    "docx",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry(
                    "docm",
                    "application/vnd.ms-word.document.macroenabled.12"),
            Map.entry(
                    "dotx",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.template"),
            Map.entry("doc", "application/msword"),
            Map.entry("odt", "application/vnd.oasis.opendocument.text"),
            Map.entry("rtf", "application/rtf"),
            Map.entry("txt", "text/plain"),
            Map.entry("md", "text/markdown"),
            Map.entry("markdown", "text/markdown"),
            Map.entry("log", "text/plain"),
            Map.entry("tex", "text/x-tex"),
            Map.entry("csv", "text/csv"),
            Map.entry("tsv", "text/tab-separated-values"),
            Map.entry("html", "text/html"),
            Map.entry("htm", "text/html"),
            Map.entry("css", "text/css"),
            Map.entry("scss", "text/x-scss"),
            Map.entry("less", "text/x-less"),
            Map.entry("js", "text/javascript"),
            Map.entry("mjs", "text/javascript"),
            Map.entry("cjs", "text/javascript"),
            Map.entry("jsx", "text/jsx"),
            Map.entry("tsx", "text/tsx"),
            Map.entry("vue", "text/x-vue"),
            Map.entry("java", "text/x-java-source"),
            Map.entry("c", "text/x-c"),
            Map.entry("h", "text/x-c"),
            Map.entry("cc", "text/x-c++"),
            Map.entry("cpp", "text/x-c++"),
            Map.entry("cxx", "text/x-c++"),
            Map.entry("hpp", "text/x-c++"),
            Map.entry("py", "text/x-python"),
            Map.entry("go", "text/x-go"),
            Map.entry("rs", "text/x-rust"),
            Map.entry("json", "application/json"),
            Map.entry("jsonc", "application/json"),
            Map.entry("json5", "application/json"),
            Map.entry("yaml", "application/yaml"),
            Map.entry("yml", "application/yaml"),
            Map.entry("xml", "application/xml"),
            Map.entry("sql", "application/sql"),
            Map.entry("sh", "text/x-shellscript"),
            Map.entry("bash", "text/x-shellscript"),
            Map.entry("zsh", "text/x-shellscript"),
            Map.entry("ps1", "text/x-powershell"),
            Map.entry("toml", "application/toml"));

    private FilePreviewType() {
    }

    public static String extensionOf(String name) {
        if (name == null) return "";
        String cleanName = name.replace("\\", "/");
        cleanName = cleanName.substring(cleanName.lastIndexOf('/') + 1);
        int index = cleanName.lastIndexOf('.');
        if (index <= 0 || index == cleanName.length() - 1) return "";
        return cleanName.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    public static String contentType(String name, String storedContentType) {
        String extension = extensionOf(name);
        String detected = CONTENT_TYPES.get(extension);
        if (detected != null) return detected;
        if (storedContentType == null || storedContentType.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
        try {
            return MediaType.parseMediaType(storedContentType).toString();
        } catch (RuntimeException exception) {
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
    }

    public static String previewKind(String name, String contentType) {
        String extension = extensionOf(name);
        if ("pdf".equals(extension)) return "pdf";
        if (WORD_EXTENSIONS.contains(extension)) return "word";
        if (IMAGE_EXTENSIONS.contains(extension)
                || startsWith(contentType, "image/")) return "image";
        if (VIDEO_EXTENSIONS.contains(extension)
                || startsWith(contentType, "video/")) return "video";
        if (AUDIO_EXTENSIONS.contains(extension)
                || startsWith(contentType, "audio/")) return "audio";
        if (CODE_EXTENSIONS.contains(extension)
                || isCodeContentType(contentType)) return "code";
        if (TEXT_EXTENSIONS.contains(extension)
                || startsWith(contentType, "text/")) return "text";
        return "unsupported";
    }

    public static boolean isTextPreview(String previewKind) {
        return "text".equals(previewKind) || "code".equals(previewKind);
    }

    private static boolean startsWith(String value, String prefix) {
        return value != null && value.toLowerCase(Locale.ROOT).startsWith(prefix);
    }

    private static boolean isCodeContentType(String contentType) {
        if (contentType == null) return false;
        String value = contentType.toLowerCase(Locale.ROOT);
        return value.contains("json")
                || value.contains("xml")
                || value.contains("javascript")
                || value.contains("typescript")
                || value.contains("yaml")
                || value.contains("sql");
    }
}

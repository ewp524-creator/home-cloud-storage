package com.filehome.file;

public final class ByteRangeParser {

    private ByteRangeParser() {
    }

    public static Result parse(String header, long size) {
        if (header == null || header.isBlank()) {
            return size == 0
                    ? new Result(true, false, 0, 0)
                    : new Result(true, false, 0, size - 1);
        }
        String value = header.trim();
        if (!value.regionMatches(true, 0, "bytes=", 0, 6)
                || value.contains(",")
                || size <= 0) {
            return Result.invalid();
        }
        String specification = value.substring(6).trim();
        int separator = specification.indexOf('-');
        if (separator < 0 || specification.indexOf('-', separator + 1) >= 0) {
            return Result.invalid();
        }
        String startValue = specification.substring(0, separator).trim();
        String endValue = specification.substring(separator + 1).trim();
        try {
            long start;
            long end;
            if (startValue.isEmpty()) {
                if (endValue.isEmpty()) return Result.invalid();
                long suffixLength = Long.parseLong(endValue);
                if (suffixLength <= 0) return Result.invalid();
                start = Math.max(0, size - suffixLength);
                end = size - 1;
            } else {
                start = Long.parseLong(startValue);
                end = endValue.isEmpty() ? size - 1 : Long.parseLong(endValue);
                if (start < 0 || start >= size || end < start) {
                    return Result.invalid();
                }
                end = Math.min(end, size - 1);
            }
            return new Result(true, true, start, end);
        } catch (NumberFormatException exception) {
            return Result.invalid();
        }
    }

    public record Result(
            boolean valid,
            boolean partial,
            long start,
            long end) {

        public long length() {
            return end - start + 1;
        }

        static Result invalid() {
            return new Result(false, false, 0, 0);
        }
    }
}

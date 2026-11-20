package com.levi9.reddit.post.core.model;

import java.util.Arrays;
import java.util.Optional;

/**
 * The accepted image formats, recognised by their first bytes ("magic numbers"). The Content-Type a client
 * declares for a file part is never trusted: anything can be renamed to {@code cat.png}.
 */
public enum ImageType {
    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png");

    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    private final String contentType;
    private final String extension;

    ImageType(String contentType, String extension) {
        this.contentType = contentType;
        this.extension = extension;
    }

    public String contentType() {
        return contentType;
    }

    public String extension() {
        return extension;
    }

    public static Optional<ImageType> detect(byte[] content) {
        if (startsWith(content, JPEG_MAGIC)) {
            return Optional.of(JPEG);
        }
        if (startsWith(content, PNG_MAGIC)) {
            return Optional.of(PNG);
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] content, byte[] prefix) {
        return content.length >= prefix.length
                && Arrays.equals(content, 0, prefix.length, prefix, 0, prefix.length);
    }
}

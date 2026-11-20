package com.levi9.reddit.post.core;

import java.util.Objects;
import java.util.Optional;

/**
 * Where image files live (S3 in every environment; Moto locally and in tests). Only file bytes go here: which
 * image belongs to which post is recorded in the database ({@code post_images.s3_key}).
 */
public interface ImageStorage {

    void store(String key, byte[] content, String contentType);

    /** The file and the content type it was stored with; empty if there is no file under this key. */
    Optional<StoredImage> load(String key);

    /** Removing a key that doesn't exist is not an error. */
    void delete(String key);

    record StoredImage(byte[] content, String contentType) {
        public StoredImage {
            Objects.requireNonNull(content, "content");
            Objects.requireNonNull(contentType, "contentType");
        }
    }
}

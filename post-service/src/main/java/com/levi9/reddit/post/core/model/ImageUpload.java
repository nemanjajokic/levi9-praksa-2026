package com.levi9.reddit.post.core.model;

import com.levi9.reddit.post.core.exception.ImageTooLargeException;
import com.levi9.reddit.post.core.exception.TooManyImagesException;
import com.levi9.reddit.post.core.exception.UnsupportedImageTypeException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * One checked image of a new post: its bytes and the format found in them.
 *
 * @param content the file's bytes
 * @param type    detected from the bytes, never from the client's declared Content-Type
 */
public record ImageUpload(byte[] content, ImageType type) {
    /** Per image. Keep in line with {@code spring.servlet.multipart.max-file-size} in application.yml. */
    public static final long MAX_BYTES = 5L * 1024 * 1024;

    public ImageUpload {
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(type, "type");
    }

    /**
     * Checks the raw files of a new post, all before anything is stored: at most {@link Post#MAX_IMAGES},
     * each at most {@link #MAX_BYTES} and a supported format. Positions in error messages count from 1.
     */
    public static List<ImageUpload> validate(List<byte[]> files) {
        if (files.size() > Post.MAX_IMAGES) {
            throw new TooManyImagesException(Post.MAX_IMAGES);
        }
        List<ImageUpload> images = new ArrayList<>(files.size());
        for (int i = 0; i < files.size(); i++) {
            byte[] content = files.get(i);
            int position = i + 1;
            if (content.length > MAX_BYTES) {
                throw new ImageTooLargeException(position, MAX_BYTES);
            }
            ImageType type = ImageType.detect(content)
                    .orElseThrow(() -> new UnsupportedImageTypeException(position));
            images.add(new ImageUpload(content, type));
        }
        return List.copyOf(images);
    }
}

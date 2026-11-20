package com.levi9.reddit.post.core.exception;

/** One image is over the size limit. */
public final class ImageTooLargeException extends ValidationException {
    public ImageTooLargeException(int position, long maxBytes) {
        super(ErrorType.IMAGE_TOO_LARGE,
                "Image " + position + " is larger than " + (maxBytes / (1024 * 1024)) + " MB");
    }
}

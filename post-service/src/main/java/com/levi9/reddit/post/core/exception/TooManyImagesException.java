package com.levi9.reddit.post.core.exception;

/** A post has at most {@code max} images. */
public final class TooManyImagesException extends ValidationException {
    public TooManyImagesException(int max) {
        super(ErrorType.TOO_MANY_IMAGES, "A post can have at most " + max + " images");
    }
}

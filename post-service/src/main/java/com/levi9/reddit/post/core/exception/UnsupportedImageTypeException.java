package com.levi9.reddit.post.core.exception;

/** The file is not one of the accepted image formats (checked by its content, not its declared type). */
public final class UnsupportedImageTypeException extends ValidationException {
    public UnsupportedImageTypeException(int position) {
        super(ErrorType.UNSUPPORTED_IMAGE_TYPE,
                "Image " + position + " is not a JPEG or PNG image");
    }
}

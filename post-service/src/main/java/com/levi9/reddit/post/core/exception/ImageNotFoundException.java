package com.levi9.reddit.post.core.exception;

/** The post has no image with this id (or the image's file is missing from storage). */
public final class ImageNotFoundException extends NotFoundException {
    public ImageNotFoundException(Long postId, Long imageId) {
        super(ErrorType.IMAGE_NOT_FOUND, "Post " + postId + " has no image " + imageId);
    }
}

package com.levi9.reddit.post.contracts;

/** Field limits shared by the post requests. */
public final class PostLimits {
    /** Matches {@code posts.title VARCHAR(300)}. */
    public static final int TITLE_MAX = 300;
    /** {@code posts.content} is TEXT; this cap only keeps a single request reasonably small. */
    public static final int CONTENT_MAX = 40_000;

    private PostLimits() {}
}

package com.levi9.reddit.post.core.model;

import java.util.Objects;

/**
 * The logged-in user performing a write. Built from the access token at the web edge, so the rules in the
 * service don't depend on how the caller was authenticated (real JWT, mock filter or a test).
 *
 * @param id       user id from the token's {@code sub}; never taken from a request body
 * @param username copied onto new posts and comments as {@code author_username}
 * @param admin    whether the caller has the ADMIN role
 */
public record Actor(long id, String username, boolean admin) {

    public Actor {
        if (id <= 0) {
            throw new IllegalArgumentException("Actor id must be positive");
        }
        Objects.requireNonNull(username, "username");
        if (username.isBlank()) {
            throw new IllegalArgumentException("Actor username must not be blank");
        }
    }

    public boolean isAuthorOf(Post post) {
        return post.getAuthorId() == id;
    }
}

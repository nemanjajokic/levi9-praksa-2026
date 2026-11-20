package com.levi9.reddit.post.core.model;

import com.levi9.reddit.post.core.exception.NotPostAuthorException;
import com.levi9.reddit.post.core.exception.PostNotFoundException;
import com.levi9.reddit.post.core.exception.PostRemovalForbiddenException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "posts")
public class Post {
    public static final int MAX_IMAGES = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(columnDefinition = "text")
    private String content;

    @Column(name = "author_id", nullable = false, updatable = false)
    private Long authorId;

    @Column(name = "author_username", nullable = false, length = 30)
    private String authorUsername;

    @Column(name = "community_id", nullable = false, updatable = false)
    private Long communityId;

    @Column(nullable = false)
    private int score;

    @Column(name = "comment_count", nullable = false)
    private int commentCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "removed_at")
    private Instant removedAt;

    @Column(name = "removed_by")
    private Long removedBy;

    @OneToMany(mappedBy = "post", cascade = CascadeType.PERSIST, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<PostImage> images = new ArrayList<>();

    protected Post() {}

    public Post(String title, Long authorId, String authorUsername, Long communityId) {
        this(title, null, authorId, authorUsername, communityId);
    }

    /** A new post; {@code updatedAt} stays null until the author edits it. */
    public Post(String title, String content, Long authorId, String authorUsername, Long communityId) {
        this.content = content;
        this.title = Objects.requireNonNull(title, "title");
        this.authorId = Objects.requireNonNull(authorId, "authorId");
        this.authorUsername = Objects.requireNonNull(authorUsername, "authorUsername");
        this.communityId = Objects.requireNonNull(communityId, "communityId");
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public Long getAuthorId() {
        return authorId;
    }

    public String getAuthorUsername() {
        return authorUsername;
    }

    public Long getCommunityId() {
        return communityId;
    }

    public int getScore() {
        return score;
    }

    public int getCommentCount() {
        return commentCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getRemovedAt() {
        return removedAt;
    }

    public Long getRemovedBy() {
        return removedBy;
    }

    public List<PostImage> getImages() {
        return List.copyOf(images);
    }

    public PostImage addImage(String s3Key) {
        if (images.size() >= MAX_IMAGES) {
            throw new IllegalStateException("A post can have at most three images");
        }
        PostImage image = new PostImage(this, s3Key);
        images.add(image);
        return image;
    }

    public void removeImage(PostImage image) {
        if (images.remove(image)) {
            image.detachFromPost();
        }
    }

    public boolean isRemoved() {
        return removedAt != null;
    }

    /**
     * Edit on behalf of a caller: a removed post is gone (404), and only the author may edit, admins included.
     */
    public void editBy(Actor actor, String title, String content, Instant at) {
        Objects.requireNonNull(actor, "actor");
        if (isRemoved()) {
            throw new PostNotFoundException(id);
        }
        if (!actor.isAuthorOf(this)) {
            throw new NotPostAuthorException(id);
        }
        edit(title, content, at);
    }

    /**
     * Soft remove on behalf of a caller: the author or an admin. Idempotent: removing an already removed post
     * succeeds without changes, so the original {@code removedBy} / {@code removedAt} are kept. The permission
     * check comes first, so a stranger is refused whether or not the post is already removed.
     */
    public void removeBy(Actor actor, Instant at) {
        Objects.requireNonNull(actor, "actor");
        if (!actor.isAuthorOf(this) && !actor.admin()) {
            throw new PostRemovalForbiddenException(id);
        }
        if (isRemoved()) {
            return;
        }
        remove(actor.id(), at);
    }

    /** Low-level change without permission checks; callers acting for a user use {@link #editBy}. */
    public void edit(String title, String content, Instant at) {
        Instant updatedAt = Objects.requireNonNull(at, "at");
        this.title = Objects.requireNonNull(title, "title");
        this.updatedAt = updatedAt;
        this.content = content;
    }

    public void updateScore(int score) {
        this.score = score;
    }

    public void updateCommentCount(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("Comment count cannot be negative");
        }
        this.commentCount = count;
    }

    /** Low-level change without permission checks; callers acting for a user use {@link #removeBy}. */
    public void remove(Long userId, Instant at) {
        Instant removedAt = Objects.requireNonNull(at, "at");
        this.removedBy = Objects.requireNonNull(userId, "userId");
        this.removedAt = removedAt;
    }
}

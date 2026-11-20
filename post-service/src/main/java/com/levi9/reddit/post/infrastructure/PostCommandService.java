package com.levi9.reddit.post.infrastructure;

import com.levi9.reddit.post.contracts.CreatePostRequest;
import com.levi9.reddit.post.contracts.EditPostRequest;
import com.levi9.reddit.post.contracts.PostResponse;
import com.levi9.reddit.post.core.CommunityDirectory;
import com.levi9.reddit.post.core.ImageStorage;
import com.levi9.reddit.post.core.exception.CommunityUnavailableException;
import com.levi9.reddit.post.core.exception.NotCommunityMemberException;
import com.levi9.reddit.post.core.exception.PostNotFoundException;
import com.levi9.reddit.post.core.exception.ValidationException;
import com.levi9.reddit.post.core.model.Actor;
import com.levi9.reddit.post.core.model.ImageUpload;
import com.levi9.reddit.post.core.model.Post;
import com.levi9.reddit.post.infrastructure.repository.PostRepository;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Creating, editing and removing posts on behalf of a logged-in {@link Actor}. Who may edit or remove is decided by
 * {@link Post#editBy} and {@link Post#removeBy}; this service loads, checks the community and saves.
 */
@Service
@Transactional
public class PostCommandService {
    private static final Logger log = LoggerFactory.getLogger(PostCommandService.class);

    private final PostRepository posts;
    private final CommunityDirectory communities;
    private final ImageStorage imageStorage;
    private final Clock clock;

    public PostCommandService(
            PostRepository posts, CommunityDirectory communities, ImageStorage imageStorage, Clock clock) {
        this.posts = posts;
        this.communities = communities;
        this.imageStorage = imageStorage;
        this.clock = clock;
    }

    /**
     * Creates a post with up to three images. Everything that can reject the request (images, community,
     * membership) is checked before any file goes to storage. S3 is not part of the database transaction, so files
     * uploaded here are deleted again if the transaction rolls back.
     *
     * @param images raw file bytes in the order the client sent them; may be empty
     * @throws ValidationException           too many images, one too large or not an image (422)
     * @throws CommunityUnavailableException the community doesn't exist or was shut down (422)
     * @throws NotCommunityMemberException   the caller hasn't joined the community (403)
     */
    public PostResponse create(CreatePostRequest request, List<byte[]> images, Actor actor) {
        List<ImageUpload> uploads = ImageUpload.validate(images);
        long communityId = request.communityId();
        if (!communities.isActive(communityId)) {
            throw new CommunityUnavailableException(communityId);
        }
        if (!communities.isMember(communityId, actor.id())) {
            throw new NotCommunityMemberException(communityId);
        }

        // The author always comes from the caller, never from the request.
        Post post = new Post(request.title(), request.content(), actor.id(), actor.username(), communityId);
        List<String> storedKeys = uploads.isEmpty() ? List.of() : deleteOnRollback();
        for (ImageUpload upload : uploads) {
            String key = "posts/" + UUID.randomUUID() + "." + upload.type().extension();
            imageStorage.store(key, upload.content(), upload.type().contentType());
            storedKeys.add(key);
            post.addImage(key);
        }
        return PostMapper.toResponse(posts.save(post));
    }

    /**
     * Only the author may edit; a removed post is a 404.
     *
     * @throws PostNotFoundException the post doesn't exist or was removed (404)
     */
    public PostResponse edit(long postId, EditPostRequest request, Actor actor) {
        Post post = loadForUpdate(postId);
        post.editBy(actor, request.title(), request.content(), clock.instant());
        return PostMapper.toResponse(post);
    }

    /**
     * Soft remove by the author or an admin. Removing an already removed post succeeds without changes.
     *
     * @throws PostNotFoundException the post never existed (404)
     */
    public void delete(long postId, Actor actor) {
        loadForUpdate(postId).removeBy(actor, clock.instant());
    }

    /**
     * A list the caller fills with stored image keys; when the surrounding transaction rolls back (an exception
     * here, a failed flush or a failed commit), every key in it is deleted from storage. A failed delete is only
     * logged: the request already failed for another reason, and an orphaned file is harmless.
     */
    private List<String> deleteOnRollback() {
        List<String> keys = new ArrayList<>();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) {
                    return;
                }
                for (String key : keys) {
                    try {
                        imageStorage.delete(key);
                    } catch (RuntimeException e) {
                        log.warn("Could not delete image {} after a failed post creation", key, e);
                    }
                }
            }
        });
        return keys;
    }

    // The row lock makes concurrent edits/removals of one post run one after another instead of interleaving.
    private Post loadForUpdate(long postId) {
        return posts.findByIdForUpdate(postId).orElseThrow(() -> new PostNotFoundException(postId));
    }
}

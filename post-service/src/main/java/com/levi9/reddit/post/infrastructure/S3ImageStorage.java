package com.levi9.reddit.post.infrastructure;

import com.levi9.reddit.post.core.ImageStorage;
import java.util.Optional;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

/** {@link ImageStorage} on the bucket from {@code app.s3.bucket}. The content type is kept as object metadata. */
@Component
class S3ImageStorage implements ImageStorage {
    private static final String FALLBACK_CONTENT_TYPE = "application/octet-stream";

    private final S3Client s3;
    private final String bucket;

    S3ImageStorage(S3Client s3, S3Properties properties) {
        this.s3 = s3;
        this.bucket = properties.bucket();
    }

    @Override
    public void store(String key, byte[] content, String contentType) {
        s3.putObject(b -> b.bucket(bucket).key(key).contentType(contentType), RequestBody.fromBytes(content));
    }

    @Override
    public Optional<StoredImage> load(String key) {
        try {
            var object = s3.getObjectAsBytes(b -> b.bucket(bucket).key(key));
            String contentType = object.response().contentType();
            return Optional.of(new StoredImage(
                    object.asByteArray(), contentType == null ? FALLBACK_CONTENT_TYPE : contentType));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        }
    }

    @Override
    public void delete(String key) {
        s3.deleteObject(b -> b.bucket(bucket).key(key)); // S3 answers 204 for a missing key too
    }
}

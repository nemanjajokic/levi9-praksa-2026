package com.levi9.reddit.post.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.levi9.reddit.post.core.exception.CommunityUnavailableException;
import com.levi9.reddit.post.core.exception.DomainException;
import com.levi9.reddit.post.core.exception.ImageNotFoundException;
import com.levi9.reddit.post.core.exception.ImageTooLargeException;
import com.levi9.reddit.post.core.exception.TooManyImagesException;
import com.levi9.reddit.post.core.exception.UnsupportedImageTypeException;
import com.levi9.reddit.post.core.exception.NotCommunityMemberException;
import com.levi9.reddit.post.core.exception.NotPostAuthorException;
import com.levi9.reddit.post.core.exception.PostNotFoundException;
import com.levi9.reddit.post.core.exception.PostRemovalForbiddenException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.ProblemDetail;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    static Stream<Arguments> domainExceptions() {
        return Stream.of(
                Arguments.of(new PostNotFoundException(3L), 404, "POST_NOT_FOUND"),
                Arguments.of(new ImageNotFoundException(3L, 10L), 404, "IMAGE_NOT_FOUND"),
                Arguments.of(new NotPostAuthorException(3L), 403, "NOT_POST_AUTHOR"),
                Arguments.of(new PostRemovalForbiddenException(3L), 403, "POST_REMOVAL_FORBIDDEN"),
                Arguments.of(new NotCommunityMemberException(1L), 403, "NOT_COMMUNITY_MEMBER"),
                Arguments.of(new CommunityUnavailableException(1L), 422, "COMMUNITY_UNAVAILABLE"),
                Arguments.of(new TooManyImagesException(3), 422, "TOO_MANY_IMAGES"),
                Arguments.of(new UnsupportedImageTypeException(1), 422, "UNSUPPORTED_IMAGE_TYPE"),
                Arguments.of(new ImageTooLargeException(1, 5L * 1024 * 1024), 422, "IMAGE_TOO_LARGE"));
    }

    @ParameterizedTest
    @MethodSource("domainExceptions")
    void mapsEachDomainExceptionToStatusAndErrorType(DomainException ex, int status, String errorType) {
        ProblemDetail problem = handler.handleDomain(ex);

        assertThat(problem.getStatus()).isEqualTo(status);
        assertThat(problem.getProperties()).containsEntry("errorType", errorType);
        assertThat(problem.getDetail()).isEqualTo(ex.getMessage());
    }

    @Test
    void unexpectedException_isBare500WithoutInternals() {
        ProblemDetail problem = handler.handleUnexpected(new IllegalStateException("db password is hunter2"));

        assertThat(problem.getStatus()).isEqualTo(500);
        assertThat(problem.getDetail()).isNull();
        assertThat(problem.getProperties()).isNull();
    }
}

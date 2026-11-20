package com.levi9.reddit.post.contracts;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PostRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    void create_valid_withAndWithoutContent() {
        assertThat(invalidFields(new CreatePostRequest("Title", "Body", 1L))).isEmpty();
        assertThat(invalidFields(new CreatePostRequest("Title", null, 1L))).isEmpty();
    }

    @Test
    void create_titleIsRequiredAndLimited() {
        assertThat(invalidFields(new CreatePostRequest(null, null, 1L))).containsExactly("title");
        assertThat(invalidFields(new CreatePostRequest("   ", null, 1L))).containsExactly("title");
        assertThat(invalidFields(new CreatePostRequest("x".repeat(PostLimits.TITLE_MAX), null, 1L))).isEmpty();
        assertThat(invalidFields(new CreatePostRequest("x".repeat(PostLimits.TITLE_MAX + 1), null, 1L)))
                .containsExactly("title");
    }

    @Test
    void create_contentIsLimited() {
        assertThat(invalidFields(new CreatePostRequest("Title", "x".repeat(PostLimits.CONTENT_MAX + 1), 1L)))
                .containsExactly("content");
    }

    @Test
    void create_communityIdIsRequiredAndPositive() {
        assertThat(invalidFields(new CreatePostRequest("Title", null, null))).containsExactly("communityId");
        assertThat(invalidFields(new CreatePostRequest("Title", null, 0L))).containsExactly("communityId");
    }

    @Test
    void edit_valid_withAndWithoutContent() {
        assertThat(invalidFields(new EditPostRequest("New title", "New body"))).isEmpty();
        assertThat(invalidFields(new EditPostRequest("New title", null))).isEmpty();
    }

    @Test
    void edit_titleIsRequiredAndLimited_contentIsLimited() {
        assertThat(invalidFields(new EditPostRequest("", null))).containsExactly("title");
        assertThat(invalidFields(new EditPostRequest("x".repeat(PostLimits.TITLE_MAX + 1), null)))
                .containsExactly("title");
        assertThat(invalidFields(new EditPostRequest("Title", "x".repeat(PostLimits.CONTENT_MAX + 1))))
                .containsExactly("content");
    }

    private static Set<String> invalidFields(Object request) {
        return validator.validate(request).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }
}

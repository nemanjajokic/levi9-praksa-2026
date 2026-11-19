package com.levi9.reddit.auth.contracts;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The request rules agreed on 2026-11-18 (workspace DECISIONS.md) and the response shape clients rely on.
 * Each test names the rule, not the annotation that implements it.
 */
class AuthContractsTest {
    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();

    private static final String VALID_PASSWORD = "correct horse battery";

    @AfterAll
    static void closeFactory() {
        FACTORY.close();
    }

    // --- Register: username is 3-30 letters, digits or underscores ---

    @ParameterizedTest
    @ValueSource(strings = {"abc", "marko_92", "ABC_def_123", "a23456789012345678901234567890"})
    void acceptsUsernamesOfLettersDigitsAndUnderscores(String username) {
        assertThat(violations(register(username, "marko@example.test", VALID_PASSWORD))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ab", "a234567890123456789012345678901", "marko markovic", "marko/admin",
            "marko?x", "márko", "marko.m", "marko-m", ""})
    void rejectsUsernamesThatBreakTheFormat(String username) {
        assertThat(violatedFields(register(username, "marko@example.test", VALID_PASSWORD))).containsOnly("username");
    }

    // --- Register: password is at least 12 characters of any kind, at most 72 bytes (BCrypt), no composition rules ---

    @ParameterizedTest
    @ValueSource(strings = {
            "twelve chars",                 // exactly 12, spaces allowed
            "alllowercaseletters",          // no digit or symbol required
            "šđčćž ünïcødé pässwörd",       // any characters allowed
    })
    void acceptsAnyPasswordOfAtLeastTwelveCharacters(String password) {
        assertThat(violations(register("marko", "marko@example.test", password))).isEmpty();
    }

    @Test
    void acceptsAPasswordOfExactly72Bytes() {
        assertThat(violations(register("marko", "marko@example.test", "a".repeat(72)))).isEmpty();
        assertThat(violations(register("marko", "marko@example.test", "š".repeat(36)))).isEmpty(); // 2 bytes each
    }

    @Test
    void rejectsAPasswordLongerThan72Bytes() {
        assertThat(violatedFields(register("marko", "marko@example.test", "a".repeat(73)))).containsOnly("password");
    }

    @Test
    void countsBytesNotCharactersForTheMaximum() {
        // 37 characters, but 74 bytes in UTF-8: BCrypt would ignore or reject the end of it.
        assertThat(violatedFields(register("marko", "marko@example.test", "š".repeat(37)))).containsOnly("password");
    }

    @Test
    void rejectsAPasswordShorterThanTwelveCharacters() {
        assertThat(violatedFields(register("marko", "marko@example.test", "elevenchars"))).containsOnly("password");
    }

    @Test
    void rejectsABlankPassword() {
        assertThat(violatedFields(register("marko", "marko@example.test", " ".repeat(12)))).containsOnly("password");
    }

    // --- Register: email and names ---

    @ParameterizedTest
    @ValueSource(strings = {"", "not-an-email", "marko@"})
    void rejectsAnInvalidEmail(String email) {
        assertThat(violatedFields(register("marko", email, VALID_PASSWORD))).containsOnly("email");
    }

    @Test
    void rejectsAnEmailLongerThanTheColumn() {
        String email = "a".repeat(64) + "@" + "b".repeat(185) + ".test"; // 255 characters

        assertThat(violatedFields(register("marko", email, VALID_PASSWORD))).contains("email");
    }

    @Test
    void requiresFirstAndLastNameWithinTheColumnLength() {
        var blank = new RegisterRequest("marko", "marko@example.test", VALID_PASSWORD, " ", "");
        var tooLong = new RegisterRequest("marko", "marko@example.test", VALID_PASSWORD, "a".repeat(51), "b".repeat(51));

        assertThat(violatedFields(blank)).containsOnly("firstName", "lastName");
        assertThat(violatedFields(tooLong)).containsOnly("firstName", "lastName");
    }

    private static RegisterRequest register(String username, String email, String password) {
        return new RegisterRequest(username, email, password, "Marko", "Markovic");
    }

    private static <T> Set<ConstraintViolation<T>> violations(T request) {
        return VALIDATOR.validate(request);
    }

    private static <T> Set<String> violatedFields(T request) {
        return violations(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(java.util.stream.Collectors.toSet());
    }
}

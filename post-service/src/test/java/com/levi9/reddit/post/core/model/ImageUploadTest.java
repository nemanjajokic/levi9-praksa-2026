package com.levi9.reddit.post.core.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.levi9.reddit.post.core.exception.ImageTooLargeException;
import com.levi9.reddit.post.core.exception.TooManyImagesException;
import com.levi9.reddit.post.core.exception.UnsupportedImageTypeException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class ImageUploadTest {

    static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10};
    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 0x0D};
    static final byte[] GIF = "GIF89a....".getBytes(StandardCharsets.US_ASCII);
    static final byte[] WEBP = "RIFF\0\0\0\0WEBPVP8 ".getBytes(StandardCharsets.US_ASCII);
    static final byte[] TEXT = "hello, I am not an image".getBytes(StandardCharsets.US_ASCII);

    @Test
    void detectsJpegAndPngByTheirBytes() {
        assertThat(ImageType.detect(JPEG)).contains(ImageType.JPEG);
        assertThat(ImageType.detect(PNG)).contains(ImageType.PNG);
    }

    @Test
    void rejectsGifAndWebp() {
        assertThat(ImageType.detect(GIF)).isEmpty();
        assertThat(ImageType.detect(WEBP)).isEmpty();
        assertThatThrownBy(() -> ImageUpload.validate(List.of(GIF)))
                .isInstanceOf(UnsupportedImageTypeException.class)
                .hasMessage("Image 1 is not a JPEG or PNG image");
        assertThatThrownBy(() -> ImageUpload.validate(List.of(PNG, WEBP)))
                .isInstanceOf(UnsupportedImageTypeException.class)
                .hasMessageContaining("Image 2");
    }

    @Test
    void rejectsOtherOrTruncatedContent() {
        assertThat(ImageType.detect(TEXT)).isEmpty();
        assertThat(ImageType.detect(new byte[0])).isEmpty();
        assertThat(ImageType.detect(new byte[] {(byte) 0xFF, (byte) 0xD8})).as("too short for JPEG").isEmpty();
        assertThat(ImageType.detect(new byte[] {(byte) 0x89, 'P', 'N', 'G'})).as("too short for PNG").isEmpty();
    }

    @Test
    void validate_acceptsUpToThreeImagesInOrder() {
        List<ImageUpload> images = ImageUpload.validate(List.of(PNG, JPEG, PNG));

        assertThat(images).extracting(ImageUpload::type)
                .containsExactly(ImageType.PNG, ImageType.JPEG, ImageType.PNG);
    }

    @Test
    void validate_noImages_isFine() {
        assertThat(ImageUpload.validate(List.of())).isEmpty();
    }

    @Test
    void validate_moreThanThree_isRejected() {
        assertThatThrownBy(() -> ImageUpload.validate(List.of(PNG, PNG, PNG, PNG)))
                .isInstanceOf(TooManyImagesException.class);
    }

    @Test
    void validate_notAnImage_namesItsPosition() {
        assertThatThrownBy(() -> ImageUpload.validate(List.of(PNG, TEXT)))
                .isInstanceOf(UnsupportedImageTypeException.class)
                .hasMessageContaining("Image 2");
    }

    @Test
    void validate_tooLarge_isRejected_exactLimitIsFine() {
        byte[] atLimit = Arrays.copyOf(PNG, (int) ImageUpload.MAX_BYTES);
        byte[] overLimit = Arrays.copyOf(PNG, (int) ImageUpload.MAX_BYTES + 1);

        assertThat(ImageUpload.validate(List.of(atLimit))).hasSize(1);
        assertThatThrownBy(() -> ImageUpload.validate(List.of(overLimit)))
                .isInstanceOf(ImageTooLargeException.class)
                .hasMessageContaining("Image 1");
    }
}

package com.levi9.reddit.post.api;

import com.levi9.reddit.post.contracts.CreatePostRequest;
import java.util.List;
import org.springframework.core.ResolvableType;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads the JSON {@code post} part of {@code POST /api/posts} when the client sent it without a Content-Type.
 *
 * <p>Swagger UI (and some form libraries) send a multipart text field without a type, so Spring sees
 * {@code application/octet-stream} and answers 415. Clients that set {@code application/json} on the part
 * (curl {@code ;type=application/json}, Postman, the frontend) don't need this.
 *
 * <p>Kept narrow on purpose: only for reading, only octet-stream, and only into request records of the
 * {@code contracts} package. It never writes responses.
 */
@Component
class OctetStreamJsonPartConverter extends JacksonJsonHttpMessageConverter {
    private static final String CONTRACTS_PACKAGE = CreatePostRequest.class.getPackageName();

    OctetStreamJsonPartConverter(JsonMapper jsonMapper) {
        super(jsonMapper);
        setSupportedMediaTypes(List.of(MediaType.APPLICATION_OCTET_STREAM));
    }

    @Override
    public boolean canRead(ResolvableType type, MediaType mediaType) {
        Class<?> target = type.resolve();
        return target != null
                && CONTRACTS_PACKAGE.equals(target.getPackageName())
                && super.canRead(type, mediaType);
    }

    @Override
    public boolean canWrite(ResolvableType type, Class<?> valueClass, MediaType mediaType) {
        return false;
    }
}

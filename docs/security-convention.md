# Security convention

How users log in to levddit and how every service knows who is calling. It is OAuth 2.1 with OpenID Connect,
with the gateway as a **BFF** (backend for frontend): the browser never holds a token.

## Who holds what

| Who | Holds | Never holds |
|---|---|---|
| Browser | The gateway's session cookie (`HttpOnly`, so scripts cannot read it) | Any token |
| Gateway | The user's access, refresh and ID tokens, in its session (Postgres) | The signing key, passwords |
| auth-service | Users, password hashes, the signing key, issued authorizations (`auth.oauth2_authorization`) | |
| post-service, community-service | Nothing: they check the access token on every request | Refresh tokens, sessions |

- **auth-service is the authorization server.** It logs users in and issues tokens. Its one OAuth client is the
  gateway (`levddit-gateway`, a confidential client with a secret, PKCE required).
- **Only auth-service holds the private key.** Everyone else checks signatures with the public key from
  `GET /oauth2/jwks`. A public key can check a signature but cannot create one, so it is safe to hand out.

## Tokens

| Token | What it is | Lifetime | Used by |
|---|---|---|---|
| Access token | JWT signed by auth-service with RS256 | 15 minutes | The gateway attaches it to every API call; services check it with the public key, no database |
| Refresh token | Opaque string, stored by auth-service | 7 days, rotated on every use | Only the gateway, to get a new access token from auth-service |
| ID token | JWT for the gateway, saying who logged in | Read once, at login | Only the gateway; also its proof of who is logging out |

**Rotation:** every refresh returns a new refresh token, and the old one stops working.

**Revocation:** a password change or an account deletion deletes the user's authorizations, so the gateway's next
refresh fails and its session loses the tokens. That alone is not enough: auth-service's own login session survives
and could get a new code from `/oauth2/authorize` without a password. So every token issue also checks the user, and
refuses (`invalid_grant`) when the account is deleted or banned, or the password changed after that login. An access
token already issued keeps working until it expires, at most 15 minutes: that is the trade-off of self-contained
tokens, and why they are short.

## Logging in

The browser only ever sees URLs on one origin (dev `http://localhost:3000`); the gateway routes each to its owner.

1. React sends the browser to the gateway's `/oauth2/authorization/levddit`.
2. The gateway redirects to auth-service's `/oauth2/authorize`. Nobody is logged in yet, so it redirects to the
   React page `/signin` (and remembers the authorize request in its session).
3. The page gets a CSRF token from `GET /api/auth/csrf`, then posts the form to `POST /api/auth/login`
   (`email`, `password`, the token in the named header). The answer is JSON:
   - `200 {"redirectUrl": "..."}`: go there (a full page navigation).
   - `401`: wrong email or password. The same answer for unknown, deleted and banned accounts, on purpose.
   - `429`: too many attempts from this IP; `Retry-After` says when to try again.
4. Back at `/oauth2/authorize`, auth-service now knows the user and redirects to the gateway's
   `/login/oauth2/code/levddit` with a code.
5. The gateway exchanges the code (with its secret and the PKCE verifier) at `/oauth2/token`, keeps the tokens in
   its session, and sends the browser to `/`.

**Register** is `POST /api/auth/register` (with the same CSRF token). It answers `201` with the new user and no
tokens; the page then logs in as above.

## How an API request travels

1. The browser calls `/api/...` on the gateway, with its session cookie.
2. The gateway finds the session and attaches `Authorization: Bearer <access token>`, refreshing it first if it
   has expired. No session (or the refresh failed): `401`, and the frontend sends the user to log in.
3. The service checks the token with its cached public key (signature, expiry, issuer), then reads the user from it.

Services check the token themselves, so they never trust that a request came through the gateway. Only the
frontend, gateway and Postgres are published to the host; services are reachable only inside the Docker network.

**The gateway does not decide which routes need a login.** Each service's `SecurityConfig` decides what is public
and what needs a token, so the list of public routes lives in one place: the service that owns them.

## Access token claims

| Claim | Value | Use |
|---|---|---|
| `iss` | The public origin, e.g. `http://localhost:3000` (`APP_PUBLIC_URL`) | Reject tokens from anyone else |
| `sub` | User id, as a string | Who is calling. **Always take the user id from here, never from the request body.** |
| `username` | Username | Copied onto posts and comments (`author_username`) |
| `roles` | e.g. `["USER"]` or `["ADMIN"]` | Mapped to `ROLE_USER` / `ROLE_ADMIN` |
| `exp`, `iat` | Expiry and issue time | Checked automatically |

`username` and `roles` are read from the database each time a token is issued, so a role change shows up at the
gateway's next refresh, within 15 minutes.

## Adding it to your service

The dependency `spring-boot-starter-security-oauth2-resource-server` is already in every service's pom.
auth-service's own API (`/api/users/**`) is set up the same way: see its `UserApiSecurityConfig`.

### 1. `application.yml`

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          jwk-set-uri: ${AUTH_JWKS_URI:http://localhost:8081/oauth2/jwks}
          issuer-uri: ${APP_PUBLIC_URL:http://localhost:3000}
          authorities-claim-name: roles
          authority-prefix: ROLE_
```

- `jwk-set-uri`: where to fetch the public key, auth-service's internal address. This is the only key setting.
- `issuer-uri`: used only for the `iss` check here, because `jwk-set-uri` supplies the keys. It must be the
  public origin, exactly as auth-service issues it. Cover it with a test (a token with a different `iss` gets 401).
- `authorities-claim-name` + `authority-prefix`: turn our `roles` claim into Spring authorities from config alone,
  so `hasRole('ADMIN')` works without a custom converter.

### 2. `docker-compose.yml`

```yaml
  post-service:
    environment:
      <<: *db-env
      AUTH_JWKS_URI: http://auth-service:8081/oauth2/jwks
```

### 3. `SecurityConfig`

```java
@Configuration(proxyBeanMethods = false)
class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable) // bearer tokens only: no cookie a forged request could ride on
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/posts/**", "/api/comments/**").permitAll()
                        .requestMatchers("/actuator/health/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()));
        return http.build();
    }
}
```

- `.oauth2ResourceServer(... jwt ...)` switches on bearer-JWT authentication.
- Public routes come from the endpoints document ("public" = no login). Everything else needs a token.
- `anyRequest().authenticated()` stays **last**, so a new endpoint is closed until someone opens it on purpose.
- CSRF is off because services accept bearer tokens only. The browser's cookies stop at the gateway, which checks
  CSRF itself.

### 4. Reading the caller in a controller

```java
@PostMapping
ResponseEntity<PostResponse> create(@Valid @RequestBody CreatePostRequest request,
                                    @AuthenticationPrincipal Jwt jwt) {
    long authorId = Long.parseLong(jwt.getSubject());
    String authorUsername = jwt.getClaimAsString("username");
    ...
}
```

Role gates that only depend on the role ("ADMIN only") can be URL rules or `@PreAuthorize("hasRole('ADMIN')")`
(needs `@EnableMethodSecurity`). Rules about **which** row ("author or ADMIN", "member of this community") belong
in the service: pass the caller's id and roles in, and let the service decide.

### 5. Tests

Tests never call auth-service. `spring-security-test` creates an authenticated JWT directly:

```java
mockMvc.perform(post("/api/posts")
                .with(jwt().jwt(j -> j.subject("42").claim("username", "marko"))
                           .authorities(new SimpleGrantedAuthority("ROLE_USER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated());
```

Every protected endpoint gets at least: no token → 401, wrong role or not the owner → 403, correct caller → success.

## Errors from the security layer

A missing, expired or invalid token is rejected by the security filters **before** any controller runs, so
`GlobalExceptionHandler` never sees it (see `error-convention.md`). Each service must configure an
`AuthenticationEntryPoint` (401) and `AccessDeniedHandler` (403) that write the same problem-detail shape, without
an `errorType`.

## Rules

- Never log tokens, sessions, client secrets or passwords.
- Never put a token in a URL or query string. Only the `Authorization` header.
- Tokens never reach the browser. Anything the frontend needs about the user comes from an API call.
- The user id comes from `sub`, never from the request.
- New endpoints are closed by default; opening one is a deliberate line in `SecurityConfig`.

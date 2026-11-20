# Things to change in production

Local defaults that are fine for `docker compose up` on a laptop but must change before the AWS deploy.
Add an entry whenever you introduce a local-only shortcut. Each entry says what to change and why.

## auth-service

### JWT signing key
- **Local:** `APP_JWT_GENERATE_KEY=true` makes auth-service generate a temporary key pair in memory at every
  start. Nothing secret is needed, but every restart invalidates issued access tokens.
- **Production:** do **not** set `APP_JWT_GENERATE_KEY`. `docker-compose.yml` sets it to `true` for local runs,
  so a production compose file or environment must leave it out. Create one key pair and give auth-service:
  - `APP_JWT_PRIVATE_KEY`: the contents of `private.pem` (one line or several both work)
  - `APP_JWT_KEY_ID`: a name for the key, for example `2026-11`

  ```bash
  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out private.pem
  ```
- Only auth-service gets the private key. The other services need nothing secret: they fetch the public key
  from auth-service's `/.well-known/jwks.json`.
- Never commit `private.pem`. Store it in the environment settings of the deploy target or a secret store.
- If neither a key nor the flag is set, auth-service refuses to start. That is on purpose.

### Rate limits and the client IP
- Login (10 per minute) and register (10 per hour) are limited per client IP, in memory. One auth-service
  instance is assumed: with several, each counts on its own, so the real limit multiplies.
- The client IP comes from the gateway's `X-Forwarded-For` (`server.forward-headers-strategy: native`), which
  auth-service trusts from any private-network address. **auth-service must only be reachable through the
  gateway** (never publish its port or open it in a security group), or a caller could pick its own IP.

## gateway

### Trusted proxies
- **Local and single EC2:** `trusted-proxies` trusts only loopback, because nothing sits in front of the gateway.
  The gateway drops a client's own `X-Forwarded-For` and sends the address it saw.
- **With a load balancer or reverse proxy in front** (ALB, Beanstalk's nginx): the gateway would then see the
  proxy's address for every request, so every user would share one rate limit. Set
  `SPRING_CLOUD_GATEWAY_SERVER_WEBMVC_TRUSTEDPROXIES` to match the proxy's addresses, and check with a
  request through the whole chain which address reaches auth-service before relying on it.

### CORS origin
- **Local:** `app.cors.allowed-origins` is `http://localhost:3000`, the frontend container.
- **Production:** set `APP_CORS_ALLOWED_ORIGINS` to the real frontend URL (the S3 static site or its domain).
  Never use `*`.

## Database

### Credentials
- **Local:** `docker-compose.yml` falls back to `reddit` / `reddit_local` when `.env` does not override them.
- **Production:** set a strong `POSTGRES_PASSWORD` (or the RDS credentials) through the environment. Never rely on
  the compose defaults.

### Published port
- **Local:** Postgres is published on `5432` so you can connect from the host.
- **Production:** do not publish the database port. Only the services should reach it.

## community-service

### Mock authentication
- **Local:** `docker-compose.yml` sets `APP_MOCKAUTH_ENABLED=true`, which puts a hard-coded `ROLE_ADMIN` caller with
  user id `1` into the security context. auth-service cannot issue access tokens yet, so this keeps the ADMIN
  endpoints usable. Startup logs a WARN while the mock filter is registered.
- **Production:** the flag defaults to `false` in code, so leave `APP_MOCKAUTH_ENABLED` unset or `false` and wire the
  real JWT resource server as described in `security-convention.md`. Turning it on would let anyone perform ADMIN
  operations.

## post-service

### S3
- **Local:** `APP_S3_ENDPOINT=http://moto:5000` points post-service at Moto, a fake S3.
- **Production:** remove `APP_S3_ENDPOINT` so the AWS SDK uses real S3, and set `S3_BUCKET` and `AWS_REGION`
  if they differ from the defaults (`levddit-post-images`, `eu-central-1`).
- **Upload size:** `POST /api/posts` accepts up to 3 images of 5 MB each (`spring.servlet.multipart.*`, 16 MB per
  request). Any proxy in front of the gateway (load balancer, ingress, nginx) must allow request bodies of at least
  16 MB, or uploads fail there with 413 before reaching the service.

### Mock authentication
- **Local:** `docker-compose.yml` sets `APP_MOCKAUTH_ENABLED=true`, which gives every request without a token a
  made-up JWT caller: by default user id `1`, username `mockuser`, role `USER`. The optional headers
  `X-Mock-User-Id`, `X-Mock-Username` and `X-Mock-Roles` (comma-separated, e.g. `USER,ADMIN`) switch the caller per
  request, e.g. to check that editing someone else's post is rejected. auth-service cannot issue access tokens yet,
  so this keeps the write endpoints usable. Startup logs a WARN while the mock filter is registered.
- **Production:** the flag defaults to `false` in code, so leave `APP_MOCKAUTH_ENABLED` unset or `false` and wire the
  real JWT resource server as described in `security-convention.md`. Turning it on would let anyone act as any user,
  including ADMIN, by sending a header.

### Community stub
- **Local:** community-service does not have the `/internal/communities/...` endpoints yet, so
  `APP_COMMUNITYSTUB_ENABLED=true` replaces those calls with a stub: every community is active and every user is a
  member of every community. `APP_COMMUNITYSTUB_UNAVAILABLECOMMUNITYIDS` and `APP_COMMUNITYSTUB_NONMEMBERUSERIDS`
  (comma-separated ids, `999` in `docker-compose.yml`) carve out exceptions, so creating a post in community `999`
  gives 422 and creating one as user `999` (`X-Mock-User-Id: 999`) gives 403. Startup logs a WARN while the stub is
  registered.
- **Production:** leave `APP_COMMUNITYSTUB_*` unset. The flag defaults to `false`; without it and without the real
  community-service client the app fails to start instead of letting everyone post everywhere.

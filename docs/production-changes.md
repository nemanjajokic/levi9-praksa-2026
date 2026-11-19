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
  from auth-service's `/oauth2/jwks`.
- Never commit `private.pem`. Store it in the environment settings of the deploy target or a secret store.
- If neither a key nor the flag is set, auth-service refuses to start. That is on purpose.

### OAuth: public URL, gateway client secret, session cookie
- **Local:** the public URL defaults to `http://localhost:3000` (the issuer in every token, and the base of the
  gateway's redirect URI). `docker-compose.yml` sets `APP_OAUTH_GATEWAY_CLIENT_SECRET=local-gateway-secret`.
- **Production:**
  - `APP_PUBLIC_URL`: the site's own `https://` origin, exactly as the browser sees it, no trailing slash.
  - `APP_OAUTH_GATEWAY_CLIENT_SECRET`: a long random value (`openssl rand -base64 32`), given to auth-service and
    the gateway alike. auth-service refuses to start without it.
  - `SERVER_SERVLET_SESSION_COOKIE_SECURE=true`, so the login session cookie is only sent over HTTPS.

### Rate limits and the client IP
- Login (10 per minute) and register (10 per hour) are limited per client IP, in memory. One auth-service
  instance is assumed: with several, each counts on its own, so the real limit multiplies.
- The client IP comes from the gateway's `X-Forwarded-For` (`server.forward-headers-strategy: native`), which
  auth-service trusts from any private-network address. **auth-service must only be reachable through the
  gateway** (never publish its port or open it in a security group), or a caller could pick its own IP.

## gateway

### Sessions and the OAuth client
- The gateway keeps each browser's session, and with it the user's tokens, in Postgres (schema `gateway`), so it
  needs the same `SPRING_DATASOURCE_*` settings as the services.
- **Production:**
  - `APP_PUBLIC_URL`: the same `https://` origin as auth-service's. The login and logout redirects are built on it.
  - `APP_OAUTH_GATEWAY_CLIENT_SECRET`: the same value auth-service has.
  - `SERVER_SERVLET_SESSION_COOKIE_SECURE=true`, so the session cookie is only sent over HTTPS.
- Sessions expire after 30 minutes without a request (Spring's default). Expired rows are deleted by Spring Session
  itself.

### Trusted proxies
- **Local and single EC2:** `trusted-proxies` trusts only loopback, because nothing sits in front of the gateway.
  The gateway drops a client's own `X-Forwarded-For` and sends the address it saw.
- **With a load balancer or reverse proxy in front** (ALB, Beanstalk's nginx): the gateway would then see the
  proxy's address for every request, so every user would share one rate limit. Set
  `SPRING_CLOUD_GATEWAY_SERVER_WEBMVC_TRUSTEDPROXIES` to match the proxy's addresses, and check with a
  request through the whole chain which address reaches auth-service before relying on it.

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

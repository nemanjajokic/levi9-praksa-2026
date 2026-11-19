package com.levi9.reddit.auth.api;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TimeMeter;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * One budget per client IP, refilled gradually. Used for login (a servlet filter, {@link LoginRateLimitFilter}) and
 * register (an MVC interceptor, {@link RateLimitInterceptor}).
 * <p>
 * The client is {@code request.getRemoteAddr()}. Behind the gateway that would be the gateway itself, so
 * {@code server.forward-headers-strategy: native} makes Tomcat set it from {@code X-Forwarded-For} instead: it reads
 * the list from the right, skips private-network addresses (the gateway), and takes the first public one. Entries a
 * client adds on the left are never reached.
 * <p>
 * Buckets live in memory, so each auth-service instance counts on its own. Fine for the one instance we run.
 */
class RateLimiter {
    /** Memory bound. If an attacker uses more IPs than this, the oldest buckets are dropped and start full again. */
    private static final long MAX_TRACKED_CLIENTS = 100_000;

    private final Bandwidth bandwidth;
    private final TimeMeter time;
    private final Cache<String, Bucket> buckets;

    RateLimiter(RateLimitProperties.Limit limit, Clock clock) {
        this.bandwidth = Bandwidth.builder()
                .capacity(limit.requests())
                .refillGreedy(limit.requests(), limit.per())
                .build();
        this.time = new ClockTimeMeter(clock);
        // A bucket nobody used for a whole period has refilled completely, so dropping it changes nothing.
        this.buckets = Caffeine.newBuilder()
                .expireAfterAccess(limit.per())
                .maximumSize(MAX_TRACKED_CLIENTS)
                .build();
    }

    /** Spends one attempt for this client. Empty when allowed; otherwise how long until the next one would be. */
    Optional<Duration> tryAcquire(String client) {
        Bucket bucket = buckets.get(client, ignored -> newBucket());
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        return probe.isConsumed() ? Optional.empty() : Optional.of(Duration.ofNanos(probe.getNanosToWaitForRefill()));
    }

    private Bucket newBucket() {
        return Bucket.builder()
                .addLimit(bandwidth)
                .withCustomTimePrecision(time)
                .build();
    }

    /** Bucket4j's time source, read from the service's {@link Clock} so tests can move time instead of waiting. */
    private record ClockTimeMeter(Clock clock) implements TimeMeter {
        @Override
        public long currentTimeNanos() {
            Instant now = clock.instant();
            return now.getEpochSecond() * 1_000_000_000L + now.getNano();
        }

        @Override
        public boolean isWallClockBased() {
            return true;
        }
    }
}

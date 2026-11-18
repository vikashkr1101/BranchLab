package com.branchlab.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

@Component
class AuthLimits {
    private final Cache<String, AtomicInteger> attempts = Caffeine.newBuilder()
            .maximumSize(20_000).expireAfterWrite(Duration.ofMinutes(1)).build();

    void checkAddress(String address) {
        count("ip:" + address, 120);
    }

    void checkAccount(String account) {
        count("account:" + account, 20);
    }

    private void count(String key, int limit) {
        if (attempts.get(key, ignored -> new AtomicInteger()).incrementAndGet() > limit) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Please try again in one minute.");
        }
    }
}

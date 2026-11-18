package com.branchlab.auth;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Component
class Tokens {
    static final Duration ACCESS_LIFETIME = Duration.ofMinutes(15);
    static final Duration REFRESH_LIFETIME = Duration.ofDays(7);
    private static final String ISSUER = "branchlab";

    private final JwtEncoder encoder;
    final JwtDecoder access;
    final JwtDecoder refresh;

    Tokens(@Value("${branchlab.jwt-secret}") String secret) throws GeneralSecurityException {
        SecretKey key;
        if (secret.isBlank()) {
            var generator = KeyGenerator.getInstance("HmacSHA256");
            generator.init(256);
            key = generator.generateKey();
        } else {
            byte[] bytes = Base64.getDecoder().decode(secret);
            if (bytes.length < 32) throw new IllegalArgumentException("JWT signing key must be at least 32 bytes");
            key = new SecretKeySpec(bytes, "HmacSHA256");
        }
        encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        access = decoder(key, "access");
        refresh = decoder(key, "refresh");
    }

    private JwtDecoder decoder(SecretKey key, String use) {
        var decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(ISSUER),
                new JwtClaimValidator<String>("token_use", use::equals)));
        return decoder;
    }

    String issue(UUID userId, String use, Duration lifetime) {
        var now = Instant.now();
        var claims = JwtClaimsSet.builder().issuer(ISSUER).subject(userId.toString()).id(UUID.randomUUID().toString())
                .issuedAt(now).expiresAt(now.plus(lifetime)).claim("token_use", use).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}

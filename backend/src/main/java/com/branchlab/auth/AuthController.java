package com.branchlab.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.UUID;

@RestController
@RequestMapping(value = "/api/v1/auth", consumes = MediaType.APPLICATION_JSON_VALUE)
class AuthController {
    record Signup(@NotBlank @Size(max=120) String workspaceName, @NotBlank @Email @Size(max=254) String email,
                  @NotBlank @Size(min=8, max=72) String password) {
        @Override public String toString() { return "Signup"; }
    }
    record Login(@NotBlank @Email @Size(max=254) String email, @NotBlank @Size(max=72) String password) {
        @Override public String toString() { return "Login"; }
    }
    record Session(String accessToken) {
        @Override public String toString() { return "Session"; }
    }

    private final Accounts accounts;
    private final Tokens tokens;
    private final AuthLimits limits;
    private final boolean secureCookies;

    AuthController(Accounts accounts, Tokens tokens, AuthLimits limits, @Value("${branchlab.secure-cookies}") boolean secureCookies) {
        this.accounts = accounts;
        this.tokens = tokens;
        this.limits = limits;
        this.secureCookies = secureCookies;
    }

    @PostMapping("/signup")
    ResponseEntity<Session> signup(@Valid @RequestBody Signup input, HttpServletRequest request) {
        limits.checkAddress(request.getRemoteAddr());
        limits.checkAccount(Accounts.normalize(input.email()));
        return session(accounts.signup(input.workspaceName(), input.email(), input.password()), HttpStatus.CREATED);
    }

    @PostMapping("/login")
    ResponseEntity<Session> login(@Valid @RequestBody Login input, HttpServletRequest request) {
        limits.checkAddress(request.getRemoteAddr());
        limits.checkAccount(Accounts.normalize(input.email()));
        return session(accounts.login(input.email(), input.password()), HttpStatus.OK);
    }

    @PostMapping("/refresh")
    Session refresh(@CookieValue(name="branchlab_refresh", defaultValue="") String refresh, HttpServletRequest request) {
        limits.checkAddress(request.getRemoteAddr());
        if (!refresh.isBlank()) {
            try {
                var id = UUID.fromString(tokens.refresh.decode(refresh).getSubject());
                limits.checkAccount(id.toString());
                return new Session(tokens.issue(id, "access", Tokens.ACCESS_LIFETIME));
            } catch (JwtException | IllegalArgumentException ignored) {
                // Missing, expired and invalid refresh tokens require a new login.
            }
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please sign in again.");
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout() {
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO)).build();
    }

    private ResponseEntity<Session> session(UUID id, HttpStatus status) {
        var refresh = tokens.issue(id, "refresh", Tokens.REFRESH_LIFETIME);
        return ResponseEntity.status(status).header(HttpHeaders.SET_COOKIE, cookie(refresh, Tokens.REFRESH_LIFETIME))
                .body(new Session(tokens.issue(id, "access", Tokens.ACCESS_LIFETIME)));
    }

    private String cookie(String token, Duration lifetime) {
        return ResponseCookie.from("branchlab_refresh", token).httpOnly(true).secure(secureCookies)
                .sameSite("Strict").path("/api/v1/auth").maxAge(lifetime).build().toString();
    }
}

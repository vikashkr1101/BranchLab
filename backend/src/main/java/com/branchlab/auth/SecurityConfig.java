package com.branchlab.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
class SecurityConfig {
    @Bean
    PasswordEncoder passwords() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain security(HttpSecurity http, Tokens tokens, ObjectMapper json) throws Exception {
        AuthenticationEntryPoint unauthorized = (req, res, error) -> {
            res.setStatus(401);
            res.setHeader("WWW-Authenticate", "Bearer");
            res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            json.writeValue(res.getOutputStream(), ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Please sign in."));
        };
        // The API uses bearer authentication. Cookie endpoints require same-origin JSON POSTs.
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(r -> r.requestMatchers("/api/v1/auth/**", "/actuator/health", "/error").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e.authenticationEntryPoint(unauthorized))
                .oauth2ResourceServer(o -> o.jwt(j -> j.decoder(tokens.access)).authenticationEntryPoint(unauthorized))
                .build();
    }
}

package com.branchlab.auth;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

@Service
class Accounts {
    private final JdbcTemplate db;
    private final PasswordEncoder passwords;
    private final String dummyHash;

    Accounts(JdbcTemplate db, PasswordEncoder passwords) {
        this.db = db;
        this.passwords = passwords;
        this.dummyHash = passwords.encode(UUID.randomUUID().toString());
    }

    @Transactional
    UUID signup(String name, String email, String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password exceeds 72 UTF-8 bytes.");
        }
        var workspace = UUID.randomUUID();
        var user = UUID.randomUUID();
        db.update("insert into workspace (id,name) values (?,?)", workspace, name.strip());
        try {
            db.update("insert into app_user (id,workspace_id,email,password_hash,role) values (?,?,?,?,'OWNER')",
                    user, workspace, normalize(email), passwords.encode(password));
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account already exists for this email.");
        }
        return user;
    }

    UUID login(String email, String password) {
        record Account(UUID id, String hash) {}
        var users = db.query("select id,password_hash from app_user where email=?",
                (rs, row) -> new Account(rs.getObject("id", UUID.class), rs.getString("password_hash")), normalize(email));
        var account = users.stream().findFirst();
        if (password.getBytes(StandardCharsets.UTF_8).length > 72 ||
                !passwords.matches(password, account.map(Account::hash).orElse(dummyHash)) || account.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email or password is incorrect.");
        }
        return account.get().id();
    }

    static String normalize(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}

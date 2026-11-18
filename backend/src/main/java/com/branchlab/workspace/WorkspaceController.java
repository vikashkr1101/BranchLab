package com.branchlab.workspace;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
class WorkspaceController {
    record Summary(String name, int datasets, int policies, List<String> recentRuns) {}
    private final JdbcTemplate db;

    WorkspaceController(JdbcTemplate db) {
        this.db = db;
    }

    @GetMapping("/api/v1/workspace")
    Summary workspace(@AuthenticationPrincipal Jwt jwt) {
        return db.query("""
                select w.name,
                    (select count(*) from dataset where workspace_id=w.id) as datasets,
                    (select count(*) from policy where workspace_id=w.id) as policies,
                    array(select e.name from run r join experiment e on e.id=r.experiment_id
                          where e.workspace_id=w.id order by r.created_at desc, r.id limit 5) as recent
                from workspace w join app_user u on u.workspace_id=w.id where u.id=?
                """, (rs, row) -> new Summary(rs.getString("name"), rs.getInt("datasets"), rs.getInt("policies"),
                        List.of((String[]) rs.getArray("recent").getArray())), UUID.fromString(jwt.getSubject()))
                .stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account unavailable."));
    }
}

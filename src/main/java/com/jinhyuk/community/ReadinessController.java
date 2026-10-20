package com.jinhyuk.community;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReadinessController {

    private final JdbcTemplate jdbcTemplate;

    public ReadinessController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/ready")
    public String ready() {
        jdbcTemplate.queryForObject("select 1", Integer.class);
        return "ready";
    }
}

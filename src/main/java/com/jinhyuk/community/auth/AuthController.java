package com.jinhyuk.community.auth;

import java.util.Map;

import com.jinhyuk.community.user.User;
import com.jinhyuk.community.user.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/csrf")
    public CsrfToken csrf(CsrfToken csrfToken) {
        return csrfToken;
    }

    @PostMapping("/register")
    public ResponseEntity<Void> register(
            @RequestParam String email,
            @RequestParam String password) {
        if (!userService.register(email, password)) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping("/me")
    public Map<String, Object> me(Authentication authentication) {
        User currentUser = userService.findByEmail(authentication.getName()).orElseThrow();
        return Map.of(
                "id", currentUser.getId(),
                "email", currentUser.getEmail());
    }
}

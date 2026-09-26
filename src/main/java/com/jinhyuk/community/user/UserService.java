package com.jinhyuk.community.user;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private static final int MAX_EMAIL_LENGTH = 254;
    private static final int MIN_PASSWORD_LENGTH = 15;
    private static final int MAX_PASSWORD_BYTES = 72;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public boolean register(String email, String rawPassword) {
        String normalizedEmail = normalizeEmail(email);
        if (!isValidEmail(normalizedEmail) || !isValidPassword(rawPassword)) {
            return false;
        }
        if (userRepository.existsByEmail(normalizedEmail)) {
            return false;
        }

        String encodedPassword = passwordEncoder.encode(rawPassword);
        try {
            userRepository.saveAndFlush(new User(normalizedEmail, encodedPassword));
            return true;
        } catch (DataIntegrityViolationException exception) {
            return false;
        }
    }

    public Optional<User> findByEmail(String email) {
        String normalizedEmail = normalizeEmail(email);
        if (!isValidEmail(normalizedEmail)) {
            return Optional.empty();
        }
        return userRepository.findByEmail(normalizedEmail);
    }

    private String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        return email.strip().toLowerCase(Locale.ROOT);
    }

    private boolean isValidEmail(String email) {
        if (email == null || email.isBlank() || email.length() > MAX_EMAIL_LENGTH) {
            return false;
        }

        int at = email.indexOf('@');
        return at > 0
                && at == email.lastIndexOf('@')
                && at < email.length() - 1
                && email.chars().noneMatch(Character::isWhitespace);
    }

    private boolean isValidPassword(String password) {
        return password != null
                && !password.isBlank()
                && password.length() >= MIN_PASSWORD_LENGTH
                && password.getBytes(StandardCharsets.UTF_8).length <= MAX_PASSWORD_BYTES;
    }
}

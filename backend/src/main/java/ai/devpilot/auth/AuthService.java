package ai.devpilot.auth;

import ai.devpilot.auth.dto.AuthResponse;
import ai.devpilot.auth.dto.SigninRequest;
import ai.devpilot.auth.dto.SignupRequest;
import ai.devpilot.user.User;
import ai.devpilot.user.UserRepository;

import java.util.Locale;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public AuthResponse signup(SignupRequest request) {
        String email = normalizeEmail(request.email());

        if (userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT, "Email is already registered"
            );
        }

        User user = new User();
        user.setFullName(request.fullName().trim());
        user.setEmail(email);
        user.setProfileId("dp_" + UUID.randomUUID());
        user.setPasswordHash(passwordEncoder.encode(request.password()));

        User savedUser = userRepository.save(user);
        return toResponse(savedUser);
    }

    @Transactional(readOnly = true)
    public AuthResponse signin(SigninRequest request) {
        String email = normalizeEmail(request.email());

        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.UNAUTHORIZED, "Invalid email or password"
            ));

        if (!passwordEncoder.matches(
                request.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(
                HttpStatus.UNAUTHORIZED, "Invalid email or password"
            );
        }

        return toResponse(user);
    }

    @Transactional
    public AuthResponse authenticateGoogle(String email, String fullName) {
        String normalizedEmail = normalizeEmail(email);
        User user = userRepository.findByEmail(normalizedEmail).orElseGet(() -> {
            User newUser = new User();
            newUser.setFullName(fullName == null || fullName.isBlank()
                ? normalizedEmail.substring(0, normalizedEmail.indexOf('@'))
                : fullName.trim());
            newUser.setEmail(normalizedEmail);
            newUser.setProfileId("dp_" + UUID.randomUUID());
            // OAuth accounts do not use a local password, but the existing schema requires a value.
            newUser.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
            return userRepository.save(newUser);
        });
        return toResponse(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse findById(Long id) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.UNAUTHORIZED, "User account no longer exists"));
        return toResponse(user);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private AuthResponse toResponse(User user) {
        return new AuthResponse(
            user.getId(),
            user.getProfileId(),
            user.getFullName(),
            user.getEmail()
        );
    }
}

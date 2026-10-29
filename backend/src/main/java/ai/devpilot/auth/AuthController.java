package ai.devpilot.auth;

import ai.devpilot.auth.dto.AuthResponse;
import ai.devpilot.auth.dto.SigninRequest;
import ai.devpilot.auth.dto.SignupRequest;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> signup(
            @Valid @RequestBody SignupRequest request) {
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(authService.signup(request));
    }

    @PostMapping("/signin")
    public ResponseEntity<AuthResponse> signin(
            @Valid @RequestBody SigninRequest request) {
        return ResponseEntity.ok(authService.signin(request));
    }
}
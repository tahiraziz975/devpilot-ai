package ai.devpilot.auth;

import ai.devpilot.auth.dto.AuthResponse;
import ai.devpilot.auth.dto.SigninRequest;
import ai.devpilot.auth.dto.SignupRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final boolean googleOAuthEnabled;

    public AuthController(
            AuthService authService,
            @Value("${devpilot.oauth.google.enabled:false}") boolean googleOAuthEnabled) {
        this.authService = authService;
        this.googleOAuthEnabled = googleOAuthEnabled;
    }

    @GetMapping("/providers")
    public Map<String, Boolean> providers() {
        return Map.of("google", googleOAuthEnabled);
    }

    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> signup(
            @Valid @RequestBody SignupRequest request,
            HttpServletRequest servletRequest) {
        AuthResponse user = authService.signup(request);
        setSignedInUser(servletRequest, user.id());
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(user);
    }

    @PostMapping("/signin")
    public ResponseEntity<AuthResponse> signin(
            @Valid @RequestBody SigninRequest request,
            HttpServletRequest servletRequest) {
        AuthResponse user = authService.signin(request);
        setSignedInUser(servletRequest, user.id());
        return ResponseEntity.ok(user);
    }

    @GetMapping("/me")
    public AuthResponse currentUser(HttpServletRequest servletRequest) {
        HttpSession session = servletRequest.getSession(false);
        if (session == null || session.getAttribute("devpilotUserId") == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in to continue");
        }
        return authService.findById((Long) session.getAttribute("devpilotUserId"));
    }

    @PostMapping("/signout")
    public ResponseEntity<Void> signout(HttpServletRequest servletRequest) {
        HttpSession session = servletRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ResponseEntity.noContent().build();
    }

    private void setSignedInUser(HttpServletRequest servletRequest, Long userId) {
        HttpSession session = servletRequest.getSession(true);
        servletRequest.changeSessionId();
        session.setAttribute("devpilotUserId", userId);
    }
}

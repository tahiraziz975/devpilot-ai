package ai.devpilot.auth;

import ai.devpilot.auth.dto.AuthResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {
    private final AuthService authService;
    private final String frontendUrl;

    public OAuth2LoginSuccessHandler(
            AuthService authService,
            @Value("${devpilot.oauth.frontend-url:http://localhost:5173}") String frontendUrl) {
        this.authService = authService;
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {
        OAuth2User googleUser = (OAuth2User) authentication.getPrincipal();
        String email = googleUser.getAttribute("email");
        Boolean emailVerified = googleUser.getAttribute("email_verified");

        if (email == null || !Boolean.TRUE.equals(emailVerified)) {
            response.sendRedirect(frontendUrl + "/signin?oauthError=unverified_email");
            return;
        }

        AuthResponse user = authService.authenticateGoogle(email, googleUser.getAttribute("name"));
        HttpSession session = request.getSession(true);
        request.changeSessionId();
        session.setAttribute("devpilotUserId", user.id());
        response.sendRedirect(frontendUrl + "/");
    }
}

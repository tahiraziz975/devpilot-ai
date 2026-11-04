package ai.devpilot.user;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserSettingsService {
    private static final String USER_ID_SESSION_KEY = "devpilotUserId";

    private final UserRepository userRepository;
    private final UserSettingsRepository settingsRepository;

    public UserSettingsService(UserRepository userRepository, UserSettingsRepository settingsRepository) {
        this.userRepository = userRepository;
        this.settingsRepository = settingsRepository;
    }

    @Transactional
    public UserSettingsResponse get(HttpServletRequest request) {
        return toResponse(getOrCreate(currentUserId(request)));
    }

    @Transactional
    public UserSettingsResponse updateTheme(HttpServletRequest request, String theme) {
        UserSettings settings = getOrCreate(currentUserId(request));
        settings.setTheme(theme);
        return toResponse(settingsRepository.save(settings));
    }

    private UserSettings getOrCreate(Long userId) {
        return settingsRepository.findByUser_Id(userId).orElseGet(() -> {
            User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User account not found"));
            UserSettings settings = new UserSettings();
            settings.setUser(user);
            return settingsRepository.save(settings);
        });
    }

    private Long currentUserId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Object value = session == null ? null : session.getAttribute(USER_ID_SESSION_KEY);
        if (value instanceof Long userId) return userId;
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in to manage your settings");
    }

    private UserSettingsResponse toResponse(UserSettings settings) {
        return new UserSettingsResponse(settings.getTheme(), settings.isEmailNotifications());
    }
}

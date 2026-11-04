package ai.devpilot.user;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings")
public class UserSettingsController {
    private final UserSettingsService settingsService;

    public UserSettingsController(UserSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping
    public UserSettingsResponse get(HttpServletRequest request) {
        return settingsService.get(request);
    }

    @PutMapping
    public UserSettingsResponse updateTheme(
            @Valid @RequestBody UpdateThemeRequest body,
            HttpServletRequest request) {
        return settingsService.updateTheme(request, body.theme());
    }
}

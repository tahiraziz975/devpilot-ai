
package ai.devpilot.user;

import jakarta.persistence.*;

@Entity
@Table(name = "user_settings")
public class UserSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false, length = 20)
    private String theme = "dark";

    @Column(name = "email_notifications", nullable = false)
    private boolean emailNotifications = true;

    protected UserSettings() {}

    public Long getId() { return id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getTheme() { return theme; }
    public void setTheme(String theme) { this.theme = theme; }

    public boolean isEmailNotifications() { return emailNotifications; }
    public void setEmailNotifications(boolean enabled) {
        this.emailNotifications = enabled;
    }
}

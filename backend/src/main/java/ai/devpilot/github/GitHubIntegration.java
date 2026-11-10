package ai.devpilot.github;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "github_integrations", uniqueConstraints = {
    @UniqueConstraint(name = "uk_github_installation_id", columnNames = "installation_id")
})
public class GitHubIntegration {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;
    @Column(name = "installation_id", nullable = false, unique = true)
    private Long installationId;
    @Column(name = "account_id", nullable = false)
    private Long accountId;
    @Column(name = "account_login", nullable = false, length = 100)
    private String accountLogin;
    @Column(name = "account_type", nullable = false, length = 30)
    private String accountType;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected GitHubIntegration() {}
    public GitHubIntegration(Long userId, Long installationId, Long accountId,
            String accountLogin, String accountType) {
        this.userId = userId;
        this.installationId = installationId;
        this.accountId = accountId;
        this.accountLogin = accountLogin;
        this.accountType = accountType;
        this.status = "ACTIVE";
        this.updatedAt = Instant.now();
    }
    public Long getUserId() { return userId; }
    public Long getInstallationId() { return installationId; }
    public Long getAccountId() { return accountId; }
    public String getAccountLogin() { return accountLogin; }
    public String getAccountType() { return accountType; }
    public String getStatus() { return status; }
    public void refresh(Long accountId, String accountLogin, String accountType) {
        this.accountId = accountId;
        this.accountLogin = accountLogin;
        this.accountType = accountType;
        this.status = "ACTIVE";
        this.updatedAt = Instant.now();
    }
    public void markUninstalled() { status = "UNINSTALLED"; updatedAt = Instant.now(); }
}

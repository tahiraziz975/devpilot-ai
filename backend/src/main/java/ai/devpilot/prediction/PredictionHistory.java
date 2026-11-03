package ai.devpilot.prediction;

import ai.devpilot.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "prediction_history")
public class PredictionHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "issue_title", nullable = false, columnDefinition = "TEXT")
    private String issueTitle;

    @Column(name = "issue_body", columnDefinition = "LONGTEXT")
    private String issueBody;

    @Column(name = "predicted_label", nullable = false, length = 100)
    private String predictedLabel;

    @Column(columnDefinition = "JSON")
    private String probabilities;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected PredictionHistory() {}

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getIssueTitle() { return issueTitle; }
    public void setIssueTitle(String issueTitle) { this.issueTitle = issueTitle; }
    public String getIssueBody() { return issueBody; }
    public void setIssueBody(String issueBody) { this.issueBody = issueBody; }
    public String getPredictedLabel() { return predictedLabel; }
    public void setPredictedLabel(String predictedLabel) { this.predictedLabel = predictedLabel; }
    public String getProbabilities() { return probabilities; }
    public void setProbabilities(String probabilities) { this.probabilities = probabilities; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}

package ai.devpilot.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "ai.devpilot")
@EntityScan(basePackages = "ai.devpilot")
@EnableJpaRepositories(basePackages = "ai.devpilot")
public class DevPilotBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(DevPilotBackendApplication.class, args);
    }
}
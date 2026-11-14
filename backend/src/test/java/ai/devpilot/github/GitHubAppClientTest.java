package ai.devpilot.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.client.RestClient;

class GitHubAppClientTest {
    @Test
    void missingRepositoryFileIsReportedAsNotFound() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GitHubAppClient client = new GitHubAppClient("", "", builder);
        server.expect(requestTo("https://api.github.com/repos/octo/app/contents/src/Main.java?ref=main"))
            .andRespond(withStatus(HttpStatus.NOT_FOUND));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
            () -> client.readRepositoryFile("token", "octo/app", "src/Main.java", "main"));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        server.verify();
    }
}

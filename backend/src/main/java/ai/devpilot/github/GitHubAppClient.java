package ai.devpilot.github;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Component
public class GitHubAppClient {
    private final String appId;
    private final String privateKey;
    private final RestClient client;

    public GitHubAppClient(@Value("${devpilot.github.app-id:}") String appId,
            @Value("${devpilot.github.private-key:}") String privateKey,
            RestClient.Builder builder) {
        this.appId = appId;
        this.privateKey = privateKey;
        this.client = builder.baseUrl("https://api.github.com").build();
    }

    public Installation verifyInstallation(long installationId) {
        if (appId.isBlank() || privateKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "GitHub integration is not configured");
        }
        try {
            return client.get().uri("/app/installations/{id}", installationId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + appJwt())
                .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .retrieve().body(Installation.class);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "GitHub installation could not be verified");
        }
    }

    private String appJwt() throws Exception {
        long now = Instant.now().getEpochSecond();
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String header = encoder.encodeToString("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = encoder.encodeToString(("{\"iat\":" + (now - 30) + ",\"exp\":" + (now + 540) + ",\"iss\":\"" + appId + "\"}").getBytes(StandardCharsets.UTF_8));
        String input = header + "." + payload;
        String pem = privateKey.replace("\\n", "\n").replaceAll("-----BEGIN (RSA )?PRIVATE KEY-----", "").replaceAll("-----END (RSA )?PRIVATE KEY-----", "").replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(pem);
        if (privateKey.contains("BEGIN RSA PRIVATE KEY")) der = wrapPkcs1(der);
        var key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(key);
        signer.update(input.getBytes(StandardCharsets.US_ASCII));
        return input + "." + encoder.encodeToString(signer.sign());
    }

    private byte[] wrapPkcs1(byte[] pkcs1) {
        byte[] version = new byte[] {0x02, 0x01, 0x00};
        byte[] algorithm = new byte[] {0x30, 0x0d, 0x06, 0x09, 0x2a, (byte)0x86, 0x48,
            (byte)0x86, (byte)0xf7, 0x0d, 0x01, 0x01, 0x01, 0x05, 0x00};
        byte[] privateKeyBytes = derValue((byte)0x04, pkcs1);
        return derValue((byte)0x30, concatenate(version, algorithm, privateKeyBytes));
    }
    private byte[] derValue(byte tag, byte[] value) {
        int count = 0, remaining = value.length;
        while (remaining > 0) { count++; remaining >>>= 8; }
        byte[] length;
        if (value.length < 128) length = new byte[] {(byte)value.length};
        else {
            length = new byte[count + 1]; length[0] = (byte)(0x80 | count);
            for (int i = count; i > 0; i--) length[i] = (byte)(value.length >>> (8 * (count - i)));
        }
        byte[] out = new byte[1 + length.length + value.length]; out[0] = tag;
        System.arraycopy(length, 0, out, 1, length.length);
        System.arraycopy(value, 0, out, 1 + length.length, value.length);
        return out;
    }
    private byte[] concatenate(byte[]... values) {
        int size = 0; for (byte[] value : values) size += value.length;
        byte[] result = new byte[size]; int offset = 0;
        for (byte[] value : values) { System.arraycopy(value, 0, result, offset, value.length); offset += value.length; }
        return result;
    }

    public record Installation(Long id, Account account, Map<String, String> permissions) {
        public boolean hasReadOnlyCodePermissions() {
            return permissions != null && "read".equals(permissions.get("metadata"))
                && "read".equals(permissions.get("contents"));
        }
    }
    public record Account(Long id, String login, @JsonProperty("type") String type) {}
}

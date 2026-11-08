package ai.devpilot.auth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@ConditionalOnProperty(name = "devpilot.oauth.google.enabled", havingValue = "true")
public class GoogleOAuthSecurityConfig {
    @Bean
    ClientRegistrationRepository clientRegistrationRepository(
            org.springframework.core.env.Environment environment) {
        String clientId = environment.getRequiredProperty("devpilot.oauth.google.client-id");
        String clientSecret = environment.getRequiredProperty("devpilot.oauth.google.client-secret");
        String redirectUri = environment.getProperty(
            "devpilot.oauth.google.redirect-uri",
            "http://localhost:8080/login/oauth2/code/google");

        ClientRegistration google = ClientRegistration.withRegistrationId("google")
            .clientId(clientId)
            .clientSecret(clientSecret)
            .clientName("Google")
            .authorizationGrantType(org.springframework.security.oauth2.core.AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri(redirectUri)
            .scope("openid", "profile", "email")
            .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
            .tokenUri("https://oauth2.googleapis.com/token")
            .userInfoUri("https://www.googleapis.com/oauth2/v3/userinfo")
            .userNameAttributeName("sub")
            .jwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
            .issuerUri("https://accounts.google.com")
            .build();
        return new InMemoryClientRegistrationRepository(google);
    }

    @Bean
    @Order(1)
    SecurityFilterChain googleOAuthSecurityFilterChain(
            HttpSecurity http,
            OAuth2LoginSuccessHandler successHandler,
            @Value("${devpilot.oauth.frontend-url:http://localhost:5173}") String frontendUrl) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .httpBasic(basic -> basic.disable())
            .formLogin(form -> form.disable())
            .authorizeHttpRequests(auth -> auth
                .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR).permitAll()
                .requestMatchers(
                    "/api/auth/**", "/api/predictions/**", "/api/chat", "/api/chat/**",
                    "/api/settings/**", "/oauth2/**", "/login/oauth2/**",
                    "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**")
                .permitAll()
                .anyRequest().authenticated())
            .oauth2Login(oauth -> oauth
                .successHandler(successHandler)
                .failureUrl(frontendUrl.replaceAll("/+$", "") + "/signin?oauthError=failed"));
        return http.build();
    }
}

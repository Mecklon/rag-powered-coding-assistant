package com.mecklon.RagPoweredCodingAssistant.auth;

import java.io.IOException;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.mecklon.RagPoweredCodingAssistant.configs.JwtService;
import com.mecklon.RagPoweredCodingAssistant.user.User;
import com.mecklon.RagPoweredCodingAssistant.user.UserService;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Runs after a successful GitHub OAuth login. Persists the user (with the
 * GitHub access token), issues a JWT, stores it in an http-only cookie and
 * redirects the browser back to the frontend.
 */
@Component
public class OAuth2LoginSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    private final UserService userService;
    private final JwtService jwtService;
    private final OAuth2AuthorizedClientService authorizedClientService;
    private final String frontendUrl;
    private final boolean secureCookie;

    public OAuth2LoginSuccessHandler(UserService userService,
                                     JwtService jwtService,
                                     OAuth2AuthorizedClientService authorizedClientService,
                                     @Value("${app.frontend-url}") String frontendUrl,
                                     @Value("${app.cookie.secure:false}") boolean secureCookie) {
        this.userService = userService;
        this.jwtService = jwtService;
        this.authorizedClientService = authorizedClientService;
        this.frontendUrl = frontendUrl;
        this.secureCookie = secureCookie;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {

        OAuth2AuthenticationToken token = (OAuth2AuthenticationToken) authentication;
        OAuth2User oauth2User = token.getPrincipal();

        Map<String, Object> attrs = oauth2User.getAttributes();
        String githubId = String.valueOf(attrs.get("id"));
        String login = (String) attrs.get("login");
        String name = (String) attrs.get("name");
        String email = (String) attrs.get("email");
        String avatarUrl = (String) attrs.get("avatar_url");

        // The GitHub access token is available on the OAuth2AuthorizedClient.
        String accessToken = resolveAccessToken(token);

        User user = userService.upsertFromGithub(githubId, login, name, email, avatarUrl, accessToken);

        String jwt = jwtService.generateToken(user.getId(), user.getGithubId(), user.getLogin());

        Cookie cookie = new Cookie("auth_token", jwt);
        cookie.setHttpOnly(true);
        cookie.setSecure(secureCookie);
        cookie.setPath("/");
        cookie.setMaxAge((int) (jwtService.getExpirationMs() / 1000));
        response.addCookie(cookie);

        response.sendRedirect(frontendUrl);
    }

    private String resolveAccessToken(OAuth2AuthenticationToken token) {
        OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient(
                token.getAuthorizedClientRegistrationId(), token.getName());
        return client == null ? null : client.getAccessToken().getTokenValue();
    }
}
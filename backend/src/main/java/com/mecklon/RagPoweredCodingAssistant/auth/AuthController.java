package com.mecklon.RagPoweredCodingAssistant.auth;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mecklon.RagPoweredCodingAssistant.user.User;
import com.mecklon.RagPoweredCodingAssistant.user.UserDto;
import com.mecklon.RagPoweredCodingAssistant.user.UserService;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    /**
     * Called by the frontend on first load. If the http-only JWT cookie is
     * valid, returns the (sanitized) user details; otherwise 401.
     */
    @GetMapping("/autologin")
    public ResponseEntity<?> autologin(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser principal)) {
            return ResponseEntity.status(401).body(Map.of("message", "Not authenticated"));
        }
        User user = userService.findById(principal.id()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("message", "User not found"));
        }
        return ResponseEntity.ok(UserDto.from(user));
    }

    /**
     * Clears the http-only cookie.
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletResponse response) {
        Cookie cookie = new Cookie("auth_token", null);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
        return ResponseEntity.ok(Map.of("message", "Logged out"));
    }
}
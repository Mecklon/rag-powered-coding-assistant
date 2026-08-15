package com.mecklon.RagPoweredCodingAssistant.github;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mecklon.RagPoweredCodingAssistant.auth.AuthenticatedUser;
import com.mecklon.RagPoweredCodingAssistant.user.UserService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/github")
@RequiredArgsConstructor
public class GithubController {

    private final UserService userService;
    private final GithubClientService githubClientService;

    /**
     * Returns the authenticated user's GitHub repositories, fetched live from
     * the GitHub REST API. The JWT identifies the user, whose GitHub access
     * token is read from our database.
     */
    @GetMapping("/repos")
    public ResponseEntity<?> getRepositories(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser principal)) {
            return ResponseEntity.status(401).body(Map.of("message", "Not authenticated"));
        }

        String accessToken = userService.getAccessToken(principal.id());
        if (accessToken == null || accessToken.isBlank()) {
            return ResponseEntity.status(500).body(Map.of("message", "GitHub access token missing"));
        }

        List<GithubRepository> repos = githubClientService.getUserRepositories(accessToken);
        return ResponseEntity.ok(repos);
    }

    /**
     * Checks whether the given repository already has a RAG IDE branch.
     */
    @GetMapping("/repos/{owner}/{repo}/rag-branch")
    public ResponseEntity<?> hasRagBranch(@PathVariable String owner,
                                          @PathVariable String repo,
                                          Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser principal)) {
            return ResponseEntity.status(401).body(Map.of("message", "Not authenticated"));
        }

        String accessToken = userService.getAccessToken(principal.id());
        if (accessToken == null || accessToken.isBlank()) {
            return ResponseEntity.status(500).body(Map.of("message", "GitHub access token missing"));
        }

        boolean exists = githubClientService.hasRagBranch(accessToken, owner, repo);
        return ResponseEntity.ok(Map.of("exists", exists));
    }

    /**
     * Creates a RAG IDE branch in the given repository (branching off the
     * default branch) and returns the created branch name.
     */
    @PostMapping("/repos/{owner}/{repo}/rag-branch")
    public ResponseEntity<?> createRagBranch(@PathVariable String owner,
                                             @PathVariable String repo,
                                             Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser principal)) {
            return ResponseEntity.status(401).body(Map.of("message", "Not authenticated"));
        }

        String accessToken = userService.getAccessToken(principal.id());
        if (accessToken == null || accessToken.isBlank()) {
            return ResponseEntity.status(500).body(Map.of("message", "GitHub access token missing"));
        }

        String branchName = githubClientService.createRagBranch(accessToken, owner, repo);
        return ResponseEntity.ok(Map.of("branch", branchName));
    }
}
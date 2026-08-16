package com.mecklon.RagPoweredCodingAssistant.rag;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mecklon.RagPoweredCodingAssistant.auth.AuthenticatedUser;
import com.mecklon.RagPoweredCodingAssistant.user.UserService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final RepoChatService repoChatService;
    private final UserService userService;

    /**
     * Receives a chat prompt with dirty files, orchestrates the RAG pipeline,
     * and returns a reply (placeholder for now).
     */
    @PostMapping("/prompt")
    public ResponseEntity<?> prompt(@RequestBody ChatRequest request, Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser principal)) {
            return ResponseEntity.status(401).body(Map.of("message", "Not authenticated"));
        }

        String owner = request.owner();
        String repo = request.repo();
        if (owner == null || repo == null || request.prompt() == null || request.prompt().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "owner, repo and prompt are required"));
        }

        List<DirtyFile> dirtyFiles = request.dirtyFiles() == null ? List.of() : request.dirtyFiles();
        Map<String, Object> result = repoChatService.handlePrompt(
                owner, repo, request.defaultBranch(), request.prompt(), request.sessionId(), dirtyFiles);

        return ResponseEntity.ok(result);
    }

    /**
     * Accepts a proposed change: refreshes the backend's local copy of the file
     * and re-vectorizes it.
     */
    @PostMapping("/accept")
    public ResponseEntity<?> acceptChange(@RequestBody AcceptChangeRequest request, Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser principal)) {
            return ResponseEntity.status(401).body(Map.of("message", "Not authenticated"));
        }

        String owner = request.owner();
        String repo = request.repo();
        if (owner == null || repo == null || request.filePath() == null || request.newContent() == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "owner, repo, filePath and newContent are required"));
        }

        repoChatService.acceptChange(owner, repo, null, request.filePath(), request.newContent());
        return ResponseEntity.ok(Map.of("message", "Change accepted"));
    }
}
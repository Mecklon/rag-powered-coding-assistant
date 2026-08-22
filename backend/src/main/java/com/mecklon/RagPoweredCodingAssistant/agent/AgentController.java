package com.mecklon.RagPoweredCodingAssistant.agent;

import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.mecklon.RagPoweredCodingAssistant.auth.AuthenticatedUser;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Exposes the agent loop as a streaming SSE endpoint. Intermediate tool events
 * (TOOL_START, etc.) are streamed to the frontend as they happen.
 */
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentController {

    private static final Logger log = LoggerFactory.getLogger(AgentController.class);

    private final RepoAgentService repoAgentService;

    /**
     * Runs the agent loop for a user request and streams intermediate events
     * (TOOL_START, DONE, ERROR) to the client via Server-Sent Events.
     */
    @PostMapping(value = "/run", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter run(@RequestBody AgentRequest request, Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser principal)) {
            log.warn("[API] /api/agent/run rejected: not authenticated");
            SseEmitter emitter = new SseEmitter();
            try {
                emitter.send(SseEmitter.event().data(Map.of("type", "ERROR", "message", "Not authenticated")));
            } catch (Exception ignored) {
            }
            emitter.complete();
            return emitter;
        }

        String owner = request.owner();
        String repo = request.repo();
        if (owner == null || repo == null || request.prompt() == null || request.prompt().isBlank()) {
            log.warn("[API] /api/agent/run rejected: missing owner/repo/prompt");
            SseEmitter emitter = new SseEmitter();
            try {
                emitter.send(SseEmitter.event().data(Map.of("type", "ERROR", "message", "owner, repo and prompt are required")));
            } catch (Exception ignored) {
            }
            emitter.complete();
            return emitter;
        }

        log.info("[API] POST /api/agent/run | user={} repo={}/{} session={} prompt=\"{}\"",
                principal.id(), owner, repo, request.sessionId(), request.prompt());

        SseEmitter emitter = new SseEmitter(0L); // no timeout

        // Capture the request's SecurityContext for the async worker thread.
        SecurityContext securityContext = SecurityContextHolder.getContext();

        // Run the agent loop asynchronously so the SSE response can stream.
        Thread thread = new Thread(() -> {
            SecurityContextHolder.setContext(securityContext);
            try {
                log.info("[API] starting agent worker thread for {}/{}", owner, repo);
                repoAgentService.run(
                        owner, repo, request.defaultBranch(), request.prompt(), request.sessionId(), emitter);
                log.info("[API] agent worker thread finished for {}/{}", owner, repo);
            } finally {
                // Clear the context to avoid leaking auth state across threads.
                SecurityContextHolder.clearContext();
            }
        });
        thread.setDaemon(true);
        thread.start();
        log.info("[API] agent worker thread spawned, returning SseEmitter");
        return emitter;
    }
}
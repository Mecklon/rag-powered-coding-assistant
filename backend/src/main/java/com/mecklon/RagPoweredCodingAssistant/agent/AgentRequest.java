package com.mecklon.RagPoweredCodingAssistant.agent;

/**
 * Request body for the agent run endpoint.
 */
public record AgentRequest(
        String prompt,
        String owner,
        String repo,
        String defaultBranch,
        String sessionId
) {
}
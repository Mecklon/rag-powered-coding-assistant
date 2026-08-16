package com.mecklon.RagPoweredCodingAssistant.rag;

import java.util.List;

/**
 * Request body for a chat prompt from the frontend.
 */
public record ChatRequest(
        String prompt,
        String owner,
        String repo,
        String defaultBranch,
        String sessionId,
        List<DirtyFile> dirtyFiles
) {
}
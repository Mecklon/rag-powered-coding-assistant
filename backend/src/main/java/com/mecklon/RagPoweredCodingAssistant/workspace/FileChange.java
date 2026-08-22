package com.mecklon.RagPoweredCodingAssistant.workspace;

/**
 * A single file modification performed by the workspace layer. The backend
 * tracks changes independently of the LLM's textual response so the frontend
 * can render an A/M/D/R change list for review.
 *
 * @param path      the file path relative to the repo root
 * @param operation CREATE / MODIFY / DELETE / RENAME
 * @param before    the previous content (or null for CREATE)
 * @param after     the new content (or null for DELETE, or the new path for RENAME)
 */
public record FileChange(
        String path,
        String operation,
        String before,
        String after
) {
}
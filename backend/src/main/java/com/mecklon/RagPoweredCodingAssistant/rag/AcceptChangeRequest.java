package com.mecklon.RagPoweredCodingAssistant.rag;

/**
 * Request to accept a proposed file change: the backend refreshes its local
 * copy of the file and re-vectorizes it.
 */
public record AcceptChangeRequest(
        String owner,
        String repo,
        String filePath,
        String newContent
) {
}
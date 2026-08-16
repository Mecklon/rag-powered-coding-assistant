package com.mecklon.RagPoweredCodingAssistant.rag;

/**
 * A file that has been modified in the frontend and sent to the backend.
 */
public record DirtyFile(
        String path,
        String content
) {
}
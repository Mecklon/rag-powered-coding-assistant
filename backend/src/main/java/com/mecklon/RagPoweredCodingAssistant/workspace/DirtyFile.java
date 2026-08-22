package com.mecklon.RagPoweredCodingAssistant.workspace;

/**
 * A file that has been modified in the frontend and sent to the backend.
 */
public record DirtyFile(
        String path,
        String content
) {
}
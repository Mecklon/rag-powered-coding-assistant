package com.mecklon.RagPoweredCodingAssistant.chunker;

/**
 * Represents a single source file to be chunked.
 */
public record SourceFile(
        String path,
        String language,
        String content,
        String repositoryId,
        String branch
) {
}
package com.mecklon.RagPoweredCodingAssistant.chunker;

/**
 * A single chunk of code produced by the AST chunker.
 */
public class CodeChunk {

    private String content;
    private ChunkMetadata metadata;

    public CodeChunk() {
    }

    public CodeChunk(String content, ChunkMetadata metadata) {
        this.content = content;
        this.metadata = metadata;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public ChunkMetadata getMetadata() {
        return metadata;
    }

    public void setMetadata(ChunkMetadata metadata) {
        this.metadata = metadata;
    }
}
package com.mecklon.RagPoweredCodingAssistant.chunker;

/**
 * Result of parsing a source file into an AST.
 */
public class ParseResult {

    private final boolean parseFailed;
    private final AstNode root;

    public ParseResult(boolean parseFailed, AstNode root) {
        this.parseFailed = parseFailed;
        this.root = root;
    }

    public boolean isParseFailed() {
        return parseFailed;
    }

    public AstNode getRoot() {
        return root;
    }
}
package com.mecklon.RagPoweredCodingAssistant.chunker;

/**
 * Parses source code into a language-agnostic AST.
 */
public interface CodeParser {

    /**
     * Parses the given source content. Returns a result with parseFailed=true
     * if the language is unsupported or parsing fails.
     */
    ParseResult parse(String content, String language);
}
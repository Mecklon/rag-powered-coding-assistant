package com.mecklon.RagPoweredCodingAssistant.chunker;

import org.springframework.stereotype.Component;

/**
 * Deterministic token estimator. Approximates tokens from characters using a
 * heuristic (roughly 4 chars per token, with a floor per word), which is
 * stable and deterministic across runs.
 */
@Component
public class TokenEstimator {

    /**
     * Estimates the number of tokens in the given text.
     */
    public int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        // Heuristic: ~4 characters per token, but never fewer than 1 token
        // per whitespace-delimited word.
        int charTokens = (int) Math.ceil(text.length() / 4.0);
        int wordCount = text.trim().isEmpty() ? 0 : text.trim().split("\\s+").length;
        return Math.max(charTokens, wordCount);
    }
}
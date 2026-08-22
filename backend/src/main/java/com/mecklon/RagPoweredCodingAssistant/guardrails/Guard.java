package com.mecklon.RagPoweredCodingAssistant.guardrails;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.stereotype.Component;

/**
 * Security / validation boundary for the tool layer. Every tool request passes
 * through these guards before the workspace is touched. This prevents path
 * traversal, arbitrary backend file access, ambiguous edits, and oversized
 * reads.
 */
@Component
public class Guard {

    /** Maximum file size (bytes) the workspace will read or write. */
    private static final long MAX_FILE_SIZE = 2_000_000;

    /**
     * Resolves a user-supplied relative path against the repo root, rejecting
     * any path that escapes the repository (e.g. via {@code ../}).
     */
    public Path resolve(Path repoRoot, String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("Path must not be empty");
        }
        Path rootAbs = repoRoot.toAbsolutePath().normalize();
        Path target = rootAbs.resolve(path).normalize();
        if (!target.startsWith(rootAbs)) {
            throw new IllegalArgumentException("Path escapes repository root: " + path);
        }
        return target;
    }

    /** Validates that a file exists, is a regular file, and is within size limits. */
    public void validateRead(Path repoRoot, Path target) {
        if (!Files.exists(target) || !Files.isRegularFile(target)) {
            throw new IllegalArgumentException("File does not exist: " + target.getFileName());
        }
        try {
            if (Files.size(target) > MAX_FILE_SIZE) {
                throw new IllegalArgumentException("File too large to read: " + target.getFileName());
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to stat file: " + target, e);
        }
    }

    /** Validates that a file does not already exist before create_file. */
    public void validateCreate(Path repoRoot, Path target) {
        if (Files.exists(target)) {
            throw new IllegalArgumentException("File already exists: " + target.getFileName());
        }
    }

    /** Validates that an edit target exists and is readable. */
    public void validateEdit(Path repoRoot, Path target) {
        validateRead(repoRoot, target);
    }

    /**
     * Validates that the oldText appears in the file exactly once, so the
     * replacement is unambiguous.
     */
    public void validateEditUnambiguous(String content, String oldText) {
        if (oldText == null || oldText.isEmpty()) {
            throw new IllegalArgumentException("oldText must not be empty");
        }
        int count = countOccurrences(content, oldText);
        if (count == 0) {
            throw new IllegalArgumentException("oldText not found in the file");
        }
        if (count > 1) {
            throw new IllegalArgumentException("oldText is ambiguous: found " + count + " occurrences");
        }
    }

    /** Validates that a delete target exists and is readable. */
    public void validateDelete(Path repoRoot, Path target) {
        validateRead(repoRoot, target);
    }

    /** Validates a rename: source must exist, destination must not. */
    public void validateRename(Path repoRoot, Path source, Path destination) {
        validateRead(repoRoot, source);
        if (Files.exists(destination)) {
            throw new IllegalArgumentException("Destination already exists: " + destination.getFileName());
        }
    }

    private int countOccurrences(String haystack, String needle) {
        int count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) != -1) {
            count++;
            idx += needle.length();
        }
        return count;
    }
}
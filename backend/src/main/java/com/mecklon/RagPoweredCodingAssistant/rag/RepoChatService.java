package com.mecklon.RagPoweredCodingAssistant.rag;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.springframework.stereotype.Service;

import com.mecklon.RagPoweredCodingAssistant.chunker.AstCodeChunker;
import com.mecklon.RagPoweredCodingAssistant.chunker.CodeChunk;
import com.mecklon.RagPoweredCodingAssistant.chunker.SourceFile;

/**
 * Orchestrates the RAG pipeline per chat prompt:
 * 1. Ensure the repo is cloned (only cloned on the first prompt).
 * 2. Apply dirty files to the local clone.
 * 3. Chunk the repo (first time) or just the dirty files (later).
 * 4. Index into the vector DB (placeholder).
 */
@Service
public class RepoChatService {

    private final RepoCloneService cloneService;
    private final AstCodeChunker chunker;
    private final VectorIndexService vectorIndexService;
    private final PromptModelService promptModelService;

    // Tracks which repos have already been cloned+chunked+indexed in this
    // backend instance.
    private final Map<String, Boolean> loadedRepos = new HashMap<>();

    public RepoChatService(RepoCloneService cloneService,
                           AstCodeChunker chunker,
                           VectorIndexService vectorIndexService,
                           PromptModelService promptModelService) {
        this.cloneService = cloneService;
        this.chunker = chunker;
        this.vectorIndexService = vectorIndexService;
        this.promptModelService = promptModelService;
    }

    /**
     * Handles a chat prompt with the supplied dirty files. Returns a response
     * (currently a placeholder from the model service).
     */
    public Map<String, Object> handlePrompt(String owner, String repo, String defaultBranch,
                                            String prompt, String sessionId, List<DirtyFile> dirtyFiles) {
        // 1. First-prompt check: clone + full index if not already loaded.
        boolean isFirst = !isRepoLoaded(owner, repo);
        if (isFirst) {
            // Clone if not already on disk (defensive), then index everything.
            if (!repoIsCloned(owner, repo)) {
                ensureRepo(owner, repo, defaultBranch);
            }
            List<CodeChunk> allChunks = indexEntireRepo(owner, repo);
            vectorIndexService.indexRepoChunks(owner, repo, allChunks);
            markLoaded(owner, repo);
        }

        // 2. Apply dirty files to the local clone.
        applyDirtyFiles(owner, repo, dirtyFiles);

        // 3. Re-chunk and re-index only the dirty files.
        List<CodeChunk> dirtyChunks = chunkDirtyFiles(owner, repo, dirtyFiles);
        vectorIndexService.replaceFileVectors(owner, repo, dirtyFiles.stream().map(DirtyFile::path).toList());
        vectorIndexService.indexDirtyChunks(owner, repo, dirtyChunks);

        // 4. Get the answer from the model.
        return promptModelService.generate(prompt, owner, repo, sessionId);
    }

    /**
     * Applies an accepted change to the local clone and re-vectorizes the file.
     */
    public void acceptChange(String owner, String repo, String defaultBranch,
                             String filePath, String newContent) {
        if (!repoIsCloned(owner, repo)) {
            ensureRepo(owner, repo, defaultBranch);
        }
        applyDirtyFiles(owner, repo, List.of(new DirtyFile(filePath, newContent)));

        List<CodeChunk> chunks = chunkDirtyFiles(owner, repo, List.of(new DirtyFile(filePath, newContent)));
        vectorIndexService.replaceFileVectors(owner, repo, List.of(filePath));
        vectorIndexService.indexDirtyChunks(owner, repo, chunks);
    }

    private String repoKey(String owner, String repo) {
        return owner + "/" + repo;
    }

    private boolean isRepoLoaded(String owner, String repo) {
        return Boolean.TRUE.equals(loadedRepos.get(repoKey(owner, repo)));
    }

    private void markLoaded(String owner, String repo) {
        loadedRepos.put(repoKey(owner, repo), true);
    }

    private java.io.File ensureRepo(String owner, String repo, String defaultBranch) {
        try {
            return cloneService.cloneRepo(owner, repo, defaultBranch);
        } catch (GitAPIException | IOException e) {
            throw new RuntimeException("Failed to clone repo " + owner + "/" + repo, e);
        }
    }

    private boolean repoIsCloned(String owner, String repo) {
        return cloneService.isCloned(owner, repo);
    }

    private void applyDirtyFiles(String owner, String repo, List<DirtyFile> dirtyFiles) {
        java.io.File root = cloneService.repoDir(owner, repo);
        for (DirtyFile dirty : dirtyFiles) {
            Path target = root.toPath().resolve(dirty.path()).normalize();
            // Prevent path traversal outside the clone.
            Path rootPath = root.toPath().toAbsolutePath().normalize();
            if (!target.toAbsolutePath().normalize().startsWith(rootPath)) {
                throw new IllegalArgumentException("Invalid file path: " + dirty.path());
            }
            try {
                Files.createDirectories(target.getParent());
                Files.writeString(target, dirty.content(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new RuntimeException("Failed to write dirty file " + dirty.path(), e);
            }
        }
    }

    private List<CodeChunk> indexEntireRepo(String owner, String repo) {
        java.io.File root = cloneService.repoDir(owner, repo);
        List<CodeChunk> all = new ArrayList<>();
        try (var stream = Files.walk(root.toPath())) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> !p.toString().contains("/.git/"))
                    .forEach(p -> {
                        String rel = root.toPath().relativize(p).toString();
                        String lang = guessLanguage(rel);
                        try {
                            String content = Files.readString(p, StandardCharsets.UTF_8);
                            all.addAll(chunker.chunkFile(new SourceFile(rel, lang, content, owner + "/" + repo, "RAG")));
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException e) {
            throw new RuntimeException("Failed to walk repo " + owner + "/" + repo, e);
        }
        return all;
    }

    private List<CodeChunk> chunkDirtyFiles(String owner, String repo, List<DirtyFile> dirtyFiles) {
        List<CodeChunk> chunks = new ArrayList<>();
        for (DirtyFile dirty : dirtyFiles) {
            String lang = guessLanguage(dirty.path());
            chunks.addAll(chunker.chunkFile(new SourceFile(dirty.path(), lang, dirty.content(), owner + "/" + repo, "RAG")));
        }
        return chunks;
    }

    private String guessLanguage(String path) {
        String ext = path.substring(path.lastIndexOf('.') + 1).toLowerCase();
        return switch (ext) {
            case "java" -> "java";
            case "py" -> "python";
            case "js", "jsx" -> "javascript";
            case "ts", "tsx" -> "typescript";
            default -> "text";
        };
    }
}
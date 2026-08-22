package com.mecklon.RagPoweredCodingAssistant.retrieval;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mecklon.RagPoweredCodingAssistant.chunker.AstCodeChunker;
import com.mecklon.RagPoweredCodingAssistant.chunker.CodeChunk;
import com.mecklon.RagPoweredCodingAssistant.chunker.SourceFile;
import com.mecklon.RagPoweredCodingAssistant.rag.VectorIndexService;
import com.mecklon.RagPoweredCodingAssistant.workspace.WorkspaceService;

/**
 * Indexing capability: re-chunks and re-embeds files after modification. The
 * strategy is intentionally simple — when a file changes, delete its old
 * vectors, read the complete updated file, AST-chunk it, embed, and insert.
 * No incremental chunk-level updates yet.
 */
@Service
public class IndexingService {

    private static final Logger log = LoggerFactory.getLogger(IndexingService.class);

    private final AstCodeChunker chunker;
    private final VectorIndexService vectorIndexService;
    private final WorkspaceService workspaceService;

    public IndexingService(AstCodeChunker chunker,
                           VectorIndexService vectorIndexService,
                           WorkspaceService workspaceService) {
        this.chunker = chunker;
        this.vectorIndexService = vectorIndexService;
        this.workspaceService = workspaceService;
    }

    /** Indexes the entire repo (used on first load). */
    public void indexRepo(String owner, String repo) {
        log.info("[INDEX] indexRepo {}/{} starting", owner, repo);
        Path root = workspaceService.repoRoot(owner, repo);
        List<CodeChunk> all = new ArrayList<>();
        try (var stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> !p.toString().contains("/.git/"))
                    .forEach(p -> {
                        String rel = root.relativize(p).toString().replace('\\', '/');
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
        vectorIndexService.indexRepoChunks(owner, repo, all);
        log.info("[INDEX] indexRepo {}/{} complete ({}) chunks", owner, repo, all.size());
    }

    /**
     * Re-indexes a single file: delete its old vectors, re-chunk the complete
     * updated file, embed, and insert.
     */
    public void reindexFile(String owner, String repo, String path) {
        String content = workspaceService.readFile(owner, repo, path);
        String lang = guessLanguage(path);
        List<CodeChunk> chunks = chunker.chunkFile(new SourceFile(path, lang, content, owner + "/" + repo, "RAG"));
        vectorIndexService.replaceFileVectors(owner, repo, List.of(path));
        vectorIndexService.indexDirtyChunks(owner, repo, chunks);
        log.debug("[INDEX] reindexFile {} ({} chunks)", path, chunks.size());
    }

    /** Re-indexes all currently dirty files and clears the dirty set. */
    public void reindexDirtyFiles(String owner, String repo) {
        for (String path : workspaceService.dirtyFiles(owner, repo)) {
            reindexFile(owner, repo, path);
        }
        workspaceService.clearDirty(owner, repo);
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
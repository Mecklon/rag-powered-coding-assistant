package com.mecklon.RagPoweredCodingAssistant.rag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import com.mecklon.RagPoweredCodingAssistant.chunker.ChunkMetadata;
import com.mecklon.RagPoweredCodingAssistant.chunker.CodeChunk;

/**
 * Vector database integration: embeds code chunks and stores them in pgvector,
 * replaces vectors for changed files, and retrieves the k most similar chunks
 * for a prompt.
 */
@Service
public class VectorIndexService {

    private final VectorStore vectorStore;

    public VectorIndexService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /**
     * Index a whole repo's chunks into the vector DB.
     */
    public void indexRepoChunks(String owner, String repo, List<CodeChunk> chunks) {
        vectorStore.add(toDocuments(chunks));
    }

    /**
     * Index only the dirty files' chunks into the vector DB.
     */
    public void indexDirtyChunks(String owner, String repo, List<CodeChunk> chunks) {
        vectorStore.add(toDocuments(chunks));
    }

    /**
     * Remove the existing vectors for the given file paths and re-add their
     * updated chunks.
     */
    public void replaceFileVectors(String owner, String repo, List<String> filePaths) {
        if (filePaths == null || filePaths.isEmpty()) {
            return;
        }
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        // Scope the delete to this repo so identical file paths in other repos
        // are never affected.
        var condition = b.and(
                b.eq("repositoryId", owner + "/" + repo),
                b.in("filePath", filePaths)
        );
        vectorStore.delete(condition.build());
    }

    /**
     * Retrieves the k most similar chunks for the given prompt, scoped to the
     * repo.
     */
    public List<Document> retrieveSimilar(String owner, String repo, String prompt, int k) {
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        SearchRequest request = SearchRequest.builder()
                .query(prompt)
                .topK(k)
                .filterExpression(b.eq("repositoryId", owner + "/" + repo).build())
                .build();
        return vectorStore.similaritySearch(request);
    }

    /**
     * Maps CodeChunks to Spring AI Documents, carrying the chunk metadata.
     */
    private List<Document> toDocuments(List<CodeChunk> chunks) {
        List<Document> docs = new ArrayList<>();
        for (CodeChunk chunk : chunks) {
            ChunkMetadata m = chunk.getMetadata();
            Map<String, Object> metadata = new HashMap<>();
            putIfNotNull(metadata, "repositoryId", m.getRepositoryId());
            putIfNotNull(metadata, "branch", m.getBranch());
            putIfNotNull(metadata, "filePath", m.getFilePath());
            putIfNotNull(metadata, "language", m.getLanguage());
            putIfNotNull(metadata, "startLine", m.getStartLine());
            putIfNotNull(metadata, "endLine", m.getEndLine());
            putIfNotNull(metadata, "startColumn", m.getStartColumn());
            putIfNotNull(metadata, "endColumn", m.getEndColumn());
            putIfNotNull(metadata, "nodeType", m.getNodeType());
            putIfNotNull(metadata, "symbolName", m.getSymbolName());
            putIfNotNull(metadata, "parentSymbol", m.getParentSymbol());
            putIfNotNull(metadata, "symbolType", m.getSymbolType());
            putIfNotNull(metadata, "packageName", m.getPackageName());
            putIfNotNull(metadata, "className", m.getClassName());
            putIfNotNull(metadata, "module", m.getModule());
            putIfNotNull(metadata, "chunkId", m.getChunkId());
            docs.add(new Document(chunk.getContent(), metadata));
        }
        return docs;
    }

    private void putIfNotNull(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }
}
package com.mecklon.RagPoweredCodingAssistant.retrieval;

import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import com.mecklon.RagPoweredCodingAssistant.rag.VectorIndexService;

/**
 * Knowledge retrieval capability, decoupled from the chat orchestration. The
 * agent decides when it needs additional context and calls the search_code tool,
 * which delegates here. This is the semantic search path.
 */
@Service
public class RetrievalService {

    private final VectorIndexService vectorIndexService;

    public RetrievalService(VectorIndexService vectorIndexService) {
        this.vectorIndexService = vectorIndexService;
    }

    /**
     * Semantic search over the repo's vector index. Returns the k most similar
     * chunks to the query, scoped to the repo.
     */
    public List<Document> semanticSearch(String owner, String repo, String query, int k) {
        return vectorIndexService.retrieveSimilar(owner, repo, query, k);
    }
}
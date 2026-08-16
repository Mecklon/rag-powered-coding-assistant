package com.mecklon.RagPoweredCodingAssistant.rag;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.document.Document;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.stereotype.Service;

/**
 * Real model call. Retrieves relevant chunks, builds a role-aware prompt
 * template, keeps conversation memory via an advisor (keyed by sessionId), and
 * parses the structured output (ChangeResponse) with BeanOutputConverter.
 */
@Service
public class PromptModelService {

    private final VectorIndexService vectorIndexService;
    private final ChatClient chatClient;

    private static final String SYSTEM_TEMPLATE = """
            You are an expert software developer with 10+ years of experience across
            multiple languages and paradigms. You are working inside an AI-assisted
            IDE on the repository {owner}/{repo}.

            You have access to the most relevant code chunks from the repository.
            Analyze the user's request carefully and make precise, minimal, idiomatic
            changes when the user asks you to modify code.

            CRITICAL — decide based on the user's intent:
            - If the user is ASKING A QUESTION, REQUESTING A SUMMARY/EXPLANATION, or
              otherwise does NOT ask you to modify code, set the "changes" array to an
              empty list ([]). Answer only in the "summary" field.
            - ONLY when the user explicitly asks you to edit, refactor, fix, or generate
              code changes should you populate the "changes" array.
            - Never fabricate diffs for non-code-change requests.

            When proposing a change, output the FULL new file content for each file you modify,
            including the file path and the full old content.

            IMPORTANT: Return ONLY a JSON object that matches the required schema exactly.
            """;

    private static final String CONTEXT_TEMPLATE = """
            Here are the most relevant code snippets from the repository:

            {chunks}

            User request:
            {prompt}
            """;

    public PromptModelService(VectorIndexService vectorIndexService,
                              ChatClient.Builder chatClientBuilder,
                              ChatMemory chatMemory) {
        this.vectorIndexService = vectorIndexService;
        this.chatClient = chatClientBuilder
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    public Map<String, Object> generate(String prompt, String owner, String repo, String sessionId) {
        int k = 10;
        List<Document> relevant = vectorIndexService.retrieveSimilar(owner, repo, prompt, k);

        String chunks = relevant.stream()
                .map(d -> "--- " + d.getMetadata().getOrDefault("filePath", "unknown") + " [lines "
                        + d.getMetadata().getOrDefault("startLine", "?") + "-"
                        + d.getMetadata().getOrDefault("endLine", "?") + "] ---\n" + d.getText())
                .collect(Collectors.joining("\n\n"));

        PromptTemplate contextTemplate = new PromptTemplate(CONTEXT_TEMPLATE);
        contextTemplate.add("chunks", chunks);
        contextTemplate.add("prompt", prompt);

        BeanOutputConverter<ChangeResponse> converter = new BeanOutputConverter<>(ChangeResponse.class);

        String userMessage = contextTemplate.render() + "\n\n" +
                "Return a response as JSON that matches the schema:\n" + converter.getFormat();

        ChangeResponse response = chatClient.prompt()
                .system(s -> s.text(SYSTEM_TEMPLATE).param("owner", owner).param("repo", repo))
                .user(userMessage)
                // Conversation memory keyed by the frontend-provided sessionId.
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
                .call()
                .entity(converter);

        if (response == null) {
            response = new ChangeResponse();
            response.setSummary("The model returned no structured response.");
            response.setChanges(List.of());
        }

        return Map.of(
                "summary", response.getSummary(),
                "changes", response.getChanges() == null ? List.of() : response.getChanges()
        );
    }
}
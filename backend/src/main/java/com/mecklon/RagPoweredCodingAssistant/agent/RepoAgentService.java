package com.mecklon.RagPoweredCodingAssistant.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.mecklon.RagPoweredCodingAssistant.rag.RepoCloneService;
import com.mecklon.RagPoweredCodingAssistant.retrieval.IndexingService;
import com.mecklon.RagPoweredCodingAssistant.tools.AgentToolService;
import com.mecklon.RagPoweredCodingAssistant.workspace.FileChange;
import com.mecklon.RagPoweredCodingAssistant.workspace.WorkspaceService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The agent orchestration layer. The agent is a controller around the LLM, not
 * the LLM itself. It runs the tool-calling loop: call the LLM, if it requests a
 * tool, execute it deterministically, feed the result back, and repeat until the
 * task is complete or the iteration limit is reached.
 *
 * <p>Uses user-controlled tool execution (ChatModel + ToolCallingManager) so
 * that intermediate tool events can be streamed to the frontend via SSE.
 */
@Service
public class RepoAgentService {

    private static final Logger log = LoggerFactory.getLogger(RepoAgentService.class);

    private final ChatModel chatModel;
    private final ToolCallingManager toolCallingManager;
    private final AgentToolService agentToolService;
    private final WorkspaceService workspaceService;
    private final IndexingService indexingService;
    private final RepoCloneService cloneService;

    @Value("${app.agent.max-iterations:10}")
    private int maxIterations;

    @Value("${app.agent.initial-context-k:5}")
    private int initialContextK;

    private static final String SYSTEM_TEMPLATE = """
            You are an expert software developer working inside an AI-assisted IDE on the
            repository {owner}/{repo}.

            You have access to a set of deterministic tools. Decide which tool to call based
            on the user's request. You may call tools in any order and as many times as needed.

            Guidelines:
            - Use list_files to understand the repository structure.
            - Use search_code_semantic to find relevant code by meaning, and search_code_regex
              to find exact text or symbols.
            - Use read_file to read the complete current content of a file (authoritative).
            - Use create_file / edit_file / delete_file / rename_file to modify files.
            - Make precise, minimal, idiomatic changes.
            - When you have completed the task, produce a concise final summary of what you did.

            IMPORTANT: You are the decision maker. The backend executes the tools. Do not
            fabricate file contents — always read files before editing them.
            """;

    public RepoAgentService(ChatModel chatModel,
                            ToolCallingManager toolCallingManager,
                            AgentToolService agentToolService,
                            WorkspaceService workspaceService,
                            IndexingService indexingService,
                            RepoCloneService cloneService) {
        this.chatModel = chatModel;
        this.toolCallingManager = toolCallingManager;
        this.agentToolService = agentToolService;
        this.workspaceService = workspaceService;
        this.indexingService = indexingService;
        this.cloneService = cloneService;
    }

    /**
     * Runs the agent loop for a user request, streaming intermediate tool events
     * to the frontend via SSE.
     */
    public void run(String owner, String repo, String defaultBranch, String prompt,
                    String sessionId, SseEmitter emitter) {
        AgentState state = new AgentState(owner, repo, sessionId, prompt);
        log.info("[agent] run start | owner={} repo={} session={} prompt=\"{}\"", owner, repo, sessionId, prompt);

        // Ensure the repo is cloned and indexed on first use.
        ensureRepoReady(owner, repo, defaultBranch);

        // Build the tool callbacks and tool context (owner/repo not sent to model).
        ToolCallback[] tools = ToolCallbacks.from(agentToolService);
        Map<String, Object> toolContext = Map.of("owner", owner, "repo", repo);
        // Use the provider-native GoogleGenAiChatOptions so the underlying model
        // can bind tools (Generic options cause a ClassCastException in the model).
        ToolCallingChatOptions chatOptions = GoogleGenAiChatOptions.builder()
                .toolCallbacks(tools)
                .toolContext(toolContext)
                .model("gemini-3.5-flash")
                .build();
        log.debug("[AGENT] registered {} tools", tools.length);

        // Initial message list: system + user request.
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(SYSTEM_TEMPLATE
                .replace("{owner}", owner)
                .replace("{repo}", repo)));
        messages.add(new UserMessage(prompt));

        Prompt promptObj = new Prompt(messages, chatOptions);

        try {
            while (!state.finished() && state.iteration() < maxIterations) {
                state.incrementIteration();
                log.info("[AGENT] iteration {}/{} | awaiting model reply", state.iteration(), maxIterations);

                ChatResponse response = chatModel.call(promptObj);

                if (response == null || !response.hasToolCalls()) {
                    // Final answer.
                    String finalText = response != null && response.getResult() != null
                            ? response.getResult().getOutput().getText()
                            : "No response.";
                    log.info("[AGENT] iteration {} | FINAL ANSWER: {}", state.iteration(), finalText);
                    emit(emitter, Map.of("type", "DONE", "summary", finalText));
                    state.setFinished(true);
                    break;
                }

                // Emit a TOOL_START event for each requested tool.
                for (var generation : response.getResults()) {
                    var toolCalls = generation.getOutput().getToolCalls();
                    if (toolCalls == null) continue;
                    for (var toolCall : toolCalls) {
                        log.info("[AGENT] iteration {} | TOOL_CALL request: name={} args={}", state.iteration(), toolCall.name(), toolCall.arguments());
                        emit(emitter, Map.of(
                                "type", "TOOL_START",
                                "tool", toolCall.name(),
                                "message", "Executing " + toolCall.name() + "..."
                        ));
                    }
                }

                // Execute the requested tools deterministically.
                ToolExecutionResult result = toolCallingManager.executeToolCalls(promptObj, response);
                log.info("[AGENT] iteration {} | TOOL_EXECUTION complete", state.iteration());

                // Record any file changes made by the tools.
                for (FileChange change : workspaceService.drainChanges(owner, repo)) {
                    state.addFileChange(change);
                    log.info("[AGENT] file change {} | {}({})", change.operation(), change.path(), change.after());
                }

                // Feed the tool results back to the model.
                promptObj = new Prompt(result.conversationHistory(), chatOptions);
            }

            if (!state.finished()) {
                log.warn("[AGENT] termination | reached max iterations {}", maxIterations);
                emit(emitter, Map.of(
                        "type", "DONE",
                        "summary", "Reached the maximum number of iterations (" + maxIterations + ")."
                ));
            }

            // Re-index all dirty files after the agent run.
            log.info("[AGENT] re-indexing dirty files for {}/{}", owner, repo);
            indexingService.reindexDirtyFiles(owner, repo);

        } catch (Exception e) {
            log.error("[AGENT] run failed | error={}", e.getMessage(), e);
            emit(emitter, Map.of("type", "ERROR", "message", e.getMessage()));
        } finally {
            log.info("[AGENT] run end | owner={} repo={} session={}", owner, repo, sessionId);
            emitter.complete();
        }
    }

    private void ensureRepoReady(String owner, String repo, String defaultBranch) {
        if (!cloneService.isCloned(owner, repo)) {
            log.info("[AGENT] repo not cloned, cloning {}/{}", owner, repo);
            try {
                cloneService.cloneRepo(owner, repo, defaultBranch);
                log.info("[AGENT] repo cloned {}/{}", owner, repo);
            } catch (Exception e) {
                throw new RuntimeException("Failed to clone repo " + owner + "/" + repo, e);
            }
        }
        // Index the repo if not yet indexed (tracked in-memory).
        if (!indexedRepos.contains(owner + "/" + repo)) {
            log.info("[AGENT] indexing repo {}/{}", owner, repo);
            indexingService.indexRepo(owner, repo);
            indexedRepos.add(owner + "/" + repo);
            log.info("[AGENT] indexed repo {}/{}", owner, repo);
        }
    }

    private final java.util.Set<String> indexedRepos = new java.util.HashSet<>();

    private void emit(SseEmitter emitter, Map<String, Object> data) {
        try {
            emitter.send(SseEmitter.event().data(data));
        } catch (Exception e) {
            // Client disconnected; stop emitting.
            throw new RuntimeException("Failed to emit SSE event", e);
        }
    }
}
package com.mecklon.RagPoweredCodingAssistant.tools;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import com.mecklon.RagPoweredCodingAssistant.retrieval.RetrievalService;
import com.mecklon.RagPoweredCodingAssistant.workspace.FileChange;
import com.mecklon.RagPoweredCodingAssistant.workspace.WorkspaceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The deterministic tool layer exposed to the LLM via Spring AI @Tool function
 * calling. Tools validate/guard the request and delegate to the workspace and
 * retrieval services — they never touch the filesystem or vector store
 * directly.
 *
 * <p>The repo identity (owner/repo) is passed through {@link ToolContext} so
 * it is never exposed to the model.
 */
@Service
public class AgentToolService {

    private static final Logger log = LoggerFactory.getLogger(AgentToolService.class);

    private final WorkspaceService workspaceService;
    private final RetrievalService retrievalService;

    public AgentToolService(WorkspaceService workspaceService, RetrievalService retrievalService) {
        this.workspaceService = workspaceService;
        this.retrievalService = retrievalService;
    }

    // ─────────────────────────────────────────────
    // READ-ONLY TOOLS
    // ─────────────────────────────────────────────

    @Tool(description = "List the files in the repository. Returns relative paths. Use this to understand the repository structure.")
    public String listFiles(ToolContext context) {
        String owner = owner(context);
        String repo = repo(context);
        log.info("[TOOL] list_files called | repo={}/{}", owner, repo);
        List<String> files = workspaceService.listFiles(owner, repo);
        if (files.isEmpty()) {
            return "Repository is empty.";
        }
        return files.stream().collect(Collectors.joining("\n"));
    }

    @Tool(description = "Read the complete current content of a file from the workspace. This is the authoritative current source (unlike search results which may be slightly stale).")
    public String readFile(
            @ToolParam(description = "Path of the file to read, relative to the repository root") String path,
            ToolContext context) {
        String owner = owner(context);
        String repo = repo(context);
        log.info("[TOOL] read_file called path={}", path);
        return workspaceService.readFile(owner, repo, path);
    }

    @Tool(description = "Semantic search over the codebase. Use when you need to find relevant code by meaning or natural-language description. Returns the most similar code chunks with file paths and line ranges.")
    public String searchCodeSemantic(
            @ToolParam(description = "Natural-language query describing what you are looking for") String query,
            @ToolParam(description = "Number of results to return (default 5)", required = false) Integer k,
            ToolContext context) {
        String owner = owner(context);
        String repo = repo(context);
        int topK = (k == null || k < 1) ? 5 : k;
        log.info("[tool] search_code_semantic query=\"{}\" k={}", query, topK);
        List<Document> docs = retrievalService.semanticSearch(owner, repo, query, topK);
        if (docs.isEmpty()) {
            return "No semantically similar code found.";
        }
        return docs.stream()
                .map(d -> "--- " + d.getMetadata().getOrDefault("filePath", "unknown")
                        + " [lines " + d.getMetadata().getOrDefault("startLine", "?")
                        + "-" + d.getMetadata().getOrDefault("endLine", "?") + "] ---\n" + d.getText())
                .collect(Collectors.joining("\n\n"));
    }

    @Tool(description = "Regex/text search over the workspace files. Use when you need to find exact text, symbols, or patterns in the current files. Returns matching file paths and line numbers.")
    public String searchCodeRegex(
            @ToolParam(description = "Regular expression or literal text to search for") String pattern,
            @ToolParam(description = "Optional file path to restrict the search to", required = false) String path,
            ToolContext context) {
        String owner = owner(context);
        String repo = repo(context);
        log.info("[tool] search_code_regex pattern={} path={}", pattern, path);
        Pattern regex;
        try {
            regex = Pattern.compile(pattern);
        } catch (Exception e) {
            return "Invalid regex pattern: " + e.getMessage();
        }
        List<String> files = path != null && !path.isBlank()
                ? List.of(path)
                : workspaceService.listFiles(owner, repo);
        StringBuilder sb = new StringBuilder();
        for (String file : files) {
            String content;
            try {
                content = workspaceService.readFile(owner, repo, file);
            } catch (Exception e) {
                continue;
            }
            String[] lines = content.split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                if (regex.matcher(lines[i]).find()) {
                    sb.append(file).append(":").append(i + 1).append(": ").append(lines[i].trim()).append("\n");
                }
            }
        }
        return sb.length() == 0 ? "No matches found." : sb.toString();
    }

    // ─────────────────────────────────────────────
    // LOW-RISK WRITE TOOLS
    // ─────────────────────────────────────────────

    @Tool(description = "Create a new file with the given content. Fails if the file already exists.")
    public String createFile(
            @ToolParam(description = "Path of the new file, relative to the repository root") String path,
            @ToolParam(description = "Full content of the new file") String content,
            ToolContext context) {
        String owner = owner(context);
        String repo = repo(context);
        log.info("[tool] create_file path={}", path);
        FileChange change = workspaceService.createFile(owner, repo, path, content);
        workspaceService.markDirty(owner, repo, path);
        return "Created " + path;
    }

    @Tool(description = "Apply a granular edit to an existing file: replace oldText with newText. The oldText must appear exactly once in the file.")
    public String editFile(
            @ToolParam(description = "Path of the file to edit, relative to the repository root") String path,
            @ToolParam(description = "The exact existing text to replace (must be unique in the file)") String oldText,
            @ToolParam(description = "The new text to substitute") String newText,
            ToolContext context) {
        String owner = owner(context);
        String repo = repo(context);        log.info("[tool] edit_file path={}", path);        FileChange change = workspaceService.editFile(owner, repo, path, oldText, newText);
        workspaceService.markDirty(owner, repo, path);
        return "Edited " + path;
    }

    // ─────────────────────────────────────────────
    // PRIVILEGED / DESTRUCTIVE TOOLS
    // ─────────────────────────────────────────────

    @Tool(description = "Delete a file from the repository. This is destructive and irreversible.")
    public String deleteFile(
            @ToolParam(description = "Path of the file to delete, relative to the repository root") String path,
            ToolContext context) {
        String owner = owner(context);
        String repo = repo(context);
        log.info("[tool] delete_file path={}", path);
        FileChange change = workspaceService.deleteFile(owner, repo, path);
        workspaceService.markDirty(owner, repo, path);
        return "Deleted: " + path;
    }

    @Tool(description = "Rename or move a file within the repository.")
    public String renameFile(
            @ToolParam(description = "Current path of the file, relative to the repository root") String source,
            @ToolParam(description = "New path of the file, relative to the repository root") String destination,
            ToolContext context) {
        String owner = owner(context);
        String repo = repo(context);
        log.info("[tool] rename_file {} -> {}", source, destination);
        FileChange change = workspaceService.renameFile(owner, repo, source, destination);
        workspaceService.markDirty(owner, repo, source);
        workspaceService.markDirty(owner, repo, destination);
        return "Renamed " + source + " -> " + destination;
    }

    // ─────────────────────────────────────────────
    // GIT / GITHUB PLACEHOLDERS
    // ─────────────────────────────────────────────

    @Tool(description = "Show the current git status of the workspace (modified files). Placeholder for future git integration.")
    public String gitStatus(ToolContext context) {
        String owner = owner(context);
        String repo = repo(context);
        log.info("[tool] git_status called | repo={}/{}", owner, repo);
        List<String> dirty = workspaceService.dirtyFiles(owner, repo).stream().toList();
        return dirty.isEmpty() ? "Working tree clean." : "Modified files:\n" + String.join("\n", dirty);
    }

    @Tool(description = "Generate a commit message for the current changes. Placeholder for future git integration.")
    public String commit(
            @ToolParam(description = "Commit message describing the changes") String message,
            ToolContext context) {
        log.info("[tool] commit called (placeholder) message=\"{}\"", message);
        return "Commit staged (placeholder): " + message;
    }

    @Tool(description = "Push the current branch to the remote. Placeholder for future git integration.")
    public String push(ToolContext context) {
        log.info("[tool] push called (placeholder)");
        return "Push executed (placeholder).";
    }

    // ─────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────

    private String owner(ToolContext context) {
        return (String) context.getContext().get("owner");
    }

    private String repo(ToolContext context) {
        return (String) context.getContext().get("repo");
    }
}
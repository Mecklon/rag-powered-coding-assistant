package com.mecklon.RagPoweredCodingAssistant.github;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import lombok.RequiredArgsConstructor;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class GithubClientService {

    private static final String GITHUB_API = "https://api.github.com";
    private static final String USER_AGENT = "RagPoweredCodingAssistant";
    private static final String REPOS_URI = GITHUB_API + "/user/repos?per_page=100&sort=updated";
    private static final String RAG_BRANCH_PREFIX = "MecklonsRAGIDE.";

    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;

    /**
     * Fetches the repositories of the authenticated GitHub user. The response
     * is large and detailed, so only the needed fields are mapped into a
     * lightweight GithubRepository DTO. Nothing is persisted.
     */
    public List<GithubRepository> getUserRepositories(String accessToken) {
        String body = restClientBuilder.build()
                .get()
                .uri(REPOS_URI)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header("User-Agent", USER_AGENT)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(String.class);

        List<GithubRepository> repos = new ArrayList<>();
        if (body == null || body.isBlank()) {
            return repos;
        }

        try {
            JsonNode root = objectMapper.readTree(body);
            if (root.isArray()) {
                for (JsonNode node : root) {
                    repos.add(GithubRepository.fromJson(node));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse GitHub repositories response", e);
        }
        return repos;
    }

    /**
     * Checks whether the given repository already has a RAG IDE branch
     * (a branch whose name starts with {@value #RAG_BRANCH_PREFIX}).
     */
    public boolean hasRagBranch(String accessToken, String owner, String repo) {
        String body = restClientBuilder.build()
                .get()
                .uri(GITHUB_API + "/repos/{owner}/{repo}/branches", owner, repo)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header("User-Agent", USER_AGENT)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(String.class);

        if (body == null || body.isBlank()) {
            return false;
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            if (root.isArray()) {
                for (JsonNode node : root) {
                    String name = node.path("name").asText(null);
                    if (name != null && name.startsWith(RAG_BRANCH_PREFIX)) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse GitHub branches response", e);
        }
        return false;
    }

    /**
     * Creates a RAG IDE branch in the given repository, branching off the
     * repository's default branch. Returns the created branch name.
     */
    public String createRagBranch(String accessToken, String owner, String repo) {
        // 1. Get the default branch and its head SHA.
        String repoBody = restClientBuilder.build()
                .get()
                .uri(GITHUB_API + "/repos/{owner}/{repo}", owner, repo)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header("User-Agent", USER_AGENT)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(String.class);

        String defaultBranch;
        String headSha;
        try {
            JsonNode repoNode = objectMapper.readTree(repoBody);
            defaultBranch = repoNode.path("default_branch").asText("main");
            String branchUri = GITHUB_API + "/repos/" + owner + "/" + repo + "/branches/" + defaultBranch;
            String branchBody = restClientBuilder.build()
                    .get()
                    .uri(branchUri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .header("User-Agent", USER_AGENT)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(String.class);
            headSha = objectMapper.readTree(branchBody).path("commit").path("sha").asText(null);
        } catch (Exception e) {
            throw new RuntimeException("Failed to resolve default branch for " + owner + "/" + repo, e);
        }

        if (headSha == null) {
            throw new RuntimeException("Could not resolve head SHA for default branch " + defaultBranch);
        }

        // 2. Create the branch via the git refs API.
        String branchName = RAG_BRANCH_PREFIX + defaultBranch;
        String createBody = "{\"ref\":\"refs/heads/" + branchName + "\",\"sha\":\"" + headSha + "\"}";

        if (hasRagBranch(accessToken, owner, repo)) {
            // Branch already exists (possibly stale). Force-update it to the
            // current default branch head so it tracks the latest code.
            String updateBody = "{\"sha\":\"" + headSha + "\",\"force\":true}";
            restClientBuilder.build()
                    .patch()
                    .uri(GITHUB_API + "/repos/{owner}/{repo}/git/refs/heads/{branch}", owner, repo, branchName)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .header("User-Agent", USER_AGENT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(updateBody)
                    .retrieve()
                    .toBodilessEntity();
        } else {
            restClientBuilder.build()
                    .post()
                    .uri(GITHUB_API + "/repos/{owner}/{repo}/git/refs", owner, repo)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .header("User-Agent", USER_AGENT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(createBody)
                    .retrieve()
                    .toBodilessEntity();
        }

        return branchName;
    }

    /**
     * Fetches the recursive file tree of the given repository's RAG IDE branch
     * (falling back to the default branch). Returns a list of file paths.
     */
    public List<String> getFileTree(String accessToken, String owner, String repo) {
        String branch = resolveRagBranchName(accessToken, owner, repo);
        System.out.println("RESOLVED BRANCH: " + branch);

        String body = restClientBuilder.build()
                .get()
                .uri(GITHUB_API + "/repos/{owner}/{repo}/git/trees/{branch}?recursive=1", owner, repo, branch)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header("User-Agent", USER_AGENT)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(String.class);

        List<String> paths = new ArrayList<>();
        if (body == null || body.isBlank()) {
            return paths;
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            System.out.println("TREE truncated=" + root.path("truncated").asBoolean() + " sha=" + root.path("sha").asText());
            JsonNode tree = root.path("tree");
            if (tree.isArray()) {
                for (JsonNode node : tree) {
                    String type = node.path("type").asText(null);
                    String path = node.path("path").asText(null);
                    if ("blob".equals(type) && path != null) {
                        paths.add(path);
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse GitHub file tree response", e);
        }
        System.out.println(paths);
        return paths;
    }

    /**
     * Fetches the raw content of a single file from the given repository's RAG
     * IDE branch (falling back to the default branch).
     */
    public String getFileContent(String accessToken, String owner, String repo, String path) {
        String branch = resolveRagBranchName(accessToken, owner, repo);

        return restClientBuilder.build()
                .get()
                .uri(GITHUB_API + "/repos/{owner}/{repo}/contents/{path}?ref={branch}", owner, repo, path, branch)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header("User-Agent", USER_AGENT)
                // Request the raw file body (plain text), not the JSON object
                // with base64-encoded content.
                .accept(MediaType.parseMediaType("application/vnd.github.raw"))
                .retrieve()
                .body(String.class);
    }

    /**
     * Resolves the RAG IDE branch name for a repo, or falls back to the
     * default branch if no RAG branch exists yet.
     */
    private String resolveRagBranchName(String accessToken, String owner, String repo) {
        String repoBody = restClientBuilder.build()
                .get()
                .uri(GITHUB_API + "/repos/{owner}/{repo}", owner, repo)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header("User-Agent", USER_AGENT)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(String.class);

        String defaultBranch;
        try {
            defaultBranch = objectMapper.readTree(repoBody).path("default_branch").asText("main");
        } catch (Exception e) {
            throw new RuntimeException("Failed to resolve default branch for " + owner + "/" + repo, e);
        }

        if (hasRagBranch(accessToken, owner, repo)) {
            return RAG_BRANCH_PREFIX + defaultBranch;
        }
        return defaultBranch;
    }
}
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
    private static final String RAG_BRANCH_PREFIX = "MecklonsRAGIDE/";

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

        restClientBuilder.build()
                .post()
                .uri(GITHUB_API + "/repos/{owner}/{repo}/git/refs", owner, repo)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header("User-Agent", USER_AGENT)
                .contentType(MediaType.APPLICATION_JSON)
                .body(createBody)
                .retrieve()
                .toBodilessEntity();

        return branchName;
    }
}
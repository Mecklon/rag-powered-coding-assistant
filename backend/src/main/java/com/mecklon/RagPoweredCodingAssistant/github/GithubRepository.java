package com.mecklon.RagPoweredCodingAssistant.github;

/**
 * DTO representing a GitHub repository. Only non-sensitive metadata is exposed
 * to the frontend. Fetched fresh from the GitHub API on every request - never
 * persisted in our database.
 */
public record GithubRepository(
        Long id,
        String name,
        String fullName,
        String description,
        String htmlUrl,
        String language,
        String defaultBranch,
        boolean isPrivate,
        boolean fork,
        boolean archived
) {

    public static GithubRepository fromJson(tools.jackson.databind.JsonNode repo) {
        return new GithubRepository(
                repo.path("id").asLong(),
                repo.path("name").asText(null),
                repo.path("full_name").asText(null),
                repo.path("description").asText(null),
                repo.path("html_url").asText(null),
                repo.path("language").asText(null),
                repo.path("default_branch").asText(null),
                repo.path("private").asBoolean(false),
                repo.path("fork").asBoolean(false),
                repo.path("archived").asBoolean(false)
        );
    }
}
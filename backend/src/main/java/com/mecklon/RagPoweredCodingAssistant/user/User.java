package com.mecklon.RagPoweredCodingAssistant.user;

import java.time.LocalDateTime;

/**
 * Plain data model describing the users table row.
 * Persisted via JDBC (spring-jdbc) using JdbcTemplate + RowMapper.
 */
public class User {

    private Long id;
    private String githubId;
    private String login;
    private String name;
    private String email;
    private String avatarUrl;
    private String accessToken;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public User() {
    }

    public User(Long id, String githubId, String login, String name, String email,
                String avatarUrl, String accessToken, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.githubId = githubId;
        this.login = login;
        this.name = name;
        this.email = email;
        this.avatarUrl = avatarUrl;
        this.accessToken = accessToken;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getGithubId() {
        return githubId;
    }

    public void setGithubId(String githubId) {
        this.githubId = githubId;
    }

    public String getLogin() {
        return login;
    }

    public void setLogin(String login) {
        this.login = login;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
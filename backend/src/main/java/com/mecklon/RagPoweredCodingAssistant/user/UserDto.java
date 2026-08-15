package com.mecklon.RagPoweredCodingAssistant.user;

/**
 * DTO sent to the frontend. Contains only non-critical data.
 * Notably the OAuth access token is never exposed.
 */
public class UserDto {

    private Long id;
    private String githubId;
    private String login;
    private String name;
    private String email;
    private String avatarUrl;

    public UserDto() {
    }

    public UserDto(Long id, String githubId, String login, String name,
                   String email, String avatarUrl) {
        this.id = id;
        this.githubId = githubId;
        this.login = login;
        this.name = name;
        this.email = email;
        this.avatarUrl = avatarUrl;
    }

    public static UserDto from(User user) {
        return new UserDto(
                user.getId(),
                user.getGithubId(),
                user.getLogin(),
                user.getName(),
                user.getEmail(),
                user.getAvatarUrl()
        );
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
}